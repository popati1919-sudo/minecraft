package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class LarpersAuth implements Listener {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 15 * 60_000L;
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration accounts;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Boolean> authenticated = new ConcurrentHashMap<>();
    private final Map<UUID, Location> joinLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<>();

    LarpersAuth(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auth.yml");
        this.accounts = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void handleJoin(Player player) {
        authenticated.remove(player.getUniqueId());
        joinLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.sendMessage(ChatColor.GOLD + "Bienvenue sur Larpers Coin SMP.");
        player.sendMessage(ChatColor.YELLOW + "Vérification de ton compte...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || authenticated.containsKey(player.getUniqueId())) return;
            if (isFloodgate(player) || isPremium(player)) {
                authenticated.put(player.getUniqueId(), true);
                player.sendMessage(ChatColor.GREEN + "Connexion automatique réussie.");
                joinLocations.remove(player.getUniqueId());
                return;
            }
            String path = accountPath(player.getName());
            if (accounts.contains(path + ".hash")) {
                player.sendMessage(ChatColor.RED + "Connecte-toi avec /login <motdepasse>.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Crée ton compte avec /register <motdepasse> <confirmation>.");
            }
            player.sendMessage(ChatColor.GRAY + "Tu restes dans le lobby jusqu'à ton authentification.");
        }, 40L);
    }

    boolean isAuthenticated(Player player) {
        return authenticated.getOrDefault(player.getUniqueId(), false);
    }

    boolean command(Player player, String label, String[] args) {
        if (label.equalsIgnoreCase("register")) {
            if (args.length != 2) {
                player.sendMessage(ChatColor.YELLOW + "Usage : /register <motdepasse> <confirmation>");
                return true;
            }
            if (isAuthenticated(player)) {
                player.sendMessage(ChatColor.GREEN + "Tu es déjà connecté.");
                return true;
            }
            String path = accountPath(player.getName());
            if (accounts.contains(path + ".hash")) {
                player.sendMessage(ChatColor.RED + "Ce pseudo possède déjà un compte. Utilise /login.");
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(ChatColor.RED + "Les deux mots de passe ne correspondent pas.");
                return true;
            }
            if (args[0].length() < 8 || args[0].length() > 64) {
                player.sendMessage(ChatColor.RED + "Le mot de passe doit contenir entre 8 et 64 caractères.");
                return true;
            }
            try {
                byte[] salt = new byte[16];
                random.nextBytes(salt);
                accounts.set(path + ".salt", Base64.getEncoder().encodeToString(salt));
                accounts.set(path + ".hash", hash(args[0].toCharArray(), salt));
                accounts.set(path + ".iterations", ITERATIONS);
                accounts.set(path + ".name", player.getName());
                save();
                markAuthenticated(player);
                player.sendMessage(ChatColor.GREEN + "Compte créé. Bienvenue sur Larpers Coin !");
            } catch (GeneralSecurityException ex) {
                plugin.getLogger().severe("Erreur de hachage du mot de passe: " + ex.getMessage());
                player.sendMessage(ChatColor.RED + "Erreur interne. Réessaie plus tard.");
            }
            return true;
        }
        if (label.equalsIgnoreCase("login")) {
            if (args.length != 1) {
                player.sendMessage(ChatColor.YELLOW + "Usage : /login <motdepasse>");
                return true;
            }
            if (isAuthenticated(player)) {
                player.sendMessage(ChatColor.GREEN + "Tu es déjà connecté.");
                return true;
            }
            long lock = lockedUntil.getOrDefault(player.getUniqueId(), 0L);
            if (System.currentTimeMillis() < lock) {
                long seconds = Math.max(1, (lock - System.currentTimeMillis()) / 1000);
                player.sendMessage(ChatColor.RED + "Trop d'essais. Réessaie dans " + seconds + " secondes.");
                return true;
            }
            String path = accountPath(player.getName());
            if (!accounts.contains(path + ".hash")) {
                player.sendMessage(ChatColor.RED + "Aucun compte trouvé. Utilise /register <motdepasse> <confirmation>.");
                return true;
            }
            try {
                byte[] salt = Base64.getDecoder().decode(accounts.getString(path + ".salt", ""));
                String expected = accounts.getString(path + ".hash", "");
                int iterations = Math.max(100_000, accounts.getInt(path + ".iterations", ITERATIONS));
                String actual = hash(args[0].toCharArray(), salt, iterations);
                if (MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                    attempts.remove(player.getUniqueId());
                    lockedUntil.remove(player.getUniqueId());
                    markAuthenticated(player);
                    player.sendMessage(ChatColor.GREEN + "Connexion réussie. Bon jeu !");
                } else {
                    int count = attempts.merge(player.getUniqueId(), 1, Integer::sum);
                    if (count >= MAX_ATTEMPTS) {
                        lockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + LOCK_MILLIS);
                        attempts.remove(player.getUniqueId());
                        player.kickPlayer(ChatColor.RED + "Trop de mots de passe incorrects. Réessaie dans 15 minutes.");
                    } else {
                        player.sendMessage(ChatColor.RED + "Mot de passe incorrect (" + count + "/" + MAX_ATTEMPTS + ").");
                    }
                }
            } catch (GeneralSecurityException | IllegalArgumentException ex) {
                plugin.getLogger().warning("Impossible de vérifier le compte " + player.getName());
                player.sendMessage(ChatColor.RED + "Erreur de vérification du compte.");
            }
            return true;
        }
        return false;
    }

    private void markAuthenticated(Player player) {
        authenticated.put(player.getUniqueId(), true);
        joinLocations.remove(player.getUniqueId());
        attempts.remove(player.getUniqueId());
    }

    private String accountPath(String name) {
        return "accounts." + name.toLowerCase(Locale.ROOT);
    }

    private String hash(char[] password, byte[] salt) throws GeneralSecurityException {
        return hash(password, salt, ITERATIONS);
    }

    private String hash(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            byte[] result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(result);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(password, '\\0');
        }
    }

    private void save() {
        try { accounts.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Impossible de sauvegarder auth.yml: " + ex.getMessage()); }
    }

    private boolean isFloodgate(Player player) {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId());
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private boolean isPremium(Player player) {
        Plugin fastLogin = Bukkit.getPluginManager().getPlugin("FastLoginPlus");
        if (fastLogin == null) fastLogin = Bukkit.getPluginManager().getPlugin("FastLogin");
        if (fastLogin == null || !fastLogin.isEnabled()) return false;
        try {
            Object status = fastLogin.getClass().getMethod("getStatus", UUID.class).invoke(fastLogin, player.getUniqueId());
            return status != null && status.toString().equalsIgnoreCase("PREMIUM");
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private boolean exempt(Player player) {
        return isAuthenticated(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { handleJoin(event.getPlayer()); }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        authenticated.remove(id); joinLocations.remove(id); attempts.remove(id); lockedUntil.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (exempt(event.getPlayer()) || event.getTo() == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX()!=to.getBlockX() || from.getBlockY()!=to.getBlockY() || from.getBlockZ()!=to.getBlockZ()) {
            Location safe = joinLocations.get(event.getPlayer().getUniqueId());
            if (safe != null) event.setTo(safe);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (exempt(event.getPlayer())) return;
        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        if (!(msg.startsWith("/login ") || msg.equals("/login") || msg.startsWith("/register ") || msg.equals("/register"))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Connecte-toi d'abord avec /login ou /register.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) { if (!exempt(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (!exempt(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (!exempt(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) { if (!exempt(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { if (!exempt(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { if (!exempt(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) { if (!exempt(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && !exempt(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && !exempt(p)) e.setCancelled(true);
        if (e.getEntity() instanceof Player p && !exempt(p)) e.setCancelled(true);
    }
}
