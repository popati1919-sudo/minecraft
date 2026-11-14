package ca.larperscoin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentification premium/crack maison pour Velocity.
 *
 * Le proxy reste en online-mode=false. Pour chaque connexion Java, le plugin
 * demande à l'API officielle Mojang si le pseudo existe:
 * - profil trouvé: forceOnlineMode() impose une vraie vérification de session Microsoft;
 * - profil absent (HTTP 204): forceOfflineMode() autorise le flux crack.
 * - erreur API: refus temporaire, jamais de repli non sécurisé en offline.
 *
 * Les joueurs Bedrock Floodgate sont reconnus par leur préfixe et restent en
 * offline-mode; Floodgate valide leur identité avec sa propre clé.
 */
@Plugin(id = "larperscoinvelocity", name = "LarpersCoinVelocity", version = "1.1.0",
        description = "Authentification premium/crack native LPC pour Velocity", authors = {"Larpers Coin"})
public final class LarpersCoinVelocity {
    private static final String MOJANG_PROFILE_URL =
            "https://api.mojang.com/users/profiles/minecraft/";
    private static final long CACHE_TTL_MILLIS = Duration.ofMinutes(10).toMillis();

    private final ProxyServer proxy;
    private final Logger logger;
    private final HttpClient http;
    private final Map<String, CacheEntry> profileCache = new ConcurrentHashMap<>();
    private volatile String bedrockPrefix = ".";

    @Inject
    public LarpersCoinVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("[LPC Auth] Authentification maison initialisee.");
        logger.info("[LPC Auth] Java Premium: verification Mojang + session obligatoire.");
        logger.info("[LPC Auth] Java crack: mode offline puis /register et /login dans le Lobby.");
        logger.info("[LPC Auth] Bedrock: prefix Floodgate '{}' (auth-type=floodgate).", bedrockPrefix);

        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("lpcproxy").plugin(this).build(),
                new SimpleCommand() {
                    @Override
                    public void execute(Invocation invocation) {
                        if (invocation.arguments().length != 1
                                || !invocation.arguments()[0].equalsIgnoreCase("status")) {
                            invocation.source().sendMessage(Component.text("Usage: /lpcproxy status"));
                            return;
                        }
                        invocation.source().sendMessage(Component.text("[LPC Auth] Actif."));
                        invocation.source().sendMessage(Component.text("Java Premium: verification Mojang obligatoire."));
                        invocation.source().sendMessage(Component.text("Java crack: /register puis /login au Lobby."));
                        invocation.source().sendMessage(Component.text("Bedrock: Floodgate, sans mot de passe."));
                    }
                });
    }

    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            String username = event.getUsername();
            if (username == null) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text("Pseudo Minecraft invalide.")));
                return;
            }

            // Les pseudos Floodgate sont préfixés ('.' par défaut). On vérifie le
            // suffixe séparément, puis on laisse Floodgate valider la clé Bedrock.
            if (username.startsWith(bedrockPrefix)) {
                String bedrockName = username.substring(bedrockPrefix.length());
                if (!bedrockName.matches("[A-Za-z0-9_]{1,16}")) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text("Pseudo Bedrock invalide.")));
                    return;
                }
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                return;
            }

            if (!username.matches("[A-Za-z0-9_]{3,16}")) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text("Pseudo Minecraft invalide.")));
                return;
            }

            try {
                boolean premium = isRegisteredMojangName(username);
                event.setResult(premium
                        ? PreLoginEvent.PreLoginComponentResult.forceOnlineMode()
                        : PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                logger.info("[LPC Auth] {}: {}", username,
                        premium ? "compte Java officiel - session Microsoft requise"
                                : "pseudo non premium - authentification LPC requise");
            } catch (Exception ex) {
                logger.warn("[LPC Auth] Verification Mojang impossible pour {}: {}", username, ex.toString());
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text("Verification Minecraft indisponible. Reessaie dans quelques instants.")));
            }
        });
    }

    private boolean isRegisteredMojangName(String username) throws Exception {
        String key = username.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CacheEntry cached = profileCache.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached.premium;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOJANG_PROFILE_URL + username))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "LarpersCoin-Auth/1.1.0")
                .GET()
                .build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());

        final boolean premium;
        if (response.statusCode() == 200) {
            premium = true;
        } else if (response.statusCode() == 204 || response.statusCode() == 404) {
            premium = false;
        } else {
            // 429, 5xx et autres réponses ne doivent jamais être interprétés
            // comme "pseudo libre", sinon un attaquant pourrait usurper un premium.
            throw new IllegalStateException("Mojang HTTP " + response.statusCode());
        }

        profileCache.put(key, new CacheEntry(premium, now + CACHE_TTL_MILLIS));
        return premium;
    }

    private record CacheEntry(boolean premium, long expiresAt) {}
}
