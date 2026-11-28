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
import java.util.Map;
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
    private final DecimalFormat money = new DecimalFormat("#,##0.0000", DecimalFormatSymbols.getInstance(Locale.CANADA_FRENCH));

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Impossible de créer le dossier de données.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dataFile = new File(getDataFolder(), "data.yml");
        try {
            data=new YamlConfiguration();if(dataFile.exists())data.load(dataFile);
            File featuresFile=new File(getDataFolder(),"features.yml");
            if(featuresFile.exists())new YamlConfiguration().load(featuresFile);
            migrateMoneyUnits();
        }catch(Exception ex){data=null;getLogger().severe("Données illisibles : arrêt de sécurité, aucun fichier écrasé. "+ex.getMessage());getServer().shutdown();getServer().getPluginManager().disablePlugin(this);return;}
        for (String name : getDescription().getCommands().keySet()) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        // Désactive la barre de localisation des joueurs dans tous les mondes chargés.
        Bukkit.getWorlds().forEach(this::disablePlayerLocatorBar);
        getConfig().addDefault("economy.sale-tax-percent",5.0);
        new LarryModeration(this);
        new RamCommand(this);
        auth = new LarpersAuth(this);
        features = new LarpersFeatures(this);
        antiCheat = new LarpersAntiCheat(this);
        webDashboard = new LarpersWebDashboard(this, this);
        long lotteryEnd=data.getLong("lottery.ends-at",0L);
        if(data.getBoolean("lottery.active",false)) {
            long remaining=Math.max(1L,lotteryEnd-System.currentTimeMillis());
            lotteryTimer=getServer().getScheduler().runTaskLater(this,()->finishLottery(true),Math.max(1L,remaining/50L));
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

    boolean saveData() {
        if(dataFile==null||data==null)return false;
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
            return true;
        } catch(IOException ex) {
            getLogger().severe("Échec de sauvegarde atomique de data.yml: "+ex.getMessage());
            try { Files.deleteIfExists(temporary); } catch(IOException ignored) { }
            return false;
        }
    }

    // Keep legacy decimal mirrors for existing readers; integer units are authoritative.
    private double ledgerAmount(String path,double fallback){
        Object stored=data.get("money-units."+path);
        if(stored!=null){
            if(!(stored instanceof Integer||stored instanceof Long))throw new IllegalStateException("Unités invalides: "+path);
            long units=((Number)stored).longValue();
            if(units<0||units>AntiDuplication.MAX_UNITS)throw new IllegalStateException("Unités hors limites: "+path);
            return AntiDuplication.amount(units);
        }
        Object raw=data.get(path);
        if(raw!=null&&!(raw instanceof Number))throw new IllegalStateException("Montant invalide: "+path);
        long units=AntiDuplication.units(raw==null?fallback:((Number)raw).doubleValue());
        if(units<0)throw new IllegalStateException("Montant négatif: "+path);
        return AntiDuplication.amount(units);
    }
    private void setLedgerAmount(String path,double amount){
        long units=AntiDuplication.units(amount);
        if(units<0)throw new IllegalArgumentException("Montant négatif: "+path);
        data.set("money-units."+path,units);data.set(path,AntiDuplication.amount(units));
    }
    private void migrateMoneyUnits()throws IOException{
        if(data.getBoolean("money-units-v1"))return;
        if(dataFile.exists()){
            Path source=dataFile.toPath(),backup=source.resolveSibling("data.yml.pre-units.bak");
            if(!Files.exists(backup))Files.copy(source,backup);
        }
        for(String group:List.of("balances","bounties","clan-banks")){
            var section=data.getConfigurationSection(group);if(section==null)continue;
            for(String name:new ArrayList<>(section.getKeys(false))){
                String path=group+"."+name;setLedgerAmount(path,ledgerAmount(path,0));
            }
        }
        setLedgerAmount("lottery.pot",ledgerAmount("lottery.pot",0));
        data.set("money-units-v1",true);
        if(!saveData())throw new IOException("Migration monétaire non sauvegardée");
    }

    private String key(UUID uuid) {
        return uuid.toString();
    }

    double balance(UUID uuid) {
        return ledgerAmount("balances."+key(uuid),0);
    }

    public double bountyAmount(UUID uuid) {
        return ledgerAmount("bounties."+key(uuid),0);
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
        setLedgerAmount("balances." + key(uuid), Math.max(0.0, amount));
        saveData();
    }

    boolean debit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || balance(uuid) < amount) return false;
        try{setLedgerAmount("balances."+key(uuid),AntiDuplication.add(balance(uuid),-amount));}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        return true;
    }

    void credit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        double next;try{next=AntiDuplication.add(balance(uuid),amount);}catch(ArithmeticException|IllegalArgumentException ex){getLogger().warning("Crédit hors limites: "+uuid);return;}
        if (!Double.isFinite(next)) {
            getLogger().warning("Crédit refusé: dépassement numérique pour " + uuid);
            return;
        }
        setLedgerAmount("balances." + key(uuid), next);
    }
    double clanBank(String bankId,double legacy){
        return ledgerAmount("clan-banks."+bankId,legacy);
    }
    boolean changeClanBank(String bankId,double legacy,UUID player,double amount,boolean deposit){
        if(!Double.isFinite(amount)||amount<=0)return false;
        String bankPath="clan-banks."+bankId,playerPath="balances."+key(player);
        double oldBank=clanBank(bankId,legacy),oldBalance=balance(player);
        if(deposit?oldBalance<amount:oldBank<amount)return false;
        double nextBank,nextBalance;try{nextBank=AntiDuplication.add(oldBank,deposit?amount:-amount);nextBalance=AntiDuplication.add(oldBalance,deposit?-amount:amount);}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        if(!Double.isFinite(nextBank)||!Double.isFinite(nextBalance))return false;
        Object oldStored=data.get(bankPath);var history=data.getStringList("transactions");
        setLedgerAmount(bankPath,nextBank);setLedgerAmount(playerPath,nextBalance);
        UUID system=new UUID(0L,0L);
        appendTransaction(deposit?"CLAN_DEPOSIT":"CLAN_WITHDRAW",deposit?player:system,deposit?system:player,amount);
        if(saveData())return true;
        if(oldStored==null){data.set(bankPath,null);data.set("money-units."+bankPath,null);}else setLedgerAmount(bankPath,oldBank);setLedgerAmount(playerPath,oldBalance);data.set("transactions",history);return false;
    }
    boolean changeBalanceSaved(UUID player,double delta){
        double previous=balance(player),next;try{next=AntiDuplication.add(previous,delta);}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        if(!Double.isFinite(delta)||!Double.isFinite(next)||next<0)return false;
        setLedgerAmount("balances."+key(player),next);
        if(saveData())return true;
        setLedgerAmount("balances."+key(player),previous);return false;
    }
    boolean hasQuestClaim(String claim){return data.getBoolean("quest-claims."+claim,false);}
    boolean commitQuestClaim(UUID player,String claim,double reward,boolean weekly){
        if(hasQuestClaim(claim)||!Double.isFinite(reward)||reward<=0)return false;
        double before=balance(player),after;try{after=AntiDuplication.add(before,reward);}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        var history=data.getStringList("transactions");
        setLedgerAmount("balances."+key(player),after);data.set("quest-claims."+claim,true);
        appendTransaction(weekly?"QUEST_WEEKLY":"QUEST_DAILY",new UUID(0L,0L),player,reward);
        if(saveData())return true;
        setLedgerAmount("balances."+key(player),before);data.set("quest-claims."+claim,null);data.set("transactions",history);return false;
    }
    boolean rewardClaimed(String claim){return data.getBoolean("reward-claims."+claim);}
    boolean commitReward(UUID player,String claim,double reward,String type){
        if(rewardClaimed(claim)||reward<=0)return false;
        double before=balance(player),after;try{after=AntiDuplication.add(before,reward);}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        var history=data.getStringList("transactions");
        setLedgerAmount("balances."+key(player),after);data.set("reward-claims."+claim,true);
        appendTransaction(type,new UUID(0L,0L),player,reward);
        if(saveData())return true;
        setLedgerAmount("balances."+key(player),before);data.set("reward-claims."+claim,null);data.set("transactions",history);return false;
    }
    boolean initChest(String id,List<ItemStack> legacy,int level){
        String path="clan-chests."+id;
        if(data.contains(path))return true;
        data.set(path+".items",legacy);data.set(path+".level",level);
        if(saveData())return true;data.set(path,null);return false;
    }
    int chestLevel(String id,int legacy){return Math.max(1,Math.min(7,data.getInt("clan-chests."+id+".level",legacy)));}
    ItemStack[] chestItems(String id){
        var list=data.getList("clan-chests."+id+".items");
        if(list==null||list.size()!=54)throw new IllegalStateException("Coffre incomplet");
        ItemStack[] result=new ItemStack[54];
        for(int i=0;i<54;i++){Object v=list.get(i);if(v!=null&&!(v instanceof ItemStack))throw new IllegalStateException("Objet de coffre invalide");result[i]=v==null?null:((ItemStack)v).clone();}
        return result;
    }
    boolean commitChest(String tx,String id,ItemStack[] expected,ItemStack[] after){
        if(commerceCommitted(tx)||!java.util.Arrays.equals(chestItems(id),expected)||after.length!=54)return false;
        String path="clan-chests."+id+".items";
        data.set(path,java.util.Arrays.asList(after));data.set("commerce-commits."+tx,true);
        if(saveData())return true;
        data.set(path,java.util.Arrays.asList(expected));data.set("commerce-commits."+tx,null);return false;
    }
    boolean upgradeChest(String id,UUID player,int level,double cost){
        String path="clan-chests."+id+".level";
        if(!data.contains(path)||chestLevel(id,1)!=level||level>=7)return false;
        double before=balance(player),after;try{after=AntiDuplication.add(before,-cost);}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        var history=data.getStringList("transactions");data.set(path,level+1);setLedgerAmount("balances."+key(player),after);
        appendTransaction("CLAN_CHEST_UPGRADE",player,new UUID(0L,0L),cost);
        if(saveData())return true;
        data.set(path,level);setLedgerAmount("balances."+key(player),before);data.set("transactions",history);return false;
    }
    boolean commitTradeMoney(UUID first,UUID second,double firstPays,double secondPays,String tradeId) {
        double a=balance(first),b=balance(second);
        if(first.equals(second)||hasTradeCommit(tradeId)||!Double.isFinite(firstPays)||!Double.isFinite(secondPays)||firstPays<0||secondPays<0||a<firstPays||b<secondPays)return false;
        double nextA,nextB;try{nextA=AntiDuplication.add(AntiDuplication.add(a,-firstPays),secondPays);nextB=AntiDuplication.add(AntiDuplication.add(b,-secondPays),firstPays);}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        if(!Double.isFinite(nextA)||!Double.isFinite(nextB))return false;
        var history=data.getStringList("transactions");
        setLedgerAmount("balances."+key(first),nextA);
        setLedgerAmount("balances."+key(second),nextB);
        appendTransaction("TRADE",first,second,firstPays);
        appendTransaction("TRADE",second,first,secondPays);
        data.set("trade-commits."+tradeId,true);
        if(saveData())return true;
        setLedgerAmount("balances."+key(first),a);setLedgerAmount("balances."+key(second),b);
        data.set("transactions",history);data.set("trade-commits."+tradeId,null);
        return false;
    }
    boolean migrateAuctions(org.bukkit.configuration.ConfigurationSection legacy){
        if(data.getBoolean("auction-migrated"))return true;
        if(legacy!=null)for(String id:legacy.getKeys(false)){
            var entry=legacy.getConfigurationSection(id);
            if(entry!=null)data.createSection("auctions."+id,entry.getValues(true));
        }
        data.set("auction-migrated",true);
        if(saveData())return true;
        data.set("auction-migrated",null);data.set("auctions",null);return false;
    }
    org.bukkit.configuration.ConfigurationSection auctions(){return data.getConfigurationSection("auctions");}
    org.bukkit.configuration.ConfigurationSection auctionEntry(String id){return data.getConfigurationSection("auctions."+id);}
    boolean commerceCommitted(String id){return data.getBoolean("commerce-commits."+id);}
    void clearCommerceCommit(String id){data.set("commerce-commits."+id,null);saveData();}
    boolean commitCommerce(String tx,UUID player,double delta,UUID seller,double sellerCredit,String auctionId,Map<String,Object> listing,boolean remove,String type){
        if(commerceCommitted(tx)||!Double.isFinite(delta)||!Double.isFinite(sellerCredit)||sellerCredit<0)return false;
        double before=balance(player),after,oldSeller=seller==null?0:balance(seller),newSeller;
        try{after=AntiDuplication.add(before,delta);newSeller=AntiDuplication.add(oldSeller,sellerCredit);}catch(ArithmeticException|IllegalArgumentException ex){return false;}
        if(!Double.isFinite(after)||after<0||!Double.isFinite(newSeller)||player.equals(seller))return false;
        String path=auctionId==null?null:"auctions."+auctionId;
        var oldEntry=path==null?null:data.getConfigurationSection(path);
        if(path!=null&&(remove?oldEntry==null:oldEntry!=null))return false;
        Map<String,Object> oldListing=oldEntry==null?null:new java.util.LinkedHashMap<>(oldEntry.getValues(false));
        var history=data.getStringList("transactions");
        setLedgerAmount("balances."+key(player),after);
        if(seller!=null)setLedgerAmount("balances."+key(seller),newSeller);
        if(path!=null){data.set(path,null);if(!remove)data.createSection(path,listing);}
        UUID system=new UUID(0L,0L);
        if(delta!=0)appendTransaction(type,delta<0?player:system,delta<0?(seller==null?system:seller):player,Math.abs(delta));
        data.set("commerce-commits."+tx,true);
        if(saveData())return true;
        setLedgerAmount("balances."+key(player),before);
        if(seller!=null)setLedgerAmount("balances."+key(seller),oldSeller);
        if(path!=null){data.set(path,null);if(oldListing!=null)data.createSection(path,oldListing);}
        data.set("transactions",history);data.set("commerce-commits."+tx,null);return false;
    }
    boolean hasTradeCommit(String tradeId){return data.getBoolean("trade-commits."+tradeId,false);}
    void clearTradeCommit(String tradeId){
        data.set("trade-commits."+tradeId,null);saveData();
    }
    void recordTransaction(String type, UUID from, UUID to, double amount) {
        appendTransaction(type,from,to,amount);saveData();
    }
    private void appendTransaction(String type, UUID from, UUID to, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        List<String> records = data.getStringList("transactions");
        records.add(System.currentTimeMillis() + "|" + type + "|" + from + "|" + to + "|" + amount);
        if (records.size() > 2000) records = new ArrayList<>(records.subList(records.size() - 2000, records.size()));
        data.set("transactions", records);
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
            return Double.isFinite(value) && value >=0.0001 && java.math.BigDecimal.valueOf(value).stripTrailingZeros().scale()<=4 && AntiDuplication.units(value)>0 ? value : null;
        } catch (IllegalArgumentException|ArithmeticException ex) {
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
                    double beforeSender=balance(player.getUniqueId()),beforeTarget=balance(target.getUniqueId());
                    List<String> beforeRecords=data.getStringList("transactions");
                    if (!debit(player.getUniqueId(), amount)) {
                        reply(sender, "Fonds insuffisants.");
                        return true;
                    }
                    double receiverNext;try{receiverNext=AntiDuplication.add(beforeTarget,amount);}catch(ArithmeticException|IllegalArgumentException ex){setLedgerAmount("balances."+key(player.getUniqueId()),beforeSender);reply(sender,"Solde du destinataire hors limites.");return true;}
                    setLedgerAmount("balances."+key(target.getUniqueId()),receiverNext);
                    appendTransaction("PAY",player.getUniqueId(),target.getUniqueId(),amount);
                    if(!saveData()){
                        setLedgerAmount("balances."+key(player.getUniqueId()),beforeSender);
                        setLedgerAmount("balances."+key(target.getUniqueId()),beforeTarget);
                        data.set("transactions",beforeRecords);
                        reply(sender,"Paiement annulé : sauvegarde impossible.");return true;
                    }
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
                    ids.removeIf(id->{try{return balance(UUID.fromString(id))<=0;}catch(IllegalArgumentException ex){return true;}});
                    ids.sort(Comparator.comparingDouble((String id) -> balance(UUID.fromString(id))).reversed());
                    reply(sender, "§6--- Classement des fortunes Larry ---");
                    int rank = 1;
                    for (String id : ids) {
                        if (rank > 10) break;
                        OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(id));
                        String playerName = offline.getName() == null ? id.substring(0, 8) : offline.getName();
                        reply(sender, rank + ". " + playerName + " : " + format(balance(UUID.fromString(id))));
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
                    double previousBounty=bountyAmount(target.getUniqueId()), previousBalance=balance(player.getUniqueId());
                    var previousHistory=data.getStringList("transactions");
                    double nextBounty;try{nextBounty=AntiDuplication.add(previousBounty,amount);}catch(ArithmeticException|IllegalArgumentException ex){reply(sender,"Prime hors limites.");return true;}
                    if (!Double.isFinite(nextBounty)) {
                        reply(sender, "La prime totale dépasserait la limite numérique.");
                        return true;
                    }
                    if (!debit(player.getUniqueId(), amount)) {
                        reply(sender, "Fonds insuffisants.");
                        return true;
                    }
                    setLedgerAmount("bounties." + targetId, nextBounty);
                    appendTransaction("BOUNTY_PLACE",player.getUniqueId(),new UUID(0L,0L),amount);
                    if(!saveData()){setLedgerAmount("bounties."+targetId,previousBounty);setLedgerAmount("balances."+key(player.getUniqueId()),previousBalance);data.set("transactions",previousHistory);reply(sender,"Prime annulée : sauvegarde impossible.");return true;}
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
                    OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if(target==null||(!target.hasPlayedBefore()&&!target.isOnline())){reply(sender,"Compte inconnu.");return true;}
                    var previousHistory=data.getStringList("transactions");
                    Double amount = parseAmount(args[2]);
                    if(args[0].equalsIgnoreCase("set") && args[2].matches("0+(?:[.,]0+)?"))amount=0.0;
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
                        case "give" -> {try{setLedgerAmount("balances."+key(target.getUniqueId()),AntiDuplication.add(oldBalance,amount));}catch(ArithmeticException|IllegalArgumentException ex){reply(sender,"Solde hors limites.");return true;}}
                        case "take" -> {
                            recordedAmount = Math.min(oldBalance, amount);
                            setLedgerAmount("balances." + key(target.getUniqueId()), oldBalance - recordedAmount);
                        }
                        case "set" -> {
                            recordedAmount = Math.abs(amount - oldBalance);
                            setLedgerAmount("balances." + key(target.getUniqueId()), amount);
                        }
                        default -> {
                            reply(sender, "Action invalide : give, take ou set.");
                            return true;
                        }
                    }
                    if (recordedAmount > 0) {
                        String action = args[0].toUpperCase(Locale.ROOT);
                        if (action.equals("TAKE") || action.equals("SET") && amount < oldBalance)
                            appendTransaction("ECO_" + action, target.getUniqueId(), systemAccount, recordedAmount);
                        else appendTransaction("ECO_" + action, systemAccount, target.getUniqueId(), recordedAmount);
                    }
                    if(!saveData()){setLedgerAmount("balances."+key(target.getUniqueId()),oldBalance);data.set("transactions",previousHistory);reply(sender,"Opération annulée : sauvegarde impossible.");return true;}
                    reply(sender, "Solde de " + args[1] + " : " + format(balance(target.getUniqueId())));
                    return true;
                }
                case "lottery" -> {
                    if(args.length==0||args[0].equalsIgnoreCase("menu")||args[0].equalsIgnoreCase("pot")) {
                        Player viewer=requirePlayer(sender);if(viewer!=null)features.openLotteryMenu(viewer);return true;
                    }
                    if(args[0].equalsIgnoreCase("buy")) {
                        Player player=requirePlayer(sender);if(player==null)return true;
                        if(!lotteryActive()||System.currentTimeMillis()>=lotteryEndsAt()){reply(sender,"Aucune loterie ouverte. Attends le prochain tirage.");return true;}
                        int count=args.length>=2?Integer.parseInt(args[1]):1;
                        if(count<1||count>1000){reply(sender,"Choisis entre 1 et 1 000 tickets.");return true;}
                        if(lotteryTicketCount()>100000-count){reply(sender,"Limite totale de tickets atteinte.");return true;}
                        double cost=count*50.0,nextPot;try{nextPot=AntiDuplication.add(lotteryPot(),cost);}catch(ArithmeticException|IllegalArgumentException ex){reply(sender,"Jackpot hors limites.");return true;}
                        if(!Double.isFinite(nextPot)){reply(sender,"Le jackpot dépasserait la limite numérique.");return true;}
                        double beforeBalance=balance(player.getUniqueId()),beforePot=lotteryPot();
                        List<String> beforeTickets=data.getStringList("lottery.tickets"),beforeRecords=data.getStringList("transactions");
                        if(!debit(player.getUniqueId(),cost)){reply(sender,"Fonds insuffisants : "+format(cost)+".");return true;}
                        List<String> tickets=new ArrayList<>(beforeTickets);for(int i=0;i<count;i++)tickets.add(key(player.getUniqueId()));
                        data.set("lottery.tickets",tickets);setLedgerAmount("lottery.pot",nextPot);
                        appendTransaction("LOTTERY_BUY",player.getUniqueId(),new UUID(0L,0L),cost);
                        if(!saveData()){setLedgerAmount("balances."+key(player.getUniqueId()),beforeBalance);setLedgerAmount("lottery.pot",beforePot);data.set("lottery.tickets",beforeTickets);data.set("transactions",beforeRecords);reply(sender,"Achat annulé : sauvegarde impossible.");return true;}
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
    public double lotteryPot(){return ledgerAmount("lottery.pot",0);}
    public int lotteryTicketCount(){return data.getStringList("lottery.tickets").size();}
    public boolean lotteryActive(){return data.getBoolean("lottery.active",false);}
    public long lotteryEndsAt(){return data.getLong("lottery.ends-at",0L);}
    private boolean isLpcAdminSender(CommandSender sender){return sender.isOp()||sender.hasPermission("larpers.admin");}
    public boolean isLpcAdmin(Player player){return isLpcAdminSender(player);}
    boolean isEconomyPlayer(Player player){return player.isOnline()&&!player.isDead()&&auth!=null&&auth.isAuthenticated(player)&&!getConfig().getBoolean("network.auth-only",false)&&player.getGameMode()==org.bukkit.GameMode.SURVIVAL;}

    private org.bukkit.scheduler.BukkitTask lotteryTimer;

    private void startLottery(int minutes,String starter) {
        long end=System.currentTimeMillis()+minutes*60_000L;
        data.set("lottery.active",true);data.set("lottery.ends-at",end);saveData();
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"§l✦ LOTERIE LARRY SMP ✦");
        Bukkit.broadcastMessage(ChatColor.YELLOW+"Une nouvelle loterie vient de commencer!");
        Bukkit.broadcastMessage(ChatColor.GREEN+"Ticket: 50 Larry | Durée: "+minutes+" minute(s) | Jackpot: "+format(lotteryPot()));
        Bukkit.broadcastMessage(ChatColor.AQUA+"Ouvre /lottery pour acheter tes tickets!");
        Bukkit.broadcastMessage(ChatColor.GRAY+"Lancée par "+starter+".");
        lotteryTimer=getServer().getScheduler().runTaskLater(this,()->finishLottery(true),minutes*60L*20L);
    }
    private void finishLottery(boolean automatic) {
        if(!lotteryActive())return;
        if(lotteryTimer!=null){lotteryTimer.cancel();lotteryTimer=null;}
        List<String> tickets=data.getStringList("lottery.tickets");
        if(tickets.isEmpty()) {
            data.set("lottery.active",false);data.set("lottery.ends-at",0L);saveData();
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[LOTERIE] Fin du tirage: aucun ticket vendu. Le jackpot est conservé.");
            return;
        }
        drawLottery();
    }
    private String rankPrefix(Player p) {
        if(p.hasPermission("group.owner"))return ChatColor.DARK_RED+"[OWNER] ";
        if(isLpcAdmin(p))return ChatColor.RED+"[ADMIN] ";
        if(p.hasPermission("larpers.staff")||p.hasPermission("larpers.anticheat.mod"))return ChatColor.GREEN+"[MOD] ";
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
        if (!Bukkit.getPluginManager().isPluginEnabled("GrimAC"))return;
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
            if(!player.hasPermission(permission)){var attachment = player.addAttachment(this);attachment.setPermission(permission, true);}
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
        tickets.removeIf(id->{try{UUID.fromString(id);return false;}catch(IllegalArgumentException ex){return true;}});
        if(tickets.isEmpty()){getLogger().severe("Tirage suspendu: aucun ticket valide. Données conservées.");return;}
        String winnerId = tickets.get(ThreadLocalRandom.current().nextInt(tickets.size()));
        double jackpot = lotteryPot();
        UUID winnerUuid = UUID.fromString(winnerId);
        if (!Double.isFinite(jackpot) || jackpot <= 0.0 || !Double.isFinite(balance(winnerUuid) + jackpot)) {
            getLogger().severe("Tirage annulé: jackpot ou solde du gagnant invalide.");
            return;
        }
        double beforeBalance=balance(winnerUuid);long beforeEnd=lotteryEndsAt();
        List<String> beforeTickets=data.getStringList("lottery.tickets"),beforeRecords=data.getStringList("transactions");
        double winnerBalance;try{winnerBalance=AntiDuplication.add(beforeBalance,jackpot);}catch(ArithmeticException|IllegalArgumentException ex){getLogger().severe("Jackpot hors limite : tirage suspendu.");return;}
        setLedgerAmount("balances."+key(winnerUuid),winnerBalance);
        data.set("lottery.tickets", new ArrayList<String>());
        setLedgerAmount("lottery.pot", 0.0);
        data.set("lottery.active",false);data.set("lottery.ends-at",0L);
        appendTransaction("LOTTERY_WIN",new UUID(0L,0L),winnerUuid,jackpot);
        if(!saveData()){setLedgerAmount("balances."+key(winnerUuid),beforeBalance);data.set("lottery.tickets",beforeTickets);setLedgerAmount("lottery.pot",jackpot);data.set("lottery.active",true);data.set("lottery.ends-at",beforeEnd);data.set("transactions",beforeRecords);getLogger().severe("Tirage annulé : sauvegarde impossible. Réessayer /lottery draw.");return;}
        OfflinePlayer winner = Bukkit.getOfflinePlayer(UUID.fromString(winnerId));
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Larry] " + (winner.getName() == null ? "Un joueur" : winner.getName()) + " remporte la loterie : " + format(jackpot) + " !");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getUniqueId().equals(event.getEntity().getUniqueId())||!isEconomyPlayer(killer)) return;
        UUID victim=event.getEntity().getUniqueId();
        String first=killer.getUniqueId().toString(),second=victim.toString();
        String pair=first.compareTo(second)<0?first+"_"+second:second+"_"+first;
        String cooldown="bounty-pairs."+pair;
        long previousKill=data.getLong(cooldown,0L),now=System.currentTimeMillis();
        if(now-previousKill<86400000L)return;
        String victimId = key(event.getEntity().getUniqueId());
        double bounty = bountyAmount(victim);
        if (!Double.isFinite(bounty) || bounty <= 0.0) return;
        if (!Double.isFinite(balance(killer.getUniqueId()) + bounty)) {
            getLogger().warning("Prime conservée: solde maximal atteint pour " + killer.getName());
            return;
        }
        double previous=balance(killer.getUniqueId());var history=data.getStringList("transactions");
        double after;try{after=AntiDuplication.add(previous,bounty);}catch(ArithmeticException|IllegalArgumentException ex){return;}
        setLedgerAmount("bounties." + victimId, 0.0);data.set(cooldown,now);
        setLedgerAmount("balances."+key(killer.getUniqueId()),after);
        appendTransaction("BOUNTY_WIN",new UUID(0L,0L),killer.getUniqueId(),bounty);
        if(!saveData()){data.set(cooldown,previousKill);setLedgerAmount("bounties."+victimId,bounty);setLedgerAmount("balances."+key(killer.getUniqueId()),previous);data.set("transactions",history);getLogger().warning("Prime non versée : sauvegarde impossible.");return;}
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
