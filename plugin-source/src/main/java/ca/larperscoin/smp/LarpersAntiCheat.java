package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conservative, alert-only anti-cheat checks for the Larpers Coin SMP.
 * Suspicion is never treated as proof: this class does not kick or ban players.
 */
public final class LarpersAntiCheat implements Listener, CommandExecutor {
    private static final String PREFIX = ChatColor.DARK_RED + "[LPC AntiCheat] " + ChatColor.RESET;
    private static final long GRACE_MILLIS = 1_500L;
    private static final long ALERT_COOLDOWN_MILLIS = 4_000L;
    private static final double MAX_GROUND_HORIZONTAL = 1.12D;
    private static final double MAX_UPWARD_STEP = 0.72D;
    private static final double REACH_ALERT_DISTANCE = 4.45D;

    private final JavaPlugin plugin;
    private final Map<UUID, Long> graceUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> upwardTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();
    private final Set<UUID> alertsEnabled = ConcurrentHashMap.newKeySet();

    public LarpersAntiCheat(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("ac") != null) {
            plugin.getCommand("ac").setExecutor(this);
        }
        for (Player player : Bukkit.getOnlinePlayers()) grantGrace(player);
        plugin.getLogger().info("AntiCheat LPC chargé (détections conservatrices, alertes staff, aucune sanction automatique).");
    }

    private void grantGrace(Player player) {
        graceUntil.put(player.getUniqueId(), System.currentTimeMillis() + GRACE_MILLIS);
        upwardTicks.remove(player.getUniqueId());
    }

    private boolean inGrace(Player player) {
        return graceUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private boolean exemptFromMovementChecks(Player player) {
        return player.isDead()
                || player.isInsideVehicle()
                || player.isFlying()
                || player.getAllowFlight()
                || player.isGliding()
                || player.isSwimming()
                || player.isRiptiding()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.hasPermission("larpers.anticheat.bypass")
                || inGrace(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        grantGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        grantGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        grantGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (exemptFromMovementChecks(player)) {
            upwardTicks.remove(id);
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double limit = MAX_GROUND_HORIZONTAL;
        if (player.isSprinting()) limit += 0.12D;
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            var effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null) limit += 0.18D * (effect.getAmplifier() + 1);
        }

        if (player.isOnGround() && horizontal > limit) {
            flag(player, "SPEED", String.format(java.util.Locale.ROOT,
                    "déplacement %.2f blocs/tick (seuil %.2f)", horizontal, limit));
        }

        double dy = to.getY() - from.getY();
        if (!player.isOnGround() && dy > MAX_UPWARD_STEP) {
            int ticks = upwardTicks.merge(id, 1, Integer::sum);
            if (ticks >= 3) {
                flag(player, "FLY", "montée verticale répétée sans autorisation");
                upwardTicks.put(id, 0);
            }
        } else if (dy <= 0.0D || player.isOnGround()) {
            upwardTicks.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        Entity target = event.getEntity();
        if (!(target instanceof Player victim)) return;
        if (attacker.getWorld() != victim.getWorld() || inGrace(attacker)
                || attacker.hasPermission("larpers.anticheat.bypass")) return;

        // Eye-to-eye distance is deliberately conservative; this only raises a staff alert.
        double distance = attacker.getEyeLocation().distance(victim.getEyeLocation());
        if (distance > REACH_ALERT_DISTANCE) {
            flag(attacker, "REACH", String.format(java.util.Locale.ROOT,
                    "distance de frappe %.2f blocs (seuil %.2f)", distance, REACH_ALERT_DISTANCE));
        }
    }

    private void flag(Player player, String check, String detail) {
        int total = violations.merge(player.getUniqueId(), 1, Integer::sum);
        long now = System.currentTimeMillis();
        String cooldownKey = player.getUniqueId() + ":" + check;
        long previous = lastAlert.getOrDefault(cooldownKey, 0L);
        if (now - previous < ALERT_COOLDOWN_MILLIS) return;
        lastAlert.put(cooldownKey, now);

        String message = PREFIX + ChatColor.YELLOW + player.getName()
                + ChatColor.GRAY + " — " + ChatColor.RED + check
                + ChatColor.GRAY + " — " + detail
                + ChatColor.DARK_GRAY + " (VL " + total + ")";
        plugin.getLogger().warning("[AntiCheat] " + player.getName() + " " + check + ": " + detail + " (VL " + total + ")");
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("larpers.staff") || staff.hasPermission("larpers.admin") || staff.isOp()) {
                if (alertsEnabled.contains(staff.getUniqueId())) staff.sendMessage(message);
            }
        }
    }

    private boolean isStaff(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("larpers.staff") || sender.hasPermission("larpers.admin");
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isStaff(sender)) {
            reply(sender, ChatColor.RED + "Permission insuffisante.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            reply(sender, "/ac alerts | info <joueur> | reset <joueur>");
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    reply(sender, "Cette option doit être utilisée en jeu.");
                    return true;
                }
                UUID id = player.getUniqueId();
                if (!alertsEnabled.add(id)) {
                    alertsEnabled.remove(id);
                    reply(sender, "Alertes désactivées.");
                } else {
                    reply(sender, "Alertes activées.");
                }
                return true;
            }
            case "info" -> {
                if (args.length != 2) {
                    reply(sender, "Usage : /ac info <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    reply(sender, "Joueur introuvable ou hors ligne.");
                    return true;
                }
                reply(sender, target.getName() + " — violations observées : "
                        + violations.getOrDefault(target.getUniqueId(), 0));
                return true;
            }
            case "reset" -> {
                if (args.length != 2) {
                    reply(sender, "Usage : /ac reset <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    reply(sender, "Joueur introuvable ou hors ligne.");
                    return true;
                }
                violations.remove(target.getUniqueId());
                upwardTicks.remove(target.getUniqueId());
                reply(sender, "Compteur de " + target.getName() + " remis à zéro.");
                return true;
            }
            default -> {
                reply(sender, "Sous-commande inconnue. Utilise /ac help.");
                return true;
            }
        }
    }
}
