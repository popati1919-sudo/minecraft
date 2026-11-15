package ca.larperscoin.smp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LarpersWebDashboard {
    private final JavaPlugin plugin;
    private final LarpersCoin core;
    private HttpServer server;
    private String token;

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
                plugin.getLogger().warning("Dashboard LPC: nouveau jeton administrateur créé. Il est enregistré dans "
                        + file.getAbsolutePath() + " — ne le partage jamais.");
            }
            String bind = plugin.getConfig().getString("web-admin.bind", "127.0.0.1");
            int port = Math.max(1024, Math.min(65535, plugin.getConfig().getInt("web-admin.port", 8765)));
            server = HttpServer.create(new InetSocketAddress(bind, port), 32);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "LPC-WebAdmin"); t.setDaemon(true); return t;
            }));
            server.start();
            plugin.getLogger().info("Dashboard LPC actif sur http://" + bind + ":" + port
                    + " (jeton dans plugins/LarpersCoin/web-admin.yml).");
        } catch (Exception ex) {
            plugin.getLogger().severe("Impossible de démarrer le dashboard web: " + ex.getMessage());
        }
    }

    void shutdown() {
        if (server != null) server.stop(1);
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
                send(exchange, 200, "application/json; charset=utf-8", statusJson());
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
        String supplied = exchange.getRequestHeaders().getFirst("X-LPC-Token");
        if (supplied == null) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header != null && header.startsWith("Bearer ")) supplied = header.substring(7);
        }
        return supplied != null && java.security.MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
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
        out.append("],\"onlineCount\":").append(Bukkit.getOnlinePlayers().size()).append(",\"alerts\":[");
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
        return out.append("]}").toString();
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
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
<title>Larpers Coin — Admin</title><style>
*{box-sizing:border-box}body{margin:0;background:#0b1020;color:#e8edf8;font:15px system-ui}header{padding:24px 5%;background:#111a30;border-bottom:1px solid #263451}h1{margin:0;font-size:24px}main{max-width:1100px;margin:28px auto;padding:0 18px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:16px}.card{background:#141e33;border:1px solid #293955;border-radius:14px;padding:18px;margin-bottom:16px}.muted{color:#9eacc5}input,button{font:inherit;border-radius:9px;padding:11px;border:1px solid #394c6c;background:#0d1628;color:#fff}button{background:#7658e8;border:0;cursor:pointer;font-weight:700}button:hover{filter:brightness(1.15)}.row{display:flex;gap:9px;flex-wrap:wrap}ul{padding-left:20px}li{padding:5px 0;border-bottom:1px solid #263451}.value{font-size:30px;font-weight:800}.hidden{display:none}.danger{background:#a83c52}#login{max-width:520px;margin:40px auto}small{color:#aab6ca}</style></head>
<body><header><h1>◈ Larpers Coin — Dashboard</h1><div class="muted">Administration sécurisée · actualisation automatique</div></header>
<main><section id="login" class="card"><h2>Connexion administrateur</h2><p class="muted">Colle le jeton contenu dans plugins/LarpersCoin/web-admin.yml.</p><div class="row"><input id="token" type="password" placeholder="Jeton administrateur" style="flex:1;min-width:220px"><button onclick="connect()">Connexion</button></div><p id="loginError"></p></section>
<section id="app" class="hidden"><div class="grid"><div class="card"><div class="muted">Joueurs connectés</div><div class="value" id="count">0</div></div><div class="card"><div class="muted">Alertes anti-triche en mémoire</div><div class="value" id="alertsCount">0</div></div></div>
<div class="card"><h2>Joueurs en ligne</h2><ul id="players"></ul></div>
<div class="card"><h2>Réinitialiser un mot de passe</h2><p class="muted">Le compte est révoqué, le joueur est expulsé s'il est connecté et devra créer un nouveau mot de passe avec /register.</p><div class="row"><input id="playerName" placeholder="Pseudo exact"><button class="danger" onclick="resetPass()">Réinitialiser</button></div><p id="resetResult"></p></div>
<div class="card"><h2>Alertes anti-triche</h2><small>Les alertes ne déclenchent jamais de kick ni de ban automatique. Vérifie les preuves avant toute sanction manuelle.</small><ul id="alerts"></ul></div>
<div class="card"><h2>Joueurs signalés</h2><ul id="violations"></ul></div></section></main>
<script>
let key=localStorage.getItem('lpc-admin-token')||'';
const $=id=>document.getElementById(id);
async function api(path,opts={}){return fetch(path,{...opts,headers:{'X-LPC-Token':key,'Content-Type':'application/json',...(opts.headers||{})}})}
async function connect(){key=$('token').value.trim();try{let r=await api('/api/status');if(!r.ok)throw Error('Jeton invalide ou accès refusé');localStorage.setItem('lpc-admin-token',key);$('login').classList.add('hidden');$('app').classList.remove('hidden');refresh()}catch(e){$('loginError').textContent=e.message}}
async function refresh(){try{let r=await api('/api/status');if(!r.ok)throw Error();let d=await r.json();$('count').textContent=d.onlineCount;$('alertsCount').textContent=d.alerts.length;$('players').innerHTML=d.online.map(p=>'<li>'+esc(p.name)+' — ping '+p.ping+' ms</li>').join('')||'<li>Aucun joueur connecté</li>';$('alerts').innerHTML=d.alerts.map(x=>'<li>'+esc(x)+'</li>').join('')||'<li>Aucune alerte récente</li>';$('violations').innerHTML=d.violations.sort((a,b)=>b.count-a.count).map(x=>'<li>'+esc(x.name)+' — '+x.count+' signalement(s)</li>').join('')||'<li>Aucun joueur signalé</li>'}catch(e){$('app').classList.add('hidden');$('login').classList.remove('hidden');$('loginError').textContent='Session expirée. Reconnecte-toi.'}}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
async function resetPass(){let name=$('playerName').value.trim();if(!/^[A-Za-z0-9_]{3,16}$/.test(name)){$('resetResult').textContent='Pseudo invalide.';return}if(!confirm('Réinitialiser le compte de '+name+' ?'))return;let r=await api('/api/reset-password',{method:'POST',body:JSON.stringify({player:name})});let d=await r.json();$('resetResult').textContent=d.message||d.error;refresh()}
if(key){$('token').value=key;connect()}setInterval(()=>{if(!$('app').classList.contains('hidden'))refresh()},10000);
</script></body></html>
""";
}
