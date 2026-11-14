package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LarpersCoin extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor, TabCompleter {
    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (!"empty".equalsIgnoreCase(id)) return null;
        return new org.bukkit.generator.ChunkGenerator() {
            @Override
            public org.bukkit.generator.ChunkGenerator.ChunkData generateChunkData(
                    org.bukkit.World world, java.util.Random random, int chunkX, int chunkZ, BiomeGrid biome) {
                ChunkData data = createChunkData(world);
                // Petite plateforme de départ au centre; tous les autres blocs restent dans le vide.
                if (chunkX == 0 && chunkZ == 0) {
                    for (int x = 3; x <= 12; x++) {
                        for (int z = 3; z <= 12; z++) {
                            data.setBlock(x, 80, z, org.bukkit.Material.SMOOTH_STONE);
                        }
                    }
                }
                return data;
            }
        };
    }
    private YamlConfiguration data;
    private LarpersFeatures features;
    private LarpersAntiCheat antiCheat;
    private LarpersAuth auth;
    private LarpersModeration moderation;
    private LarpersPlayerTools playerTools;
    private LarpersServerTools serverTools;
    private File dataFile;
    private String globalAnnouncement = "";
    private final DecimalFormat money = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.CANADA_FRENCH));

    /**
     * Crée une copie de sécurité des fichiers YAML avant chaque chargement du plugin.
     * Les sauvegardes restent indépendantes du JAR et les 10 plus récentes sont conservées.
     */
    private boolean backupPluginData() {
        File[] yamlFiles = getDataFolder().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (yamlFiles == null || yamlFiles.length == 0) return true;
        Path backupRoot = getDataFolder().toPath().resolve("backups");
        Path backupDir = backupRoot.resolve("backup-" + System.currentTimeMillis());
        try {
            Files.createDirectories(backupDir);
            for (File yaml : yamlFiles) {
                Files.copy(yaml.toPath(), backupDir.resolve(yaml.getName()), StandardCopyOption.COPY_ATTRIBUTES);
            }
            try (var backups = Files.list(backupRoot)) {
                for (Path oldBackup : backups.filter(Files::isDirectory)
                        .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                        .skip(10).toList()) {
                    try (var oldFiles = Files.list(oldBackup)) {
                        for (Path oldFile : oldFiles.toList()) Files.deleteIfExists(oldFile);
                    }
                    Files.deleteIfExists(oldBackup);
                }
            }
            getLogger().info("Sauvegarde de sécurité créée : " + backupDir.getFileName());
            return true;
        } catch (IOException ex) {
            getLogger().severe("Impossible de créer la sauvegarde de sécurité : " + ex.getMessage());
            return false;
        }
    }

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Impossible de créer le dossier de données.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!backupPluginData()) {
            getLogger().severe("Sauvegarde de sécurité impossible : démarrage annulé pour protéger les données.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String name : getDescription().getCommands().keySet()) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "larperscoin:announce", (channel, source, payload) -> {
            String announcement = new String(payload, StandardCharsets.UTF_8).trim();
            if (announcement.isEmpty()) return;
            if (announcement.length() > 100) announcement = announcement.substring(0, 100);
            globalAnnouncement = announcement;
            Bukkit.broadcastMessage(ChatColor.GOLD + "✦ ANNONCE LARPERS COIN ✦ " + ChatColor.YELLOW + announcement);
            updatePlayerTags();
        });
        auth = new LarpersAuth(this);
        features = new LarpersFeatures(this);
        antiCheat = new LarpersAntiCheat(this);
        moderation = new LarpersModeration(this);
        playerTools = new LarpersPlayerTools(this);
        serverTools = new LarpersServerTools(this, this);
        long lotteryEnd=data.getLong("lottery.ends-at",0L);
        if(data.getBoolean("lottery.active",false)) {
            long remaining=Math.max(1L,lotteryEnd-System.currentTimeMillis());
            getServer().getScheduler().runTaskLater(this,()->finishLottery(true),Math.max(1L,remaining/50L));
        }
        getServer().getScheduler().runTaskTimer(this,this::updatePlayerTags,40L,40L);
        getServer().getScheduler().runTaskLater(this, () -> {
            org.bukkit.World lobby = Bukkit.getWorld("world");
            if (lobby != null && getConfig().getBoolean("network.auth-only", false)) {
                lobby.setSpawnLocation(8, 81, 8);
            }
        }, 1L);
        getLogger().info("LarpersCoin 1.0.8 actif. Aucun claim ni protection de parcelle n'est enregistré.");
    }

    @Override
    public void onDisable() {
        if (features != null) features.shutdown();
        if (antiCheat != null) antiCheat.shutdown();
        if (playerTools != null) playerTools.shutdown();
        saveData();
    }

    void saveData() {
        Path target=dataFile.toPath();
        Path temporary=target.resolveSibling(target.getFileName()+".tmp");
        try {
            byte[] bytes=data.saveToString().getBytes(StandardCharsets.UTF_8);
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException ex) {
            getLogger().severe("Échec de sauvegarde atomique de data.yml: "+ex.getMessage());
            try { Files.deleteIfExists(temporary); } catch(IOException ignored) { }
        }
    }

    private String key(UUID uuid) {
        return uuid.toString();
    }

    double balance(UUID uuid) {
        double value=data.getDouble("balances." + key(uuid),0.0);
        return Double.isFinite(value)&&value>0.0?value:0.0;
    }

    public double bountyAmount(UUID uuid) {
        double value=data.getDouble("bounties."+key(uuid),0.0);
        return Double.isFinite(value)&&value>0.0?value:0.0;
    }

    public java.util.Map<UUID,Double> bountyLeaderboard() {
        java.util.Map<UUID,Double> result=new java.util.HashMap<>();
        var section=data.getConfigurationSection("bounties");
        if(section==null)return result;
        for(String raw:section.getKeys(false)) {
            try {
                UUID uuid=UUID.fromString(raw);
                double value=bountyAmount(uuid);
                if(value>0.0)result.put(uuid,value);
            } catch(IllegalArgumentException ignored) { }
        }
        return result;
    }

    private void setBalance(UUID uuid, double amount) {
        if (!Double.isFinite(amount)) return;
        data.set("balances." + key(uuid), Math.max(0.0, amount));
        saveData();
    }

    boolean debit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || balance(uuid) + 1.0e-9 < amount) return false;
        data.set("balances." + key(uuid), Math.max(0.0,balance(uuid) - amount));
        return true;
    }

    void credit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        double next = balance(uuid) + amount;
        if (!Double.isFinite(next)) {
            getLogger().warning("Crédit refusé: dépassement numérique pour " + uuid);
            return;
        }
        data.set("balances." + key(uuid), next);
    }
    void recordTransaction(String type, UUID from, UUID to, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        List<String> records = data.getStringList("transactions");
        records.add(System.currentTimeMillis() + "|" + type + "|" + from + "|" + to + "|" + amount);
        if (records.size() > 2000) records = new ArrayList<>(records.subList(records.size() - 2000, records.size()));
        data.set("transactions", records);
        saveData();
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[LPC] " + ChatColor.RESET + message);
    }

    void resetAuthPassword(CommandSender sender, String targetName) {
        if (auth == null) {
            reply(sender, "Le système d'authentification n'est pas disponible.");
            return;
        }
        auth.resetPassword(sender, targetName);
    }

    void broadcastAnnouncement(CommandSender sender, String message) {
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty() || clean.length() > 100) {
            reply(sender, "L'annonce doit contenir entre 1 et 100 caractères.");
            return;
        }
        Player relay = sender instanceof Player player ? player : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (relay == null) {
            globalAnnouncement = clean;
            Bukkit.broadcastMessage(ChatColor.GOLD + "✦ ANNONCE LARPERS COIN ✦ " + ChatColor.YELLOW + clean);
            updatePlayerTags();
            reply(sender, "Annonce locale publiée; aucun joueur en ligne ne peut relayer le message au réseau.");
            return;
        }
        try {
            byte[] payload = clean.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF("larperscoin:announce");
            out.writeShort(payload.length);
            out.write(payload);
            relay.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
            reply(sender, "Annonce envoyée à tous les serveurs du réseau.");
        } catch (IOException ex) {
            reply(sender, "Impossible d'envoyer l'annonce au réseau.");
            getLogger().warning("Échec de l'annonce réseau: " + ex.getMessage());
        }
    }

    void recordHistory(Player player, String event) {
        if (player == null || event == null || event.isBlank()) return;
        String path = "player-history." + key(player.getUniqueId());
        List<String> records = data.getStringList(path);
        records.add(System.currentTimeMillis() + "|" + event + "|" + player.getName());
        if (records.size() > 100) records = new ArrayList<>(records.subList(records.size() - 100, records.size()));
        data.set(path, records);
        saveData();
    }

    private void checkSuspiciousTransfer(Player from, Player to, double amount) {
        long now=System.currentTimeMillis();
        List<String> records=data.getStringList("transactions");
        int recentLarge=0;
        for(int i=records.size()-1;i>=0;i--) {
            String[] part=records.get(i).split("\\|");
            if(part.length!=5)continue;
            long stamp;
            double value;
            try { stamp=Long.parseLong(part[0]); value=Double.parseDouble(part[4]); }
            catch(NumberFormatException ex) { continue; }
            if(now-stamp>60_000L)break;
            if(part[1].equals("PAY")&&part[2].equals(from.getUniqueId().toString())&&value>=1000.0)recentLarge++;
        }
        boolean suspicious=amount>=5000.0||recentLarge>=3;
        if(!suspicious)return;
        String detail=from.getName()+" -> "+to.getName()+" : "+format(amount)+" (transferts élevés sur 60 s: "+recentLarge+")";
        List<String> alerts=data.getStringList("suspicious-transfers");
        alerts.add(now+"|"+detail);
        if(alerts.size()>500)alerts=new ArrayList<>(alerts.subList(alerts.size()-500,alerts.size()));
        data.set("suspicious-transfers",alerts);
        saveData();
        getLogger().warning("[Économie] Transfert à vérifier: "+detail);
        for(Player staff:Bukkit.getOnlinePlayers()) {
            if(staff.isOp()||staff.hasPermission("larpers.economy.alerts")||staff.hasPermission("larpers.admin"))
                staff.sendMessage(ChatColor.RED+"[LPC ÉCONOMIE] Transfert à vérifier: "+detail);
        }
    }

    private String format(double amount) {
        return money.format(amount) + " LPC";
    }

    private boolean isStaff(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("larpers.staff") || sender.hasPermission("larpers.admin");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        reply(sender, "Cette commande doit être utilisée en jeu.");
        return null;
    }

    private Double parseAmount(String raw) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            return Double.isFinite(value) && value > 0.0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (getConfig().getBoolean("network.auth-only", false) && !name.equals("register") && !name.equals("login")) {
            reply(sender, "Le lobby sert uniquement à l’authentification. Connecte-toi pour rejoindre le SMP.");
            return true;
        }
        try {
            switch (name) {
                case "register", "login" -> {
                    Player player = requirePlayer(sender);
                    if (player != null) auth.command(player, name, args);
                    return true;
                }
                case "redemarrer" -> {
                    if (!isStaff(sender)) {
                        reply(sender, "Permission insuffisante. Seuls les administrateurs peuvent redémarrer le réseau.");
                        return true;
                    }
                    saveData();
                    try {
                        Path networkRoot = getDataFolder().toPath().toAbsolutePath().getParent().getParent().getParent();
                        Path request = networkRoot.resolve("restart.request");
                        Files.writeString(request, "requested-by=" + sender.getName() + System.lineSeparator(),
                                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                        Bukkit.broadcastMessage(ChatColor.GOLD + "[LPC] Redémarrage du réseau demandé. Lobby, SMP et Velocity vont redémarrer.");
                        getLogger().warning("Redémarrage global demandé par " + sender.getName());
                    } catch (IOException | RuntimeException ex) {
                        reply(sender, "Impossible de demander le redémarrage global. Vérifie que le réseau est lancé avec START-NETWORK.bat.");
                        getLogger().severe("Échec de la demande de redémarrage: " + ex.getMessage());
                    }
                    return true;
                }
                case "balance" -> {
                    if (args.length == 0) {
                        Player player = requirePlayer(sender);
                        if (player == null) return true;
                        reply(sender, "Ton solde : " + format(balance(player.getUniqueId())));
                    } else {
                        if (!isStaff(sender)) {
                            reply(sender, "Permission insuffisante.");
                            return true;
                        }
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                        reply(sender, "Solde de " + args[0] + " : " + format(balance(target.getUniqueId())));
                    }
                    return true;
                }
                case "pay" -> {
                    Player player = requirePlayer(sender);
                    if (player == null) return true;
                    if (args.length != 2) {
                        reply(sender, "Usage : /pay <joueur> <montant>");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    Double amount = parseAmount(args[1]);
                    if (target == null || target.equals(player) || amount == null) {
                        reply(sender, "Joueur en ligne ou montant invalide.");
                        return true;
                    }
                    if (!Double.isFinite(balance(target.getUniqueId()) + amount)) {
                        reply(sender, "Le solde du destinataire dépasserait la limite numérique.");
                        return true;
                    }
                    if (!debit(player.getUniqueId(), amount)) {
                        reply(sender, "Fonds insuffisants.");
                        return true;
                    }
                    credit(target.getUniqueId(), amount);
                    saveData();
                    recordTransaction("PAY", player.getUniqueId(), target.getUniqueId(), amount);
                    checkSuspiciousTransfer(player, target, amount);
                    reply(player, "Tu as envoyé " + format(amount) + " à " + target.getName() + ".");
                    reply(target, "Tu as reçu " + format(amount) + " de " + player.getName() + ".");
                    return true;
                }
                case "history" -> {
                    if(args.length>1){reply(sender,"Usage : /history [joueur]");return true;}
                    UUID targetId;
                    String targetName;
                    if (args.length == 0) {
                        Player player = requirePlayer(sender);
                        if (player == null) return true;
                        targetId = player.getUniqueId();
                        targetName = player.getName();
                    } else {
                        if (!isStaff(sender)) { reply(sender, "Permission insuffisante."); return true; }
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                        targetId = target.getUniqueId();
                        targetName = args[0];
                    }
                    List<String> playerEvents = data.getStringList("player-history." + targetId);
                    reply(sender, "Historique de " + targetName + " (connexions et sanctions) :");
                    int eventCount = 0;
                    for (int i = playerEvents.size() - 1; i >= 0 && eventCount < 10; i--) {
                        String[] event = playerEvents.get(i).split("\\|", 3);
                        if (event.length < 3) continue;
                        long stamp;
                        try { stamp = Long.parseLong(event[0]); } catch (NumberFormatException ex) { continue; }
                        String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(stamp));
                        reply(sender, date + " " + event[1] + " (" + event[2] + ")");
                        eventCount++;
                    }
                    List<String> records = data.getStringList("transactions");
                    int shown = 0;
                    reply(sender, "Transactions LPC (10 dernières) :");
                    for (int i = records.size() - 1; i >= 0 && shown < 10; i--) {
                        String[] parts = records.get(i).split("\\|");
                        if (parts.length != 5) continue;
                        if (!parts[2].equals(targetId.toString()) && !parts[3].equals(targetId.toString())) continue;
                        long stamp;
                        try { stamp = Long.parseLong(parts[0]); } catch (NumberFormatException ex) { continue; }
                        String direction = parts[2].equals(targetId.toString()) ? "-" : "+";
                        String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(stamp));
                        reply(sender, date + " " + parts[1] + " " + direction + parts[4] + " LPC");
                        shown++;
                    }
                    if (shown == 0) reply(sender, "Aucune transaction enregistrée.");
                    if (eventCount == 0) reply(sender, "Aucun événement de connexion ou de sanction enregistré.");
                    return true;
                }
                case "baltop" -> {
                    var section = data.getConfigurationSection("balances");
                    if (section == null || section.getKeys(false).isEmpty()) {
                        reply(sender, "Aucun solde enregistré.");
                        return true;
                    }
                    List<String> ids = new ArrayList<>(section.getKeys(false));
                    ids.sort(Comparator.comparingDouble((String id) -> data.getDouble("balances." + id)).reversed());
                    reply(sender, "§6--- Classement des fortunes LPC ---");
                    int rank = 1;
                    for (String id : ids) {
                        if (rank > 10) break;
                        OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(id));
                        String playerName = offline.getName() == null ? id.substring(0, 8) : offline.getName();
                        reply(sender, rank + ". " + playerName + " : " + format(data.getDouble("balances." + id)));
                        rank++;
                    }
                    return true;
                }
                case "daily" -> {
                    Player player = requirePlayer(sender);
                    if (player == null) return true;
                    String id = key(player.getUniqueId());
                    long now = System.currentTimeMillis();
                    long previous = data.getLong("daily." + id, 0L);
                    long cooldown = 86_400_000L;
                    if (now - previous < cooldown) {
                        long hours = (cooldown - (now - previous) + 3_599_999L) / 3_600_000L;
                        reply(sender, "Tu as déjà réclamé ta récompense. Reviens dans environ " + hours + " h.");
                        return true;
                    }
                    long previousDay = previous;
                    int previousStreak = data.getInt("streak." + id, 0);
                    int streak = previousDay > 0 && now - previousDay <= 172_800_000L
                            ? previousStreak + 1 : 1;
                    double reward = 100.0 + Math.min(streak, 7) * 20.0;
                    if (!Double.isFinite(balance(player.getUniqueId()) + reward)) {
                        reply(sender, "Ton solde ne peut pas recevoir cette récompense.");
                        return true;
                    }
                    data.set("daily." + id, now);
                    data.set("streak." + id, streak);
                    credit(player.getUniqueId(), reward);
                    saveData();
                    recordTransaction("DAILY", new UUID(0L,0L), player.getUniqueId(), reward);
                    reply(sender, "Récompense quotidienne : +" + format(reward) + " (série " + streak + ").");
                    return true;
                }
                case "bounty" -> {
                    if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("list")) {
                        features.openBountyMenu(sender);
                        return true;
                    }
                    Player player = requirePlayer(sender);
                    if (player == null) return true;
                    if (args.length != 2) {
                        reply(sender, "Usage : /bounty <joueur> <montant>");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    Double amount = parseAmount(args[1]);
                    if (target == null || target.equals(player) || amount == null) {
                        reply(sender, "Cible ou montant invalide.");
                        return true;
                    }
                    String targetId = key(target.getUniqueId());
                    double nextBounty = data.getDouble("bounties." + targetId) + amount;
                    if (!Double.isFinite(nextBounty)) {
                        reply(sender, "La prime totale dépasserait la limite numérique.");
                        return true;
                    }
                    if (!debit(player.getUniqueId(), amount)) {
                        reply(sender, "Fonds insuffisants.");
                        return true;
                    }
                    data.set("bounties." + targetId, nextBounty);
                    saveData();
                    recordTransaction("BOUNTY_PLACE", player.getUniqueId(), new UUID(0L,0L), amount);
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[LPC] " + player.getName() + " place une prime de " + format(amount) + " sur " + target.getName() + " !");
                    return true;
                }
                case "eco" -> {
                    if (!sender.hasPermission("larpers.admin")) {
                        reply(sender, "Permission insuffisante.");
                        return true;
                    }
                    if (args.length != 3) {
                        reply(sender, "Usage : /eco <give|take|set> <joueur> <montant>");
                        return true;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    Double amount = parseAmount(args[2]);
                    if (amount == null) {
                        reply(sender, "Montant invalide.");
                        return true;
                    }
                    double oldBalance = balance(target.getUniqueId());
                    if (args[0].equalsIgnoreCase("give") && !Double.isFinite(oldBalance + amount)) {
                        reply(sender, "Le solde du joueur dépasserait la limite numérique.");
                        return true;
                    }
                    double recordedAmount = amount;
                    UUID systemAccount = new UUID(0L,0L);
                    switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "give" -> credit(target.getUniqueId(), amount);
                        case "take" -> {
                            recordedAmount = Math.min(oldBalance, amount);
                            data.set("balances." + key(target.getUniqueId()), oldBalance - recordedAmount);
                        }
                        case "set" -> {
                            recordedAmount = Math.abs(amount - oldBalance);
                            data.set("balances." + key(target.getUniqueId()), amount);
                        }
                        default -> {
                            reply(sender, "Action invalide : give, take ou set.");
                            return true;
                        }
                    }
                    saveData();
                    if (recordedAmount > 0) {
                        String action = args[0].toUpperCase(Locale.ROOT);
                        if (action.equals("TAKE") || action.equals("SET") && amount < oldBalance)
                            recordTransaction("ECO_" + action, target.getUniqueId(), systemAccount, recordedAmount);
                        else recordTransaction("ECO_" + action, systemAccount, target.getUniqueId(), recordedAmount);
                    }
                    reply(sender, "Solde de " + args[1] + " : " + format(balance(target.getUniqueId())));
                    return true;
                }
                case "lottery" -> {
                    if(args.length==0||args[0].equalsIgnoreCase("menu")||args[0].equalsIgnoreCase("pot")) {
                        Player viewer=requirePlayer(sender);if(viewer!=null)features.openLotteryMenu(viewer);return true;
                    }
                    if(args[0].equalsIgnoreCase("buy")) {
                        Player player=requirePlayer(sender);if(player==null)return true;
                        if(!lotteryActive()){reply(sender,"Aucune loterie en cours. Attends l'annonce d'un administrateur.");return true;}
                        int count=args.length>=2?Integer.parseInt(args[1]):1;
                        if(count<1||count>10000){reply(sender,"Achète entre 1 et 10 000 tickets par commande; tu peux refaire plusieurs achats sans limite totale.");return true;}
                        double cost=count*25.0,nextPot=lotteryPot()+cost;
                        if(!Double.isFinite(nextPot)){reply(sender,"Le jackpot dépasserait la limite numérique.");return true;}
                        if(!debit(player.getUniqueId(),cost)){reply(sender,"Fonds insuffisants : "+format(cost)+".");return true;}
                        List<String> tickets=data.getStringList("lottery.tickets");for(int i=0;i<count;i++)tickets.add(key(player.getUniqueId()));
                        data.set("lottery.tickets",tickets);data.set("lottery.pot",nextPot);saveData();
                        recordTransaction("LOTTERY_BUY",player.getUniqueId(),new UUID(0L,0L),cost);
                        reply(sender,"Tickets achetés : "+count+". Jackpot : "+format(nextPot));
                        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[LOTERIE] "+player.getName()+" vient d'acheter "+count+" ticket(s)!");
                        return true;
                    }
                    if(args[0].equalsIgnoreCase("start")) {
                        if(!isLpcAdminSender(sender)){reply(sender,"Seuls les administrateurs peuvent démarrer une loterie.");return true;}
                        int minutes=args.length>=2?Integer.parseInt(args[1]):10;
                        if(minutes<1||minutes>1440){reply(sender,"Durée autorisée : 1 à 1440 minutes.");return true;}
                        if(lotteryActive()){reply(sender,"Une loterie est déjà en cours.");return true;}
                        startLottery(minutes,sender.getName());return true;
                    }
                    if(args[0].equalsIgnoreCase("draw")||args[0].equalsIgnoreCase("stop")) {
                        if(!isLpcAdminSender(sender)){reply(sender,"Seuls les administrateurs peuvent terminer une loterie.");return true;}
                        finishLottery(false);reply(sender,"La loterie a été terminée.");return true;
                    }
                    reply(sender,"Usage : /lottery [menu] | buy [quantité] | start [minutes] (admin) | draw (admin)");return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (NumberFormatException ex) {
            reply(sender, "Nombre invalide.");
            return true;
        } catch (RuntimeException ex) {
            getLogger().warning("Erreur commande /" + name + " : " + ex.getMessage());
            reply(sender, "Une erreur est survenue. Consulte la console.");
            return true;
        }
    }

    public double lotteryPot(){return Math.max(0.0,data.getDouble("lottery.pot",0.0));}
    public int lotteryTicketCount(){return data.getStringList("lottery.tickets").size();}
    public int lotteryPlayerTicketCount(UUID playerId){
        int count=0;
        for(String ticket:data.getStringList("lottery.tickets"))if(ticket.equals(playerId.toString()))count++;
        return count;
    }
    public double lotteryChancePercent(UUID playerId){
        int total=lotteryTicketCount();
        return total==0?0.0:(lotteryPlayerTicketCount(playerId)*100.0/total);
    }
    public boolean lotteryActive(){return data.getBoolean("lottery.active",false);}
    public long lotteryEndsAt(){return data.getLong("lottery.ends-at",0L);}
    private boolean isLpcAdminSender(CommandSender sender){return sender.isOp()||sender.hasPermission("larpers.admin");}
    public boolean isLpcAdmin(Player player){return isLpcAdminSender(player);}

    private void startLottery(int minutes,String starter) {
        long end=System.currentTimeMillis()+minutes*60_000L;
        data.set("lottery.active",true);data.set("lottery.ends-at",end);saveData();
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"§l✦ LOTERIE LARPERS COIN ✦");
        Bukkit.broadcastMessage(ChatColor.YELLOW+"Une nouvelle loterie vient de commencer!");
        Bukkit.broadcastMessage(ChatColor.GREEN+"Ticket: 25 LPC | Durée: "+minutes+" minute(s) | Jackpot: "+format(lotteryPot()));
        Bukkit.broadcastMessage(ChatColor.AQUA+"Ouvre /lottery pour acheter tes tickets!");
        Bukkit.broadcastMessage(ChatColor.GRAY+"Lancée par "+starter+".");
        getServer().getScheduler().runTaskLater(this,()->finishLottery(true),minutes*60L*20L);
    }
    private void finishLottery(boolean automatic) {
        if(!lotteryActive())return;
        data.set("lottery.active",false);data.set("lottery.ends-at",0L);saveData();
        List<String> tickets=data.getStringList("lottery.tickets");
        if(tickets.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[LOTERIE] Fin du tirage: aucun ticket vendu. Le jackpot est conservé.");
            return;
        }
        drawLottery();
    }
    private String rankPrefix(Player p) {
        if(isLpcAdmin(p))return ChatColor.RED+"[ADMIN] ";
        if(p.hasPermission("larpers.staff")||p.hasPermission("larpers.anticheat.mod"))return ChatColor.GOLD+"[MOD] ";
        if(p.hasPermission("larpers.vip"))return ChatColor.AQUA+"[VIP] ";
        return ChatColor.GRAY+"[JOUEUR] ";
    }
    private void updatePlayerTags() {
        for(Player p:Bukkit.getOnlinePlayers()) {
            p.setPlayerListName(rankPrefix(p)+ChatColor.WHITE+p.getName());
            String header = globalAnnouncement.isBlank() ? ChatColor.GOLD+"Larpers Coin SMP"
                    : ChatColor.GOLD+"✦ "+globalAnnouncement+" ✦";
            if(lotteryActive()) header += "\n"+ChatColor.LIGHT_PURPLE+"✦ LOTERIE LPC EN COURS ✦";
            p.setPlayerListHeaderFooter(header, lotteryActive()
                    ? ChatColor.YELLOW+"/lottery • Jackpot: "+format(lotteryPot())
                    : ChatColor.GRAY+"Bonne chance et bon jeu!");
        }
    }
    @EventHandler public void onPlayerJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(this,()->updatePlayerTags());
        e.getPlayer().sendMessage(ChatColor.GOLD+"Bienvenue sur Larpers Coin SMP!");
        recordHistory(e.getPlayer(), "CONNEXION");
    }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent e) {
        recordHistory(e.getPlayer(), "DÉCONNEXION");
    }
    @EventHandler public void onPlayerChat(AsyncPlayerChatEvent e) {
        e.setFormat(rankPrefix(e.getPlayer())+ChatColor.WHITE+"%1$s"+ChatColor.DARK_GRAY+" » "+ChatColor.RESET+"%2$s");
    }

    private void drawLottery() {
        List<String> tickets = data.getStringList("lottery.tickets");
        if (tickets.isEmpty()) return;
        String winnerId = tickets.get(ThreadLocalRandom.current().nextInt(tickets.size()));
        double jackpot = data.getDouble("lottery.pot", 0.0);
        UUID winnerUuid = UUID.fromString(winnerId);
        if (!Double.isFinite(jackpot) || jackpot <= 0.0 || !Double.isFinite(balance(winnerUuid) + jackpot)) {
            getLogger().severe("Tirage annulé: jackpot ou solde du gagnant invalide.");
            return;
        }
        credit(winnerUuid, jackpot);
        data.set("lottery.tickets", new ArrayList<String>());
        data.set("lottery.pot", 0.0);
        saveData();
        recordTransaction("LOTTERY_WIN", new UUID(0L,0L), winnerUuid, jackpot);
        OfflinePlayer winner = Bukkit.getOfflinePlayer(UUID.fromString(winnerId));
        Bukkit.broadcastMessage(ChatColor.GOLD + "[LPC] " + (winner.getName() == null ? "Un joueur" : winner.getName()) + " remporte la loterie : " + format(jackpot) + " !");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        String victimId = key(event.getEntity().getUniqueId());
        double bounty = data.getDouble("bounties." + victimId, 0.0);
        if (!Double.isFinite(bounty) || bounty <= 0.0) return;
        if (!Double.isFinite(balance(killer.getUniqueId()) + bounty)) {
            getLogger().warning("Prime conservée: solde maximal atteint pour " + killer.getName());
            return;
        }
        data.set("bounties." + victimId, 0.0);
        credit(killer.getUniqueId(), bounty);
        saveData();
        recordTransaction("BOUNTY_WIN", new UUID(0L,0L), killer.getUniqueId(), bounty);
        Bukkit.broadcastMessage(ChatColor.GOLD + "[LPC] " + killer.getName() + " a récupéré la prime de " + format(bounty) + " sur " + event.getEntity().getName() + " !");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("eco") && args.length == 1 && sender.hasPermission("larpers.admin")) {
            return List.of("give", "take", "set").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if(command.getName().equalsIgnoreCase("lottery")&&args.length==1) {
            List<String> choices=isLpcAdminSender(sender)?List.of("menu","buy","start","draw"):List.of("menu","buy");
            return choices.stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
