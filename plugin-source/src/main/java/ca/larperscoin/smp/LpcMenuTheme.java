package ca.larperscoin.smp;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Bordure commune, discrète et lisible pour les menus LPC sur Java et Bedrock. */
public final class LpcMenuTheme {
    private LpcMenuTheme() {}

    public static void frame(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + " ");
        pane.setItemMeta(meta);
        int size = inventory.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean border = row == 0 || row == rows - 1 || column == 0 || column == 8;
            if (border && inventory.getItem(slot) == null) inventory.setItem(slot, pane.clone());
        }
    }
}
