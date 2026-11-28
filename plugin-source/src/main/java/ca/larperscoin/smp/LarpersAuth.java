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
    private boolean accountsReadable;
    private long lastRead=-1;
    private long lastSize=-1;
    private final Map<UUID,Long> attemptExpiry=new ConcurrentHashMap<>();
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
        try {
            long modified=file.exists()?file.lastModified():0, size=file.exists()?file.length():0;
            if(accountsReadable && modified==lastRead && size==lastSize)return;
            var next=new YamlConfiguration();
            if(file.exists())next.load(file);
            accounts=next;accountsReadable=true;lastRead=modified;lastSize=size;
        }catch(Exception ex){
            if(accounts==null)accounts=new YamlConfiguration();
            if(accountsReadable||lastRead==-1)plugin.getLogger().severe("auth.yml illisible; aucune modification autorisée.");
            accountsReadable=false;lastRead=0;
        }
    }

    boolean resetAccount(String playerName) {
        reloadAccounts();
        if(!accountsReadable || playerName==null || !playerName.matches("[A-Za-z0-9_]{3,16}"))return false;
        String path = accountPath(playerName);
        if (!accounts.contains(path + ".hash")) return false;
        accounts.set(path, null);
        accounts.set("revoked." + playerName.toLowerCase(Locale.ROOT), System.currentTimeMillis());
        if(!save())return false;
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
                    && accountsReadable && player.getAddress()!=null && player.getAddress().getAddress()!=null
                    && accounts.getLong(path+".ip-expires",0)>System.currentTimeMillis()
                    && accounts.contains(path + ".hash") && accounts.contains(path + ".ip-hash")
                    && player.getUniqueId().toString().equals(accounts.getString(path+".uuid",""))
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
        long now=System.currentTimeMillis();
        lockedUntil.entrySet().removeIf(e->e.getValue()<=now);
        attemptExpiry.entrySet().removeIf(e->{if(e.getValue()>now)return false;attempts.remove(e.getKey());return true;});
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

    private final java.util.Set<UUID> hashing=ConcurrentHashMap.newKeySet();
    boolean command(Player player,String label,String[] args){
        boolean register=label.equalsIgnoreCase("register");
        if(!register&&!label.equalsIgnoreCase("login"))return false;
        if(args.length!=1){player.sendMessage("Usage : /"+label+" <motdepasse>");return true;}
        if(isAuthenticated(player)){player.sendMessage("Tu es déjà connecté.");return true;}
        if(args[0].length()<8||args[0].length()>64){player.sendMessage("Mot de passe : 8 à 64 caractères.");return true;}
        UUID id=player.getUniqueId();
        if(lockedUntil.getOrDefault(id,0L)>System.currentTimeMillis()){player.sendMessage("Trop d'essais. Réessaie plus tard.");return true;}
        reloadAccounts();
        if(!accountsReadable){player.sendMessage("Authentification indisponible.");return true;}
        String path=accountPath(player.getName());
        String expected=accounts.getString(path+".hash","");
        String owner=accounts.getString(path+".uuid","");
        if(!owner.isEmpty()&&!owner.equals(id.toString())){player.sendMessage("Compte associé à un autre UUID. Contacte un admin.");return true;}
        if(register&&!expected.isEmpty()){player.sendMessage("Compte existant. Utilise /login.");return true;}
        if(!register&&expected.isEmpty()){player.sendMessage("Aucun compte. Utilise /register.");return true;}
        final byte[] salt;
        final int iterations;
        try{
            if(register){salt=new byte[16];random.nextBytes(salt);iterations=ITERATIONS;}
            else{salt=Base64.getDecoder().decode(accounts.getString(path+".salt",""));iterations=accounts.getInt(path+".iterations",ITERATIONS);}
            if(iterations<100000||iterations>1000000||salt.length<16||salt.length>64)throw new IllegalArgumentException();
        }catch(IllegalArgumentException ex){player.sendMessage("Compte invalide. Contacte un admin.");return true;}
        if(hashing.size()>=8||!hashing.add(id)){player.sendMessage("Vérification déjà en cours ou serveur occupé.");return true;}
        char[] password=args[0].toCharArray();
        long joined=sessionJoinedAt.getOrDefault(id,0L);
        long revoked=accounts.getLong("revoked."+player.getName().toLowerCase(Locale.ROOT),0L);
        player.sendMessage("Vérification en cours...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            String calculated=null;
            try{calculated=hash(password,salt,iterations);}
            catch(GeneralSecurityException|RuntimeException ex){plugin.getLogger().warning("Calcul de mot de passe impossible.");}
            finally{java.util.Arrays.fill(password,'\0');}
            final String actual=calculated;
            Bukkit.getScheduler().runTask(plugin,()->{
                hashing.remove(id);
                if(!player.isOnline()||sessionJoinedAt.getOrDefault(id,0L)!=joined||isAuthenticated(player))return;
                reloadAccounts();
                if(actual==null||!accountsReadable||!expected.equals(accounts.getString(path+".hash",""))||!owner.equals(accounts.getString(path+".uuid",""))||revoked!=accounts.getLong("revoked."+player.getName().toLowerCase(Locale.ROOT),0L)){
                    player.sendMessage("Le compte a changé ou est indisponible. Réessaie.");return;
                }
                if(!register&&!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))){
                    attemptExpiry.put(id,System.currentTimeMillis()+LOCK_MILLIS);
                    int count=attempts.merge(id,1,Integer::sum);
                    if(count>=MAX_ATTEMPTS){lockedUntil.put(id,System.currentTimeMillis()+LOCK_MILLIS);attempts.remove(id);player.kickPlayer("Trop de mots de passe incorrects. Réessaie dans 15 minutes.");}
                    else player.sendMessage("Mot de passe incorrect ("+count+"/"+MAX_ATTEMPTS+").");
                    return;
                }
                if(register){accounts.set(path+".salt",Base64.getEncoder().encodeToString(salt));accounts.set(path+".hash",actual);accounts.set(path+".iterations",iterations);}
                accounts.set(path+".uuid",id.toString());accounts.set(path+".name",player.getName());
                accounts.set("revoked."+player.getName().toLowerCase(Locale.ROOT),null);
                rememberIp(player,path);
                if(!save()){player.sendMessage("Sauvegarde impossible. Réessaie.");return;}
                attempts.remove(id);lockedUntil.remove(id);attemptExpiry.remove(id);
                markAuthenticated(player);player.sendMessage(register?"Compte créé. Bienvenue sur Larry SMP !":"Connexion réussie. Bon jeu !");
            });
        });
        return true;
    }

    private void markAuthenticated(Player player) {
        authenticated.put(player.getUniqueId(), true);
        joinLocations.remove(player.getUniqueId());
        attempts.remove(player.getUniqueId());
        if (plugin.getConfig().getBoolean("lobby-return-compass.enabled", false)) {
            Bukkit.getScheduler().runTask(plugin, () -> giveLobbyCompass(player));
        }
        if (plugin.getConfig().getBoolean("auth.connect-to-smp", false) && !plugin.getConfig().getBoolean("lobby-return-compass.enabled",false)) {
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
        meta.setDisplayName(ChatColor.GOLD + "Rejoindre le SMP");
        meta.setLore(java.util.List.of(ChatColor.GRAY + "Clic droit pour rejoindre le SMP."));
        meta.getPersistentDataContainer().set(lobbyCompassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        if(!player.getInventory().addItem(compass).isEmpty())player.sendMessage(ChatColor.YELLOW+"Libère une place puis reconnecte-toi pour recevoir la boussole.");
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
            player.sendMessage(ChatColor.GREEN + "Téléportation vers le SMP...");
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de transférer " + player.getName() + " vers le serveur configuré: " + ex.getMessage());
            player.sendMessage(ChatColor.RED + "Impossible de rejoindre le serveur pour le moment.");
        }
    }

    private void rememberIp(Player player, String path) {
        if (plugin.getConfig().getBoolean("auth.remember-ip", false)) {
            if(player.getAddress()==null || player.getAddress().getAddress()==null){accounts.set(path+".ip-hash",null);accounts.set(path+".ip-expires",null);return;}
            accounts.set(path + ".ip-hash", ipHash(player));
            accounts.set(path+".ip-expires",System.currentTimeMillis()+30L*86400000L);
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

    private boolean save() {
        java.nio.file.Path target=file.toPath().toAbsolutePath(),tmp=null;
        try {
            if(!accountsReadable) return false;
            try(var channel=java.nio.channels.FileChannel.open(target.resolveSibling("auth.yml.lock"),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE);var lock=channel.lock()){
            if((file.exists()?file.lastModified():0)!=lastRead || (file.exists()?file.length():0)!=lastSize)throw new IOException("Fichier modifié par une autre instance");
            tmp=java.nio.file.Files.createTempFile(target.getParent(),"auth-",".tmp");
            accounts.save(tmp.toFile());
            if(file.exists())java.nio.file.Files.copy(target,target.resolveSibling("auth.yml.bak"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try{java.nio.file.Files.move(tmp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){java.nio.file.Files.move(tmp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            lastRead=file.lastModified();lastSize=file.length();return true;
            }
        }catch(IOException ex){
            plugin.getLogger().severe("Impossible de sauvegarder auth.yml: "+ex.getMessage());
            lastRead=-1;reloadAccounts();return false;
        }finally{if(tmp!=null)try{java.nio.file.Files.deleteIfExists(tmp);}catch(IOException ignored){}}
    }

    private boolean isFloodgate(Player player) {
        try {
            var floodgate=Bukkit.getPluginManager().getPlugin("floodgate");
            if(floodgate==null || !floodgate.isEnabled())return false;
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi",true,floodgate.getClass().getClassLoader());
            Object instance = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId());
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private boolean exempt(Player player) {
        // Le systeme d'authentification et ses blocages sont reserves au Lobby.
        // Sur le SMP, tout joueur est libre: ne jamais appliquer les restrictions /login.
        return plugin.getConfig().getBoolean("auth.proxy-authenticated",false) || isAuthenticated(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { handleJoin(event.getPlayer()); }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        authenticated.remove(id); joinLocations.remove(id); sessionJoinedAt.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Le verrouillage de déplacement ne concerne que le Lobby d'authentification.
        // Le SMP est derrière Velocity: les joueurs ne doivent jamais y être immobilisés
        // par le système /login, même pendant les premiers ticks de connexion.
        if (exempt(event.getPlayer()) || event.getTo() == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getX()!=to.getX() || from.getY()!=to.getY() || from.getZ()!=to.getZ()) {
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
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p && !exempt(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent e){if(e.getEntity() instanceof Player p && !exempt(p))e.setCancelled(true);}
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && !exempt(p)) e.setCancelled(true);
        if (e.getEntity() instanceof Player p && !exempt(p)) e.setCancelled(true);
    }
}
