package ca.larperscoin.smp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Statistic;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LarpersWebDashboard {
    private final JavaPlugin plugin;
    private final LarpersCoin core;
    private HttpServer server;
    private String token;
    private final Deque<String> consoleLines = new ConcurrentLinkedDeque<>();
    private final Map<String, GrimAlertSummary> grimAlerts = new ConcurrentHashMap<>();
    private Handler consoleHandler;

    private static final class GrimAlertSummary {
        private int count;
        private String latest = "";
        private final Deque<String> recent = new ConcurrentLinkedDeque<>();
        synchronized void add(String line) {
            count++;
            latest = line;
            recent.addFirst(line);
            while (recent.size() > 5) recent.pollLast();
        }
        synchronized String toJson(String player, LarpersWebDashboard owner) {
            StringBuilder out = new StringBuilder("{\"player\":\"")
                    .append(owner.json(player)).append("\",\"count\":").append(count)
                    .append(",\"latest\":\"").append(owner.json(latest)).append("\",\"details\":[");
            boolean first = true;
            for (String item : recent) {
                if (!first) out.append(',');
                first = false;
                out.append("\"").append(owner.json(item)).append("\"");
            }
            return out.append("]}").toString();
        }
    }

    void recordGrimFlag(String player, String check, String verbose) {
        String name = (player == null || player.isBlank()) ? "Joueur inconnu" : player;
        String checkName = check == null ? "Check inconnu" : check;
        String category = classifyGrimCheck(checkName);
        String detail = java.time.LocalTime.now().withNano(0) + " [GRIM] " + name + " | "
                + category + " | " + checkName + " | " + (verbose == null ? "" : verbose);
        // Les alertes Grim restent dans leur panneau regroupé; ne pas les dupliquer dans la console web.
        grimAlerts.computeIfAbsent(name, ignored -> new GrimAlertSummary()).add(detail);
    }

    LarpersWebDashboard(JavaPlugin plugin, LarpersCoin core) {
        this.plugin = plugin;
        this.core = core;
        if (plugin.getConfig().getBoolean("network.auth-only", false)
                || !plugin.getConfig().getBoolean("web-admin.enabled", true)) return;
        try {
            File file = new File(plugin.getDataFolder(), "web-admin.yml");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            token = cfg.getString("token", "");
            if (token == null || token.length() < 40) {
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                cfg.set("token", token);
                cfg.save(file);
                plugin.getLogger().warning("Dashboard Larry: nouveau jeton administrateur créé. Il est enregistré dans "
                        + file.getAbsolutePath() + " — ne le partage jamais.");
            }
            String bind = plugin.getConfig().getString("web-admin.bind", "127.0.0.1");
            int port = Math.max(1, Math.min(65535, plugin.getConfig().getInt("web-admin.port", 80)));
            try {
                server = HttpServer.create(new InetSocketAddress(bind, port), 32);
            } catch (IOException bindError) {
                if (port != 80) throw bindError;
                port = 8765;
                server = HttpServer.create(new InetSocketAddress(bind, port), 32);
                plugin.getLogger().warning("Le port 80 est occupé; dashboard déplacé sur le port 8765.");
            }
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "Larry-WebAdmin"); t.setDaemon(true); return t;
            }));
            consoleHandler = new Handler() {
                @Override public void publish(LogRecord record) {
                    if (record == null || !isLoggable(record)) return;
                    String message = record.getMessage();
                    if (message == null || message.isBlank()) return;
                    // Grim peut émettre une ligne par tick/flag. Les événements sont déjà
                    // conservés dans grimAlerts; ne pas les recopier dans la console temps réel.
                    if (message.contains("[GRIM]")) return;
                    String line = java.time.LocalTime.now().withNano(0) + " [" + record.getLevel().getName()
                            + "] " + message;
                    consoleLines.addLast(line);
                    while (consoleLines.size() > 400) consoleLines.pollFirst();
                    if (record.getThrown() != null) {
                        String detail = record.getThrown().toString();
                        consoleLines.addLast(java.time.LocalTime.now().withNano(0) + " [ERROR] " + detail);
                    }
                }
                @Override public void flush() { }
                @Override public void close() { }
            };
            Logger.getLogger("").addHandler(consoleHandler);
            // LarpersCoin est chargé tôt pour fournir le générateur du lobby.
            // Attendre le tick suivant garantit que GrimAC (chargé après STARTUP) est activé.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (Bukkit.getPluginManager().isPluginEnabled("GrimAC")) {
                    try {
                        Class<?> hook = Class.forName("ca.larperscoin.smp.GrimAlertHook");
                        hook.getMethod("register", JavaPlugin.class, LarpersWebDashboard.class).invoke(null, plugin, this);
                    } catch (Throwable error) {
                        plugin.getLogger().severe("Impossible de connecter les alertes Grim au dashboard: " + error.getMessage());
                    }
                } else {
                    plugin.getLogger().warning("GrimAC absent: le panneau d'alertes Grim restera vide sur cette instance.");
                }
            }, 1L);
            server.start();
            String dashboardUrl = port == 80 ? "http://" + bind : "http://" + bind + ":" + port;
            plugin.getLogger().info("Dashboard Larry actif sur " + dashboardUrl
                    + " (jeton dans plugins/LarpersCoin/web-admin.yml).");
        } catch (Exception ex) {
            plugin.getLogger().severe("Impossible de démarrer le dashboard web: " + ex.getMessage());
        }
    }

    void shutdown() {
        if (server != null) server.stop(1);
        if (consoleHandler != null) {
            Logger.getLogger("").removeHandler(consoleHandler);
            consoleHandler.close();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") && exchange.getRequestMethod().equals("GET")) {
                send(exchange, 200, "text/html; charset=utf-8", PAGE);
                return;
            }
            if (!authorized(exchange)) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                send(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"Authentification administrateur requise\"}");
                return;
            }
            if (path.equals("/api/status") && exchange.getRequestMethod().equals("GET")) {
                send(exchange, 200, "application/json; charset=utf-8", statusJsonOnMainThread());
                return;
            }
            if (path.equals("/api/alerts/clear") && exchange.getRequestMethod().equals("POST")) {
                try {
                    Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                        grimAlerts.clear();
                        consoleLines.removeIf(line -> line.contains("[GRIM]") || line.contains("[AntiCheat]"));
                        if (core.getAntiCheat() != null) core.getAntiCheat().clearDashboardReports();
                        return true;
                    }).get(15, TimeUnit.SECONDS);
                    send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"message\":\"Signalements effacés. Les détections continuent normalement.\"}");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Effacement des signalements impossible: " + ex.getMessage());
                    send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"Impossible d'effacer les signalements pour le moment.\"}");
                }
                return;
            }
            if (path.equals("/api/command") && exchange.getRequestMethod().equals("POST")) {
                String body = new String(exchange.getRequestBody().readNBytes(2048), StandardCharsets.UTF_8);
                Matcher matcher = Pattern.compile("\"command\"\\s*:\\s*\"([^\\r\\n]{1,512})\"").matcher(body);
                if (!matcher.find()) {
                    send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Commande invalide (512 caractères maximum)\"}");
                    return;
                }
                String commandText = matcher.group(1).trim();
                if (commandText.startsWith("/")) commandText = commandText.substring(1).trim();
                if (commandText.isEmpty() || commandText.indexOf('\n') >= 0 || commandText.indexOf('\r') >= 0) {
                    send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Commande invalide\"}");
                    return;
                }
                // Accepte aussi les termes courants "bois", "wood" et "log" pour le chêne.
                Matcher legacyLog = Pattern.compile("(?i)^give\\s+(\\S+)\\s+(?:log|wood|bois)(?:\\s+(\\d+))?(.*)$").matcher(commandText);
                if (legacyLog.matches()) {
                    String amount = legacyLog.group(1 + 1);
                    if (amount == null || amount.isBlank()) amount = "1";
                    String suffix = legacyLog.group(3) == null ? "" : legacyLog.group(3);
                    commandText = "give " + legacyLog.group(1) + " minecraft:oak_log " + amount + suffix;
                }
                final String safeCommand = commandText;
                try {
                    boolean accepted = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                        consoleLines.addLast(java.time.LocalTime.now().withNano(0) + " [WEB] > " + safeCommand);
                        while (consoleLines.size() > 400) consoleLines.pollFirst();
                        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), safeCommand);
                    }).get(30, TimeUnit.SECONDS);
                    send(exchange, accepted ? 200 : 400, "application/json; charset=utf-8",
                            accepted ? "{\"ok\":true,\"message\":\"Commande exécutée par la console.\"}"
                                     : "{\"ok\":false,\"message\":\"Commande inconnue ou refusée; vérifie la syntaxe.\"}");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Commande web non exécutée (" + safeCommand + "): " + ex.getClass().getSimpleName()
                            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
                    send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"Le serveur ne répond pas à temps. Réessaie.\"}");
                }
                return;
            }
            if (path.equals("/api/suggest") && exchange.getRequestMethod().equals("GET")) {
                String query = exchange.getRequestURI().getRawQuery();
                String prefix = "";
                if (query != null) for (String part : query.split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2 && pair[0].equals("q")) prefix = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
                final String completionInput = prefix.startsWith("/") ? prefix.substring(1) : prefix;
                java.util.concurrent.CompletableFuture<List<String>> result = new java.util.concurrent.CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        // Utilise la CommandMap Bukkit/Paper: elle connait les commandes vanilla
                        // ET celles des plugins, y compris les suggestions d'arguments.
                        Object commandMap = Bukkit.getServer().getClass().getMethod("getCommandMap")
                                .invoke(Bukkit.getServer());
                        Object raw = commandMap.getClass()
                                .getMethod("tabComplete", org.bukkit.command.CommandSender.class, String.class)
                                .invoke(commandMap, Bukkit.getConsoleSender(), completionInput);
                        if (raw instanceof List<?> values) {
                            result.complete(values.stream().filter(String.class::isInstance)
                                    .map(String.class::cast).distinct().limit(30).toList());
                        } else result.complete(List.of());
                    } catch (Throwable error) {
                        try {
                            // Compatibilité avec les implémentations qui exposent cette méthode.
                            Object raw = Bukkit.getServer().getClass().getMethod("tabCompleteCommand", String.class)
                                    .invoke(Bukkit.getServer(), completionInput);
                            if (raw instanceof List<?> values) {
                                result.complete(values.stream().filter(String.class::isInstance)
                                        .map(String.class::cast).distinct().limit(30).toList());
                            } else result.complete(List.of());
                        } catch (Throwable ignored) {
                            result.complete(List.of("list", "tps", "plugins", "version", "whitelist list", "save-all")
                                    .stream().filter(x -> x.startsWith(completionInput)).toList());
                        }
                    }
                });
                try {
                    StringBuilder json = new StringBuilder("{\"suggestions\":[");
                    boolean first = true;
                    for (String item : result.get(5, TimeUnit.SECONDS)) {
                        if (!first) json.append(',');
                        first = false;
                        json.append("\"").append(json(item)).append("\"");
                    }
                    send(exchange, 200, "application/json; charset=utf-8", json.append("]}").toString());
                } catch (Exception ex) { send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"Suggestions indisponibles\"}"); }
                return;
            }
            if (path.equals("/api/player") && exchange.getRequestMethod().equals("GET")) {
                String requested = "";
                String query = exchange.getRequestURI().getRawQuery();
                if (query != null) for (String part : query.split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2 && pair[0].equals("name")) requested = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
                final String playerName = requested;
                java.util.concurrent.CompletableFuture<String> result = new java.util.concurrent.CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(playerName);
                    result.complete(target == null ? null : playerJson(target));
                });
                try {
                    String json = result.get(8, TimeUnit.SECONDS);
                    if (json == null) send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"Joueur non connecté. Les statistiques détaillées et l'inventaire sont disponibles lorsqu'il est en ligne.\"}");
                    else send(exchange, 200, "application/json; charset=utf-8", json);
                } catch (Exception ex) { send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"Lecture du joueur indisponible\"}"); }
                return;
            }
            if (path.equals("/api/reset-password") && exchange.getRequestMethod().equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Matcher matcher = Pattern.compile("\"player\"\\s*:\\s*\"([A-Za-z0-9_]{3,16})\"").matcher(body);
                if (!matcher.find()) {
                    send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Pseudo invalide\"}");
                    return;
                }
                String playerName = matcher.group(1);
                java.util.concurrent.CompletableFuture<Boolean> result = new java.util.concurrent.CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> result.complete(core.resetAuthAccount(playerName)));
                boolean ok;
                try { ok = result.get(10, TimeUnit.SECONDS); }
                catch (Exception ex) { send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"Serveur occupé\"}"); return; }
                send(exchange, ok ? 200 : 404, "application/json; charset=utf-8",
                        ok ? "{\"ok\":true,\"message\":\"Compte réinitialisé; le joueur devra refaire /register.\"}"
                           : "{\"error\":\"Aucun compte enregistré pour ce pseudo\"}");
                return;
            }
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"Introuvable\"}");
        } finally {
            exchange.close();
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String supplied = exchange.getRequestHeaders().getFirst("X-Larry-Token");
        if (supplied == null) supplied = exchange.getRequestHeaders().getFirst("X-LPC-Token");
        if (supplied == null) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header != null && header.startsWith("Bearer ")) supplied = header.substring(7);
        }
        return supplied != null && java.security.MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private String statusJsonOnMainThread() throws IOException {
        try { return Bukkit.getScheduler().callSyncMethod(plugin, this::statusJson).get(8, TimeUnit.SECONDS); }
        catch (Exception ex) { throw new IOException("Impossible de lire l'état du serveur", ex); }
    }

    private String statusJson() {
        StringBuilder out = new StringBuilder("{\"online\":[");
        boolean first = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!first) out.append(',');
            first = false;
            out.append("{\"name\":\"").append(json(p.getName())).append("\",\"uuid\":\"")
                    .append(p.getUniqueId()).append("\",\"ping\":").append(p.getPing()).append('}');
        }
        Runtime runtime = Runtime.getRuntime();
        long ramUsed = runtime.totalMemory() - runtime.freeMemory();
        long ramAllocated = runtime.totalMemory();
        long ramMax = runtime.maxMemory();
        out.append("],\"onlineCount\":").append(Bukkit.getOnlinePlayers().size())
                .append(",\"ramUsed\":").append(ramUsed)
                .append(",\"ramAllocated\":").append(ramAllocated)
                .append(",\"ramMax\":").append(ramMax)
                .append(",\"alerts\":[");
        List<String> alerts = core.getAntiCheat() == null ? List.of() : core.getAntiCheat().dashboardEvidence();
        first = true;
        for (String alert : alerts) {
            if (!first) out.append(',');
            first = false;
            out.append("\"").append(json(alert)).append("\"");
        }
        out.append("],\"violations\":[");
        Map<UUID,Integer> counts = core.getAntiCheat() == null ? Map.of() : core.getAntiCheat().dashboardViolations();
        first = true;
        for (var entry : counts.entrySet()) {
            if (!first) out.append(',');
            first = false;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            out.append("{\"name\":\"").append(json(name == null ? entry.getKey().toString() : name))
                    .append("\",\"count\":").append(entry.getValue()).append('}');
        }
        out.append("],\"grimAlerts\":[");
        first = true;
        for (var entry : new java.util.TreeMap<>(grimAlerts).entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(entry.getValue().toJson(entry.getKey(), this));
        }
        out.append("],\"console\":[");
        first = true;
        for (String line : new ArrayList<>(consoleLines)) {
            if (!first) out.append(',');
            first = false;
            out.append("\"").append(json(line)).append("\"");
        }
        YamlConfiguration market=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"features.yml"));
        out.append("],\"market\":{\"catalogueSize\":").append(market.getConfigurationSection("shop.sell-prices")==null?0:market.getConfigurationSection("shop.sell-prices").getKeys(false).size())
                .append(",\"salesTotal\":").append(market.getLong("market.sales-total",0))
                .append(",\"salesCount\":").append(market.getLong("market.sales-count",0))
                .append(",\"lastRefresh\":").append(market.getLong("market.last-refresh",0))
                .append(",\"nextRefresh\":").append(market.getLong("market.next-refresh",0))
                .append(",\"history\":[");
        boolean firstSale=true;
        for(Map<?,?> sale:market.getMapList("market.history")){
            if(!firstSale)out.append(',');firstSale=false;
            out.append("{\"item\":\"").append(json(String.valueOf(sale.get("item"))))
                    .append("\",\"player\":\"").append(json(String.valueOf(sale.get("player"))))
                    .append("\",\"amount\":").append(sale.get("amount") instanceof Number n?n.longValue():0)
                    .append(",\"total\":").append(sale.get("total") instanceof Number n?n.doubleValue():0)
                    .append(",\"time\":").append(sale.get("time") instanceof Number n?n.longValue():0).append('}');
        }
        out.append("]}}");
        return out.toString();
    }

    private String classifyGrimCheck(String check) {
        String value = check.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        if (value.contains("fly") || value.contains("flight") || value.contains("prediction") || value.contains("elytra")) return "Vol / Fly suspect";
        if (value.contains("speed") || value.contains("timer") || value.contains("velocity") || value.contains("strafe")) return "Speed / Timer suspect";
        if (value.contains("aura") || value.contains("reach") || value.contains("hitbox") || value.contains("aim") || value.contains("rotation") || value.contains("autoclick") || value.contains("attack")) return "Combat suspect";
        if (value.contains("scaffold") || value.contains("tower") || value.contains("jesus") || value.contains("nofall") || value.contains("step") || value.contains("vehicle")) return "Déplacement / Scaffold suspect";
        return "Autre vérification Grim";
    }

    private String itemJson(ItemStack item, int slot) {
        if (item == null || item.getType().isAir()) return "null";
        ItemMeta meta = item.getItemMeta();
        String display = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
        return "{\"slot\":" + slot + ",\"type\":\"" + json(item.getType().getKey().toString())
                + "\",\"name\":\"" + json(display) + "\",\"amount\":" + item.getAmount() + "}";
    }

    private String playerJson(Player p) {
        StringBuilder out = new StringBuilder("{\"name\":\"").append(json(p.getName()))
                .append("\",\"uuid\":\"").append(p.getUniqueId())
                .append("\",\"ping\":").append(p.getPing())
                .append(",\"health\":").append(String.format(java.util.Locale.ROOT, "%.1f", p.getHealth()))
                .append(",\"maxHealth\":").append(String.format(java.util.Locale.ROOT, "%.1f", p.getMaxHealth()))
                .append(",\"food\":").append(p.getFoodLevel()).append(",\"level\":").append(p.getLevel())
                .append(",\"xp\":").append(String.format(java.util.Locale.ROOT, "%.1f", p.getExp()))
                .append(",\"world\":\"").append(json(p.getWorld().getName()))
                .append("\",\"x\":").append(p.getLocation().getBlockX())
                .append(",\"y\":").append(p.getLocation().getBlockY())
                .append(",\"z\":").append(p.getLocation().getBlockZ())
                .append(",\"gameMode\":\"").append(p.getGameMode().name())
                .append("\",\"playTicks\":").append(p.getStatistic(Statistic.PLAY_ONE_MINUTE))
                .append(",\"deaths\":").append(p.getStatistic(Statistic.DEATHS))
                .append(",\"playerKills\":").append(p.getStatistic(Statistic.PLAYER_KILLS))
                .append(",\"mobKills\":").append(p.getStatistic(Statistic.MOB_KILLS))
                .append(",\"inventory\":[");
        boolean first = true;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null || contents[i].getType().isAir()) continue;
            if (!first) out.append(',');
            first = false;
            out.append(itemJson(contents[i], i));
        }
        out.append("],\"armor\":[");
        first = true;
        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] == null || armor[i].getType().isAir()) continue;
            if (!first) out.append(',');
            first = false;
            out.append(itemJson(armor[i], i));
        }
        return out.append("],\"offhand\":").append(itemJson(p.getInventory().getItemInOffHand(), -1)).append('}').toString();
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\", "\\\").replace("\"", "\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private void send(HttpExchange exchange, int code, String type, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self' 'unsafe-inline'; connect-src 'self'");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final String PAGE = """
<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Larry SMP — Administration</title><style>
*{box-sizing:border-box}body{margin:0;background:#0b1020;color:#e8edf8;font:15px system-ui}header{padding:24px 5%;background:#111a30;border-bottom:1px solid #263451}h1{margin:0;font-size:24px}main{max-width:1250px;margin:28px auto;padding:0 18px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:16px}.card{background:#141e33;border:1px solid #293955;border-radius:14px;padding:18px;margin-bottom:16px}.muted{color:#9eacc5}input,button{font:inherit;border-radius:9px;padding:11px;border:1px solid #394c6c;background:#0d1628;color:#fff}button{background:#7658e8;border:0;cursor:pointer;font-weight:700}button:hover{filter:brightness(1.15)}.row{display:flex;gap:9px;flex-wrap:wrap}ul{padding-left:20px}li{padding:5px 0;border-bottom:1px solid #263451}.value{font-size:30px;font-weight:800}.hidden{display:none}.danger{background:#a83c52}#login{max-width:520px;margin:40px auto}small{color:#aab6ca}#consoleOutput{height:320px;overflow:auto;white-space:pre-wrap;background:#070b14;border:1px solid #293955;border-radius:10px;padding:12px;color:#c8f7d2;font:12px ui-monospace,monospace;margin:12px 0}#consoleCommand{flex:1;min-width:220px;font-family:ui-monospace,monospace}#suggestions{display:flex;gap:7px;flex-wrap:wrap;margin-top:9px}#suggestions button{font-size:12px;padding:7px 10px;background:#263858}.statgrid{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:8px}.stat{background:#0d1628;border-radius:8px;padding:10px}.stat strong{display:block;font-size:20px}#inventoryGrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(145px,1fr));gap:8px}.slot{background:#0d1628;border:1px solid #293955;border-radius:8px;padding:9px;font-size:13px}.slot b{display:block;color:#d8c9ff}</style></head>
<body><header><h1>◈ Larry SMP — Dashboard</h1><div class="muted">Administration sécurisée · actualisation automatique</div></header>
<main><section id="login" class="card"><h2>Connexion administrateur</h2><p class="muted">Colle le jeton contenu dans plugins/LarpersCoin/web-admin.yml.</p><div class="row"><input id="token" type="password" placeholder="Jeton administrateur" style="flex:1;min-width:220px"><button onclick="connect()">Connexion</button></div><p id="loginError"></p></section>
<section id="app" class="hidden"><div class="grid"><div class="card"><div class="muted">Joueurs connectés</div><div class="value" id="count">0</div></div><div class="card"><div class="muted">RAM utilisée par le serveur Java</div><div class="value" id="ramUsed">—</div><div class="muted" id="ramDetails">Chargement…</div><div style="height:9px;background:#263451;border-radius:8px;overflow:hidden;margin-top:10px"><div id="ramBar" style="height:100%;width:0%;background:#8b75ed;border-radius:8px"></div></div></div><div class="card"><div class="muted">Alertes anti-triche en mémoire</div><div class="value" id="alertsCount">0</div></div></div>
<div class="card"><h2>Marché Larry — statistiques</h2><p class="muted">Prix recalculés toutes les 6 heures; les ventes sont enregistrées immédiatement.</p><div class="statgrid" id="marketStats"></div><h3>Historique récent des ventes</h3><ul id="marketHistory"></ul></div><div class="card"><h2>Joueurs en ligne</h2><p class="muted">Clique sur un joueur pour afficher ses statistiques et son inventaire.</p><ul id="players"></ul></div><div class="card"><h2>Fiche joueur</h2><div class="row"><input id="inspectName" placeholder="Pseudo exact"><button id="inspectButton">Afficher</button></div><p id="playerError" class="muted"></p><div id="playerDetails"></div></div>
<div class="card"><h2>Réinitialiser un mot de passe</h2><p class="muted">Le compte est révoqué, le joueur est expulsé s'il est connecté et devra créer un nouveau mot de passe avec /register.</p><div class="row"><input id="playerName" placeholder="Pseudo exact"><button class="danger" onclick="resetPass()">Réinitialiser</button></div><p id="resetResult"></p></div>
<div class="card"><h2>Console du serveur — temps réel</h2><p class="muted">Les dernières lignes du journal. Les commandes sont exécutées comme console serveur.</p><pre id="consoleOutput">Connexion à la console…</pre><div class="row"><input id="consoleCommand" autocomplete="off" placeholder="Commande Minecraft (ex. list, say Bonjour, lp...)"><button onclick="sendCommand()">Exécuter</button></div><div id="suggestions"></div><div class="row" id="quickCommands"><button data-cmd="list">Joueurs</button><button data-cmd="tps">TPS</button><button data-cmd="plugins">Plugins</button><button data-cmd="version">Version</button><button data-cmd="whitelist list">Whitelist</button><button data-cmd="save-all">Sauvegarder</button></div><p id="commandResult"></p></div>
<div class="card"><div class="row" style="justify-content:space-between;align-items:center"><h2>Alertes GrimAC — regroupées par joueur</h2><button id="clearReports" class="secondary">Effacer les signalements</button></div><small>Chaque joueur apparaît une seule fois. Les catégories indiquent le type de contrôle déclenché; une alerte est un indice à vérifier, pas une preuve certaine.</small><ul id="grimAlerts"></ul></div>
<div class="card"><h2>Alertes anti-triche Larry</h2><small>Les alertes ne déclenchent jamais de kick ni de ban automatique. Vérifie les preuves avant toute sanction manuelle.</small><ul id="alerts"></ul></div>
<div class="card"><h2>Joueurs signalés</h2><ul id="violations"></ul></div></section></main>
<script>
let key=localStorage.getItem('larry-admin-token')||localStorage.getItem('lpc-admin-token')||'';
const $=id=>document.getElementById(id);
async function api(path,opts={}){return fetch(path,{...opts,headers:{'X-Larry-Token':key,'Content-Type':'application/json',...(opts.headers||{})}})}
async function connect(){key=$('token').value.trim();try{let r=await api('/api/status');if(!r.ok)throw Error('Jeton invalide ou accès refusé');localStorage.setItem('larry-admin-token',key);$('login').classList.add('hidden');$('app').classList.remove('hidden');refresh()}catch(e){$('loginError').textContent=e.message}}
async function refresh(){try{let r=await api('/api/status');if(!r.ok)throw Error();let d=await r.json();$('count').textContent=d.onlineCount;const mb=n=>(n/1024/1024).toFixed(0)+' Mo';const ramPct=d.ramMax>0?Math.min(100,Math.round(d.ramUsed/d.ramMax*100)):0;$('ramUsed').textContent=mb(d.ramUsed);$('ramDetails').textContent='Allouée : '+mb(d.ramAllocated)+' · Maximum : '+mb(d.ramMax)+' · '+ramPct+' % du maximum';$('ramBar').style.width=ramPct+'%';const m=d.market||{};$('marketStats').innerHTML=[['Objets au catalogue',m.catalogueSize||0],['Objets vendus',m.salesTotal||0],['Transactions',m.salesCount||0],['Prochaine mise à jour',m.nextRefresh?new Date(m.nextRefresh).toLocaleString('fr-CA'):'Au démarrage']].map(x=>'<div class="stat"><small>'+x[0]+'</small><strong>'+esc(x[1])+'</strong></div>').join('');$('marketHistory').innerHTML=(m.history||[]).slice(0,20).map(x=>'<li><b>'+esc(x.player)+'</b> — '+esc(x.amount)+' × '+esc(x.item)+' — '+esc(x.total)+' Larry <small>('+new Date(x.time).toLocaleString('fr-CA')+')</small></li>').join('')||'<li>Aucune vente enregistrée</li>';$('alertsCount').textContent=d.alerts.length+(d.grimAlerts||[]).reduce((n,a)=>n+a.count,0);
$('grimAlerts').innerHTML=(d.grimAlerts||[]).sort((a,b)=>b.count-a.count).map(a=>'<li><b>'+esc(a.player)+'</b> — <b>x'+a.count+'</b><br><small>Dernière alerte : '+esc(a.latest)+'</small><details><summary>Voir les 5 dernières</summary>'+a.details.map(x=>'<div>'+esc(x)+'</div>').join('')+'</details></li>').join('')||'<li>Aucune alerte Grim reçue dans le journal pour le moment</li>';$('players').innerHTML=d.online.map(p=>'<li><button data-player=\"'+esc(p.name)+'\" class=\"playerPick\">'+esc(p.name)+'</button> — ping '+p.ping+' ms</li>').join('')||'<li>Aucun joueur connecté</li>';$('alerts').innerHTML=d.alerts.map(x=>'<li>'+esc(x)+'</li>').join('')||'<li>Aucune alerte récente</li>';$('violations').innerHTML=d.violations.sort((a,b)=>b.count-a.count).map(x=>'<li>'+esc(x.name)+' — '+x.count+' signalement(s)</li>').join('')||'<li>Aucun joueur signalé</li>';$('consoleOutput').textContent=(d.console||[]).join('\\n')||'Aucune ligne reçue';$('consoleOutput').scrollTop=$('consoleOutput').scrollHeight}catch(e){$('app').classList.add('hidden');$('login').classList.remove('hidden');$('loginError').textContent='Session expirée. Reconnecte-toi.'}}
function applyConsoleSuggestion(suggestion){
 const input=$('consoleCommand'), value=input.value, split=value.lastIndexOf(' ');
 const prefix=split>=0?value.slice(0,split+1):'';
 input.value=prefix+suggestion;
 input.focus();
 input.setSelectionRange(input.value.length,input.value.length);
 getSuggestions();
}
async function getSuggestions(){let q=$('consoleCommand').value;if(!q.trim()){$('suggestions').innerHTML='';return}try{let r=await api('/api/suggest?q='+encodeURIComponent(q));let d=await r.json();$('suggestions').innerHTML=(d.suggestions||[]).slice(0,15).map(x=>'<button type="button" data-suggestion="'+esc(x)+'">'+esc(x)+'</button>').join('')}catch(e){$('suggestions').innerHTML=''}}
$('consoleCommand').addEventListener('input',getSuggestions);
$('suggestions').addEventListener('click',e=>{let b=e.target.closest('button[data-suggestion]');if(b)applyConsoleSuggestion(b.dataset.suggestion)});
// Tab insère la suggestion à la fin de la commande sans effacer les arguments déjà saisis.
$('consoleCommand').addEventListener('keydown',async e=>{
 if(e.key==='Tab'){
  e.preventDefault();
  let buttons=$('suggestions').querySelectorAll('button[data-suggestion]');
  if(buttons.length){applyConsoleSuggestion(buttons[0].dataset.suggestion);return}
  await getSuggestions();
  buttons=$('suggestions').querySelectorAll('button[data-suggestion]');
  if(buttons.length)applyConsoleSuggestion(buttons[0].dataset.suggestion);
 }
});
$('players').addEventListener('click',e=>{let b=e.target.closest('button[data-player]');if(b)viewPlayer(b.dataset.player)});
$('inspectButton').addEventListener('click',()=>viewPlayer($('inspectName').value));
async function viewPlayer(name){name=String(name||'').trim();if(!name)return;$('inspectName').value=name;$('playerError').textContent='Chargement…';$('playerDetails').innerHTML='';try{let r=await api('/api/player?name='+encodeURIComponent(name));let d=await r.json();if(!r.ok)throw Error(d.error||'Impossible de charger le joueur');$('playerError').textContent='';let minutes=Math.floor(d.playTicks/20/60),hours=Math.floor(minutes/60);let stats=[['Vie',d.health+'/'+d.maxHealth],['Nourriture',d.food+'/20'],['Niveau',d.level],['XP',d.xp],['Ping',d.ping+' ms'],['Temps joué',hours+' h '+(minutes%60)+' min'],['Morts',d.deaths],['Kills joueurs',d.playerKills],['Kills mobs',d.mobKills],['Mode',d.gameMode],['Monde',d.world],['Position',d.x+', '+d.y+', '+d.z]];let item=x=>'<div class="slot"><b>'+esc(x.name)+'</b>'+esc(x.type)+' × '+x.amount+'</div>';$('playerDetails').innerHTML='<h3>'+esc(d.name)+'</h3><div class="statgrid">'+stats.map(x=>'<div class="stat"><small>'+x[0]+'</small><strong>'+esc(x[1])+'</strong></div>').join('')+'</div><h3>Inventaire</h3><div id="inventoryGrid">'+(d.inventory.map(item).join('')||'<p>Inventaire vide</p>')+'</div><h3>Armure</h3><div id="inventoryGrid">'+(d.armor.map(item).join('')||'<p>Aucune armure</p>')+'</div><h3>Main secondaire</h3>'+(d.offhand?item(d.offhand):'<p>Vide</p>');}catch(e){$('playerError').textContent=e.message}}

function esc(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
$('quickCommands').addEventListener('click',e=>{let b=e.target.closest('button[data-cmd]');if(b){$('consoleCommand').value=b.dataset.cmd;sendCommand()}});
async function sendCommand(){let command=$('consoleCommand').value.trim();if(!command)return;let r=await api('/api/command',{method:'POST',body:JSON.stringify({command})});let d=await r.json();$('commandResult').textContent=d.message||d.error||'Commande envoyée';$('consoleCommand').value='';refresh()}
async function clearReports(){if(!confirm('Effacer les signalements et remettre à zéro les compteurs anti-triche Larry ?'))return;let r=await api('/api/alerts/clear',{method:'POST'});let d=await r.json();$('commandResult').textContent=d.message||d.error||'Opération terminée';refresh()}
$('clearReports')?.addEventListener('click',clearReports);
$('consoleCommand')?.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();sendCommand()}});
async function resetPass(){let name=$('playerName').value.trim();if(!/^[A-Za-z0-9_]{3,16}$/.test(name)){$('resetResult').textContent='Pseudo invalide.';return}if(!confirm('Réinitialiser le compte de '+name+' ?'))return;let r=await api('/api/reset-password',{method:'POST',body:JSON.stringify({player:name})});let d=await r.json();$('resetResult').textContent=d.message||d.error;refresh()}
if(key){$('token').value=key;connect()}setInterval(()=>{if(!$('app').classList.contains('hidden'))refresh()},2000);
</script></body></html>
""";
}
