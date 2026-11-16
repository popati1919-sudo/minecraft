package ca.larperscoin.smp;

import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.GrimPlugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Relie les événements officiels de GrimAC au tableau de bord Larry. */
public final class GrimAlertHook {
    private GrimAlertHook() {}

    public static void register(JavaPlugin owner, LarpersWebDashboard dashboard) {
        GrimAbstractAPI api = GrimAPIProvider.get();
        GrimPlugin grim = api.getGrimPlugin(owner);
        api.getEventBus().get(FlagEvent.class).onFlag(grim, (user, check, verbose, cancelled) -> {
            dashboard.recordGrimFlag(
                    user.getName(),
                    check.getCheckName(),
                    String.valueOf(verbose)
            );
            return cancelled;
        });
        owner.getLogger().info("Intégration GrimAPI active: les alertes sont conservées sur le dashboard, sans spam console Grim.");
    }
}
