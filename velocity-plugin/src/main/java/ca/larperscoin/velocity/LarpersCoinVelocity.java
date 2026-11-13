package ca.larperscoin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(id = "larperscoinvelocity", name = "LarpersCoinVelocity", version = "1.0.0",
        description = "Outils de diagnostic du proxy Larpers Coin", authors = {"Larpers Coin"})
public final class LarpersCoinVelocity {
    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public LarpersCoinVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        boolean onlineMode = proxy.getConfiguration().isOnlineMode();
        String mode = onlineMode ? "PREMIUM (authentification Microsoft obligatoire)" : "CRACK (authentification hors ligne)";
        logger.info("[LPC] Proxy detecte en mode {}.", mode);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("lpcproxy").plugin(this).build(),
                new SimpleCommand() {
                    @Override
                    public void execute(Invocation invocation) {
                        if (!invocation.arguments().equals(new String[]{"status"})
                                && !(invocation.arguments().length == 0)) {
                            invocation.source().sendMessage(Component.text("Usage: /lpcproxy status"));
                            return;
                        }
                        invocation.source().sendMessage(Component.text("[LPC] Mode proxy: " + mode));
                        invocation.source().sendMessage(Component.text(onlineMode
                                ? "Les joueurs Java doivent posseder un compte officiel."
                                : "Les joueurs Java doivent utiliser /register puis /login dans le Lobby."));
                    }
                });
    }
}
