package ca.larperscoin.smp;

import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimUser;

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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
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
    private static final class InvseeHolder implements InventoryHolder {
        private final UUID targetId;
        InvseeHolder(UUID targetId) { this.targetId = targetId; }
        UUID targetId() { return targetId; }
        @Override public Inventory getInventory() { return null; }
    }
    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (!"empty".equalsIgnoreCase(id)) return null;
        return new org.bukkit.generator.ChunkGenerator() {
            @Override
            public org.bukkit.generator.ChunkGenerator.ChunkData generateChunkData(
                    org.bukkit.World world, java.util.Random random, int chunkX, int chunkZ, BiomeGrid biome) {
                ChunkData data = createChunkData(world);
                // Générateur entièrement vide: aucune plateforme ni décoration.
                return data;
            }
        };
    }
    private YamlConfiguration data;
    private LarpersFeatures features;
    private LarpersAntiCheat antiCheat;
    private LarpersWebDashboard webDashboard;
    private LarpersAuth auth;
    private File dataFile;
    private final DecimalFormat money = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.CANADA_FRENCH));

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Impossible de créer le dossier de données.");
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
        // Désactive la barre de localisation des joueurs dans tous les mondes chargés.
        Bukkit.getWorlds().forEach(this::disablePlayerLocatorBar);
        auth = new LarpersAuth(this);
        getCommand("serveur").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player && auth.isAuthenticated(player) && args.length == 1 && args[0].equalsIgnoreCase("event"))
                MineAccess.join(this, player);
            else sender.sendMessage("Connecte-toi puis utilise /serveur event.");
            return true;
        });
        features = new LarpersFeatures(this);
        antiCheat = new LarpersAntiCheat(this);
        webDashboard = new LarpersWebDashboard(this, this);
        long lotteryEnd=data.getLong("lottery.ends-at",0L);
        if(data.getBoolean("lottery.active",false)) {
            long remaining=Math.max(1L,lotteryEnd-System.currentTimeMillis());
            getServer().getScheduler().runTaskLater(this,()->finishLottery(true),Math.max(1L,remaining/50L));
        }
        getServer().getScheduler().runTaskTimer(this,this::updatePlayerTags,40L,40L);
        getServer().getScheduler().runTaskLater(this, () -> {
            org.bukkit.World lobby = Bukkit.getWorld("world");
            if (lobby != null && getConfig().getBoolean("network.auth-only", false)) {
                lobby.setSpawnLocation(0, 80, 0);
                lobby.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                lobby.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                lobby.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                // Retire aussi l'ancienne plateforme des mondes de lobby déjà générés.
                for(int x=3;x<=12;x++)for(int z=3;z<=12;z++)
                    lobby.getBlockAt(x,80,z).setType(org.bukkit.Material.AIR,false);
            }
        }, 1L);
        getLogger().info("Larry SMP 1.0.8 actif. Aucun claim ni protection de parcelle n'est enregistré.");
    }

    private void disablePlayerLocatorBar(org.bukkit.World world) {
        try {
            world.setGameRule(org.bukkit.GameRule.LOCATOR_BAR, false);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Impossible de désactiver locator_bar dans " + world.getName() + ": " + ex.getMessage());
        }
    }

    @EventHandler
    public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
        disablePlayerLocatorBar(event.getWorld());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLobbyPvp(EntityDamageByEntityEvent event) {
        if (!getConfig().getBoolean("network.auth-only", false)) return;
        if (!(event.getEntity() instanceof Player)) return;
        org.bukkit.entity.Entity damager = event.getDamager();
        boolean playerAttack = damager instanceof Player
                || (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player);
        if (playerAttack) event.setCancelled(true);
    }

    @Override
    public void onDisable() {
        if (features != null) features.shutdown();
        if (antiCheat != null) antiCheat.shutdown();
        if (webDashboard != null) webDashboard.shutdown();
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
        sender.sendMessage(ChatColor.GOLD + "[Larry] " + ChatColor.RESET + message);
    }

    private String format(double amount) {
        return money.format(amount) + " Larry";
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
                case "invsee" -> {
                    if (!isStaff(sender)) {
                        reply(sender, "Permission insuffisante.");
                        return true;
                    }
                    Player viewer = requirePlayer(sender);
                    if (viewer == null) return true;
                    if (args.length != 1) {
                        reply(sender, "Usage : /invsee <joueur>");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        reply(sender, "Ce joueur doit être connecté.");
                        return true;
                    }
                    openInvsee(viewer, target);
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
                    List<String> records = data.getStringList("transactions");
                    int shown = 0;
                    reply(sender, "Historique Larry de " + targetName + " (10 dernières opérations) :");
                    for (int i = records.size() - 1; i >= 0 && shown < 10; i--) {
                        String[] parts = records.get(i).split("\\|");
                        if (parts.length != 5) continue;
                        if (!parts[2].equals(targetId.toString()) && !parts[3].equals(targetId.toString())) continue;
                        long stamp;
                        try { stamp = Long.parseLong(parts[0]); } catch (NumberFormatException ex) { continue; }
                        String direction = parts[2].equals(targetId.toString()) ? "-" : "+";
                        String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(stamp));
                        reply(sender, date + " " + parts[1] + " " + direction + parts[4] + " Larry");
                        shown++;
                    }
                    if (shown == 0) reply(sender, "Aucune transaction enregistrée.");
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
                    reply(sender, "§6--- Classement des fortunes Larry ---");
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
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[Larry] " + player.getName() + " place une prime de " + format(amount) + " sur " + target.getName() + " !");
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
                        if(count<1||count>1000){reply(sender,"Choisis entre 1 et 1 000 tickets.");return true;}
                        double cost=count*50.0,nextPot=lotteryPot()+cost;
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
                    reply(sender,"Usage : /lottery [menu] | buy <1-1000> | start [minutes] (admin) | draw (admin)");return true;
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

    private void openInvsee(Player viewer, Player target) {
        Inventory menu = Bukkit.createInventory(new InvseeHolder(target.getUniqueId()), 54,
                ChatColor.DARK_AQUA + "Inventaire: " + ChatColor.WHITE + target.getName());
        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int slot = 0; slot < Math.min(contents.length, 36); slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType() != Material.AIR) menu.setItem(slot, item.clone());
        }
        ItemStack[] armor = target.getInventory().getArmorContents();
        // Ordre Bukkit: bottes, jambières, plastron, casque.
        int[] armorSlots = {45, 46, 47, 48};
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item != null && item.getType() != Material.AIR) menu.setItem(armorSlots[i], item.clone());
        }
        ItemStack offhand = target.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) menu.setItem(50, offhand.clone());
        menu.setItem(49, namedItem(Material.SHIELD, ChatColor.YELLOW + "Main gauche"));
        menu.setItem(51, namedItem(Material.IRON_CHESTPLATE, ChatColor.YELLOW + "Armure"));
        menu.setItem(52, namedItem(Material.BARRIER, ChatColor.RED + "Consultation seulement"));
        menu.setItem(53, namedItem(Material.PLAYER_HEAD, ChatColor.AQUA + target.getName()));
        viewer.openInventory(menu);
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInvseeClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof InvseeHolder) event.setCancelled(true);
    }

    public boolean resetAuthAccount(String playerName){return auth!=null&&auth.resetAccount(playerName);}
    LarpersAntiCheat getAntiCheat(){return antiCheat;}
    public double lotteryPot(){return Math.max(0.0,data.getDouble("lottery.pot",0.0));}
    public int lotteryTicketCount(){return data.getStringList("lottery.tickets").size();}
    public boolean lotteryActive(){return data.getBoolean("lottery.active",false);}
    public long lotteryEndsAt(){return data.getLong("lottery.ends-at",0L);}
    private boolean isLpcAdminSender(CommandSender sender){return sender.isOp()||sender.hasPermission("larpers.admin");}
    public boolean isLpcAdmin(Player player){return isLpcAdminSender(player);}

    private void startLottery(int minutes,String starter) {
        long end=System.currentTimeMillis()+minutes*60_000L;
        data.set("lottery.active",true);data.set("lottery.ends-at",end);saveData();
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"§l✦ LOTERIE LARPERS COIN ✦");
        Bukkit.broadcastMessage(ChatColor.YELLOW+"Une nouvelle loterie vient de commencer!");
        Bukkit.broadcastMessage(ChatColor.GREEN+"Ticket: 50 Larry | Durée: "+minutes+" minute(s) | Jackpot: "+format(lotteryPot()));
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
        File lobbyData = new File(getServer().getWorldContainer(), "../Lobby/plugins/LarpersCoin");
        File sharedDisplayFile = new File(lobbyData, "server-display.yml");
        FileConfiguration display = sharedDisplayFile.isFile()
                ? YamlConfiguration.loadConfiguration(sharedDisplayFile) : getConfig();
        for(Player p:Bukkit.getOnlinePlayers()) {
            p.setPlayerListName(rankPrefix(p)+ChatColor.WHITE+p.getName());
            String serverName=display.getString("server-display.name",getConfig().getString("server-display.name","Larry SMP"));
            String serverIp=display.getString("server-display.ip",getConfig().getString("server-display.ip","Non configurée"));
            String status=lotteryActive()?ChatColor.LIGHT_PURPLE+"✦ LOTERIE • "+format(lotteryPot())+" Larry ✦":ChatColor.GRAY+"Bienvenue sur le SMP";
            p.setPlayerListHeaderFooter(ChatColor.GOLD+serverName,status+ChatColor.DARK_GRAY+" | "+ChatColor.YELLOW+"IP: "+serverIp+ChatColor.DARK_GRAY+" | "+ChatColor.GREEN+"Ping: "+p.getPing()+" ms");
        }
    }
    /**
     * Les joueurs dont le pseudo commence par "+" sont entièrement exemptés de GrimAC.
     * Les autres joueurs Bedrock conservent seulement l'exemption de setback prévue.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onAnticheatJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getName().startsWith("+")) {
            applyGrimPermission(player, "grim.exempt", 0, true);
        } else if (getConfig().getBoolean("anticheat.education-no-setback", true)) {
            applyEducationNoSetback(player, 0);
        }
    }

    private void applyEducationNoSetback(Player player, int attempt) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        if (!isFloodgatePlayer(uuid)) {
            if (attempt < 50) {
                Bukkit.getScheduler().runTaskLater(this, () -> applyEducationNoSetback(player, attempt + 1), 2L);
            } else {
                getLogger().warning("EduFloodgate: joueur " + player.getName()
                        + " non reconnu après 5 secondes; exemption Grim non appliquée.");
            }
            return;
        }
        applyGrimPermission(player, "grim.nosetback", attempt, false);
    }

    private void applyGrimPermission(Player player, String permission, int attempt, boolean fullExemption) {
        if (!player.isOnline()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("GrimAC")) {
            if (attempt < 50) {
                Bukkit.getScheduler().runTaskLater(this,
                        () -> applyGrimPermission(player, permission, attempt + 1, fullExemption), 2L);
            } else {
                getLogger().warning("GrimAC indisponible; permission " + permission
                        + " non appliquée à " + player.getName() + ".");
            }
            return;
        }
        try {
            var attachment = player.addAttachment(this);
            attachment.setPermission(permission, true);
            GrimAbstractAPI api = GrimAPIProvider.get();
            GrimUser grimUser = api.getGrimUser(player.getUniqueId());
            if (grimUser == null) {
                if (attempt < 50) {
                    Bukkit.getScheduler().runTaskLater(this,
                            () -> applyGrimPermission(player, permission, attempt + 1, fullExemption), 2L);
                } else {
                    getLogger().warning("GrimAC: utilisateur non prêt; permission " + permission
                            + " non confirmée pour " + player.getName() + ".");
                }
                return;
            }
            grimUser.updatePermissions();
            if (fullExemption) {
                getLogger().info("GrimAC: exemption complète (grim.exempt) appliquée à "
                        + player.getName() + " car son pseudo commence par +.");
            } else {
                getLogger().info("GrimAC: permission grim.nosetback appliquée à " + player.getName()
                        + " (détections et alertes conservées).");
            }
        } catch (Throwable ex) {
            getLogger().warning("Impossible d'appliquer " + permission + " à " + player.getName()
                    + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private boolean isFloodgatePlayer(UUID uuid) {
        org.bukkit.plugin.Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) return false;
        try {
            Class<?> apiClass = Class.forName(
                    "org.geysermc.floodgate.api.FloodgateApi", true, floodgate.getClass().getClassLoader());
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning("API Floodgate inaccessible; exemption Grim non appliquée: " + ex.getMessage());
            return false;
        }
    }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent e) {
        Player player=e.getPlayer();
        if(getConfig().getBoolean("network.auth-only",false)) {
            org.bukkit.World lobby=Bukkit.getWorld("world");
            if(lobby!=null) {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                player.setAllowFlight(true);
                player.setFlying(true);
                player.teleport(lobby.getSpawnLocation());
            }
        }
        Bukkit.getScheduler().runTask(this,()->updatePlayerTags());
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
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Larry] " + (winner.getName() == null ? "Un joueur" : winner.getName()) + " remporte la loterie : " + format(jackpot) + " !");
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
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Larry] " + killer.getName() + " a récupéré la prime de " + format(bounty) + " sur " + event.getEntity().getName() + " !");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("invsee") && args.length == 1 && isStaff(sender)) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
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
