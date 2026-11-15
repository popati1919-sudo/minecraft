package ca.larperscoin.smp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

final class LarpersWebPanel {
    private final JavaPlugin plugin;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private HttpServer server;
    private final SecureRandom random = new SecureRandom();
    private final java.util.Deque<String> liveEvents = new java.util.ArrayDeque<>();
    private Handler liveHandler;
    private synchronized void addLiveEvent(String message) {
        liveEvents.addLast(Instant.now() + " " + message);
        while (liveEvents.size() > 300) liveEvents.removeFirst();
    }
    private synchronized String liveEventsJson() {
        StringBuilder out = new StringBuilder("{\"events\":[");
        boolean first = true;
        for (String event : liveEvents) {
            if (!first) out.append(',');
            first = false;
            out.append("\"").append(escape(event)).append("\"");
        }
        return out.append("]}").toString();
    }

    LarpersWebPanel(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        String password = System.getenv("LARPERS_PANEL_PASSWORD");
        if (password == null || password.length() < 16) {
            plugin.getLogger().warning("Panel web désactivé: définis LARPERS_PANEL_PASSWORD (16 caractères minimum).");
            return;
        }
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("LARPERS_PANEL_PORT", "8765"));
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
            server.setExecutor(Executors.newFixedThreadPool(6));
            server.createContext("/", this::handle);
            liveHandler = new Handler() {
                @Override public void publish(LogRecord record) {
                    if (record != null && isLoggable(record)) addLiveEvent(record.getLevel() + " " + record.getLoggerName() + ": " + record.getMessage());
                }
                @Override public void flush() { }
                @Override public void close() { }
            };
            Bukkit.getLogger().addHandler(liveHandler);
            addLiveEvent("Panneau de contrôle connecté au journal du serveur.");
            server.start();
            plugin.getLogger().info("Panel web actif sur 127.0.0.1:" + port + " (tunnel HTTPS requis).");
        } catch (Exception ex) {
            plugin.getLogger().severe("Impossible de démarrer le panel web: " + ex.getMessage());
        }
    }

    void stop() {
        if (server != null) server.stop(1);
        if (liveHandler != null) Bukkit.getLogger().removeHandler(liveHandler);
        sessions.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:");
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.equals("/") || path.equals("/index.html")) {
                byte[] page;
                try (var in = plugin.getResource("panel/index.html")) {
                    if (in == null) { send(exchange, 404, "text/plain; charset=utf-8", "Panel UI manquant".getBytes(StandardCharsets.UTF_8)); return; }
                    page = in.readAllBytes();
                }
                send(exchange, 200, "text/html; charset=utf-8", page);
                return;
            }
            if (path.equals("/api/login") && exchange.getRequestMethod().equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String supplied = jsonString(body, "password");
                String expected = System.getenv("LARPERS_PANEL_PASSWORD");
                if (supplied == null || expected == null || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
                    audit("LOGIN_FAIL", exchange);
                    json(exchange, 401, "{\"error\":\"Identifiants invalides\"}");
                    return;
                }
                byte[] bytes = new byte[32]; random.nextBytes(bytes);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                sessions.put(token, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12));
                audit("LOGIN_OK", exchange);
                json(exchange, 200, "{\"token\":\"" + token + "\"}");
                return;
            }
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            if (token == null || !token.startsWith("Bearer ")) { json(exchange, 401, "{\"error\":\"Connexion requise\"}"); return; }
            token = token.substring(7);
            Long expiry = sessions.get(token);
            if (expiry == null || expiry < System.currentTimeMillis()) { sessions.remove(token); json(exchange, 401, "{\"error\":\"Session expirée\"}"); return; }

            if (path.equals("/api/me") && exchange.getRequestMethod().equals("GET")) {
                json(exchange, 200, "{\"ok\":true,\"server\":\"LarpersCoin SMP\"}");
            } else if (path.equals("/api/live") && exchange.getRequestMethod().equals("GET")) {
                String result = onMain(() -> {
                    StringBuilder out = new StringBuilder("{\"playersOnline\":").append(Bukkit.getOnlinePlayers().size())
                            .append(",\"maxPlayers\":").append(Bukkit.getMaxPlayers()).append(",\"events\":");
                    out.append(serverLogsJson());
                    return out.toString();
                });
                json(exchange, 200, result);
            } else if (path.equals("/api/ac/reports") && exchange.getRequestMethod().equals("GET")) {
                File reportFile = plugin.getDataFolder().toPath().resolve("anticheat-reports.yml").toFile();
                YamlConfiguration reports = YamlConfiguration.loadConfiguration(reportFile);
                List<String> ids = new ArrayList<>();
                if (reports.getConfigurationSection("reports") != null) ids.addAll(reports.getConfigurationSection("reports").getKeys(false));
                ids.sort((a,b) -> Long.compare(reports.getLong("reports." + b + ".timestamp"), reports.getLong("reports." + a + ".timestamp")));
                StringBuilder out = new StringBuilder("{\"reports\":[");
                boolean first = true;
                for (String id : ids) {
                    String base = "reports." + id + ".";
                    if (!first) out.append(',');
                    first = false;
                    out.append("{\"id\":\"").append(escape(id)).append("\",\"timestamp\":").append(reports.getLong(base + "timestamp"))
                       .append(",\"player\":\"").append(escape(reports.getString(base + "player", "Inconnu")))
                       .append("\",\"check\":\"").append(escape(reports.getString(base + "check", "UNKNOWN")))
                       .append("\",\"detail\":\"").append(escape(reports.getString(base + "detail", "")))
                       .append("\",\"violations\":").append(reports.getInt(base + "violations")).append('}');
                }
                json(exchange, 200, out.append("]}").toString());
            } else if (path.equals("/api/ac/reports") && exchange.getRequestMethod().equals("DELETE")) {
                String id = queryValue(exchange.getRequestURI().getRawQuery(), "id");
                try { UUID.fromString(id == null ? "" : id); }
                catch (IllegalArgumentException ex) { json(exchange, 400, "{\"error\":\"Identifiant invalide\"}"); return; }
                File reportFile = plugin.getDataFolder().toPath().resolve("anticheat-reports.yml").toFile();
                YamlConfiguration reports = YamlConfiguration.loadConfiguration(reportFile);
                if (!reports.contains("reports." + id)) { json(exchange, 404, "{\"error\":\"Signalement introuvable\"}"); return; }
                reports.set("reports." + id, null);
                reports.save(reportFile);
                audit("AC_REPORT_DELETE " + id, exchange);
                json(exchange, 200, "{\"deleted\":true}");
            } else if (path.equals("/api/players") && exchange.getRequestMethod().equals("GET")) {
                String result = onMain(() -> {
                    StringBuilder out = new StringBuilder("{\"players\":[");
                    boolean first = true;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!first) out.append(','); first = false;
                        out.append("{\"name\":\"").append(escape(p.getName())).append("\",\"uuid\":\"").append(p.getUniqueId())
                           .append("\",\"health\":").append(p.getHealth()).append(",\"ping\":").append(p.getPing())
                           .append(",\"world\":\"").append(escape(p.getWorld().getName())).append("\"}");
                    }
                    return out.append("]}").toString();
                });
                json(exchange, 200, result);
            } else if (path.equals("/api/inventory") && exchange.getRequestMethod().equals("GET")) {
                String name = queryValue(exchange.getRequestURI().getRawQuery(), "player");
                String result = onMain(() -> {
                    Player p = name == null ? null : Bukkit.getPlayerExact(name);
                    if (p == null) return "{\"error\":\"Joueur non connecté\"}";
                    StringBuilder out = new StringBuilder("{\"player\":\"").append(escape(p.getName())).append("\",\"items\":[");
                    ItemStack[] contents = p.getInventory().getStorageContents();
                    for (int i=0;i<contents.length;i++) {
                        if (i>0) out.append(',');
                        out.append(itemJson(contents[i]));
                    }
                    out.append("],\"armor\":[");
                    ItemStack[] armor = p.getInventory().getArmorContents();
                    for (int i=0;i<armor.length;i++) { if(i>0)out.append(','); out.append(itemJson(armor[i])); }
                    out.append("],\"offhand\":").append(itemJson(p.getInventory().getItemInOffHand())).append('}');
                    return out.toString();
                });
                json(exchange, result.contains("\"error\"") ? 404 : 200, result);
            } else if (path.equals("/api/command") && exchange.getRequestMethod().equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String command = jsonString(body, "command");
                if (command == null || command.isBlank() || command.length() > 500) { json(exchange, 400, "{\"error\":\"Commande invalide\"}"); return; }
                command = command.trim();
                while (command.startsWith("/")) command = command.substring(1);
                if (command.isBlank()) { json(exchange, 400, "{\"error\":\"Commande invalide\"}"); return; }
                final String run = command;
                String result = onMain(() -> {
                    boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
                    return "{\"accepted\":" + ok + ",\"message\":\"Commande envoyée à la console\"}";
                });
                audit("COMMAND " + run, exchange);
                json(exchange, 200, result);
            } else {
                json(exchange, 404, "{\"error\":\"Route inconnue\"}");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Erreur panel web: " + ex.getMessage());
            json(exchange, 500, "{\"error\":\"Erreur interne\"}");
        } finally {
            exchange.close();
        }
    }

    // Read the supervisor's real stdout/stderr logs so the website shows
    // Paper and Velocity console output, not only Bukkit logger records.
    private String serverLogsJson() {
        Path root = plugin.getDataFolder().toPath().getParent().getParent();
        String[] names = {"Lobby", "SMP", "Velocity"};
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String name : names) {
            Path log = root.resolve(name).resolve("logs").resolve("superviseur-console.log");
            if (!Files.isRegularFile(log)) continue;
            try {
                List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
                int start = Math.max(0, lines.size() - 100);
                for (int i = start; i < lines.size(); i++) {
                    String line = "[" + name + "] " + lines.get(i);
                    if (!first) out.append(',');
                    first = false;
                    out.append("\"").append(escape(line)).append("\"");
                }
            } catch (IOException ex) {
                String line = "[" + name + "] Impossible de lire le journal: " + ex.getMessage();
                if (!first) out.append(',');
                first = false;
                out.append("\"").append(escape(line)).append("\"");
            }
        }
        synchronized (this) {
            for (String event : liveEvents) {
                if (!first) out.append(',');
                first = false;
                out.append("\"").append(escape(event)).append("\"");
            }
        }
        return out.append("]").toString();
    }

    private String onMain(java.util.concurrent.Callable<String> task) throws Exception {
        if (Bukkit.isPrimaryThread()) return task.call();
        CompletableFuture<String> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> { try { future.complete(task.call()); } catch (Exception e) { future.completeExceptionally(e); } });
        return future.get(8, TimeUnit.SECONDS);
    }

    private String itemJson(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "{\"type\":\"AIR\",\"amount\":0}";
        return "{\"type\":\"" + item.getType().name() + "\",\"amount\":" + item.getAmount() + ",\"name\":\"" +
                escape(item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name()) + "\"}";
    }

    private String queryValue(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private String jsonString(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        if (!m.find()) return null;
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " "); }
    private void json(HttpExchange e, int status, String value) throws IOException { send(e, status, "application/json; charset=utf-8", value.getBytes(StandardCharsets.UTF_8)); }
    private void send(HttpExchange e, int status, String type, byte[] body) throws IOException {
        e.getResponseHeaders().set("Content-Type", type);
        e.sendResponseHeaders(status, body.length);
        e.getResponseBody().write(body);
    }
    private void audit(String action, HttpExchange e) {
        try {
            Files.writeString(plugin.getDataFolder().toPath().resolve("panel-audit.log"),
                Instant.now() + " " + e.getRemoteAddress().getAddress().getHostAddress() + " " + action + System.lineSeparator(),
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
}
