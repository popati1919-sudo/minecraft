package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LarpersPlayerTools implements Listener, CommandExecutor, TabCompleter {
    private static final String PREFIX = ChatColor.GOLD + "[LPC] " + ChatColor.RESET;
    private static final String REPORTS_TITLE = "LPC | Signalements";
    private static final String PROFILE_TITLE = "LPC | Profil";
    private final JavaPlugin plugin;
    private final LarpersCoin core;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, SavedState> spectating = new HashMap<>();

    private record SavedState(Location location, GameMode gameMode) {}

    public LarpersPlayerTools(JavaPlugin plugin) {
        this.plugin = plugin;
        this.core = (LarpersCoin) plugin;
        this.file = new File(plugin.getDataFolder(), "player-tools.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (String name : List.of("profile", "report", "reports", "staffspec")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveOnlineSessions, 1200L, 1200L);
    }

    private boolean staff(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("larpers.reports") || sender.hasPermission("larpers.staff") || sender.hasPermission("larpers.admin");
    }

    private void tell(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + message);
    }

    private String path(UUID id, String key) {
        return "players." + id + "." + key;
    }

    private String duration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return days + "j " + hours + "h " + minutes + "m";
    }

    private long playtime(UUID id) {
        long stored = data.getLong(path(id, "playtime-ms"), 0L);
        long session = data.getLong(path(id, "session-start"), 0L);
        return stored + (session > 0L ? Math.max(0L, System.currentTimeMillis() - session) : 0L);
    }

    private void saveOnlineSessions() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!data.contains(path(id, "session-start"))) data.set(path(id, "session-start"), now);
            data.set(path(id, "name"), player.getName());
            data.set(path(id, "last-seen"), now);
        }
        save();
    }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Impossible de sauvegarder player-tools.yml: " + ex.getMessage()); }
    }

    private ItemStack item(Material material, String title, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + title);
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.GRAY + line);
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private void openProfile(Player viewer, OfflinePlayer target) {
        UUID id = target.getUniqueId();
        String name = target.getName() == null ? id.toString().substring(0, 8) : target.getName();
        Player online = target.getPlayer();
        Inventory inv = Bukkit.createInventory(null, 27, PROFILE_TITLE);
        inv.setItem(4, item(Material.PLAYER_HEAD, "Profil de " + name,
                online == null ? "Statut: hors ligne" : "Statut: en ligne",
                "Solde: " + String.format(Locale.ROOT, "%.2f", core.balance(id)) + " LPC"));
        inv.setItem(10, item(Material.IRON_SWORD, "Éliminations", "Joueurs: " + data.getInt(path(id, "kills"), 0)));
        inv.setItem(12, item(Material.SKELETON_SKULL, "Morts", "Total: " + data.getInt(path(id, "deaths"), 0)));
        inv.setItem(14, item(Material.CLOCK, "Temps de jeu", duration(playtime(id))));
        long lastSeen = data.getLong(path(id, "last-seen"), target.getLastPlayed());
        inv.setItem(16, item(Material.MAP, "Dernière connexion",
                lastSeen <= 0L ? "Inconnue" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(lastSeen))));
        inv.setItem(22, item(Material.BARRIER, "Fermer"));
        viewer.openInventory(inv);
    }

    private Object value(Map<?, ?> row, String key, Object fallback) { Object found = row.get(key); return found == null ? fallback : found; }

    private List<Map<?, ?>> reports() {
        return data.getMapList("reports");
    }

    private void openReports(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 54, REPORTS_TITLE);
        List<Map<?, ?>> rows = new ArrayList<>(reports());
        rows.sort(Comparator.comparingLong(row -> ((Number) value(row, "timestamp", 0L)).longValue()).reversed());
        int slot = 0;
        for (Map<?, ?> row : rows) {
            if (slot >= 45) break;
            int id = ((Number) value(row, "id", 0)).intValue();
            String target = String.valueOf(value(row, "target-name", "inconnu"));
            String reporter = String.valueOf(value(row, "reporter-name", "inconnu"));
            String reason = String.valueOf(value(row, "reason", "Aucun motif"));
            String status = String.valueOf(value(row, "status", "OUVERT"));
            inv.setItem(slot++, item(Material.PAPER, "#" + id + " • " + target,
                    "Signalé par: " + reporter, "Motif: " + reason, "Statut: " + status,
                    "Clique pour afficher les détails"));
        }
        if (slot == 0) inv.setItem(22, item(Material.BOOK, "Aucun signalement", "Aucun rapport enregistré."));
        inv.setItem(49, item(Material.BARRIER, "Fermer"));
        viewer.openInventory(inv);
    }

    private void showReport(CommandSender sender, Map<?, ?> row) {
        tell(sender, "Signalement #" + row.get("id") + " — " + row.get("target-name"));
        tell(sender, "Par: " + row.get("reporter-name") + " | Motif: " + row.get("reason"));
        tell(sender, "Statut: " + row.get("status") + " | ID: " + row.get("id"));
        tell(sender, "Pour clôturer: /reports resolve " + row.get("id"));
    }

    private void resolveReport(CommandSender sender, int id) {
        List<Map<?, ?>> rows = reports();
        boolean found = false;
        List<Map<String, Object>> updated = new ArrayList<>();
        for (Map<?, ?> row : rows) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> entry : row.entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
            if (((Number) copy.getOrDefault("id", 0)).intValue() == id) {
                copy.put("status", "RÉSOLU");
                copy.put("resolved-by", sender.getName());
                copy.put("resolved-at", System.currentTimeMillis());
                found = true;
            }
            updated.add(copy);
        }
        if (!found) { tell(sender, "Signalement introuvable."); return; }
        data.set("reports", updated);
        List<String> audit = data.getStringList("staff-actions");
        audit.add(System.currentTimeMillis() + "|" + sender.getName() + "|REPORT_RESOLVE|" + id);
        if (audit.size() > 500) audit = new ArrayList<>(audit.subList(audit.size() - 500, audit.size()));
        data.set("staff-actions", audit);
        save();
        tell(sender, "Signalement #" + id + " marqué comme résolu.");
    }

    private void startSpectating(Player staff, Player target) {
        if (staff.equals(target)) { tell(staff, "Tu ne peux pas te spectater toi-même."); return; }
        if (spectating.containsKey(staff.getUniqueId())) { tell(staff, "Utilise /staffspec off avant de changer de cible."); return; }
        SavedState old = new SavedState(staff.getLocation().clone(), staff.getGameMode());
        spectating.put(staff.getUniqueId(), old);
        staff.setGameMode(GameMode.SPECTATOR);
        staff.teleport(target.getLocation());
        tell(staff, "Mode spectateur activé sur " + target.getName() + ". Fais /staffspec off pour revenir.");
        target.sendMessage(ChatColor.GRAY + "Un membre du staff observe actuellement le serveur.");
        List<String> audit = data.getStringList("staff-actions");
        audit.add(System.currentTimeMillis() + "|" + staff.getName() + "|SPECTATE|" + target.getName());
        if (audit.size() > 500) audit = new ArrayList<>(audit.subList(audit.size() - 500, audit.size()));
        data.set("staff-actions", audit);
        save();
    }

    private void stopSpectating(Player staff, boolean notify) {
        SavedState old = spectating.remove(staff.getUniqueId());
        if (old == null) { if (notify) tell(staff, "Tu n'es pas en mode spectateur staff."); return; }
        staff.setGameMode(old.gameMode());
        if (old.location().getWorld() != null) staff.teleport(old.location());
        if (notify) tell(staff, "Mode spectateur terminé; position et mode restaurés.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        data.set(path(id, "name"), event.getPlayer().getName());
        data.set(path(id, "last-seen"), System.currentTimeMillis());
        if (!data.contains(path(id, "session-start"))) data.set(path(id, "session-start"), System.currentTimeMillis());
        save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        long started = data.getLong(path(id, "session-start"), System.currentTimeMillis());
        data.set(path(id, "playtime-ms"), data.getLong(path(id, "playtime-ms"), 0L) + Math.max(0L, System.currentTimeMillis() - started));
        data.set(path(id, "session-start"), null);
        data.set(path(id, "last-seen"), System.currentTimeMillis());
        if (spectating.containsKey(id)) stopSpectating(event.getPlayer(), false);
        save();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID victim = event.getEntity().getUniqueId();
        data.set(path(victim, "deaths"), data.getInt(path(victim, "deaths"), 0) + 1);
        Player killer = event.getEntity().getKiller();
        if (killer != null) data.set(path(killer.getUniqueId(), "kills"), data.getInt(path(killer.getUniqueId(), "kills"), 0) + 1);
        save();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!REPORTS_TITLE.equals(title) && !PROFILE_TITLE.equals(title)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (event.getRawSlot() == 49 || event.getRawSlot() == 22) { viewer.closeInventory(); return; }
        if (!REPORTS_TITLE.equals(title) || event.getRawSlot() < 0 || event.getRawSlot() >= 45) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;
        String display = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (!display.startsWith("#")) return;
        try {
            int id = Integer.parseInt(display.substring(1).split(" ")[0]);
            for (Map<?, ?> row : reports()) {
                if (((Number) row.getOrDefault("id", 0)).intValue() == id) { showReport(viewer, row); return; }
            }
        } catch (NumberFormatException ignored) { }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "profile" -> {
                if (!(sender instanceof Player viewer)) { tell(sender, "Commande réservée aux joueurs."); return true; }
                if (args.length > 1) { tell(sender, "Usage: /profile [joueur]"); return true; }
                OfflinePlayer target = args.length == 0 ? viewer : Bukkit.getOfflinePlayer(args[0]);
                if (args.length == 1 && !target.hasPlayedBefore() && target.getPlayer() == null) { tell(sender, "Joueur inconnu."); return true; }
                openProfile(viewer, target);
                return true;
            }
            case "report" -> {
                if (!(sender instanceof Player reporter)) { tell(sender, "Commande réservée aux joueurs."); return true; }
                if (args.length < 2) { tell(sender, "Usage: /report <joueur> <raison>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(reporter)) { tell(sender, "Choisis un autre joueur actuellement connecté."); return true; }
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
                if (reason.length() < 8 || reason.length() > 160) { tell(sender, "La raison doit contenir entre 8 et 160 caractères."); return true; }
                long last = data.getLong("report-cooldown." + reporter.getUniqueId(), 0L);
                if (System.currentTimeMillis() - last < 30000L) { tell(sender, "Attends 30 secondes entre deux signalements."); return true; }
                int nextId = data.getInt("next-report-id", 1);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<?, ?> row : reports()) {
                    Map<String, Object> copy = new HashMap<>();
                    for (Map.Entry<?, ?> entry : row.entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    rows.add(copy);
                }
                Map<String, Object> report = new HashMap<>();
                report.put("id", nextId);
                report.put("timestamp", System.currentTimeMillis());
                report.put("reporter-id", reporter.getUniqueId().toString());
                report.put("reporter-name", reporter.getName());
                report.put("target-id", target.getUniqueId().toString());
                report.put("target-name", target.getName());
                report.put("reason", reason);
                report.put("status", "OUVERT");
                rows.add(report);
                if (rows.size() > 500) rows = new ArrayList<>(rows.subList(rows.size() - 500, rows.size()));
                data.set("reports", rows);
                data.set("next-report-id", nextId + 1);
                data.set("report-cooldown." + reporter.getUniqueId(), System.currentTimeMillis());
                save();
                tell(reporter, "Signalement #" + nextId + " envoyé. Merci de ne pas abuser de cette commande.");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (staff(online)) online.sendMessage(ChatColor.RED + "[LPC] Nouveau signalement #" + nextId + " contre " + target.getName() + ".");
                }
                return true;
            }
            case "reports" -> {
                if (!staff(sender)) { tell(sender, "Permission insuffisante."); return true; }
                if (args.length == 0) {
                    if (sender instanceof Player player) openReports(player);
                    else {
                        List<Map<?, ?>> rows = reports();
                        tell(sender, "Signalements enregistrés: " + rows.size());
                        for (int i = Math.max(0, rows.size() - 10); i < rows.size(); i++) showReport(sender, rows.get(i));
                    }
                    return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("resolve")) {
                    try { resolveReport(sender, Integer.parseInt(args[1])); }
                    catch (NumberFormatException ex) { tell(sender, "ID invalide."); }
                    return true;
                }
                tell(sender, "Usage: /reports [resolve <id>]");
                return true;
            }
            case "staffspec" -> {
                if (!(sender instanceof Player staffPlayer)) { tell(sender, "Commande réservée aux joueurs."); return true; }
                if (!staff(sender)) { tell(sender, "Permission insuffisante."); return true; }
                if (args.length == 1 && args[0].equalsIgnoreCase("off")) { stopSpectating(staffPlayer, true); return true; }
                if (args.length != 1) { tell(sender, "Usage: /staffspec <joueur|off>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { tell(sender, "Le joueur doit être connecté."); return true; }
                startSpectating(staffPlayer, target);
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("reports") && args.length == 1 && staff(sender)) return List.of("resolve");
        if (name.equals("reports") && args.length == 2 && args[0].equalsIgnoreCase("resolve")) return reports().stream().map(row -> "#" + row.get("id")).toList();
        if (name.equals("staffspec") && args.length == 1 && staff(sender)) {
            List<String> names = new ArrayList<>();
            names.add("off");
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return names.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if ((name.equals("profile") || name.equals("report")) && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) if (spectating.containsKey(player.getUniqueId())) stopSpectating(player, false);
        save();
    }
}
