package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class LarpersAuth implements Listener {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 15 * 60_000L;
    private final JavaPlugin plugin;
    private final NamespacedKey lobbyCompassKey;
    private final File file;
    private YamlConfiguration accounts;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Boolean> authenticated = new ConcurrentHashMap<>();
    private final Map<UUID, Location> joinLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionJoinedAt = new ConcurrentHashMap<>();

    LarpersAuth(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lobbyCompassKey = new NamespacedKey(plugin, "lobby_return_compass");
        File siblingLobbyData = new File(plugin.getServer().getWorldContainer(),
                "../Lobby/plugins/LarpersCoin");
        boolean networkBackend = !plugin.getConfig().getBoolean("network.auth-only", false)
                && siblingLobbyData.isDirectory();
        String storage = plugin.getConfig().getString("auth.storage-file");
        if (storage == null || storage.isBlank()) {
            storage = networkBackend ? "../Lobby/plugins/LarpersCoin/auth.yml" : "auth.yml";
        }
        Path storagePath = Path.of(storage);
        if (!storagePath.isAbsolute()) {
            storagePath = storage.equals("auth.yml")
                    ? plugin.getDataFolder().toPath().resolve(storage)
                    : plugin.getServer().getWorldContainer().toPath().resolve(storage).normalize();
        }
        this.file = storagePath.toFile();
        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        reloadAccounts();
        plugin.getConfig().addDefault("auth.proxy-authenticated", false);
        // Un serveur autonome ne doit jamais considérer tous les joueurs comme déjà authentifiés.
        if (!plugin.getConfig().getBoolean("network.auth-only", false) && !siblingLobbyData.isDirectory()) {
            plugin.getConfig().set("auth.proxy-authenticated", false);
        }
        plugin.getConfig().addDefault("auth.remember-ip", false);
        plugin.getConfig().addDefault("auth.connect-to-smp", false);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::enforceRevokedAccounts, 20L, 20L);
    }

    private void reloadAccounts() {
        accounts = YamlConfiguration.loadConfiguration(file);
    }

    boolean resetAccount(String playerName) {
        reloadAccounts();
        String path = accountPath(playerName);
        if (!accounts.contains(path + ".hash")) return false;
        accounts.set(path, null);
        accounts.set("revoked." + playerName.toLowerCase(Locale.ROOT), System.currentTimeMillis());
        save();
        Player online = null;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (candidate.getName().equalsIgnoreCase(playerName)) { online = candidate; break; }
        }
        if (online != null) {
            UUID id = online.getUniqueId();
            authenticated.remove(id);
            attempts.remove(id);
            lockedUntil.remove(id);
            online.kickPlayer(ChatColor.RED + "Ton mot de passe a été réinitialisé par l'administration. Rejoins le serveur et crée un nouveau mot de passe avec /register <motdepasse>.");
        }
        return true;
    }

    void handleJoin(Player player) {
        reloadAccounts();
        authenticated.remove(player.getUniqueId());
        boolean mustReRegister = accounts.getLong("revoked." + player.getName().toLowerCase(Locale.ROOT), 0L) > 0L;
        sessionJoinedAt.put(player.getUniqueId(), System.currentTimeMillis());
        joinLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.sendMessage(ChatColor.YELLOW + "Vérification de ton compte...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || authenticated.containsKey(player.getUniqueId())) return;
            // Le backend est privé derrière Velocity: le proxy valide les comptes Java
            // en mode online et Floodgate valide les comptes Bedrock.
            if (!mustReRegister && (plugin.getConfig().getBoolean("auth.proxy-authenticated", false)
                    || isFloodgate(player))) {
                markAuthenticated(player);
                player.sendMessage(ChatColor.GREEN + "Connexion automatique réussie.");
                return;
            }
            String path = accountPath(player.getName());
            if (!mustReRegister && plugin.getConfig().getBoolean("auth.remember-ip", false)
                    && accounts.contains(path + ".ip-hash")
                    && MessageDigest.isEqual(accounts.getString(path + ".ip-hash", "").getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    ipHash(player).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                markAuthenticated(player);
                player.sendMessage(ChatColor.GREEN + "Reconnu automatiquement depuis cette adresse IP.");
                return;
            }
            if (accounts.contains(path + ".hash")) {
                player.sendMessage(ChatColor.RED + "Connecte-toi avec /login <motdepasse>.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Crée ton compte avec /register <motdepasse>.");
            }
            player.sendMessage(ChatColor.GRAY + "Tu restes dans le lobby jusqu'à ton authentification.");
        }, 40L);
    }

    private void enforceRevokedAccounts() {
        reloadAccounts();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String revokedPath = "revoked." + player.getName().toLowerCase(Locale.ROOT);
            long revokedAt = accounts.getLong(revokedPath, 0L);
            long joinedAt = sessionJoinedAt.getOrDefault(player.getUniqueId(), Long.MAX_VALUE);
            if (revokedAt > 0L && joinedAt <= revokedAt) {
                authenticated.remove(player.getUniqueId());
                player.kickPlayer(ChatColor.RED + "Ton mot de passe a été réinitialisé. Rejoins et crée un nouveau mot de passe avec /register <motdepasse>.");
            }
        }
    }

    boolean isAuthenticated(Player player) {
        return authenticated.getOrDefault(player.getUniqueId(), false);
    }

    boolean command(Player player, String label, String[] args) {
        reloadAccounts();
        if (label.equalsIgnoreCase("register")) {
            if (args.length != 1) {
                player.sendMessage(ChatColor.YELLOW + "Usage : /register <motdepasse>");
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
                accounts.set("revoked." + player.getName().toLowerCase(Locale.ROOT), null);
                rememberIp(player, path);
                save();
                markAuthenticated(player);
                player.sendMessage(ChatColor.GREEN + "Compte créé. Bienvenue sur Larry SMP !");
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
                player.sendMessage(ChatColor.RED + "Aucun compte trouvé. Utilise /register <motdepasse>.");
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
                    rememberIp(player, path);
                    save();
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
        if (plugin.getConfig().getBoolean("lobby-return-compass.enabled", false)) {
            Bukkit.getScheduler().runTask(plugin, () -> giveLobbyCompass(player));
        }
        if (plugin.getConfig().getBoolean("auth.connect-to-smp", false)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                try {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
                    out.writeUTF("Connect");
                    out.writeUTF("smp");
                    player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
                } catch (IOException ex) {
                    plugin.getLogger().warning("Impossible de transférer " + player.getName() + " vers le SMP: " + ex.getMessage());
                }
            });
        }
    }

    private void giveLobbyCompass(Player player) {
        if (!player.isOnline()) return;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLobbyCompass(item)) return;
        }
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(ChatColor.GOLD + "Retour au lobby");
        meta.setLore(java.util.List.of(ChatColor.GRAY + "Clic droit pour retourner au lobby."));
        meta.getPersistentDataContainer().set(lobbyCompassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        player.getInventory().addItem(compass);
    }

    private boolean isLobbyCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(lobbyCompassKey, PersistentDataType.BYTE);
    }

    private void returnToLobby(Player player) {
        String target = plugin.getConfig().getString("lobby-return-compass.server", "lobby");
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(target);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            player.sendMessage(ChatColor.GREEN + "Téléportation vers le lobby...");
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de transférer " + player.getName() + " vers le lobby: " + ex.getMessage());
            player.sendMessage(ChatColor.RED + "Impossible de rejoindre le lobby pour le moment.");
        }
    }

    private void rememberIp(Player player, String path) {
        if (plugin.getConfig().getBoolean("auth.remember-ip", false)) {
            accounts.set(path + ".ip-hash", ipHash(player));
        }
    }

    private String ipHash(Player player) {
        try {
            String ip = player.getAddress() == null || player.getAddress().getAddress() == null
                    ? "unknown" : player.getAddress().getAddress().getHostAddress();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((player.getUniqueId() + "|" + ip).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
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
            java.util.Arrays.fill(password, '\0');
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

    private boolean exempt(Player player) {
        return isAuthenticated(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { handleJoin(event.getPlayer()); }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        authenticated.remove(id); joinLocations.remove(id); attempts.remove(id); lockedUntil.remove(id); sessionJoinedAt.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (exempt(event.getPlayer()) || event.getTo() == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX()!=to.getBlockX() || from.getBlockY()!=to.getBlockY() || from.getBlockZ()!=to.getBlockZ()) {
            Location safe = joinLocations.get(event.getPlayer().getUniqueId());
            if (safe != null) {
                // Bloque uniquement le déplacement avant connexion, jamais la caméra.
                // Education/Bedrock doit pouvoir tourner même si sa position reste verrouillée.
                Location blocked = safe.clone();
                blocked.setYaw(to.getYaw());
                blocked.setPitch(to.getPitch());
                event.setTo(blocked);
            }
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
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (!exempt(player)) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.getConfig().getBoolean("lobby-return-compass.enabled", false)
                || e.getHand() != EquipmentSlot.HAND
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !isLobbyCompass(e.getItem())) return;
        e.setCancelled(true);
        returnToLobby(player);
    }
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
