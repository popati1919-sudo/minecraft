package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Larpers Coin AntiCheat v1.1.
 * Conservative detection and staff alerts only. No automatic punishments.
 */
public final class LarpersAntiCheat implements Listener, CommandExecutor {
    private static final String PREFIX = ChatColor.DARK_RED + "[LPC AC] " + ChatColor.RESET;
    private static final long ALERT_COOLDOWN_MS = 4_000L;
    private static final long GRACE_MS = 1_500L;
    private static final int MAX_EVIDENCE = 50;

    private final JavaPlugin plugin;
    private final File violationsFile;
    private final Map<UUID, Long> graceUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> upwardTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastMovementTick = new ConcurrentHashMap<>();
    private final Map<UUID, Double> horizontalDistanceThisTick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastSanctionTier = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> alertsEnabled = new ConcurrentHashMap<>();
    private final Deque<String> evidence = new ArrayDeque<>();
    private YamlConfiguration storedViolations;

    public LarpersAntiCheat(JavaPlugin plugin) {
        this.plugin = plugin;
        this.violationsFile = new File(plugin.getDataFolder(), "anticheat-violations.yml");
        loadViolations();
        applyDefaults();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("ac") != null) plugin.getCommand("ac").setExecutor(this);
        for (Player player : Bukkit.getOnlinePlayers()) grantGrace(player);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveViolations, 20L * 300L, 20L * 300L);
        plugin.getLogger().info("LPC AntiCheat v1.0 chargé — alertes et journal activés, sanctions automatiques désactivées.");
    }

    private void applyDefaults() {
        var config = plugin.getConfig();
        config.addDefault("anticheat.enabled", true);
        config.addDefault("anticheat.checks.speed.enabled", true);
        config.addDefault("anticheat.checks.speed.max-ground-blocks-per-tick", 0.65D);
        config.addDefault("anticheat.checks.fly.enabled", true);
        config.addDefault("anticheat.checks.fly.upward-limit-per-tick", 0.72D);
        config.addDefault("anticheat.checks.fly.repeated-upward-ticks", 4);
        config.addDefault("anticheat.checks.reach.enabled", true);
        config.addDefault("anticheat.checks.reach.max-distance", 3.75D);
        if (config.getDouble("anticheat.checks.speed.max-ground-blocks-per-tick", 1.35D) == 1.35D)
            config.set("anticheat.checks.speed.max-ground-blocks-per-tick", 0.65D);
        if (config.getDouble("anticheat.checks.reach.max-distance", 4.45D) == 4.45D)
            config.set("anticheat.checks.reach.max-distance", 3.75D);
        config.addDefault("anticheat.alert-cooldown-seconds", 4);
        config.addDefault("anticheat.evidence-limit", MAX_EVIDENCE);
        config.addDefault("anticheat.auto-punish.enabled", true);
        config.addDefault("anticheat.auto-punish.thresholds.kick", 12);
        config.addDefault("anticheat.auto-punish.thresholds.ban-1d", 24);
        config.addDefault("anticheat.auto-punish.thresholds.ban-7d", 36);
        config.addDefault("anticheat.auto-punish.thresholds.ban-30d", 48);
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    private void loadViolations() {
        storedViolations = YamlConfiguration.loadConfiguration(violationsFile);
        for (String raw : storedViolations.getKeys(false)) {
            try {
                UUID id = UUID.fromString(raw);
                int count = Math.max(0, storedViolations.getInt(raw, 0));
                if (count > 0) violations.put(id, count);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("UUID invalide dans anticheat-violations.yml: " + raw);
            }
        }
    }

    public void shutdown() {
        saveViolations();
    }

    private void saveViolations() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Impossible de créer le dossier anti-triche.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        violations.forEach((id, count) -> yaml.set(id.toString(), count));
        Path target = violationsFile.toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            yaml.save(temp.toFile());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder les violations anti-triche: " + ex.getMessage());
        }
    }

    private void grantGrace(Player player) {
        graceUntil.put(player.getUniqueId(), System.currentTimeMillis() + GRACE_MS);
        upwardTicks.remove(player.getUniqueId());
        airTicks.remove(player.getUniqueId());
        lastMovementTick.remove(player.getUniqueId());
        horizontalDistanceThisTick.remove(player.getUniqueId());
    }

    private boolean inGrace(Player player) {
        return graceUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private boolean bypass(Player player) {
        return player.hasPermission("larpers.anticheat.bypass");
    }

    private boolean globallyEnabled() {
        return plugin.getConfig().getBoolean("anticheat.enabled", true);
    }

    private boolean movementExempt(Player p) {
        return p.isDead() || p.isInsideVehicle() || p.isFlying() || p.getAllowFlight()
                || p.isGliding() || p.isSwimming() || p.isRiptiding()
                || p.isInWater() || p.isClimbing() || p.isSleeping()
                || p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || bypass(p) || inGrace(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        grantGrace(event.getPlayer());
        UUID id = event.getPlayer().getUniqueId();
        lastSanctionTier.put(id, tierFor(violations.getOrDefault(id, 0)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        grantGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        grantGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        upwardTicks.remove(id);
        airTicks.remove(id);
        lastMovementTick.remove(id);
        horizontalDistanceThisTick.remove(id);
        graceUntil.remove(id);
        alertsEnabled.remove(id);
        lastSanctionTier.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!globallyEnabled()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (movementExempt(player)) {
            upwardTicks.remove(id);
            airTicks.remove(id);
            lastMovementTick.remove(id);
            return;
        }
        int currentTick = player.getTicksLived();
        Integer previousTick = lastMovementTick.put(id, currentTick);
        if (previousTick == null || previousTick != currentTick) horizontalDistanceThisTick.put(id, 0.0D);

        if (plugin.getConfig().getBoolean("anticheat.checks.speed.enabled", true)
                && player.isOnGround()) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double segment = Math.sqrt(dx * dx + dz * dz);
            double horizontal = horizontalDistanceThisTick.merge(id, segment, Double::sum);
            double limit = plugin.getConfig().getDouble("anticheat.checks.speed.max-ground-blocks-per-tick", 0.65D);
            if (player.isSprinting()) limit += 0.12D;
            PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
            if (speed != null) limit += 0.18D * (speed.getAmplifier() + 1);
            if (horizontal > limit) {
                flag(player, "SPEED", String.format(Locale.ROOT, "%.2f blocs/tick (seuil %.2f)", horizontal, limit));
            }
        }

        if (plugin.getConfig().getBoolean("anticheat.checks.fly.enabled", true)) {
            double dy = to.getY() - from.getY();
            double upLimit = plugin.getConfig().getDouble("anticheat.checks.fly.upward-limit-per-tick", 0.72D);
            PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
            if (jump != null) upLimit += 0.12D * (jump.getAmplifier() + 1);
            int required = Math.max(2, plugin.getConfig().getInt("anticheat.checks.fly.repeated-upward-ticks", 4));
            if (!player.isOnGround() && dy > upLimit) {
                int count = upwardTicks.merge(id, 1, Integer::sum);
                if (count >= required) {
                    flag(player, "FLY", "montées verticales répétées sans exemption");
                    upwardTicks.put(id, 0);
                }
            } else if (dy <= 0.0D || player.isOnGround()) {
                upwardTicks.remove(id);
            }
            if (!player.isOnGround() && Math.abs(dy) < 0.025D) {
                int hovering = airTicks.merge(id, 1, Integer::sum);
                if (hovering >= 40) {
                    flag(player, "FLY_HOVER", "position verticale presque immobile en plein air pendant 40 ticks");
                    airTicks.put(id, 0);
                }
            } else if (dy < -0.05D || player.isOnGround()) {
                airTicks.remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!globallyEnabled() || !plugin.getConfig().getBoolean("anticheat.checks.reach.enabled", true)) return;
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        if (attacker.getWorld() != victim.getWorld() || bypass(attacker) || inGrace(attacker)) return;
        double distance = attacker.getEyeLocation().distance(victim.getLocation().add(0.0D, 1.0D, 0.0D));
        double limit = plugin.getConfig().getDouble("anticheat.checks.reach.max-distance", 4.45D);
        if (distance > limit) {
            flag(attacker, "REACH", String.format(Locale.ROOT, "%.2f blocs (seuil %.2f)", distance, limit));
        }
    }

    private void flag(Player player, String check, String detail) {
        int total = violations.merge(player.getUniqueId(), 1, Integer::sum);
        long now = System.currentTimeMillis();
        long cooldown = Math.max(1L, plugin.getConfig().getLong("anticheat.alert-cooldown-seconds", 4L)) * 1000L;
        String key = player.getUniqueId() + ":" + check;
        long previous = lastAlert.getOrDefault(key, 0L);
        if (now - previous < cooldown) return;
        lastAlert.put(key, now);

        String plain = player.getName() + " | " + check + " | " + detail + " | VL " + total;
        synchronized (evidence) {
            evidence.addFirst(now + " | " + plain);
            while (evidence.size() > Math.max(1, plugin.getConfig().getInt("anticheat.evidence-limit", MAX_EVIDENCE))) evidence.removeLast();
        }
        plugin.getLogger().warning("[AntiCheat] " + plain);
        applyAutomaticSanction(player, total);
        String message = PREFIX + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " — "
                + ChatColor.RED + check + ChatColor.GRAY + " — " + detail
                + ChatColor.DARK_GRAY + " (VL " + total + ")";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("larpers.anticheat.alerts") && alertsEnabled.getOrDefault(staff.getUniqueId(), true)) {
                staff.sendMessage(message);
            }
        }
    }

    private int tierFor(int count) {
        var config = plugin.getConfig();
        if (count >= config.getInt("anticheat.auto-punish.thresholds.ban-30d", 48)) return 4;
        if (count >= config.getInt("anticheat.auto-punish.thresholds.ban-7d", 36)) return 3;
        if (count >= config.getInt("anticheat.auto-punish.thresholds.ban-1d", 24)) return 2;
        if (count >= config.getInt("anticheat.auto-punish.thresholds.kick", 12)) return 1;
        return 0;
    }

    private void applyAutomaticSanction(Player player, int totalViolations) {
        if (!plugin.getConfig().getBoolean("anticheat.auto-punish.enabled", true)
                || player.hasPermission("larpers.anticheat.bypass")) return;
        UUID id = player.getUniqueId();
        int tier = tierFor(totalViolations);
        int previousTier = lastSanctionTier.getOrDefault(id, tierFor(totalViolations - 1));
        if (tier <= previousTier) return;
        lastSanctionTier.put(id, tier);

        String reason;
        Date expires;
        switch (tier) {
            case 1 -> {
                reason = "Anti-cheat LPC: seuil de violations atteint (kick).";
                player.kickPlayer(ChatColor.RED + reason);
                plugin.getLogger().warning("[AntiCheat] Kick automatique: " + player.getName()
                        + " (" + totalViolations + " VL)");
                return;
            }
            case 2 -> {
                reason = "Anti-cheat LPC: violations répétées (ban 1 jour).";
                expires = new Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L);
            }
            case 3 -> {
                reason = "Anti-cheat LPC: violations répétées (ban 7 jours).";
                expires = new Date(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L);
            }
            case 4 -> {
                reason = "Anti-cheat LPC: violations répétées (ban 30 jours).";
                expires = new Date(System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L);
            }
            default -> { return; }
        }
        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, expires, "LarpersAntiCheat");
        player.kickPlayer(ChatColor.RED + reason);
        plugin.getLogger().warning("[AntiCheat] " + reason + " Joueur: " + player.getName()
                + " (" + totalViolations + " VL)");
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("larpers.admin");
    }

    private boolean isModerator(CommandSender sender) {
        return isAdmin(sender) || sender.hasPermission("larpers.anticheat.mod")
                || sender.hasPermission("larpers.anticheat.use");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isModerator(sender)) {
            send(sender, ChatColor.RED + "Permission insuffisante. Réservé au staff anti-triche.");
            return true;
        }
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if ((subcommand.equals("reset") || subcommand.equals("reload")) && !isAdmin(sender)) {
            send(sender, ChatColor.RED + "Cette action est réservée aux administrateurs.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            send(sender, "/ac status | alerts | info <joueur> | top | log | reset <joueur> | reload");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                send(sender, "État: " + (globallyEnabled() ? ChatColor.GREEN + "ACTIF" : ChatColor.RED + "DÉSACTIVÉ")
                        + ChatColor.GRAY + " | Speed/Fly/Reach configurables | sanctions automatiques progressives: actives.");
                return true;
            }
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "Commande réservée à un joueur connecté.");
                    return true;
                }
                boolean enabled = !alertsEnabled.getOrDefault(player.getUniqueId(), true);
                alertsEnabled.put(player.getUniqueId(), enabled);
                send(sender, enabled ? ChatColor.GREEN + "Alertes activées." : ChatColor.YELLOW + "Alertes désactivées.");
                return true;
            }
            case "info" -> {
                if (args.length != 2) {
                    send(sender, "Usage: /ac info <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    send(sender, "Joueur introuvable ou hors ligne.");
                    return true;
                }
                send(sender, target.getName() + " — " + violations.getOrDefault(target.getUniqueId(), 0) + " violation(s) enregistrée(s).");
                return true;
            }
            case "top" -> {
                List<Map.Entry<UUID, Integer>> top = new ArrayList<>(violations.entrySet());
                top.sort(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()));
                send(sender, "Top joueurs avec alertes:");
                int rank = 0;
                for (Map.Entry<UUID, Integer> entry : top) {
                    if (rank++ >= 10) break;
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    sender.sendMessage(ChatColor.GRAY + "" + rank + ". " + ChatColor.YELLOW
                            + (name == null ? entry.getKey().toString().substring(0, 8) : name)
                            + ChatColor.GRAY + " — " + entry.getValue() + " VL");
                }
                if (top.isEmpty()) send(sender, "Aucune violation enregistrée.");
                return true;
            }
            case "log" -> {
                synchronized (evidence) {
                    if (evidence.isEmpty()) {
                        send(sender, "Aucune alerte récente.");
                    } else {
                        send(sender, "Dernières alertes (maximum " + evidence.size() + "):");
                        int shown = 0;
                        for (String line : evidence) {
                            if (shown++ >= 10) break;
                            sender.sendMessage(ChatColor.GRAY + line);
                        }
                    }
                }
                return true;
            }
            case "reset" -> {
                if (args.length != 2) {
                    send(sender, "Usage: /ac reset <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    send(sender, "Joueur introuvable ou hors ligne.");
                    return true;
                }
                violations.remove(target.getUniqueId());
                upwardTicks.remove(target.getUniqueId());
                airTicks.remove(target.getUniqueId());
                saveViolations();
                send(sender, "Compteur de " + target.getName() + " remis à zéro.");
                return true;
            }
            case "reload" -> {
                plugin.reloadConfig();
                applyDefaults();
                send(sender, ChatColor.GREEN + "Configuration anti-triche rechargée.");
                return true;
            }
            default -> {
                send(sender, "Sous-commande inconnue. Utilise /ac help.");
                return true;
            }
        }
    }
}
