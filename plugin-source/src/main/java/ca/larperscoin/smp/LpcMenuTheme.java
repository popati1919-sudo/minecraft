package ca.larperscoin.smp;

import org.bukkit.inventory.Inventory;

/** Les menus Larry restent sans vitres décoratives pour ne jamais masquer les emplacements utiles. */
public final class LpcMenuTheme {
    private LpcMenuTheme() {}

    public static void frame(Inventory inventory) {
        // Aucun remplissage décoratif : tous les emplacements libres restent réellement libres.
    }
}
