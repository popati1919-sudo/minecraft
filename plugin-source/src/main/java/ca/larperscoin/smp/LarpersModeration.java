package ca.larperscoin.smp;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class LarpersModeration implements Listener, CommandExecutor, TabCompleter {
    private static final String PREFIX = ChatColor.GOLD + "[LPC] " + ChatColor.RESET;
    private static final String MENU_TITLE = "LPC | Modération";
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDuplicate = new ConcurrentHashMap<>();

    public LarpersModeration(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "moderation.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (String name : List.of("ban", "unban", "banip", "sanctions", "mod", "help")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean staff(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("larpers.moderation");
    }

    private void tell(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + message);
    }

    private void audit(String actor, String target, String type, String reason, long expiresAt) {
        List<String> rows = data.getStringList("history." + target.toLowerCase(Locale.ROOT));
        rows.add(System.currentTimeMillis() + "|" + actor + "|" + type + "|" + reason.replace("|", "/") + "|" + expiresAt);
        if (rows.size() > 100) rows = new ArrayList<>(rows.subList(rows.size() - 100, rows.size()));
        data.set("history." + target.toLowerCase(Locale.ROOT), rows);
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Impossible de sauvegarder moderation.yml: " + ex.getMessage()); }
    }

    private Date expiry(String raw) {
        if (raw.equalsIgnoreCase("permanent") || raw.equalsIgnoreCase("perm")) return null;
        if (!raw.matches("(?i)[1-9][0-9]*(s|m|h|d|w)")) throw new IllegalArgumentException("Durée invalide. Exemples: 30m, 2h, 7d, permanent.");
        long value = Long.parseLong(raw.substring(0, raw.length() - 1));
        long millis = switch (Character.toLowerCase(raw.charAt(raw.length() - 1))) {
            case 's' -> TimeUnit.SECONDS.toMillis(value);
            case 'm' -> TimeUnit.MINUTES.toMillis(value);
            case 'h' -> TimeUnit.HOURS.toMillis(value);
            case 'd' -> TimeUnit.DAYS.toMillis(value);
            case 'w' -> TimeUnit.DAYS.toMillis(Math.multiplyExact(value, 7));
            default -> throw new IllegalArgumentException("Unité de durée invalide.");
        };
        if (millis > TimeUnit.DAYS.toMillis(3650)) throw new IllegalArgumentException("Durée maximale: 10 ans.");
        return new Date(System.currentTimeMillis() + millis);
    }

    private String reason(String[] args, int start) {
        if (args.length <= start) return "";
        String value = String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private void openMenu(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);
        inv.setItem(10, item(Material.BOOK, "Historique des sanctions", "Utilise /sanctions <joueur>"));
        inv.setItem(12, item(Material.ANVIL, "Bannir un joueur", "Utilise /ban <joueur> <durée> <raison>"));
        inv.setItem(14, item(Material.IRON_DOOR, "Débannir un joueur", "Utilise /unban <joueur>"));
        inv.setItem(16, item(Material.PAPER, "Aide du staff", "Commandes selon tes permissions: /help"));
        inv.setItem(22, item(Material.BARRIER, "Fermer"));
        viewer.openInventory(inv);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(Arrays.stream(lore).map(line -> ChatColor.GRAY + line).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("help")) {
            tell(sender, "Commandes disponibles:");
            List<String> lines = new ArrayList<>(List.of("/balance", "/pay", "/daily", "/bounty", "/lottery", "/clan", "/shop", "/ah"));
            if (sender.hasPermission("larpers.staff") || sender.isOp()) lines.addAll(List.of("/invsee", "/freeze", "/staffchat", "/sanctions"));
            if (staff(sender)) lines.addAll(List.of("/mod", "/ban", "/unban", "/banip", "/admin"));
            if (sender.hasPermission("larpers.admin") || sender.isOp()) lines.addAll(List.of("/eco", "/announce", "/ac reset", "/admin reset"));
            for (String line : lines) tell(sender, ChatColor.YELLOW + line);
            return true;
        }
        if (!staff(sender)) {
            tell(sender, ChatColor.RED + "Permission insuffisante.");
            return true;
        }
        switch (name) {
            case "mod" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Commande réservée aux joueurs."); return true; }
                openMenu(player);
                return true;
            }
            case "sanctions" -> {
                if (args.length != 1) { tell(sender, "Usage: /sanctions <joueur>"); return true; }
                List<String> rows = data.getStringList("history." + args[0].toLowerCase(Locale.ROOT));
                tell(sender, "Historique de sanctions de " + args[0] + " (" + rows.size() + "):");
                int shown = 0;
                for (int i = rows.size() - 1; i >= 0 && shown < 15; i--, shown++) {
                    String[] part = rows.get(i).split("\\|", 5);
                    if (part.length < 5) continue;
                    tell(sender, new Date(Long.parseLong(part[0])) + " — " + part[2] + " — " + part[3] + " (par " + part[1] + ")");
                }
                if (rows.isEmpty()) tell(sender, "Aucune sanction enregistrée.");
                return true;
            }
            case "ban" -> {
                if (args.length < 3) { tell(sender, "Usage: /ban <joueur> <durée|permanent> <raison obligatoire>"); return true; }
                String why = reason(args, 2);
                if (why.isBlank()) { tell(sender, "La raison est obligatoire."); return true; }
                try {
                    Date until = expiry(args[1]);
                    String target = args[0];
                    Bukkit.getBanList(BanList.Type.NAME).addBan(target, why, until, sender.getName());
                    Player online = Bukkit.getPlayerExact(target);
                    if (online != null) online.kickPlayer(ChatColor.RED + "Banni: " + why);
                    audit(sender.getName(), target, "BAN", why, until == null ? 0L : until.getTime());
                    tell(sender, target + " a été banni " + (until == null ? "définitivement" : "jusqu'au " + until) + ".");
                } catch (IllegalArgumentException ex) { tell(sender, ChatColor.RED + ex.getMessage()); }
                return true;
            }
            case "unban" -> {
                if (args.length != 1) { tell(sender, "Usage: /unban <joueur>"); return true; }
                Bukkit.getBanList(BanList.Type.NAME).pardon(args[0]);
                audit(sender.getName(), args[0], "UNBAN", "Débannissement", 0L);
                tell(sender, args[0] + " a été débanni.");
                return true;
            }
            case "banip" -> {
                if (args.length < 3) { tell(sender, "Usage: /banip <joueur-en-ligne> <durée|permanent> <raison obligatoire>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.getAddress() == null || target.getAddress().getAddress() == null) {
                    tell(sender, "Le joueur doit être connecté pour obtenir son IP. Aucun IP n'a été deviné.");
                    return true;
                }
                String why = reason(args, 2);
                if (why.isBlank()) { tell(sender, "La raison est obligatoire."); return true; }
                try {
                    Date until = expiry(args[1]);
                    String ip = target.getAddress().getAddress().getHostAddress();
                    Bukkit.getBanList(BanList.Type.IP).addBan(ip, why, until, sender.getName());
                    audit(sender.getName(), target.getName(), "BANIP " + ip, why, until == null ? 0L : until.getTime());
                    target.kickPlayer(ChatColor.RED + "IP bannie: " + why);
                    tell(sender, "IP de " + target.getName() + " bannie. L'adresse n'est pas affichée dans le chat.");
                } catch (IllegalArgumentException ex) { tell(sender, ChatColor.RED + ex.getMessage()); }
                return true;
            }
            default -> { return false; }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("larpers.chat.bypass") || player.isOp()) return;
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        long previous = lastChat.getOrDefault(id, 0L);
        String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (now - previous < 900L || (message.equals(lastMessage.get(id)) && now - lastDuplicate.getOrDefault(id, 0L) < 5000L)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.RED + "Anti-spam: attends un peu avant de renvoyer ce message."));
            lastDuplicate.put(id, now);
            return;
        }
        lastChat.put(id, now);
        lastMessage.put(id, message);
        lastDuplicate.put(id, now);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 22) player.closeInventory();
        else if (event.getRawSlot() == 10) { player.closeInventory(); player.performCommand("sanctions"); }
        else if (event.getRawSlot() == 12) { player.closeInventory(); player.sendMessage(PREFIX + "Usage: /ban <joueur> <durée> <raison>"); }
        else if (event.getRawSlot() == 14) { player.closeInventory(); player.sendMessage(PREFIX + "Usage: /unban <joueur>"); }
        else if (event.getRawSlot() == 16) { player.closeInventory(); player.performCommand("help"); }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("ban") && args.length == 2) return List.of("30m", "2h", "1d", "7d", "30d", "permanent");
        if (name.equals("banip") && args.length == 2) return List.of("30m", "2h", "1d", "7d", "30d", "permanent");
        if (name.equals("help") && args.length == 1) return List.of("staff", "joueur");
        if (name.equals("ban") || name.equals("banip") || name.equals("unban") || name.equals("sanctions")) {
            if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (name.equals("mod") && args.length == 1) return List.of("menu");
        return List.of();
    }
}
