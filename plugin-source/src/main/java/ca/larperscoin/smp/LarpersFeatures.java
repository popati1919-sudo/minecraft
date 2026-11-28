package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.temporal.WeekFields;

/** Modules sociaux, progression, commerce et modération de LarpersCoin. */
public final class LarpersFeatures implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final LarpersCoin core;
    private final YamlConfiguration db;
    private final File file;
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<String> playerPlacedBlocks = new HashSet<>();
    private final Map<String, Inventory> clanChests = new HashMap<>();
    private final Map<String, UUID> clanChestUsers = new HashMap<>();
    private static final class ClanChestHolder implements InventoryHolder {
        final String clan; final UUID viewer; Inventory inventory;
        ClanChestHolder(String clan,UUID viewer){this.clan=clan;this.viewer=viewer;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private final Set<UUID> vanished = new HashSet<>();
    private final SecureTradeManager tradeManager;
    private final ChatEvents chatEvents;
    private final Map<UUID, Long> lastQuiz = new HashMap<>();
    private final Map<UUID, Long> questClaims = new HashMap<>();
    private final Map<UUID, UUID> pendingBids = new HashMap<>();
    private final Map<UUID, String> shopCategory = new HashMap<>();
    private final Map<UUID, Integer> shopPage = new HashMap<>();
    private final Map<UUID, String> shopSearch = new HashMap<>();
    private final Set<UUID> awaitingShopSearch = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> adminPage = new HashMap<>();
    private final Map<UUID, Integer> ahPage = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> ahSlots = new HashMap<>();
    private final Map<UUID, String> ahSearch = new HashMap<>();
    private final Set<UUID> awaitingAhSearch = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> pendingBountyTarget = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingOpTarget = new HashMap<>();
    private final Map<UUID, String> selectorAction = new HashMap<>();
    private final Map<UUID, String> selectorSearch = new HashMap<>();
    private final Set<UUID> awaitingSelectorSearch = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> bountySearch = new HashMap<>();
    private final Set<UUID> awaitingBountySearch = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingBountyListSearch = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingLotteryAmount = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingAdminIp = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final String PREFIX = ChatColor.GOLD + "[Larry] " + ChatColor.RESET;
    private static final Map<String, Material> SHOP = new LinkedHashMap<>();
    private static final Map<String, Double> SHOP_PRICES = new LinkedHashMap<>();
    private boolean saveQueued = false;

    private static final Set<Material> SHOP_BLOCKED=EnumSet.of(Material.ELYTRA,Material.NETHERITE_INGOT,Material.NETHERITE_BLOCK,Material.ANCIENT_DEBRIS,Material.BEACON);
    private static final Set<String> SHOP_BLOCKED_NAMES=Set.of("COMMAND_BLOCK","CHAIN_COMMAND_BLOCK","REPEATING_COMMAND_BLOCK","STRUCTURE_BLOCK","STRUCTURE_VOID","JIGSAW","BARRIER","BEDROCK","END_PORTAL_FRAME","SPAWNER","TRIAL_SPAWNER","VAULT","LIGHT","DEBUG_STICK","KNOWLEDGE_BOOK");
    private static final long MARKET_REFRESH_MILLIS=21600000L;
    static {for(Material m:Material.values()){String n=m.name();if(!m.isItem()||m.isAir()||SHOP_BLOCKED.contains(m)||SHOP_BLOCKED_NAMES.contains(n)||n.contains("NETHERITE")||n.endsWith("_SPAWN_EGG"))continue;String k=n.toLowerCase(Locale.ROOT);SHOP.put(k,m);SHOP_PRICES.put(k,baseShopPrice(m));}}
    private static double baseShopPrice(Material m){String n=m.name();if(n.contains("DIAMOND"))return 2.50;if(n.contains("EMERALD"))return 1.80;if(n.contains("NETHER_STAR")||n.contains("DRAGON_EGG"))return 8;if(n.contains("TRIDENT")||n.contains("HEART_OF_THE_SEA")||n.contains("TOTEM"))return 2;if(n.contains("IRON_INGOT")||n.contains("IRON_NUGGET"))return .55;if(n.contains("GOLD_INGOT")||n.contains("GOLD_NUGGET"))return .45;if(n.contains("COPPER_INGOT"))return .18;if(n.contains("LAPIS"))return .12;if(n.contains("REDSTONE"))return .10;if(n.contains("COAL"))return .16;if(n.contains("OBSIDIAN"))return .25;if(n.contains("BREAD")||n.contains("COOKED"))return .18;if(n.contains("LOG")||n.contains("WOOD")||n.contains("PLANKS")||n.contains("STEM")||n.contains("HYPHAE"))return .08;if(n.contains("WHEAT")||n.contains("CARROT")||n.contains("POTATO")||n.contains("BEETROOT")||n.contains("PUMPKIN")||n.contains("MELON"))return .08;if(n.contains("STONE")||n.contains("COBBLESTONE")||n.contains("DEEPSLATE")||n.contains("TUFF")||n.contains("GRANITE")||n.contains("DIORITE")||n.contains("ANDESITE"))return .025;if(n.contains("DIRT")||n.contains("SAND")||n.contains("GRAVEL")||n.contains("CLAY")||n.contains("NETHERRACK"))return .015;if(n.contains("BOOK")||n.contains("POTION")||n.contains("ENCHANTED"))return .30;if(n.contains("SWORD")||n.contains("PICKAXE")||n.contains("AXE")||n.contains("HOE")||n.contains("SHOVEL")||n.contains("HELMET")||n.contains("CHESTPLATE")||n.contains("LEGGINGS")||n.contains("BOOTS"))return .35;return .05;}

    private final StaffWatch watch;

    public LarpersFeatures(JavaPlugin plugin) {
        this.plugin = plugin;
        watch=new StaffWatch(plugin,this);
        this.core = (LarpersCoin) plugin;
        this.tradeManager = new SecureTradeManager(plugin, core, this::recordTradeCompleted);
        this.chatEvents = new ChatEvents(plugin, core);
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "features.yml");
        db=new YamlConfiguration();
        try{if(file.exists())db.load(file);}catch(Exception ex){throw new IllegalStateException("features.yml illisible",ex);}
        auctionsReady=core.migrateAuctions(db.getConfigurationSection("ah"));
        for(var e:SHOP_PRICES.entrySet()){String k=e.getKey();if(!db.contains("shop.sell-prices."+k))db.set("shop.sell-prices."+k,e.getValue());if(!db.contains("shop.buy-prices."+k))db.set("shop.buy-prices."+k,Math.max(.02,e.getValue()*2.8));}
        Bukkit.getScheduler().runTask(plugin,this::rebuildRecipeCaps);
        if(!db.contains("market.next-refresh"))db.set("market.next-refresh",System.currentTimeMillis()+MARKET_REFRESH_MILLIS);
        long delay=Math.max(1,(db.getLong("market.next-refresh")-System.currentTimeMillis())/50L);
        Bukkit.getScheduler().runTaskLater(plugin,()->{recalculateMarketPrices();Bukkit.getScheduler().runTaskTimer(plugin,this::recalculateMarketPrices,MARKET_REFRESH_MILLIS/50L,MARKET_REFRESH_MILLIS/50L);},delay);
        seedQuizQuestion("compass","Quel minerai permet de fabriquer une boussole?","fer",List.of("fer","cuivre","or"));
        seedQuizQuestion("faces","Combien de faces possède un bloc standard?","6",List.of("4","6","8"));
        seedQuizQuestion("creeper","Quel mob explose près du joueur?","creeper",List.of("creeper","zombie","squelette"));
        save();
        for (String cmd : List.of("clan","quest","quests","succes","top","admin","rank","vanish","invsee","freeze","staffchat","ah","trade","check","shop","sell")) {
            PluginCommand c = plugin.getCommand(cmd);
            if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::save,1200L,1200L);
        plugin.getLogger().info("Modules clans, quêtes, classements, commerce entre joueurs et modération chargés.");
    }

    private boolean save() {
        // Clan chest contents are owned by the transaction ledger, never by GUI snapshots.
        Path target=file.toPath(),temporary=target.resolveSibling(target.getFileName()+".tmp");
        try {
            byte[] bytes=db.saveToString().getBytes(StandardCharsets.UTF_8);
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch(IOException ex) {
            plugin.getLogger().severe("Échec de sauvegarde atomique de features.yml: "+ex.getMessage());
            try { Files.deleteIfExists(temporary); } catch(IOException ignored) { }
            return false;
        }
    }
    private void seedQuizQuestion(String key,String question,String answer,List<String> choices) {
        String path="quiz-bank."+key;
        if(!db.contains(path+".question")) {
            db.set(path+".question",question);
            db.set(path+".answer",answer);
            db.set(path+".choices",choices);
        }
    }
    public void shutdown() { tradeManager.shutdown(); watch.shutdown(); chatEvents.shutdown(); save(); }
    private void msg(CommandSender s, String m) { s.sendMessage(PREFIX + m); }
    private boolean staff(CommandSender s) { return s.isOp() || s.hasPermission("larpers.staff") || s.hasPermission("larpers.admin"); }
    private Player player(CommandSender s) { if (s instanceof Player p) return p; msg(s,"Commande réservée aux joueurs."); return null; }
    private String id(Player p) { return p.getUniqueId().toString(); }
    private String clanOf(UUID u) { return db.getString("clan-members."+u); }
    private String clanPath(String clan) { return "clans."+clan.toLowerCase(Locale.ROOT); }
    private boolean member(Player p) { return clanOf(p.getUniqueId()) != null; }
    private String clanBankId(String clan){return db.getString(clanPath(clan)+".bank-id","legacy-"+clan);}
    private double clanBank(String clan){return core.clanBank(clanBankId(clan),db.getDouble(clanPath(clan)+".bank"));}
    private boolean prepareChest(String clan){
        String path=clanPath(clan);
        int level=db.getInt(path+".chest-level",db.contains(path+".chest")?7:1);
        level=Math.max(1,Math.min(7,level));
        ItemStack[] legacy=new ItemStack[54];var list=db.getList(path+".chest",List.of());
        for(int i=0;i<Math.min(54,list.size());i++){
            Object value=list.get(i);
            if(value==null)continue;
            if(!(value instanceof ItemStack st))return false;
            if(st.getType().isAir()||isChestLock(st))continue;
            legacy[i]=st.clone();
            while(level<7&&i>=AntiDuplication.capacity(level))level++;
        }
        return core.initChest(clanBankId(clan),Arrays.asList(legacy),level);
    }
    private int chestLevel(String clan){return core.chestLevel(clanBankId(clan),db.getInt(clanPath(clan)+".chest-level",db.contains(clanPath(clan)+".chest")?7:1));}
    private void renderChest(ClanChestHolder holder){
        ItemStack[] items=core.chestItems(clanBankId(holder.clan));
        int unlocked=AntiDuplication.capacity(chestLevel(holder.clan));
        for(int i=0;i<54;i++)holder.inventory.setItem(i,i<unlocked?items[i]:item(Material.GRAY_STAINED_GLASS_PANE,"Emplacement verrouillé","/clan chest upgrade"));
    }
    private boolean validChest(Player p,ClanChestHolder holder){
        return holder.viewer.equals(p.getUniqueId())&&holder.clan.equals(clanOf(p.getUniqueId()))
            &&db.getStringList(clanPath(holder.clan)+".members").contains(id(p))
            &&Objects.equals(clanChestUsers.get(holder.clan),p.getUniqueId())&&clanChests.get(holder.clan)==holder.inventory;
    }
    private void transferChest(Player p,ClanChestHolder holder,int raw){
        if(!validChest(p,holder)||!core.isEconomyPlayer(p)||watch.active(p))return;
        if(p.getOpenInventory().getTopInventory()!=holder.inventory||!p.getItemOnCursor().getType().isAir())return;
        String clan=holder.clan,chestId=clanBankId(clan);
        ItemStack[] before=core.chestItems(chestId),after=Arrays.stream(before).map(x->x==null?null:x.clone()).toArray(ItemStack[]::new);
        ItemStack[] playerAfter=p.getInventory().getStorageContents();
        int unlocked=AntiDuplication.capacity(chestLevel(clan));
        String action;
        if(raw>=0&&raw<54){
            if(raw>=unlocked||before[raw]==null||before[raw].getType().isAir())return;
            Inventory test=Bukkit.createInventory(null,36);test.setStorageContents(playerAfter);
            if(!test.addItem(before[raw].clone()).isEmpty()){msg(p,"Inventaire plein.");return;}
            playerAfter=test.getStorageContents();after[raw]=null;action="retrait "+before[raw].getAmount()+" "+before[raw].getType();
        }else{
            int slot=p.getOpenInventory().convertSlot(raw);if(slot<0||slot>=36)return;
            ItemStack stack=playerAfter[slot];if(stack==null||stack.getType().isAir())return;
            int target=-1;for(int i=0;i<unlocked;i++)if(after[i]==null||after[i].getType().isAir()){target=i;break;}
            if(target<0){msg(p,"Coffre plein.");return;}
            after[target]=stack.clone();playerAfter[slot]=null;action="dépôt "+stack.getAmount()+" "+stack.getType();
        }
        if(!tradeManager.chestTransfer(p,playerAfter,chestId,before,after)){msg(p,"Transfert non confirmé.");return;}
        List<String> audit=db.getStringList(clanPath(clan)+".chest-audit");
        audit.add(System.currentTimeMillis()+"|"+id(p)+"|"+action);
        db.set(clanPath(clan)+".chest-audit",new ArrayList<>(audit.subList(Math.max(0,audit.size()-500),audit.size())));
        saveSoon();if(validChest(p,holder))renderChest(holder);
    }
    private void give(Player p, double amount) {
        core.credit(p.getUniqueId(), amount);
        core.saveData();
    }
    private boolean charge(Player p, double amount) {
        return Double.isFinite(amount)&&amount>0&&core.changeBalanceSaved(p.getUniqueId(),-amount);
    }
    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(x -> ChatColor.GRAY+x).toList());
        it.setItemMeta(meta); return it;
    }
    public void openLotteryMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Loterie");
        boolean active=core.lotteryActive();
        inv.setItem(10,item(Material.PAPER,"Acheter 1 ticket","Prix: 50 Larry","Achat rapide"));
        inv.setItem(12,item(Material.WRITABLE_BOOK,"Personnaliser","Choisis le nombre de tickets","De 1 à 1 000 tickets"));
        inv.setItem(14,item(Material.EMERALD,"Jackpot: "+core.lotteryPot()+" Larry",
            "Tickets vendus: "+core.lotteryTicketCount(),
            active?"Loterie en cours":"Aucune loterie en cours"));
        inv.setItem(16,item(Material.CLOCK,active?"Loterie en cours":"En attente d'un admin",
            active?"Fin: "+new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(core.lotteryEndsAt())):"Un administrateur doit la démarrer"));
        if(core.isLpcAdmin(p)) {
            inv.setItem(22,item(Material.COMMAND_BLOCK,active?"Tirage / terminer":"Démarrer la loterie",
                active?"Clique pour terminer maintenant":"Démarre une loterie de 10 minutes",
                "Annonce envoyée à tout le serveur"));
        } else inv.setItem(22,item(Material.BARRIER,"Action réservée aux admins","Les joueurs peuvent acheter des tickets"));
        inv.setItem(26,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }

    private void openTopMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Classements");
        inv.setItem(11,item(Material.GOLD_INGOT,"Fortunes Larry","Voir les 10 joueurs les plus riches","/baltop"));
        inv.setItem(13,item(Material.EMERALD,"Classement des clans","Voir les clans les plus puissants","/clan top"));
        inv.setItem(15,item(Material.TARGET,"Primes actives","Voir les primes sur les joueurs","/bounty list"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }

    private void openClanMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Clan");
        inv.setItem(10,item(Material.BOOK,"Informations","/clan info"));
        inv.setItem(12,item(Material.GOLD_INGOT,"Banque","/clan bank"));
        inv.setItem(14,item(Material.CHEST,"Coffre partagé","1 emplacement disponible au départ","/clan chest"));
        String own=clanOf(p.getUniqueId());
        int chestLevel=own==null?1:chestLevel(own);
        double upgradePrice=250.0*Math.pow(2,chestLevel-1);
        inv.setItem(15,item(Material.ANVIL,"Améliorer le coffre","Capacité actuelle: "+(chestLevel==1?1:(chestLevel-1)*9)+"/54","Prochaine amélioration: "+upgradePrice+" Larry","Clique pour payer et agrandir"));
        inv.setItem(16,item(Material.EMERALD,"Classement","/clan top"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private void openQuestMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Quêtes");
        inv.setItem(11,item(Material.CLOCK,"Quête quotidienne",
            "Blocs: "+db.getInt(questPath(false,p,"blocks"),0)+"/20",
            "Cultures: "+db.getInt(questPath(false,p,"crops"),0)+"/10",
            "Combats: "+db.getInt(questPath(false,p,"kills"),0)+"/5",
            "Pêche: "+db.getInt(questPath(false,p,"fish"),0)+"/3",
            "Récompense: 100 Larry","Clique pour réclamer"));
        inv.setItem(15,item(Material.DIAMOND,"Quête hebdomadaire",
            "Blocs: "+db.getInt(questPath(true,p,"blocks"),0)+"/75",
            "Cultures: "+db.getInt(questPath(true,p,"crops"),0)+"/30",
            "Combats: "+db.getInt(questPath(true,p,"kills"),0)+"/20",
            "Pêche: "+db.getInt(questPath(true,p,"fish"),0)+"/10",
            "Récompense: 400 Larry","Clique pour réclamer"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private final Map<UUID,UUID> rankTargets=new HashMap<>();
    private void openRank(Player admin,Player target) {
        if(!core.isLpcAdmin(admin)) return;
        if(!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {msg(admin,"LuckPerms est requis sur le SMP.");return;}
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Grades");
        inv.setItem(4,item(Material.PLAYER_HEAD,target.getName(),"Choisis son nouveau grade"));
        inv.setItem(10,item(Material.GRASS_BLOCK,"Joueur"));
        inv.setItem(12,item(Material.SHIELD,"Modo"));
        inv.setItem(14,item(Material.DIAMOND,"Admin"));
        if(admin.isOp()) inv.setItem(16,item(Material.NETHER_STAR,"Owner"));
        inv.setItem(22,item(Material.BARRIER,"Annuler"));
        admin.openInventory(inv);
        rankTargets.put(admin.getUniqueId(),target.getUniqueId());
    }

    private void openModerator(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Modération");
        boolean active=watch.active(p);
        inv.setItem(4,item(Material.SPYGLASS,active?"Quitter la surveillance":"Activer la surveillance"));
        if(active) {
            inv.setItem(10,item(Material.ENDER_PEARL,"Observer un joueur"));
            inv.setItem(11,item(Material.ICE,"Immobiliser ou libérer"));
            inv.setItem(12,item(Material.ENDER_EYE,"Vanish"));
        }
        inv.setItem(14,item(Material.PAPER,"Mute du chat"));
        inv.setItem(15,item(Material.FEATHER,"Retirer un mute"));
        inv.setItem(16,item(Material.COMPARATOR,"Alertes anti-triche","Afficher le journal récent"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        p.openInventory(inv);
    }

    private void openAdmin(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Administration");
        inv.setItem(10,item(Material.BOOK,"Voir inventaire","Choisir un joueur"));
        inv.setItem(12,item(Material.ICE,"Freeze / dégeler","Choisir un joueur"));
        inv.setItem(14,item(Material.ENDER_EYE,"Vanish","Activer ou désactiver"));
        inv.setItem(16,item(Material.REDSTONE_TORCH,"Staffchat","/staffchat <message>"));
        inv.setItem(19,item(Material.TRIPWIRE_HOOK,"Réinitialiser mot de passe","Choisir un joueur","Le joueur sera expulsé et devra refaire /register"));
        inv.setItem(21,item(Material.GOLD_INGOT,"Économie Larry","/eco give|take|set <joueur> <montant>"));
        inv.setItem(23,item(Material.FIREWORK_ROCKET,"Loterie","Ouvrir le menu et gérer le tirage"));
        inv.setItem(25,item(Material.EMERALD,"Classements","Ouvrir /top"));
        inv.setItem(28,item(Material.COMMAND_BLOCK,"Gestion OP","Choisir un joueur","Confirmation obligatoire"));
        inv.setItem(30,item(Material.COMPASS,"Adresse du serveur","IP actuelle: "+serverDisplay("ip","Non configurée"),"Clique puis saisis: IP nom"));
        inv.setItem(32,item(Material.SHIELD,"Anti-triche","/ac status"));
        inv.setItem(40,item(Material.NAME_TAG,"Grades des joueurs","Ouvrir /rank"));
        inv.setItem(34,item(Material.SUNFLOWER,"Actualiser le menu"));
        inv.setItem(49,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private boolean auctionsReady;
    private void openAuction(Player p,int page) {
        if(!auctionsReady){msg(p,"Hôtel des ventes indisponible.");return;}
        List<String> keys=new ArrayList<>();
        var section=core.auctions();
        if(section!=null) keys.addAll(section.getKeys(false));
        keys.removeIf(k->{var entry=core.auctionEntry(k);return entry==null||(!id(p).equals(entry.getString("sellerId"))&&System.currentTimeMillis()-entry.getLong("created",0L)>7L*86400000L);});
        String query=ahSearch.getOrDefault(p.getUniqueId(),"").toLowerCase(Locale.ROOT).trim();
        if(!query.isEmpty()) keys.removeIf(k -> {
            var entry=core.auctionEntry(k);if(entry==null)return true;
            ItemStack stack=entry.getItemStack("stack");
            String seller=entry.getString("seller","");
            String material=stack==null?"":stack.getType().name();
            String display=stack!=null&&stack.hasItemMeta()&&stack.getItemMeta().hasDisplayName()?ChatColor.stripColor(stack.getItemMeta().getDisplayName()):"";
            return !(k.toLowerCase(Locale.ROOT).contains(query)||seller.toLowerCase(Locale.ROOT).contains(query)
                ||material.toLowerCase(Locale.ROOT).contains(query)||display.toLowerCase(Locale.ROOT).contains(query));
        });
        keys.sort(Comparator.comparingLong(k->core.auctionEntry(k).getLong("created",0L)));
        Collections.reverse(keys);
        int maxPage=Math.max(0,(keys.size()-1)/45);
        page=Math.max(0,Math.min(page,maxPage));
        ahPage.put(p.getUniqueId(),page);
        Map<Integer,String> slots=new HashMap<>();
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Hôtel des ventes");
        int start=page*45;
        for(int n=start;n<Math.min(keys.size(),start+45);n++) {
            String key=keys.get(n);var entry=core.auctionEntry(key);
            ItemStack stack=entry.getItemStack("stack");
            if(stack==null||stack.getType()==Material.AIR) continue;
            ItemStack display=stack.clone();
            ItemMeta meta=display.getItemMeta();
            if(meta!=null) {
                List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
                lore.add(ChatColor.GRAY+"Vendeur: "+entry.getString("seller","inconnu"));
                lore.add(ChatColor.GOLD+"Prix: "+entry.getDouble("price")+" Larry");
                lore.add(ChatColor.YELLOW+"Clique pour acheter / récupérer");
                meta.setLore(lore);display.setItemMeta(meta);
            }
            int slot=n-start;inv.setItem(slot,display);slots.put(slot,key);
        }
        ahSlots.put(p.getUniqueId(),slots);
        inv.setItem(45,item(Material.EMERALD,"Vendre l'objet en main","Clique puis choisis un prix"));
        inv.setItem(46,item(Material.COMPASS,"Rechercher","Clique puis écris un objet ou un vendeur dans le chat","Recherche actuelle: "+(query.isEmpty()?"aucune":query)));
        inv.setItem(47,item(Material.MILK_BUCKET,"Effacer la recherche","Retirer le filtre actuel"));
        if(page>0)inv.setItem(48,item(Material.ARROW,"Page précédente"));
        inv.setItem(49,item(Material.BARRIER,"Fermer"));
        if(page<maxPage)inv.setItem(50,item(Material.ARROW,"Page suivante"));
        inv.setItem(53,item(Material.SUNFLOWER,"Actualiser",keys.size()+" annonce(s)"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    public void openBountyMenu(CommandSender sender) {
        Player viewer=player(sender);
        if(viewer==null)return;
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Primes");
        String query=bountySearch.getOrDefault(viewer.getUniqueId(),"").toLowerCase(Locale.ROOT).trim();
        List<Map.Entry<UUID,Double>> entries=core.bountyLeaderboard().entrySet().stream()
            .filter(entry -> {
                if(query.isEmpty())return true;
                String name=Bukkit.getOfflinePlayer(entry.getKey()).getName();
                return name!=null&&name.toLowerCase(Locale.ROOT).contains(query);
            })
            .sorted(Map.Entry.<UUID,Double>comparingByValue().reversed()).toList();
        int slot=0;
        for(Map.Entry<UUID,Double> entry:entries) {
            if(slot>=45)break;
            OfflinePlayer target=Bukkit.getOfflinePlayer(entry.getKey());
            String name=target.getName()==null?entry.getKey().toString().substring(0,8):target.getName();
            inv.setItem(slot++,item(Material.GOLD_INGOT,name,"Prime active: "+entry.getValue()+" Larry",target.isOnline()?"En ligne":"Hors ligne"));
        }
        if(entries.isEmpty())inv.setItem(22,item(Material.PAPER,"Aucune prime active","Place une prime sur un joueur en ligne."));
        inv.setItem(45,item(Material.TARGET,"Placer une prime","Choisir un joueur en ligne"));
        inv.setItem(46,item(Material.COMPASS,"Rechercher une prime","Filtrer par pseudo"));
        inv.setItem(47,item(Material.MILK_BUCKET,"Effacer la recherche"));
        inv.setItem(49,item(Material.BARRIER,"Fermer"));
        inv.setItem(53,item(Material.SUNFLOWER,"Actualiser",entries.size()+" prime(s) active(s)"));
        LpcMenuTheme.frame(inv);
        viewer.openInventory(inv);
    }
    private void openBountyTargets(Player viewer) {
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Choisir une cible");
        int slot=0;
        String query=bountySearch.getOrDefault(viewer.getUniqueId(),"").toLowerCase(Locale.ROOT).trim();
        for(Player target:Bukkit.getOnlinePlayers()) {
            if(target.equals(viewer)||slot>=45)continue;
            if(!query.isEmpty()&&!target.getName().toLowerCase(Locale.ROOT).contains(query))continue;
            inv.setItem(slot++,item(Material.PLAYER_HEAD,target.getName(),"Prime actuelle: "+core.bountyAmount(target.getUniqueId())+" Larry","Clique pour choisir"));
        }
        if(slot==0)inv.setItem(22,item(Material.PAPER,"Aucun joueur trouvé",query.isEmpty()?"Aucun autre joueur en ligne":"Recherche: "+query));
        inv.setItem(45,item(Material.COMPASS,"Rechercher un pseudo","Saisis le pseudo dans le chat"));
        inv.setItem(47,item(Material.MILK_BUCKET,"Effacer la recherche"));
        inv.setItem(49,item(Material.ARROW,"Retour aux primes"));
        inv.setItem(53,item(Material.SUNFLOWER,"Actualiser"));
        LpcMenuTheme.frame(inv);
        viewer.openInventory(inv);
    }
    private void openAuctionSell(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Prix de vente");
        inv.setItem(10,item(Material.IRON_INGOT,"10 Larry","Mettre l'objet en main en vente"));
        inv.setItem(12,item(Material.GOLD_INGOT,"50 Larry","Mettre l'objet en main en vente"));
        inv.setItem(14,item(Material.EMERALD,"100 Larry","Mettre l'objet en main en vente"));
        inv.setItem(16,item(Material.DIAMOND,"500 Larry","Mettre l'objet en main en vente"));
        inv.setItem(22,item(Material.BARRIER,"Annuler"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private static boolean forbiddenCommerce(Material m){return SHOP_BLOCKED.contains(m)||SHOP_BLOCKED_NAMES.contains(m.name())||m.name().contains("NETHERITE")||m.name().endsWith("_SPAWN_EGG");}
    private void createAuction(Player p,double price) {
        if(!auctionsReady||!core.isEconomyPlayer(p)){msg(p,"Hôtel des ventes indisponible.");return;}
        ItemStack hand=p.getInventory().getItemInMainHand();
        if(!Double.isFinite(price)||price<=0||hand.getType().isAir()||java.math.BigDecimal.valueOf(price).stripTrailingZeros().scale()>4){msg(p,"Prix ou objet invalide.");return;}
        if(forbiddenCommerce(hand.getType())){msg(p,"Objet interdit à la vente.");return;}
        var listings=core.auctions();
        if(listings!=null&&listings.getKeys(false).stream().filter(k->id(p).equals(core.auctionEntry(k).getString("sellerId"))).count()>=50){msg(p,"Limite de 50 annonces atteinte.");return;}
        String key=UUID.randomUUID().toString();
        Map<String,Object> listing=new LinkedHashMap<>();
        listing.put("seller",p.getName());listing.put("sellerId",id(p));listing.put("item",hand.getType().name());
        listing.put("amount",hand.getAmount());listing.put("price",price);listing.put("stack",hand.clone());listing.put("created",System.currentTimeMillis());
        ItemStack[] after=p.getInventory().getStorageContents();after[p.getInventory().getHeldItemSlot()]=null;
        if(!tradeManager.commerce(p,after,0,null,0,key,listing,false,"AH_LIST")){msg(p,"Annonce non confirmée.");return;}
        msg(p,"Annonce créée pour "+formatShopPrice(price)+" Larry. Après taxe : "+formatShopPrice(saleNet(price))+" Larry.");openAuction(p,0);
    }
    private void openOpConfirm(Player p,Player target) {
        pendingOpTarget.put(p.getUniqueId(),target.getUniqueId());
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Confirmer OP");
        inv.setItem(11,item(Material.LIME_CONCRETE,target.isOp()?"Retirer OP":"Donner OP","Joueur: "+target.getName()));
        inv.setItem(15,item(Material.RED_CONCRETE,"Annuler","Aucun changement"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private void openPlayerSelector(Player viewer,String action) {
        selectorAction.put(viewer.getUniqueId(),action);
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Sélection "+action);
        int slot=0;
        String query=selectorSearch.getOrDefault(viewer.getUniqueId(),"").toLowerCase(Locale.ROOT).trim();
        for(Player target:Bukkit.getOnlinePlayers()) {
            if(target.equals(viewer) || slot>=45) continue;
            if(!query.isEmpty()&&!target.getName().toLowerCase(Locale.ROOT).contains(query))continue;
            inv.setItem(slot++,item(Material.PLAYER_HEAD,target.getName(),"Clique pour "+action));
        }
        if(slot==0)inv.setItem(22,item(Material.PAPER,"Aucun joueur trouvé",query.isEmpty()?"Aucun autre joueur en ligne":"Recherche: "+query));
        inv.setItem(45,item(Material.COMPASS,"Rechercher un pseudo","Saisis le pseudo dans le chat"));
        inv.setItem(47,item(Material.MILK_BUCKET,"Effacer la recherche"));
        inv.setItem(49,item(Material.BARRIER,"Fermer"));
        inv.setItem(53,item(Material.SUNFLOWER,"Actualiser"));
        LpcMenuTheme.frame(inv);
        viewer.openInventory(inv);
    }

    private boolean checkCurrentChunk(Player p) {
        org.bukkit.World world=p.getWorld();
        if(world.getEnvironment()!=org.bukkit.World.Environment.NORMAL) {
            msg(p,"/check fonctionne uniquement dans l'Overworld.");
            return true;
        }
        int cx=p.getLocation().getChunk().getX(),cz=p.getLocation().getChunk().getZ();
        String key=achievementChunkKey(world,cx,cz);
        int remaining=0;
        for(int x=cx*16;x<cx*16+16;x++) for(int z=cz*16;z<cz*16+16;z++)
            for(int y=world.getMinHeight()+1;y<world.getMaxHeight();y++) {
                Material type=world.getBlockAt(x,y,z).getType();
                if(type!=Material.BEDROCK && !type.isAir()) remaining++;
            }
        List<String> columns=db.getStringList(achievementPath(p)+".chunk-columns."+key);
        int creepers=db.getInt("achievement-explosions."+key,0);
        boolean invalid=db.contains("achievement-invalid-chunks."+key);
        boolean completed=db.getStringList(achievementPath(p)+".completed-chunks").contains(key);
        String owner=db.getString("achievement-chunk-owners."+key,"Aucun");
        msg(p,ChatColor.GOLD+"========== CHECK CHUNK ==========");
        msg(p,"Monde: Overworld | Chunk: X "+cx+" / Z "+cz);
        msg(p,"Colonnes terminées: "+Math.min(columns.size(),256)+" / 256");
        msg(p,"Blocs encore à casser: "+remaining);
        msg(p,"Explosions de creepers: "+creepers+" / 4");
        msg(p,"État: "+(invalid?ChatColor.RED+"INVALIDE":completed?ChatColor.GREEN+"TERMINÉ":ChatColor.YELLOW+"VALIDABLE"));
        if(invalid) msg(p,"Raison: "+db.getString("achievement-invalid-chunks."+key,"Explosion interdite"));
        msg(p,"Joueur ayant terminé le chunk: "+owner);
        msg(p,ChatColor.GOLD+"================================");
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        try {
            switch (c) {
                case "clan" -> { return clan(sender,a); }
                case "quest", "quests" -> { return quests(sender,a); }
                case "succes" -> { Player p=player(sender); if(p!=null)openAchievementMenu(p); return true; }
                case "check" -> { Player p=player(sender); return p!=null && checkCurrentChunk(p); }
                case "top" -> { Player p=player(sender); if(p!=null)openTopMenu(p); return true; }
                case "rank" -> {
                    Player p=player(sender);
                    if(p==null || !core.isLpcAdmin(p)) return true;
                    if(a.length==0) openPlayerSelector(p,"rank");
                    else if(a.length==1 && Bukkit.getPlayerExact(a[0])!=null) openRank(p,Bukkit.getPlayerExact(a[0]));
                    else msg(p,"Utilise /rank ou /rank <joueur connecté>.");
                    return true;
                }
                case "admin" -> { return adminCommand(sender,a); }
                case "vanish" -> {
                    Player p=player(sender); if(p==null)return true; if(!staff(p)){msg(p,"Permission insuffisante.");return true;}
                    if(!watch.active(p) && !p.isOp() && !p.hasPermission("larpers.admin")) { msg(p,"Active /modo sur le SMP.");return true; }
                    setVanished(p,!isVanished(p));
                    msg(p,isVanished(p)?"Vanish activé.":"Vanish désactivé.");
                    return true;
                }
                case "freeze" -> {
                    if(!staff(sender)){msg(sender,"Permission insuffisante.");return true;}
                    if(sender instanceof Player p && !watch.active(p) && !p.isOp() && !p.hasPermission("larpers.admin")) { msg(p,"Active /modo sur le SMP.");return true; }
                    if(a.length!=1){msg(sender,"Usage : /freeze <joueur>");return true;}
                    Player t=Bukkit.getPlayerExact(a[0]); if(t==null){msg(sender,"Joueur introuvable.");return true;}
                    if(t.equals(sender) && !frozen.contains(t.getUniqueId())) {msg(sender,"Choisis un autre joueur à immobiliser.");return true;}
                    if(!frozen.add(t.getUniqueId()))frozen.remove(t.getUniqueId());
                    msg(sender,t.getName()+(frozen.contains(t.getUniqueId())?" est immobilisé.":" peut bouger."));
                    msg(t,frozen.contains(t.getUniqueId())?"Tu as été immobilisé par le staff.":"Tu peux de nouveau bouger."); return true;
                }
                case "invsee" -> {
                    if(!staff(sender)){msg(sender,"Permission insuffisante.");return true;}
                    if(a.length!=1){msg(sender,"Usage : /invsee <joueur>");return true;}
                    Player t=Bukkit.getPlayerExact(a[0]); Player p=player(sender); if(p!=null){if(t==null)msg(p,"Joueur introuvable ou hors ligne.");else p.openInventory(t.getInventory());} return true;
                }
                case "staffchat" -> {
                    if(!staff(sender)){msg(sender,"Permission insuffisante.");return true;}
                    if(a.length==0){msg(sender,"Usage : /staffchat <message>");return true;}
                    String text=String.join(" ",a);
                    for(Player p:Bukkit.getOnlinePlayers())if(staff(p))p.sendMessage(ChatColor.DARK_RED+"[STAFF] "+sender.getName()+": "+text);
                    plugin.getLogger().info("[STAFF] "+sender.getName()+": "+text); return true;
                }
                case "ah" -> { return auction(sender,a); }
                case "shop" -> { return shop(sender,a); }
                case "sell" -> { Player p=player(sender); if(p==null)return true; if(a.length==0){openSellMenu(p);return true;} if(a.length==1&&a[0].equalsIgnoreCase("all")){openSellAllConfirmation(p);return true;} msg(p,"Usage: /sell | /sell all"); return true; }
                case "trade" -> { return tradeManager.handle(sender,a); }
                default -> { return false; }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Erreur /"+c+": "+ex.getMessage()); msg(sender,"Erreur de commande. Consulte la console."); return true;
        }
    }

    private String serverDisplay(String key,String fallback) {
        File lobbyData=new File(plugin.getServer().getWorldContainer(),"../Lobby/plugins/LarpersCoin");
        File shared=new File(lobbyData,"server-display.yml");
        if(shared.isFile())return YamlConfiguration.loadConfiguration(shared).getString("server-display."+key,fallback);
        return plugin.getConfig().getString("server-display."+key,fallback);
    }

    private boolean adminCommand(CommandSender sender,String[] args) {
        if(args.length==0) {
            Player p=player(sender);
            if(p!=null){if(core.isLpcAdmin(p))openAdmin(p);else if(staff(p))openModerator(p);else msg(p,"Menu réservé au staff.");}
            return true;
        }
        if(!sender.isOp() && !sender.hasPermission("larpers.admin")) {msg(sender,"Sous-commande réservée aux administrateurs.");return true;}
        if(args[0].equalsIgnoreCase("reset")) {
            if(!sender.isOp()&&!sender.hasPermission("larpers.admin")){msg(sender,"Cette action est réservée aux administrateurs.");return true;}
            if(args.length!=2){msg(sender,"Usage: /admin reset <joueur>");return true;}
            if(!core.resetAuthAccount(args[1]))msg(sender,"Aucun compte enregistré pour "+args[1]+".");
            else msg(sender,"Mot de passe réinitialisé pour "+args[1]+". S'il était connecté, il a été expulsé et devra refaire /register.");
            return true;
        }
        if(args[0].equalsIgnoreCase("event")) return adminEventCommand(sender,args);
        if(args[0].equalsIgnoreCase("ip")) {
            if(!sender.isOp()&&!sender.hasPermission("larpers.admin")){msg(sender,"Seul un administrateur peut modifier l'adresse affichée.");return true;}
            if(args.length<3){msg(sender,"Usage: /admin ip <adresse:port> <nom du serveur>");return true;}
            String address=args[1];
            if(!address.matches("(?i)^(?:[a-z0-9.-]+|\\[[0-9a-f:]+\\])(?::[0-9]{1,5})?$")){msg(sender,"Adresse invalide. Exemple: play.exemple.ca:25565");return true;}
            int colon=address.lastIndexOf(':');
            if(colon>0&&address.charAt(0)!='['){try{int port=Integer.parseInt(address.substring(colon+1));if(port<1||port>65535)throw new NumberFormatException();}catch(NumberFormatException ex){msg(sender,"Port invalide (1 à 65535).");return true;}}
            String serverName=String.join(" ",Arrays.copyOfRange(args,2,args.length)).trim();
            if(serverName.isEmpty()||serverName.length()>32){msg(sender,"Le nom doit contenir entre 1 et 32 caractères.");return true;}
            plugin.getConfig().set("server-display.ip",address);
            plugin.getConfig().set("server-display.name",serverName);
            plugin.saveConfig();
            File lobbyData=new File(plugin.getServer().getWorldContainer(),"../Lobby/plugins/LarpersCoin");
            if(lobbyData.isDirectory()) {
                YamlConfiguration display=YamlConfiguration.loadConfiguration(new File(lobbyData,"server-display.yml"));
                display.set("server-display.ip",address);
                display.set("server-display.name",serverName);
                try { display.save(new File(lobbyData,"server-display.yml")); }
                catch(IOException ex){plugin.getLogger().warning("Impossible de sauvegarder l'adresse TAB partagée: "+ex.getMessage());}
            }
            msg(sender,"Adresse affichée mise à jour sur le réseau: "+serverName+" — "+address+".");
            return true;
        }
        msg(sender,"Sous-commandes: /admin | /admin reset <joueur> | /admin ip <adresse:port> <nom>");
        return true;
    }

    private boolean adminEventCommand(CommandSender sender,String[] args) {
        boolean admin=sender.isOp() || sender.hasPermission("larpers.admin") || (sender instanceof Player p && core.isLpcAdmin(p));
        if(!admin){msg(sender,"Les événements sont réservés aux administrateurs.");return true;}
        if(args.length==1 || args[1].equalsIgnoreCase("list")) {
            msg(sender,"Événements chat: melange, quiz, calcul, reaction.");
            msg(sender,"Usage: /admin event start <type> [récompense]");
            return true;
        }
        if(args[1].equalsIgnoreCase("status")){msg(sender,chatEvents.status());return true;}
        if(args[1].equalsIgnoreCase("stop")){msg(sender,chatEvents.stop()?"Événement arrêté.":"Aucun événement en cours.");return true;}
        if(args[1].equalsIgnoreCase("start")) {
            if(args.length<3){msg(sender,"Usage: /admin event start <melange|quiz|calcul|reaction> [récompense]");return true;}
            double reward=plugin.getConfig().getDouble("events.chat-reward",100.0);
            if(args.length>=4) {
                try{reward=Double.parseDouble(args[3].replace(',','.'));}
                catch(NumberFormatException ex){msg(sender,"Récompense invalide.");return true;}
            }
            if(!Double.isFinite(reward)||reward<1||reward>1000000){msg(sender,"Récompense entre 1 et 1 000 000 Larry.");return true;}
            if(!chatEvents.start(args[2],reward)){msg(sender,"Type invalide ou événement déjà actif. Types: melange, quiz, calcul, reaction.");return true;}
            msg(sender,"Événement chat lancé: "+args[2]+".");return true;
        }
        msg(sender,"Usage: /admin event <list|status|stop|start>");
        return true;
    }

    private boolean clan(CommandSender s,String[] a) {
        Player p=player(s); if(p==null)return true;
        String own=clanOf(p.getUniqueId());
        if(a.length==0){if(own==null){msg(p,"Tu n’as pas encore de clan. Utilise /clan create <nom> pour en créer un, /clan join <nom> pour rejoindre une invitation ou /clan top pour voir le classement.");return true;}openClanMenu(p);return true;}
        String sub=a[0].toLowerCase(Locale.ROOT);
        switch(sub) {
            case "create" -> {
                if(a.length<2||!a[1].matches("[A-Za-z0-9_]{3,16}")){msg(p,"Nom: 3 à 16 caractères alphanumériques.");return true;}
                if(own!=null){msg(p,"Tu es déjà dans un clan.");return true;}
                String n=a[1].toLowerCase(Locale.ROOT), path=clanPath(n);
                if(db.contains(path)){msg(p,"Ce clan existe déjà.");return true;}
                db.set(path+".name",a[1]); db.set(path+".owner",id(p)); db.set(path+".members",List.of(id(p))); db.set(path+".roles."+id(p),"leader");
                db.set(path+".bank",0.0); db.set(path+".bank-id",UUID.randomUUID().toString()); db.set(path+".level",1); db.set(path+".xp",0); db.set(path+".allies",new ArrayList<String>());
                db.set("clan-members."+id(p),n); save(); msg(p,"Clan "+a[1]+" créé."); return true;
            }
            case "invite" -> {
                if(a.length!=2||own==null){msg(p,"Usage : /clan invite <joueur> (dans un clan)");return true;}
                String path=clanPath(own);
                if(!clanLeaderOrOfficer(p,own)){msg(p,"Chef ou officier requis pour inviter.");return true;}
                OfflinePlayer target=Bukkit.getOfflinePlayer(a[1]);
                if(!target.hasPlayedBefore()&&!target.isOnline()){msg(p,"Compte inconnu.");return true;}
                String targetClan=db.getString("clan-members."+target.getUniqueId());
                if(targetClan!=null){msg(p,"Ce joueur appartient déjà à un clan.");return true;}
                String invite="invites."+target.getUniqueId();
                if(db.contains(invite)&&db.getLong("invite-expires."+target.getUniqueId())>System.currentTimeMillis()){msg(p,"Ce joueur a déjà une invitation.");return true;}
                db.set(invite,own);db.set("invite-expires."+target.getUniqueId(),System.currentTimeMillis()+86400000L);save();
                if(target.isOnline())msg(target.getPlayer(),"Invitation au clan "+db.getString(path+".name")+". Fais /clan join "+own);
                msg(p,"Invitation enregistrée pour "+a[1]+". Le joueur pourra la rejoindre avec /clan join "+own+".");return true;
            }
            case "join" -> {
                if(a.length!=2||own!=null){msg(p,"Usage : /clan join <nom> (sans clan)");return true;}
                String n=a[1].toLowerCase(Locale.ROOT);
                if(!n.equals(db.getString("invites."+id(p)))||!db.contains(clanPath(n))||db.getLong("invite-expires."+id(p))<=System.currentTimeMillis()){msg(p,"Aucune invitation valide pour ce clan.");return true;}
                List<String> m=db.getStringList(clanPath(n)+".members");if(m.size()>=100){msg(p,"Clan complet (100 membres).");return true;}if(!m.contains(id(p)))m.add(id(p));db.set(clanPath(n)+".members",m);db.set("clan-members."+id(p),n);db.set(clanPath(n)+".roles."+id(p),"member");db.set("invites."+id(p),null);save();msg(p,"Tu as rejoint le clan.");return true;
            }
            case "leave" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own);if(id(p).equals(db.getString(path+".owner"))){msg(p,"Transfère le rôle avec /clan transfer <joueur> ou dissous le clan avec /clan disband confirm.");return true;}
                List<String> m=db.getStringList(path+".members");m.remove(id(p));db.set(path+".members",m);db.set(path+".roles."+id(p),null);db.set("clan-members."+id(p),null);save();msg(p,"Tu as quitté le clan.");return true;
            }
            case "transfer" -> {
                if(own==null||a.length!=2||!id(p).equals(db.getString(clanPath(own)+".owner"))){msg(p,"Chef seulement: /clan transfer <joueur>.");return true;}
                OfflinePlayer target=Bukkit.getOfflinePlayer(a[1]);
                String targetId=target.getUniqueId().toString();
                if(targetId.equals(id(p))||!db.getStringList(clanPath(own)+".members").contains(targetId)){msg(p,"Le nouveau chef doit déjà être un autre membre du clan.");return true;}
                String path=clanPath(own);db.set(path+".owner",targetId);db.set(path+".roles."+targetId,"leader");db.set(path+".roles."+id(p),"officer");save();
                msg(p,"Tu as transféré le leadership à "+(target.getName()==null?a[1]:target.getName())+".");
                if(target.isOnline())msg(target.getPlayer(),"Tu es maintenant chef du clan "+db.getString(path+".name")+".");
                return true;
            }
            case "disband" -> {
                if(own==null||!id(p).equals(db.getString(clanPath(own)+".owner"))){msg(p,"Seul le chef peut dissoudre le clan.");return true;}
                if(a.length!=2||!a[1].equalsIgnoreCase("confirm")){msg(p,"Action irréversible: /clan disband confirm.");return true;}
                if(clanChestUsers.containsKey(own)){msg(p,"Ferme d'abord le coffre du clan avant de le dissoudre.");return true;}
                String path=clanPath(own);
                if(clanBank(own)>0){msg(p,"Vide la banque avant la dissolution.");return true;}
                if(!prepareChest(own)||Arrays.stream(core.chestItems(clanBankId(own))).anyMatch(x->x!=null&&!x.getType().isAir())){msg(p,"Vide le coffre avant la dissolution.");return true;}
                List<String> members=db.getStringList(path+".members");
                for(String member:members)db.set("clan-members."+member,null);
                for(String ally:db.getStringList(path+".allies")){List<String> list=db.getStringList(clanPath(ally)+".allies");list.remove(own);db.set(clanPath(ally)+".allies",list);}
                var invites=db.getConfigurationSection("invites");if(invites!=null)for(String key:new ArrayList<>(invites.getKeys(false)))if(own.equals(db.getString("invites."+key)))db.set("invites."+key,null);
                var requests=db.getConfigurationSection("alliance-requests");
                if(requests!=null)for(String key:new ArrayList<>(requests.getKeys(false))) {
                    db.set("alliance-requests."+key+"."+own,null);
                    db.set("alliance-requests."+own+"."+key,null);
                }
                db.set(path,null);clanChests.remove(own);clanChestUsers.remove(own);save();msg(p,"Clan dissous. Les membres ont été retirés.");return true;
            }
            case "info","stats","level" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own);int level=Math.max(1,db.getInt(path+".level",1));
                msg(p,db.getString(path+".name")+" | membres: "+db.getStringList(path+".members").size()+" | niveau "+level+" | XP "+db.getInt(path+".xp",0)+"/"+(level*100)+" | banque "+clanBank(own)+" Larry");
                msg(p,"Ton rôle: "+db.getString(path+".roles."+id(p),"member"));return true;
            }
            case "role" -> {
                if(own==null||a.length!=3||!id(p).equals(db.getString(clanPath(own)+".owner"))){msg(p,"Chef seulement: /clan role <joueur> <officer|member>");return true;}
                Player target=Bukkit.getPlayerExact(a[1]);
                String role=a[2].toLowerCase(Locale.ROOT);
                if(target==null||!own.equals(clanOf(target.getUniqueId()))||!(role.equals("officer")||role.equals("member"))){msg(p,"Joueur du clan en ligne et rôle valide requis.");return true;}
                if(target.equals(p)){msg(p,"Le chef ne peut pas modifier son propre rôle.");return true;}
                db.set(clanPath(own)+".roles."+id(target),role);save();msg(p,target.getName()+" est maintenant "+role+".");msg(target,"Ton rôle de clan est maintenant "+role+".");return true;
            }
            case "bank" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own)+".bank";
                if(a.length==1){msg(p,"Banque du clan: "+clanBank(own)+" Larry. /clan bank deposit|withdraw <montant>");return true;}
                if(a.length!=3){msg(p,"Usage: /clan bank deposit|withdraw <montant>");return true;}
                double v=Double.parseDouble(a[2].replace(',','.'));
                if(!Double.isFinite(v)||v<0.0001||java.math.BigDecimal.valueOf(v).stripTrailingZeros().scale()>4){msg(p,"Montant invalide (4 décimales maximum).");return true;}
                boolean deposit=a[1].equalsIgnoreCase("deposit");
                if(!deposit&&!a[1].equalsIgnoreCase("withdraw")){msg(p,"Utilise deposit ou withdraw.");return true;}
                if(!deposit&&!clanLeaderOrOfficer(p,own)){msg(p,"Retrait réservé au chef et aux officiers.");return true;}
                if(!core.changeClanBank(clanBankId(own),db.getDouble(path),p.getUniqueId(),v,deposit)){msg(p,"Opération annulée : fonds insuffisants ou sauvegarde impossible.");return true;}
                msg(p,"Opération effectuée. Banque : "+formatShopPrice(clanBank(own))+" Larry.");return true;
            }
            case "audit" -> {
                if(own==null||!clanLeaderOrOfficer(p,own)){msg(p,"Consultation réservée au chef et aux officiers.");return true;}
                List<String> entries=db.getStringList(clanPath(own)+".chest-audit");
                msg(p,"Journal du coffre — "+Math.min(10,entries.size())+" dernières modifications:");
                for(int i=Math.max(0,entries.size()-10);i<entries.size();i++)msg(p,entries.get(i));
                if(entries.isEmpty())msg(p,"Aucune modification enregistrée.");
                return true;
            }
            case "chest" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                if(!db.getStringList(clanPath(own)+".members").contains(id(p))){msg(p,"Accès réservé aux membres.");return true;}
                if(!prepareChest(own)){msg(p,"Coffre indisponible; contacte un admin.");return true;}
                if(a.length>1&&a[1].equalsIgnoreCase("upgrade")){
                    if(clanChestUsers.containsKey(own)){msg(p,"Ferme le coffre avant de l'améliorer.");return true;}
                    int level=chestLevel(own);if(level>=7){msg(p,"Coffre déjà au maximum (54).");return true;}
                    double price=250*Math.pow(2,level-1);
                    if(!core.upgradeChest(clanBankId(own),p.getUniqueId(),level,price)){msg(p,"Amélioration annulée : fonds ou sauvegarde insuffisants.");return true;}
                    msg(p,"Coffre amélioré à "+AntiDuplication.capacity(level+1)+" emplacements.");return true;
                }
                UUID user=clanChestUsers.get(own);
                if(user!=null&&!user.equals(p.getUniqueId())&&Bukkit.getPlayer(user)!=null){msg(p,"Coffre déjà utilisé.");return true;}
                p.closeInventory();
                ClanChestHolder holder=new ClanChestHolder(own,p.getUniqueId());
                holder.inventory=Bukkit.createInventory(holder,54,"Coffre clan: "+own);
                renderChest(holder);
                clanChests.put(own,holder.inventory);clanChestUsers.put(own,p.getUniqueId());
                p.openInventory(holder.inventory);
                msg(p,"Clic gauche sur une pile : dépôt ou retrait. "+AntiDuplication.capacity(chestLevel(own))+"/54 emplacements.");return true;
            }
            case "allies" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String ap=clanPath(own)+".allies";
                if(a.length==1){msg(p,"Alliés: "+String.join(", ",db.getStringList(ap)));return true;}
                if(!id(p).equals(db.getString(clanPath(own)+".owner"))||a.length!=3){msg(p,"Chef: /clan allies request|accept|remove <clan>");return true;}
                String other=a[2].toLowerCase(Locale.ROOT);if(!db.contains(clanPath(other))||other.equals(own)){msg(p,"Clan invalide.");return true;}
                if(a[1].equalsIgnoreCase("request")) {
                    db.set("alliance-requests."+other+"."+own,System.currentTimeMillis()+86400000L);save();
                    msg(p,"Demande d'alliance envoyée à "+db.getString(clanPath(other)+".name")+". Le chef adverse doit accepter.");
                    for(Player online:Bukkit.getOnlinePlayers()) if(id(online).equals(db.getString(clanPath(other)+".owner"))) msg(online,"Demande d'alliance de "+db.getString(clanPath(own)+".name")+". /clan allies accept "+own);
                    return true;
                }
                if(a[1].equalsIgnoreCase("accept")) {
                    if(db.getLong("alliance-requests."+own+"."+other,0L)<=System.currentTimeMillis()){msg(p,"Aucune demande de ce clan.");return true;}
                    List<String> mine=db.getStringList(ap), theirs=db.getStringList(clanPath(other)+".allies");
                    if(!mine.contains(other))mine.add(other);if(!theirs.contains(own))theirs.add(own);
                    db.set(ap,mine);db.set(clanPath(other)+".allies",theirs);db.set("alliance-requests."+own+"."+other,null);save();msg(p,"Alliance bilatérale créée.");return true;
                }
                if(a[1].equalsIgnoreCase("remove")) {
                    List<String> mine=db.getStringList(ap), theirs=db.getStringList(clanPath(other)+".allies");
                    mine.remove(other);theirs.remove(own);db.set(ap,mine);db.set(clanPath(other)+".allies",theirs);save();msg(p,"Alliance supprimée des deux clans.");return true;
                }
                msg(p,"Choisis request, accept ou remove.");return true;
            }
            case "top","leaderboard" -> {
                var sec=db.getConfigurationSection("clans");if(sec==null){msg(p,"Aucun clan.");return true;}
                List<String> names=new ArrayList<>(sec.getKeys(false));names.sort(Comparator.comparingDouble((String n)->clanBank(n)).reversed());
                msg(p,"Classement des clans (banque):");int i=1;for(String n:names){if(i>10)break;msg(p,i+". "+db.getString(clanPath(n)+".name")+" — "+clanBank(n)+" Larry");i++;}return true;
            }
            default -> {msg(p,"Sous-commande inconnue. /clan pour l'aide.");return true;}
        }
    }

    private String dailyQuestKey() { return LocalDate.now().toString(); }
    private String weeklyQuestKey() {
        LocalDate now=LocalDate.now();
        WeekFields wf=WeekFields.ISO;
        return now.get(wf.weekBasedYear())+"-W"+String.format(Locale.ROOT,"%02d",now.get(wf.weekOfWeekBasedYear()));
    }
    private String questPath(boolean weekly, Player p, String objective) {
        return "quests."+(weekly?"weekly."+weeklyQuestKey():"daily."+dailyQuestKey())+"."+id(p)+"."+objective;
    }
    private void progressQuest(Player p, String objective) {
        if(!core.isEconomyPlayer(p))return;
        db.set(questPath(false,p,objective),db.getInt(questPath(false,p,objective),0)+1);
        db.set(questPath(true,p,objective),db.getInt(questPath(true,p,objective),0)+1);
        addClanXp(p,1);
        saveSoon();
    }

    private String achievementPath(Player p) { return "achievements."+id(p); }
    private long achievementStat(Player p,String stat) {
        return Math.max(0L,db.getLong(achievementPath(p)+".stats."+stat,0L));
    }
    private void addAchievementProgress(Player p,String stat,long amount) {
        if(!core.isEconomyPlayer(p))return;
        String path=achievementPath(p)+".stats."+stat;
        db.set(path,Math.max(0L,db.getLong(path,0L)+amount));
        checkAchievements(p);
        saveSoon();
    }
    private boolean achievementClaimed(Player p,String key) {
        return db.getBoolean(achievementPath(p)+".claimed."+key,false)||core.rewardClaimed("achievement."+id(p)+"."+key);
    }
    private void unlockAchievement(Player p,String key,String label,String tier,double money,Material rewardMaterial,int rewardAmount) {
        if(!core.isEconomyPlayer(p)||achievementClaimed(p,key)) return;
        String rewardText="";
        if(money>0 && Double.isFinite(core.balance(p.getUniqueId())+money)) {
            if(!core.commitReward(p.getUniqueId(),"achievement."+id(p)+"."+key,money,"ACHIEVEMENT_"+key.toUpperCase(Locale.ROOT)))return;
            rewardText=" — +"+formatShopPrice(money)+" Larry";
        } else if(rewardMaterial!=null && rewardAmount>0) {
            ItemStack reward=new ItemStack(rewardMaterial,rewardAmount);
            Map<Integer,ItemStack> leftovers=p.getInventory().addItem(reward);
            leftovers.values().forEach(stack->p.getWorld().dropItemNaturally(p.getLocation(),stack));
            rewardText=" — récompense: "+rewardAmount+" "+rewardMaterial.name();
        }
        db.set(achievementPath(p)+".claimed."+key,true);
        msg(p,ChatColor.GOLD+"Succès débloqué: "+label+" ["+tier+"]"+rewardText+"!");
        if(tier.equalsIgnoreCase("EXTRÊME") || tier.equalsIgnoreCase("MYTHIQUE")) {
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE+"✦ "+ChatColor.LIGHT_PURPLE+p.getName()
                +ChatColor.GOLD+" a débloqué le succès "+ChatColor.YELLOW+label
                +ChatColor.LIGHT_PURPLE+" ["+tier+"]"+ChatColor.DARK_PURPLE+" ✦");
        }
        saveSoon();
    }
    private record AchievementTier(String key,String name,String stat,long goal,double baseReward,Material icon) {
        double reward() {
            return baseReward * 10;
        }
    }
    private static final List<Integer> GENERAL_REWARDS=List.of(10,25,100,250,400,600,800,1000);
    private static List<AchievementTier> series(String stat,String name,Material icon,int... goals) {
        List<AchievementTier> tiers=new ArrayList<>();
        for(int i=0;i<goals.length;i++)
            tiers.add(new AchievementTier(stat+"_"+(i+1),name+" "+(i+1),stat,goals[i],GENERAL_REWARDS.get(i),icon));
        return List.copyOf(tiers);
    }
    private static final List<List<AchievementTier>> GENERAL_SERIES=List.of(
        series("sheep-shorn","Tondeur",Material.SHEARS,100,1000,10000,100000,150000,250000,500000,1000000),
        series("logs-chopped","Bûcheron",Material.IRON_AXE,500,2500,10000,50000,100000,250000,500000,1000000),
        series("animals-bred","Éleveur",Material.WHEAT,25,100,500,2500,5000,10000,25000,50000),
        series("honey-bottled","Apiculteur",Material.HONEY_BOTTLE,50,250,1000,5000,10000,25000,50000,100000),
        series("bow-kills","Archer",Material.BOW,100,500,2500,10000,25000,50000,100000,250000),
        series("table-enchants","Bibliothécaire",Material.ENCHANTING_TABLE,25,100,500,2000,5000,10000,20000,50000));

    private void generalProgress(Player player,String stat) {
        if(player.getGameMode()==org.bukkit.GameMode.SURVIVAL && !plugin.getConfig().getBoolean("network.auth-only",false))
            addAchievementProgress(player,stat,1);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onShear(PlayerShearEntityEvent e) {
        if(e.getEntity() instanceof org.bukkit.entity.Sheep) generalProgress(e.getPlayer(),"sheep-shorn");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBreed(org.bukkit.event.entity.EntityBreedEvent e) {
        if(e.getBreeder() instanceof Player p && e.getEntity() instanceof org.bukkit.entity.Animals)
            generalProgress(p,"animals-bred");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onEnchant(org.bukkit.event.enchantment.EnchantItemEvent e) {
        generalProgress(e.getEnchanter(),"table-enchants");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onArrowKill(EntityDeathEvent e) {
        var source=e.getDamageSource();
        if(e.getEntity() instanceof org.bukkit.entity.Monster && source.getCausingEntity() instanceof Player p
            && (source.getDirectEntity() instanceof org.bukkit.entity.Arrow ||
                source.getDirectEntity() instanceof org.bukkit.entity.SpectralArrow ||
                source.getDirectEntity() instanceof org.bukkit.entity.Firework))
            generalProgress(p,"bow-kills");
    }
    private final Set<org.bukkit.Location> pendingHives=new HashSet<>();
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBottleHoney(PlayerInteractEvent e) {
        var block=e.getClickedBlock();
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || block==null || e.getItem()==null ||
            e.getItem().getType()!=Material.GLASS_BOTTLE || e.useInteractedBlock()==Event.Result.DENY ||
            e.useItemInHand()==Event.Result.DENY || !(block.getBlockData() instanceof org.bukkit.block.data.type.Beehive hive) ||
            hive.getHoneyLevel()!=hive.getMaximumHoneyLevel() || !pendingHives.add(block.getLocation())) return;
        Bukkit.getScheduler().runTask(plugin,()->{
            pendingHives.remove(block.getLocation());
            if(block.getBlockData() instanceof org.bukkit.block.data.type.Beehive after && after.getHoneyLevel()==0)
                generalProgress(e.getPlayer(),"honey-bottled");
        });
    }
    private List<List<AchievementTier>> achievementLanes(String category) {
        return switch(category) {
            case "Général" -> GENERAL_SERIES;
            case "Mineur" -> List.of(
                List.of(
                    new AchievementTier("miner_blocks_1","Débutant de la pioche","blocks-mined",10,10,Material.WOODEN_PICKAXE),
                    new AchievementTier("miner_blocks_2","Mineur régulier","blocks-mined",50,25,Material.STONE_PICKAXE),
                    new AchievementTier("miner_blocks_3","Mineur confirmé","blocks-mined",250,50,Material.IRON_PICKAXE),
                    new AchievementTier("miner_blocks_4","Mineur expérimenté","blocks-mined",1000,100,Material.GOLDEN_PICKAXE),
                    new AchievementTier("miner_blocks_5","Mineur expert","blocks-mined",5000,250,Material.DIAMOND_PICKAXE),
                    new AchievementTier("miner_blocks_6","Maître mineur","blocks-mined",10000,500,Material.NETHERITE_PICKAXE),
                    new AchievementTier("miner_blocks_7","Mineur légendaire","blocks-mined",50000,1000,Material.NETHERITE_PICKAXE),
                    new AchievementTier("miner_blocks_8","Seigneur des profondeurs","blocks-mined",100000,1500,Material.NETHERITE_PICKAXE),
                    new AchievementTier("miner_blocks_9","Maître absolu de la pioche","blocks-mined",250000,2500,Material.NETHERITE_PICKAXE)
                ),
                List.of(
                    new AchievementTier("miner_chunks_1","Premier chunk","chunks-cleared",1,50,Material.DEEPSLATE),
                    new AchievementTier("miner_chunks_2","Explorateur souterrain","chunks-cleared",3,150,Material.DIAMOND_ORE),
                    new AchievementTier("miner_chunks_3","Dévoreur de chunks","chunks-cleared",10,500,Material.ANCIENT_DEBRIS),
                    new AchievementTier("miner_chunks_4","Conquérant du sous-sol","chunks-cleared",25,1000,Material.DIAMOND_BLOCK),
                    new AchievementTier("miner_chunks_5","Légende des territoires","chunks-cleared",50,2000,Material.NETHERITE_BLOCK)
                )
            );
            case "Fermier" -> List.of(List.of(
                new AchievementTier("farm_1","Première récolte","farm-crops",10,10,Material.WHEAT),
                new AchievementTier("farm_2","Petit fermier","farm-crops",50,25,Material.CARROT),
                new AchievementTier("farm_3","Fermier débutant","farm-crops",250,50,Material.POTATO),
                new AchievementTier("farm_4","Grand fermier","farm-crops",1000,100,Material.BEETROOT),
                new AchievementTier("farm_5","Fermier confirmé","farm-crops",5000,200,Material.SUGAR_CANE),
                new AchievementTier("farm_6","Maître des récoltes","farm-crops",25000,350,Material.MELON),
                new AchievementTier("farm_7","Empire agricole","farm-crops",100000,750,Material.GOLDEN_CARROT),
                new AchievementTier("farm_8","Dieu de l'agriculture","farm-crops",500000,1000,Material.GOLDEN_APPLE),
                new AchievementTier("farm_9","Empereur des récoltes","farm-crops",1000000,1500,Material.GOLDEN_CARROT),
                new AchievementTier("farm_10","Jardinier mythique","farm-crops",2000000,2500,Material.ENCHANTED_GOLDEN_APPLE)
            ));
            case "PvP" -> List.of(List.of(
                new AchievementTier("pvp_1","Premier duel","player-kills",1,10,Material.WOODEN_SWORD),
                new AchievementTier("pvp_2","Combattant","player-kills",5,25,Material.STONE_SWORD),
                new AchievementTier("pvp_3","Guerrier","player-kills",10,50,Material.IRON_SWORD),
                new AchievementTier("pvp_4","Champion","player-kills",25,100,Material.DIAMOND_SWORD),
                new AchievementTier("pvp_5","Maître du PvP","player-kills",50,250,Material.NETHERITE_SWORD),
                new AchievementTier("pvp_6","Légende du PvP","player-kills",100,500,Material.NETHERITE_SWORD),
                new AchievementTier("pvp_7","Champion des arènes","player-kills",250,1000,Material.NETHERITE_AXE),
                new AchievementTier("pvp_8","Mythe du combat","player-kills",500,2000,Material.TRIDENT)
            ));
            case "Chasseur" -> List.of(
                List.of(
                    new AchievementTier("hunter_1","Premier gibier","monster-kills",10,10,Material.ROTTEN_FLESH),
                    new AchievementTier("hunter_2","Chasseur débutant","monster-kills",50,25,Material.BONE),
                    new AchievementTier("hunter_3","Chasseur confirmé","monster-kills",100,50,Material.ZOMBIE_HEAD),
                    new AchievementTier("hunter_4","Chasseur expert","monster-kills",500,100,Material.SKELETON_SKULL),
                    new AchievementTier("hunter_5","Chasseur légendaire","monster-kills",1000,250,Material.WITHER_SKELETON_SKULL),
                    new AchievementTier("hunter_6","Terreur des monstres","monster-kills",5000,750,Material.NETHER_STAR),
                    new AchievementTier("hunter_7","Fléau des créatures","monster-kills",500000,1000,Material.NETHER_STAR),
                    new AchievementTier("hunter_8","Extinction contrôlée","monster-kills",2000000,1000,Material.NETHER_STAR),
                    new AchievementTier("hunter_9","Fléau légendaire","monster-kills",3000000,1500,Material.NETHER_STAR),
                    new AchievementTier("hunter_10","Éradicateur mythique","monster-kills",5000000,2500,Material.NETHER_STAR)
                ),
                List.of(
                    new AchievementTier("wither_1","Premier Wither","wither-kills",1,100,Material.NETHER_STAR),
                    new AchievementTier("wither_5","Chasseur de Withers","wither-kills",5,250,Material.NETHER_STAR),
                    new AchievementTier("wither_10","Terreur du Wither","wither-kills",10,500,Material.NETHER_STAR),
                    new AchievementTier("wither_25","Briseur de Withers","wither-kills",25,750,Material.NETHER_STAR),
                    new AchievementTier("wither_50","Légende du Wither","wither-kills",50,1000,Material.NETHER_STAR),
                    new AchievementTier("wither_75","Fléau des Withers","wither-kills",75,1500,Material.NETHER_STAR),
                    new AchievementTier("wither_100","Roi des Withers","wither-kills",100,2500,Material.NETHER_STAR)
                ),
                List.of(
                    new AchievementTier("dragon_1","Premier dragon","dragon-kills",1,150,Material.DRAGON_EGG),
                    new AchievementTier("dragon_3","Tueur de dragons","dragon-kills",3,300,Material.DRAGON_HEAD),
                    new AchievementTier("dragon_5","Chasseur draconique","dragon-kills",5,500,Material.DRAGON_BREATH),
                    new AchievementTier("dragon_10","Dominateur de l'End","dragon-kills",10,750,Material.END_CRYSTAL),
                    new AchievementTier("dragon_25","Légende des dragons","dragon-kills",25,1000,Material.DRAGON_EGG),
                    new AchievementTier("dragon_50","Terreur draconique","dragon-kills",50,1500,Material.DRAGON_HEAD),
                    new AchievementTier("dragon_100","Maître de l'End","dragon-kills",100,2500,Material.DRAGON_EGG)
                )
            );
            case "Divers" -> List.of(
                List.of(
                    new AchievementTier("misc_fish_1","Premier poisson","fish-caught",1,10,Material.COD),
                    new AchievementTier("misc_fish_2","Pêcheur débutant","fish-caught",10,25,Material.SALMON),
                    new AchievementTier("misc_fish_3","Pêcheur confirmé","fish-caught",50,50,Material.TROPICAL_FISH),
                    new AchievementTier("misc_fish_4","Pêcheur expert","fish-caught",100,100,Material.PUFFERFISH),
                    new AchievementTier("misc_fish_5","Maître pêcheur","fish-caught",500,250,Material.FISHING_ROD),
                    new AchievementTier("misc_fish_6","Légende des océans","fish-caught",2000,500,Material.FISHING_ROD),
                    new AchievementTier("misc_fish_7","Seigneur des mers","fish-caught",10000,1000,Material.NAUTILUS_SHELL)
                ),
                List.of(
                    new AchievementTier("misc_chunks_1","Premier territoire","chunks-cleared",1,25,Material.MAP),
                    new AchievementTier("misc_chunks_2","Cartographe","chunks-cleared",5,100,Material.FILLED_MAP),
                    new AchievementTier("misc_chunks_3","Grand arpenteur","chunks-cleared",15,300,Material.MAP),
                    new AchievementTier("misc_chunks_4","Atlas des territoires","chunks-cleared",30,750,Material.FILLED_MAP)
                ),
                List.of(
                    new AchievementTier("overworld_biomes_1","Voyageur de l'Overworld","overworld-biomes",10,100,Material.GRASS_BLOCK),
                    new AchievementTier("overworld_biomes_2","Grand explorateur","overworld-biomes",25,250,Material.COMPASS),
                    new AchievementTier("overworld_biomes_3","Atlas vivant","overworld-biomes",53,1000,Material.FILLED_MAP),
                    new AchievementTier("overworld_biomes_4","Voyageur légendaire","overworld-biomes",60,1500,Material.FILLED_MAP),
                    new AchievementTier("overworld_biomes_5","Maître de tous les paysages","overworld-biomes",65,2500,Material.FILLED_MAP)
                ),
                List.of(
                    new AchievementTier("nether_biomes_1","Premier voyage au Nether","nether-biomes",2,100,Material.NETHERRACK),
                    new AchievementTier("nether_biomes_2","Cartographe infernal","nether-biomes",4,300,Material.COMPASS),
                    new AchievementTier("nether_biomes_3","Tous les biomes du Nether","nether-biomes",5,1000,Material.NETHER_STAR),
                    new AchievementTier("nether_biomes_4","Explorateur infernal","nether-biomes",6,1500,Material.NETHER_STAR),
                    new AchievementTier("nether_biomes_5","Seigneur du Nether","nether-biomes",7,2500,Material.NETHER_STAR)
                )
            );
            case "Constructeur" -> List.of(List.of(
                new AchievementTier("builder_1","Poseur débutant","blocks-placed",100,10,Material.BRICKS),
                new AchievementTier("builder_2","Petit bâtisseur","blocks-placed",1000,25,Material.STONE_BRICKS),
                new AchievementTier("builder_3","Constructeur confirmé","blocks-placed",5000,50,Material.BRICKS),
                new AchievementTier("builder_4","Maître bâtisseur","blocks-placed",25000,100,Material.QUARTZ_BLOCK),
                new AchievementTier("builder_5","Architecte","blocks-placed",100000,250,Material.COPPER_BLOCK),
                new AchievementTier("builder_6","Grand architecte","blocks-placed",250000,500,Material.DIAMOND_BLOCK),
                new AchievementTier("builder_7","Monument vivant","blocks-placed",500000,1000,Material.NETHERITE_BLOCK)
            ));
            case "Alchimiste" -> List.of(List.of(
                new AchievementTier("alchemist_1","Première potion","potions-used",1,10,Material.POTION),
                new AchievementTier("alchemist_2","Apprenti alchimiste","potions-used",25,25,Material.BREWING_STAND),
                new AchievementTier("alchemist_3","Potionneur","potions-used",100,50,Material.SPLASH_POTION),
                new AchievementTier("alchemist_4","Alchimiste confirmé","potions-used",500,100,Material.LINGERING_POTION),
                new AchievementTier("alchemist_5","Maître des potions","potions-used",2000,250,Material.GHAST_TEAR),
                new AchievementTier("alchemist_6","Grand alchimiste","potions-used",5000,500,Material.BLAZE_POWDER),
                new AchievementTier("alchemist_7","Légende alchimique","potions-used",10000,1000,Material.DRAGON_BREATH)
            ));
            case "Aventurier" -> List.of(List.of(
                new AchievementTier("adventurer_1","Premier pas","distance-traveled",1000,10,Material.LEATHER_BOOTS),
                new AchievementTier("adventurer_2","Marcheur","distance-traveled",5000,25,Material.COMPASS),
                new AchievementTier("adventurer_3","Voyageur","distance-traveled",25000,50,Material.MAP),
                new AchievementTier("adventurer_4","Randonneur","distance-traveled",100000,100,Material.FILLED_MAP),
                new AchievementTier("adventurer_5","Explorateur","distance-traveled",250000,250,Material.ENDER_EYE),
                new AchievementTier("adventurer_6","Globe-trotter","distance-traveled",500000,500,Material.ELYTRA),
                new AchievementTier("adventurer_7","Légende des chemins","distance-traveled",1000000,1000,Material.NETHER_STAR)
            ));
            case "Collectionneur" -> List.of(List.of(
                new AchievementTier("collector_1","Premier objet","unique-items",10,10,Material.CHEST),
                new AchievementTier("collector_2","Petit collectionneur","unique-items",25,25,Material.BARREL),
                new AchievementTier("collector_3","Collectionneur","unique-items",50,50,Material.SHULKER_BOX),
                new AchievementTier("collector_4","Collection complète","unique-items",100,100,Material.ENDER_CHEST),
                new AchievementTier("collector_5","Curateur","unique-items",150,250,Material.DIAMOND),
                new AchievementTier("collector_6","Conservateur","unique-items",200,500,Material.EMERALD_BLOCK),
                new AchievementTier("collector_7","Musée vivant","unique-items",250,1000,Material.NETHER_STAR)
            ));
            case "Marchand" -> List.of(List.of(
                new AchievementTier("merchant_1","Premier échange","trades-completed",100,10,Material.EMERALD),
                new AchievementTier("merchant_2","Petit commerçant","trades-completed",500,25,Material.GOLD_INGOT),
                new AchievementTier("merchant_3","Marchand","trades-completed",2500,50,Material.BARREL),
                new AchievementTier("merchant_4","Négociateur","trades-completed",10000,100,Material.GOLD_BLOCK),
                new AchievementTier("merchant_5","Commerçant confirmé","trades-completed",25000,250,Material.EMERALD_BLOCK),
                new AchievementTier("merchant_6","Grand marchand","trades-completed",50000,500,Material.DIAMOND_BLOCK),
                new AchievementTier("merchant_7","Magnat du commerce","trades-completed",100000,1000,Material.NETHERITE_INGOT)
            ));
            default -> List.of();
        };
    }
    private List<String> achievementCategories() { return List.of("Mineur","Fermier","PvP","Chasseur","Divers","Constructeur","Alchimiste","Aventurier","Collectionneur","Marchand","Général"); }
    private void checkAchievements(Player p) {
        for(String category:achievementCategories()) for(List<AchievementTier> lane:achievementLanes(category))
            for(AchievementTier tier:lane) if(achievementStat(p,tier.stat())>=tier.goal()) {
                String rarity="PROGRESSION";
                if(tier.goal()>=1000000 || tier.key().equals("dragon_25")
                        || tier.key().equals("wither_50") || tier.key().equals("overworld_biomes_3")
                        || tier.key().equals("nether_biomes_3")) rarity="MYTHIQUE";
                else if(tier.goal()>=500000 || tier.key().equals("dragon_10")
                        || tier.key().equals("wither_25")) rarity="EXTRÊME";
                unlockAchievement(p,tier.key(),tier.name(),rarity,tier.reward(),null,0);
            }
    }
    private boolean hasNoMineableBlocksBelow(org.bukkit.World world,int x,int y,int z) {
        for(int scan=world.getMinHeight()+1;scan<y;scan++) {
            var block=world.getBlockAt(x,scan,z);
            if(block.getType()==Material.BEDROCK || block.getType().isAir()) continue;
            String loc=world.getUID()+":"+x+":"+scan+":"+z;
            return false;
        }
        return true;
    }
    private boolean isAchievementColumnCleared(Player p,org.bukkit.World world,int x,int z) {
        for(int y=world.getMinHeight()+1;y<world.getMaxHeight();y++) {
            var block=world.getBlockAt(x,y,z);
            if(block.getType()==Material.BEDROCK) continue;
            String loc=world.getUID()+":"+x+":"+y+":"+z;
            if(!block.getType().isAir()) return false;
            String history=db.getString("achievement-block-history."+loc,"");
            if(!history.isEmpty() && !history.equals("creeper")
                    && !history.equals(id(p)+"|pickaxe")) return false;
        }
        return true;
    }
    private void checkMinedColumn(Player p,org.bukkit.World world,int x,int z) {
        if(world.getEnvironment()!=org.bukkit.World.Environment.NORMAL) return;
        String chunkKey=achievementChunkKey(world,Math.floorDiv(x,16),Math.floorDiv(z,16));
        if(db.contains("achievement-invalid-chunks."+chunkKey)) return;
        if(!isAchievementColumnCleared(p,world,x,z)) return;
        String path=achievementPath(p)+".chunk-columns."+chunkKey;
        List<String> columns=db.getStringList(path);
        String columnKey=Math.floorMod(x,16)+":"+Math.floorMod(z,16);
        if(!columns.contains(columnKey)) {
            columns.add(columnKey);
            db.set(path,columns);
            if(columns.size()>=256) {
                String completedPath=achievementPath(p)+".completed-chunks";
                List<String> completed=db.getStringList(completedPath);
                if(!completed.contains(chunkKey)) {
                    completed.add(chunkKey);
                    db.set(completedPath,completed);
                    db.set(achievementPath(p)+".stats.chunks-cleared",completed.size());
                    db.set("achievement-chunk-owners."+chunkKey,p.getName());
                    checkAchievements(p);
                    msg(p,ChatColor.GREEN+"Chunk entièrement miné! Progression: "+completed.size()+"/10.");
                }
            }
            saveSoon();
        }
    }
    private ItemStack achievementLaneItem(Player p,List<AchievementTier> lane) {
        AchievementTier next=null;
        for(AchievementTier tier:lane) if(!achievementClaimed(p,tier.key())) { next=tier; break; }
        if(next==null) return item(Material.LIME_STAINED_GLASS_PANE,"✓ Série terminée","Tous les objectifs de cette série sont réussis.");
        long progress=achievementStat(p,next.stat());
        return item(next.icon(),next.name(),
            "Progression: "+Math.min(progress,next.goal())+"/"+next.goal(),
            "Récompense: "+formatShopPrice(next.reward())+" Larry",
            progress>=next.goal()?"Succès atteint — récompense en cours de validation.":"Continue pour débloquer le prochain palier.",
            "Après validation, ce succès sera remplacé ici par le suivant.");
    }
    private void openAchievementMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Succès");
        List<String> categories=achievementCategories();
        Material[] icons={Material.IRON_PICKAXE,Material.WHEAT,Material.DIAMOND_SWORD,Material.ZOMBIE_HEAD,Material.NETHER_STAR,
            Material.BRICKS,Material.BREWING_STAND,Material.COMPASS,Material.CHEST,Material.EMERALD,Material.BOOK};
        int[] slots={10,12,14,16,28,30,32,34,20,24,22};
        for(int i=0;i<categories.size();i++) inv.setItem(slots[i],item(icons[i],categories.get(i),"Voir les prochains objectifs"));
        inv.setItem(49,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private void openAchievementCategory(Player p,String category) {
        List<List<AchievementTier>> lanes=achievementLanes(category);
        Inventory inv=Bukkit.createInventory(null,27,"Larry | "+category);
        int[] slots={10,12,14,16,21,23};
        for(int i=0;i<lanes.size()&&i<slots.length;i++) inv.setItem(slots[i],achievementLaneItem(p,lanes.get(i)));
        inv.setItem(18,item(Material.ARROW,"Retour","Revenir aux catégories"));
        inv.setItem(26,item(Material.BARRIER,"Fermer"));
        inv.setItem(4,item(Material.BOOK,"Succès "+category,"Chaque série avance automatiquement.","Le prochain objectif remplace le précédent dans le même emplacement."));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private void addClanXp(Player p,int amount) {
        String clan=clanOf(p.getUniqueId());
        if(clan==null) return;
        String path=clanPath(clan);
        int level=Math.max(1,db.getInt(path+".level",1));
        long xp=Math.max(0,db.getLong(path+".xp",0))+Math.max(0,amount);
        level=Math.min(1000000,level);xp=Math.min(xp,100000000L);
        while(level<1000000&&xp>=level*100L){xp-=level*100L;level++;}
        xp=Math.min(xp,level*100L-1);
        db.set(path+".xp",xp);
        db.set(path+".level",level);
    }
    private static boolean isChestLock(ItemStack st){return st.getType()==Material.GRAY_STAINED_GLASS_PANE && st.hasItemMeta() && "Emplacement verrouillé".equals(ChatColor.stripColor(st.getItemMeta().getDisplayName()));}
    private boolean clanLeaderOrOfficer(Player p,String clan) {
        String path=clanPath(clan);
        String role=db.getString(path+".roles."+id(p),"member");
        return id(p).equals(db.getString(path+".owner")) || role.equalsIgnoreCase("officer");
    }
    private void saveSoon() {
        if(saveQueued) return;
        saveQueued=true;
        Bukkit.getScheduler().runTaskLater(plugin,()->{saveQueued=false;save();},20L);
    }
    private boolean questReady(Player p, boolean weekly) {
        String[] names={"blocks","crops","kills","fish"};
        int[] goals=weekly?new int[]{75,30,20,10}:new int[]{20,10,5,3};
        for(int i=0;i<names.length;i++) if(db.getInt(questPath(weekly,p,names[i]),0)>=goals[i]) return true;
        return false;
    }
    private boolean quests(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        if(a.length==0){openQuestMenu(p);return true;}
        boolean weekly=a.length>0&&a[0].equalsIgnoreCase("weekly");
        if(a.length>0&&(a[0].equalsIgnoreCase("claim")||weekly&&a.length>1&&a[1].equalsIgnoreCase("claim"))) {
            boolean isWeekly=weekly;
            String period=isWeekly?weeklyQuestKey():dailyQuestKey();
            String claimPath="quest-claims."+(isWeekly?"weekly":"daily")+"."+period+"."+id(p);
            String ledgerClaim=claimPath.substring("quest-claims.".length());
            if(db.getBoolean(claimPath,false)||core.hasQuestClaim(ledgerClaim)){msg(p,"Cette récompense a déjà été réclamée pour cette période.");return true;}
            if(!questReady(p,isWeekly)){msg(p,"Objectifs incomplets. Fais /quests pour voir ta progression.");return true;}
            double reward=isWeekly?400.0:100.0;
            if(!Double.isFinite(core.balance(p.getUniqueId())+reward)){msg(p,"Ton solde ne peut pas recevoir cette récompense.");return true;}
            if(!core.commitQuestClaim(p.getUniqueId(),ledgerClaim,reward,isWeekly)){msg(p,"Récompense non versée : sauvegarde impossible ou déjà réclamée.");return true;}
            db.set(claimPath,true);save();
            msg(p,"Quête "+(isWeekly?"hebdomadaire":"quotidienne")+" terminée : +"+reward+" Larry.");
            return true;
        }
        String period=weekly?weeklyQuestKey():dailyQuestKey();
        int[] goals=weekly?new int[]{75,30,20,10}:new int[]{20,10,5,3};
        String[] names={"blocks","crops","kills","fish"};
        String[] labels={"blocs cassés","cultures récoltées","créatures vaincues","poissons pêchés"};
        msg(p,(weekly?"Quêtes hebdomadaires ":"Quêtes quotidiennes ")+period+":");
        for(int i=0;i<names.length;i++) msg(p,labels[i]+": "+Math.min(db.getInt(questPath(weekly,p,names[i]),0),goals[i])+"/"+goals[i]);
        msg(p,"Récompense: "+(weekly?"400":"100")+" Larry — valide un objectif pour réclamer.");
        msg(p,weekly?"/quests weekly claim":"/quests claim");
        return true;
    }

    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void onQuestBlockPlace(BlockPlaceEvent e) {
        var b=e.getBlockPlaced();
        String loc=b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();
        playerPlacedBlocks.add(loc);
        db.set("player-placed-blocks."+loc,true);
        addAchievementProgress(e.getPlayer(),"blocks-placed",1);
        saveSoon();
    }
    private String achievementChunkKey(org.bukkit.World world,int chunkX,int chunkZ) {
        return world.getUID()+":"+chunkX+":"+chunkZ;
    }
    private void invalidateAchievementChunk(org.bukkit.block.Block block,String reason) {
        String key=achievementChunkKey(block.getWorld(),block.getChunk().getX(),block.getChunk().getZ());
        db.set("achievement-invalid-chunks."+key,reason);
    }
    @EventHandler public void onAnyExplosion(EntityExplodeEvent e) {
        if(e.blockList().isEmpty()) return;
        boolean creeper=e.getEntity() instanceof org.bukkit.entity.Creeper;
        Map<String,Boolean> allowedChunks=new HashMap<>();
        for(var block:e.blockList()) {
            String key=achievementChunkKey(block.getWorld(),block.getChunk().getX(),block.getChunk().getZ());
            if(allowedChunks.containsKey(key)) continue;
            if(!creeper) {
                allowedChunks.put(key,false);
                db.set("achievement-invalid-chunks."+key,e.getEntity().getType().name());
                continue;
            }
            String path="achievement-explosions."+key;
            int used=db.getInt(path,0);
            boolean allowed=used<4;
            allowedChunks.put(key,allowed);
            if(allowed) db.set(path,used+1);
            else db.set("achievement-invalid-chunks."+key,"CREEPER_LIMIT");
        }
        // L'explosion reste naturelle: on invalide le chunk, sans annuler la destruction.
        Map<String,int[]> affectedColumns=new LinkedHashMap<>();
        org.bukkit.World explosionWorld=e.getLocation().getWorld();
        for(var block:e.blockList()) {
            String loc=block.getWorld().getUID()+":"+block.getX()+":"+block.getY()+":"+block.getZ();
            if(creeper) db.set("achievement-block-history."+loc,"creeper");
            affectedColumns.put(block.getWorld().getUID()+":"+block.getX()+":"+block.getZ(),
                new int[]{block.getX(),block.getZ()});
        }
        if(!allowedChunks.isEmpty()) saveSoon();
        if(!affectedColumns.isEmpty()) {
            saveSoon();
            Bukkit.getScheduler().runTask(plugin,()->{
                for(Player online:Bukkit.getOnlinePlayers()) for(int[] column:affectedColumns.values())
                    checkMinedColumn(online,explosionWorld,column[0],column[1]);
            });
        }
    }

    @EventHandler public void onBlockExplosion(org.bukkit.event.block.BlockExplodeEvent e) {
        if(e.blockList().isEmpty()) return;
        for(var block:e.blockList()) {
            String key=achievementChunkKey(block.getWorld(),block.getChunk().getX(),block.getChunk().getZ());
            db.set("achievement-invalid-chunks."+key,"BLOCK_EXPLOSION");
        }
        saveSoon();
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onQuestBlockBreak(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.getGameMode()==org.bukkit.GameMode.CREATIVE) return;
        var b=e.getBlock();
        String loc=b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();
        if(playerPlacedBlocks.remove(loc) || db.getBoolean("player-placed-blocks."+loc,false)) {
            db.set("player-placed-blocks."+loc,null);
            saveSoon();return; // Un bloc replacé ne génère pas une nouvelle récompense de minage.
        } else if(b.getType().name().endsWith("_LOG") && !b.getType().name().startsWith("STRIPPED_") &&
            p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) {
            generalProgress(p,"logs-chopped");
        }
        if(b.getBlockData() instanceof Ageable age && age.getAge()>=age.getMaximumAge()) {
            progressQuest(p,"crops");
            addAchievementProgress(p,"farm-crops",1);
        } else if(!b.getType().isAir() && b.getType()!=Material.BEDROCK) {
            progressQuest(p,"blocks");
            boolean usedPickaxe=p.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE");
            db.set("achievement-block-history."+loc,id(p)+(usedPickaxe?"|pickaxe":"|other"));
            saveSoon();
            if(usedPickaxe) {
                addAchievementProgress(p,"blocks-mined",1);
                if(b.getY()<=b.getWorld().getMinHeight()+5 || hasNoMineableBlocksBelow(b.getWorld(),b.getX(),b.getY(),b.getZ())) {
                    org.bukkit.World world=b.getWorld();int x=b.getX(),z=b.getZ();
                    Bukkit.getScheduler().runTask(plugin,()->checkMinedColumn(p,world,x,z));
                }
            }
        }
    }
    @EventHandler public void onPvpKill(PlayerDeathEvent e) {
        Player killer=e.getEntity().getKiller();
        Player victim=e.getEntity();
        if(killer==null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        String a=killer.getUniqueId().toString(),b=victim.getUniqueId().toString();
        String pair=a.compareTo(b)<0?a+"|"+b:b+"|"+a;
        String path="pvp-anti-farm."+pair;
        long now=System.currentTimeMillis();
        long previous=db.getLong(path,0L);
        if(previous>0L && now-previous<24L*60L*60L*1000L) {
            msg(killer,ChatColor.RED+"Kill PvP non comptabilisé: ce joueur a déjà été tué récemment par toi ou l'inverse.");
            saveSoon();
            return;
        }
        db.set(path,now);
        addAchievementProgress(killer,"player-kills",1);
        saveSoon();
    }
    @EventHandler public void onQuestMobKill(EntityDeathEvent e) {
        Player killer=e.getEntity().getKiller();
        if(killer!=null && !(e.getEntity() instanceof Player)) {
            progressQuest(killer,"kills");
            if(e.getEntity() instanceof org.bukkit.entity.Monster) addAchievementProgress(killer,"monster-kills",1);
            if(e.getEntity() instanceof org.bukkit.entity.Wither) addAchievementProgress(killer,"wither-kills",1);
            if(e.getEntity() instanceof org.bukkit.entity.EnderDragon) addAchievementProgress(killer,"dragon-kills",1);
        }
    }
    @EventHandler(ignoreCancelled=true)
    public void onExploreBiome(PlayerMoveEvent e) {
        if(watch.active(e.getPlayer())) return;
        if(e.getTo()==null || e.getFrom().getBlockX()==e.getTo().getBlockX()
                && e.getFrom().getBlockY()==e.getTo().getBlockY()
                && e.getFrom().getBlockZ()==e.getTo().getBlockZ()) return;
        Player p=e.getPlayer();
        if(e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            int dx=e.getTo().getBlockX()-e.getFrom().getBlockX();
            int dy=e.getTo().getBlockY()-e.getFrom().getBlockY();
            int dz=e.getTo().getBlockZ()-e.getFrom().getBlockZ();
            long distance=Math.round(Math.sqrt((double)dx*dx+(double)dy*dy+(double)dz*dz));
            if(distance>0) addAchievementProgress(p,"distance-traveled",distance);
        }
        org.bukkit.World.Environment env=e.getTo().getWorld().getEnvironment();
        String stat;
        if(env==org.bukkit.World.Environment.NORMAL) stat="overworld-biomes";
        else if(env==org.bukkit.World.Environment.NETHER) stat="nether-biomes";
        else return;
        String biome=e.getTo().getBlock().getBiome().name();
        String path=achievementPath(p)+".discovered-biomes."+stat;
        List<String> discovered=db.getStringList(path);
        if(!discovered.contains(biome)) {
            discovered.add(biome);
            db.set(path,discovered);
            db.set(achievementPath(p)+".stats."+stat,discovered.size());
            checkAchievements(p);
            saveSoon();
        }
    }
    @EventHandler public void onPotionUsed(PlayerItemConsumeEvent e) {
        Material type=e.getItem().getType();
        if(type==Material.POTION || type==Material.SPLASH_POTION || type==Material.LINGERING_POTION)
            addAchievementProgress(e.getPlayer(),"potions-used",1);
    }
    private void recordCollection(Player p) {
        if(p==null) return;
        String path=achievementPath(p)+".collected-materials";
        Set<String> collected=new HashSet<>(db.getStringList(path));
        for(ItemStack stack:p.getInventory().getContents()) {
            if(stack!=null && !stack.getType().isAir()) collected.add(stack.getType().name());
        }
        if(collected.size()!=db.getStringList(path).size()) {
            db.set(path,new ArrayList<>(collected));
            db.set(achievementPath(p)+".stats.unique-items",collected.size());
            checkAchievements(p);
            saveSoon();
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onItemPickup(EntityPickupItemEvent e) {
        if(e.getEntity() instanceof Player p)
            Bukkit.getScheduler().runTask(plugin,()->recordCollection(p));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onItemCraft(CraftItemEvent e) {
        if(e.getWhoClicked() instanceof Player p)
            Bukkit.getScheduler().runTask(plugin,()->recordCollection(p));
    }
    @EventHandler public void onPlayerJoinCollection(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin,()->recordCollection(e.getPlayer()));
    }
    private void recordTradeCompleted(UUID playerId) {
        Player p=Bukkit.getPlayer(playerId);
        if(p!=null) {
            addAchievementProgress(p,"trades-completed",1);
            recordCollection(p);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onQuestFish(PlayerFishEvent e) {
        if(e.getState()==PlayerFishEvent.State.CAUGHT_FISH) {
            progressQuest(e.getPlayer(),"fish");
            addAchievementProgress(e.getPlayer(),"fish-caught",1);
        }
    }
    private void openQuiz(Player p,String question,String[] choices) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Quiz");
        inv.setItem(4,item(Material.BOOK,"Question",question,"Choisis une réponse ci-dessous."));
        int[] slots={11,13,15,17,20,22};
        for(int i=0;i<Math.min(choices.length,slots.length);i++)inv.setItem(slots[i],item(Material.PAPER,choices[i],"Réponse "+(char)('A'+i)));
        p.openInventory(inv);
    }
    private boolean quiz(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        if(a.length>0&&a[0].equalsIgnoreCase("top")) {
            var section=db.getConfigurationSection("quiz-scores");
            if(section==null){msg(p,"Aucun score enregistré.");return true;}
            List<String> ids=new ArrayList<>(section.getKeys(false));
            ids.sort(Comparator.comparingInt((String x)->db.getInt("quiz-scores."+x)).reversed());
            msg(p,"Classement du quiz Minecraft:");
            for(int i=0;i<Math.min(10,ids.size());i++) {
                OfflinePlayer who=Bukkit.getOfflinePlayer(UUID.fromString(ids.get(i)));
                msg(p,(i+1)+". "+(who.getName()==null?ids.get(i):who.getName())+" — "+db.getInt("quiz-scores."+ids.get(i))+" points");
            }
            return true;
        }
        long now=System.currentTimeMillis();
        long previous=db.getLong("quiz."+id(p)+".time",0L);
        if(a.length==0&&now-previous<60_000L){msg(p,"Attends une minute avant un autre quiz.");return true;}
        var section=db.getConfigurationSection("quiz-bank");
        if(section==null||section.getKeys(false).isEmpty()){msg(p,"La banque de questions est vide. Configure quiz-bank dans features.yml.");return true;}
        List<String> keys=new ArrayList<>(section.getKeys(false));
        if(a.length==0) {
            String key=keys.get(new Random().nextInt(keys.size()));
            String path="quiz-bank."+key;
            String question=db.getString(path+".question","");
            String answer=db.getString(path+".answer","");
            List<String> choices=db.getStringList(path+".choices");
            if(question.isBlank()||answer.isBlank()||choices.size()<2){msg(p,"Question mal configurée: "+key);return true;}
            db.set("quiz."+id(p)+".answer",answer);
            db.set("quiz."+id(p)+".time",now);
            save();openQuiz(p,question,choices.toArray(String[]::new));return true;
        }
        msg(p,"Utilise /quiz pour une question ou /quiz top pour le classement.");
        return true;
    }

    private static final List<String> AISLES = List.of("Construction", "Minerais", "Équipement",
        "Nourriture", "Cultures", "Redstone", "Transport", "Alchimie", "Divers");
    private static final List<Material> AISLE_ICONS = List.of(Material.BRICKS, Material.DIAMOND,
        Material.IRON_PICKAXE, Material.BREAD, Material.WHEAT, Material.REPEATER,
        Material.MINECART, Material.BREWING_STAND, Material.CHEST);

    private List<String> shopCategories() { return AISLES; }
    private Material shopCategoryIcon(String category) { return AISLE_ICONS.get(AISLES.indexOf(category)); }

    private String shopCategoryFor(String key) {
        Material material = SHOP.get(key);
        if (key.contains("minecart") || key.endsWith("rail") || key.endsWith("_boat") ||
            key.endsWith("_raft") || key.equals("saddle")) return "Transport";
        if (key.contains("potion") || key.equals("brewing_stand") || key.equals("glass_bottle") ||
            key.equals("blaze_powder") || key.equals("nether_wart")) return "Alchimie";
        if (key.startsWith("redstone") || key.contains("piston") || key.endsWith("_button") ||
            key.endsWith("pressure_plate") || Set.of("lever","observer","repeater","comparator",
            "hopper","dispenser","dropper","target","tripwire_hook","crafter").contains(key)) return "Redstone";
        if (material.getMaxDurability() > 0 || key.endsWith("_armor") || key.endsWith("arrow")) return "Équipement";
        if (key.endsWith("seeds") || key.endsWith("sapling") || key.equals("wheat") ||
            key.equals("sugar_cane") || key.equals("cactus") || key.equals("bamboo")) return "Cultures";
        if (material.isEdible()) return "Nourriture";
        if (key.endsWith("_ore") || key.endsWith("_ingot") || key.endsWith("_nugget") ||
            Set.of("raw_iron","raw_gold","raw_copper","diamond","emerald","coal","charcoal",
            "quartz","lapis_lazuli","amethyst_shard").contains(key)) return "Minerais";
        return material.isBlock() ? "Construction" : "Divers";
    }
    private String formatShopPrice(double price){return new java.text.DecimalFormat("0.####",java.text.DecimalFormatSymbols.getInstance(Locale.CANADA_FRENCH)).format(price);}
    private String shopDisplayName(String key){String[] parts=key.toLowerCase(Locale.ROOT).split("_");StringJoiner out=new StringJoiner(" ");for(String part:parts)out.add(part.isEmpty()?part:part.substring(0,1).toUpperCase(Locale.ROOT)+part.substring(1));return out.toString();}
    private void openShop(Player p){openShopCategories(p);}
    private void openShopCategories(Player p) {
        shopCategory.put(p.getUniqueId(),"CATEGORIES");
        shopPage.put(p.getUniqueId(),0);
        Inventory inv = Bukkit.createInventory(null,54,"Larry | Catégories");
        Map<String,Long> counts = SHOP.keySet().stream().collect(
            java.util.stream.Collectors.groupingBy(this::shopCategoryFor, java.util.stream.Collectors.counting()));
        for (int i=0; i<AISLES.size(); i++) {
            String category = AISLES.get(i);
            inv.setItem(11 + (i/3)*9 + (i%3)*2, item(shopCategoryIcon(category), category,
                counts.getOrDefault(category,0L) + " articles"));
        }
        inv.setItem(45,item(Material.EMERALD,"Vendre mes objets","Ouvrir l’inventaire de vente"));
        inv.setItem(49,item(Material.COMPASS,"Rechercher un article","Saisir un nom dans le chat"));
        inv.setItem(53,item(Material.BARRIER,"Fermer"));
        p.openInventory(inv);
    }

    private void openShop(Player p,String category,int page){List<String> cats=shopCategories();if(!cats.contains(category)){openShopCategories(p);return;}final String selected=category;List<Map.Entry<String,Material>> entries=SHOP.entrySet().stream().filter(e->shopCategoryFor(e.getKey()).equals(selected)).sorted(Comparator.comparing(e->shopDisplayName(e.getKey()),String.CASE_INSENSITIVE_ORDER)).toList();openShopEntries(p,category,entries,page);}
    private void openShopSearch(Player p,int page){String q=shopSearch.getOrDefault(p.getUniqueId(),"").toLowerCase(Locale.ROOT).trim();List<Map.Entry<String,Material>> entries=SHOP.entrySet().stream().filter(e->e.getKey().contains(q)||shopDisplayName(e.getKey()).toLowerCase(Locale.ROOT).contains(q)).sorted(Comparator.comparing(e->shopDisplayName(e.getKey()),String.CASE_INSENSITIVE_ORDER)).toList();openShopEntries(p,"__SEARCH__",entries,page);}
    private void openShopEntries(Player p,String category,List<Map.Entry<String,Material>> entries,int page) {
        int pages=Math.max(1,(entries.size()+35)/36);
        page=Math.max(0,Math.min(page,pages-1));
        shopCategory.put(p.getUniqueId(),category);
        shopPage.put(p.getUniqueId(),page);
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Boutique");
        boolean search=category.equals("__SEARCH__");
        String heading=search ? shopSearch.getOrDefault(p.getUniqueId(),"") : category;
        inv.setItem(0,item(Material.ARROW,"Catégories"));
        inv.setItem(4,item(search ? Material.COMPASS : shopCategoryIcon(category),heading,
            entries.size()+" articles • Page "+(page+1)+" / "+pages));
        if(entries.isEmpty()) inv.setItem(22,item(Material.PAPER,"Aucun résultat","Utilise la boussole pour une autre recherche."));
        int start=page*36;
        for(int i=start;i<Math.min(start+36,entries.size());i++) {
            var entry=entries.get(i);
            ItemStack stack=new ItemStack(entry.getValue());
            ItemMeta meta=stack.getItemMeta();
            meta.setLore(List.of("§aAchat  §f"+formatShopPrice(shopBuyPrice(entry.getKey()))+" Larry / unité",
                "§6Vente brute  §f"+formatShopPrice(shopSellPrice(entry.getKey()))+" Larry / unité",
                "§7Taxe de vente : "+formatShopPrice(saleTaxPercent())+" %","§7Choisir la quantité…"));
            stack.setItemMeta(meta);
            inv.setItem(9+i-start,stack);
        }
        if(page>0) inv.setItem(45,item(Material.ARROW,"Page précédente"));
        inv.setItem(47,item(Material.COMPASS,"Rechercher"));
        inv.setItem(49,item(Material.CLOCK,"Marché","Prochains prix dans "+marketTimeLeft()));
        inv.setItem(51,item(Material.BARRIER,"Fermer"));
        if(page+1<pages) inv.setItem(53,item(Material.ARROW,"Page suivante"));
        p.openInventory(inv);
    }

    private void openShopQuantity(Player p,Material material) {
        Inventory inv=Bukkit.createInventory(null,27,"Larry | Quantité");
        inv.setItem(4,new ItemStack(material));
        String key=material.name().toLowerCase(Locale.ROOT);
        int slot=11;
        for(int amount : List.of(1,16,64)) {
            double price=Math.round(shopBuyPrice(key)*amount*10000)/10000.0;
            inv.setItem(slot,item(Material.EMERALD,"Acheter ×"+amount,formatShopPrice(price)+" Larry au total"));
            slot+=2;
        }
        inv.setItem(18,item(Material.ARROW,"Retour aux articles"));
        inv.setItem(22,item(Material.GOLD_INGOT,"Solde",formatShopPrice(core.balance(p.getUniqueId()))+" Larry"));
        p.openInventory(inv);
    }

    private record MarketUnit(String key, double quantity) {}
    private static final Map<String,MarketUnit> MARKET_UNITS = marketUnits();

    private static Map<String,MarketUnit> marketUnits() {
        Map<String,MarketUnit> units = new HashMap<>();
        for (String metal : List.of("iron","gold","copper")) {
            String ingot = metal+"_ingot";
            units.put(metal+"_block",new MarketUnit(ingot,9));
            units.put(metal+"_nugget",new MarketUnit(ingot,1.0/9));
            units.put("raw_"+metal,new MarketUnit(ingot,1));
            units.put("raw_"+metal+"_block",new MarketUnit(ingot,9));
        }
        for (String mineral : List.of("diamond","emerald","coal","redstone","lapis")) {
            String unit = mineral.equals("lapis") ? "lapis_lazuli" : mineral;
            units.put(mineral+"_block",new MarketUnit(unit,9));
        }
        units.put("hay_block",new MarketUnit("wheat",9));
        units.put("bone_block",new MarketUnit("bone_meal",9));
        units.put("slime_block",new MarketUnit("slime_ball",9));
        units.put("honeycomb_block",new MarketUnit("honeycomb",4));
        units.put("dried_kelp_block",new MarketUnit("dried_kelp",9));
        return Map.copyOf(units);
    }

    private double rawShopSellPrice(String key) {
        MarketUnit unit=MARKET_UNITS.get(key);
        if(unit!=null) return rawShopSellPrice(unit.key())*unit.quantity();
        return Math.max(.01,db.getDouble("shop.sell-prices."+key,SHOP_PRICES.getOrDefault(key,.05)));
    }

    private Map<String,Double> recipeSaleCaps=Map.of();
    private boolean recipePricingReady;
    private double shopSellPrice(String key){return Math.min(rawShopSellPrice(key),recipeSaleCaps.getOrDefault(key,Double.POSITIVE_INFINITY));}
    private double shopBuyPrice(String key) {
        MarketUnit unit=MARKET_UNITS.get(key);
        if(unit!=null) return shopBuyPrice(unit.key())*unit.quantity();
        double sell=rawShopSellPrice(key);
        return Math.max(sell*1.5,db.getDouble("shop.buy-prices."+key,sell*2.8));
    }

    private void rebuildRecipeCaps(){
        try{
            Map<String,Double> purchase=new HashMap<>();
            for(String key:SHOP.keySet()){
                double price=shopBuyPrice(key);
                if(Double.isFinite(price)&&price>0)purchase.put(key,Math.floor(price*10000)/10000);
            }
            List<RecipeEconomy.Rule> rules=new ArrayList<>();
            Map<RecipeChoice,List<String>> cached=new HashMap<>();
            Iterator<Recipe> iterator=Bukkit.recipeIterator();int skipped=0;
            while(iterator.hasNext()){
                Recipe recipe=iterator.next();ItemStack result=recipe.getResult();
                if(result==null||result.getType().isAir()||result.getAmount()<=0)continue;
                String output=result.getType().name().toLowerCase(Locale.ROOT);
                if(!purchase.containsKey(output))continue;
                List<RecipeChoice> choices=new ArrayList<>();
                if(recipe instanceof ShapedRecipe shaped){
                    var map=shaped.getChoiceMap();
                    for(String line:shaped.getShape())for(char symbol:line.toCharArray())if(symbol!=' '&&map.get(symbol)!=null)choices.add(map.get(symbol));
                }else if(recipe instanceof ShapelessRecipe shapeless)choices.addAll(shapeless.getChoiceList());
                else if(recipe instanceof CookingRecipe<?> cooking)choices.add(cooking.getInputChoice());
                else if(recipe instanceof StonecuttingRecipe cutter)choices.add(cutter.getInputChoice());
                else{skipped++;continue;}
                if(choices.isEmpty())continue;
                List<List<String>> inputs=new ArrayList<>();
                for(RecipeChoice choice:choices){
                    if(choice==null){inputs.add(List.of());continue;}
                    inputs.add(cached.computeIfAbsent(choice,x->{
                        if(x instanceof RecipeChoice.MaterialChoice materials)return materials.getChoices().stream().map(m->m.name().toLowerCase(Locale.ROOT)).filter(purchase::containsKey).toList();
                        if(x instanceof RecipeChoice.ExactChoice exact)return exact.getChoices().stream().filter(st->!st.hasItemMeta()).map(st->st.getType().name().toLowerCase(Locale.ROOT)).filter(purchase::containsKey).toList();
                        return SHOP.entrySet().stream().filter(e->purchase.containsKey(e.getKey())&&x.test(new ItemStack(e.getValue()))).map(Map.Entry::getKey).toList();
                    }));
                }
                rules.add(new RecipeEconomy.Rule(output,result.getAmount(),inputs));
            }
            recipeSaleCaps=RecipeEconomy.ceilings(purchase,rules);recipePricingReady=true;
            plugin.getLogger().info("Marché anti-arbitrage : "+rules.size()+" recettes contrôlées, "+skipped+" recettes spéciales non modélisées.");
        }catch(RuntimeException ex){recipePricingReady=false;plugin.getLogger().severe("Boutique suspendue : contrôle des recettes impossible : "+ex.getMessage());}
    }
    private void recalculateMarketPrices() {
        Map<String,Double> sales=new HashMap<>();
        for(String key:SHOP.keySet()) {
            long count=Math.max(0,db.getLong("market.window-sold."+key,0));
            MarketUnit unit=MARKET_UNITS.get(key);
            sales.merge(unit==null ? key : unit.key(),unit==null ? (double)count : count*unit.quantity(),Double::sum);
        }
        for(var entry:SHOP_PRICES.entrySet()) {
            String key=entry.getKey();
            if(MARKET_UNITS.containsKey(key)) continue;
            double sold=sales.getOrDefault(key,0.0);
            double sell=Math.max(.01,entry.getValue()*(1-Math.min(.60,sold/512.0*.12)));
            db.set("shop.sell-prices."+key,Math.round(sell*10000)/10000.0);
            db.set("shop.buy-prices."+key,Math.round(sell*2.8*10000)/10000.0);
        }
        rebuildRecipeCaps();
        for(String key:SHOP.keySet()) {
            List<Double> trend=db.getDoubleList("market.price-history."+key);
            trend.add(shopSellPrice(key));
            if(trend.size()>24) trend=new ArrayList<>(trend.subList(trend.size()-24,trend.size()));
            db.set("market.price-history."+key,trend);
            db.set("market.window-sold."+key,0L);
        }
        long now=System.currentTimeMillis();
        db.set("market.last-refresh",now);
        db.set("market.next-refresh",now+MARKET_REFRESH_MILLIS);
        save();
        plugin.getLogger().info("Marché Larry recalculé; prochaine actualisation dans 6 heures.");
    }
    private String marketTimeLeft(){long ms=Math.max(0,db.getLong("market.next-refresh",System.currentTimeMillis())-System.currentTimeMillis());return(ms/3600000)+" h "+((ms/60000)%60)+" min";}
    private double saleTaxPercent() {
        double rate=plugin.getConfig().getDouble("economy.sale-tax-percent",5.0);
        return Double.isFinite(rate) && rate>=0 && rate<100 ? rate : 5.0;
    }

    private double saleNet(double gross) {
        double tax=Math.min(gross,java.math.BigDecimal.valueOf(gross)
            .multiply(java.math.BigDecimal.valueOf(saleTaxPercent())).movePointLeft(2)
            .setScale(4,java.math.RoundingMode.HALF_UP).doubleValue());
        return gross-tax;
    }

    private double shopProceeds(String key,int quantity) {
        return saleNet(Math.round(shopSellPrice(key)*quantity*10000)/10000.0);
    }

    private boolean sellStack(Player p,int inventorySlot,int amount){if(!recipePricingReady){msg(p,"Boutique temporairement indisponible.");return false;}ItemStack stack=p.getInventory().getItem(inventorySlot);if(stack==null||stack.getType().isAir()||amount<1||amount>stack.getAmount()){msg(p,"Quantité invalide ou aucun objet vendable.");return false;}String k=stack.getType().name().toLowerCase(Locale.ROOT);if(!SHOP.containsKey(k)||SHOP_BLOCKED.contains(stack.getType())||SHOP_BLOCKED_NAMES.contains(stack.getType().name())||k.contains("netherite")){msg(p,"Cet objet ne peut pas être vendu.");return false;}if(stack.hasItemMeta()){msg(p,"Les objets renommés ou enchantés ne sont pas acceptés.");return false;}double total=shopProceeds(k,amount);if(total<=0||!Double.isFinite(core.balance(p.getUniqueId())+total)){msg(p,"Vente impossible.");return false;}int left=stack.getAmount()-amount;ItemStack[] after=p.getInventory().getStorageContents();if(left<=0)after[inventorySlot]=null;else {after[inventorySlot]=stack.clone();after[inventorySlot].setAmount(left);}if(!tradeManager.commerce(p,after,total,null,0,null,null,false,"SHOP_SELL")){msg(p,"Vente non confirmée.");return false;}db.set("market.window-sold."+k,db.getLong("market.window-sold."+k,0L)+amount);db.set("market.total-sold."+k,db.getLong("market.total-sold."+k,0L)+amount);db.set("market.sales-total",db.getLong("market.sales-total",0L)+amount);db.set("market.sales-count",db.getLong("market.sales-count",0L)+1);List<Map<String,Object>> h=new ArrayList<>();for(Map<?,?> old:db.getMapList("market.history")){Map<String,Object> r=new LinkedHashMap<>();for(var x:old.entrySet())r.put(String.valueOf(x.getKey()),x.getValue());h.add(r);}Map<String,Object> row=new LinkedHashMap<>();row.put("type","VENTE");row.put("item",k);row.put("amount",amount);row.put("total",total);row.put("player",p.getName());row.put("time",System.currentTimeMillis());h.add(0,row);if(h.size()>100)h=new ArrayList<>(h.subList(0,100));db.set("market.history",h);save();msg(p,"Vente: "+amount+" x "+k+" pour "+formatShopPrice(total)+" Larry. Prix mis à jour dans "+marketTimeLeft()+".");return true;}
    private boolean sellShop(Player p,int amount){return sellStack(p,p.getInventory().getHeldItemSlot(),amount);}
    private boolean isSellable(ItemStack stack){if(stack==null||stack.getType().isAir())return false;String k=stack.getType().name().toLowerCase(Locale.ROOT);return SHOP.containsKey(k)&&!SHOP_BLOCKED.contains(stack.getType())&&!SHOP_BLOCKED_NAMES.contains(stack.getType().name())&&!k.contains("netherite")&&!stack.hasItemMeta();}
    private double sellAllValue(Player p) {
        long cents=0;
        for(ItemStack stack:p.getInventory().getStorageContents()) {
            if(isSellable(stack)) cents+=Math.round(shopProceeds(stack.getType().name().toLowerCase(Locale.ROOT),stack.getAmount())*10000);
        }
        return cents/10000.0;
    }
    private void openSellMenu(Player p){Inventory inv=Bukkit.createInventory(null,54,"Larry | Vendre");int shown=0;for(int slot=0;slot<36;slot++){ItemStack stack=p.getInventory().getItem(slot);if(!isSellable(stack))continue;ItemStack display=stack.clone();ItemMeta meta=display.getItemMeta();if(meta!=null){List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();String k=stack.getType().name().toLowerCase(Locale.ROOT);lore.add(ChatColor.GRAY+"Quantité: "+stack.getAmount());lore.add(ChatColor.GOLD+"Net après taxe: "+formatShopPrice(shopProceeds(k,stack.getAmount()))+" Larry");lore.add(ChatColor.YELLOW+"Clique pour vendre cette pile");meta.setLore(lore);display.setItemMeta(meta);}inv.setItem(slot,display);shown++;}inv.setItem(45,item(Material.GOLD_INGOT,"Vendre tout","Net après taxe: "+formatShopPrice(sellAllValue(p))+" Larry","Une confirmation sera demandée"));inv.setItem(49,item(Material.BARRIER,"Fermer"));inv.setItem(53,item(Material.SUNFLOWER,"Actualiser",shown+" pile(s) vendable(s)"));p.openInventory(inv);}
    private final Map<UUID,ItemStack[]> saleSnapshots=new HashMap<>();
    private final Map<UUID,Double> saleQuotes=new HashMap<>();
    private void openSellAllConfirmation(Player p){double total=sellAllValue(p);saleSnapshots.put(p.getUniqueId(),Arrays.stream(p.getInventory().getStorageContents()).map(x->x==null?null:x.clone()).toArray(ItemStack[]::new));saleQuotes.put(p.getUniqueId(),total);Inventory inv=Bukkit.createInventory(null,27,"Larry | Confirmer vente totale");inv.setItem(11,item(Material.LIME_CONCRETE,"CONFIRMER","Vendre tous les objets admissibles","Net après taxe: "+total+" Larry","Cette action est irréversible"));inv.setItem(15,item(Material.RED_CONCRETE,"ANNULER","Ne rien vendre"));inv.setItem(13,item(Material.GOLD_INGOT,"Résumé","Net après taxe: "+total+" Larry","Les objets renommés/enchantés et interdits sont ignorés"));p.openInventory(inv);}
    private void executeSellAll(Player p){
        if(!recipePricingReady){msg(p,"Boutique temporairement indisponible.");return;}
        ItemStack[] expected=saleSnapshots.remove(p.getUniqueId());Double quote=saleQuotes.remove(p.getUniqueId());
        if(expected==null||quote==null||!Arrays.equals(expected,p.getInventory().getStorageContents())||Double.compare(quote,sellAllValue(p))!=0){msg(p,"Inventaire ou prix modifié. Confirme à nouveau.");openSellAllConfirmation(p);return;}
        ItemStack[] after=p.getInventory().getStorageContents();
        List<Map<String,Object>> history=new ArrayList<>();
        for(Map<?,?> old:db.getMapList("market.history")){Map<String,Object> row=new LinkedHashMap<>();for(var e:old.entrySet())row.put(String.valueOf(e.getKey()),e.getValue());history.add(row);}
        Map<String,Long> quantities=new HashMap<>();
        int sold=0,piles=0;long units=0;
        for(int slot=0;slot<after.length;slot++){
            ItemStack stack=after[slot];if(!isSellable(stack))continue;
            String key=stack.getType().name().toLowerCase(Locale.ROOT);int amount=stack.getAmount();
            double price=shopProceeds(key,amount);if(!Double.isFinite(price)||price<=0)continue;
            sold+=amount;piles++;units+=Math.round(price*10000);
            quantities.merge(key,(long)amount,Long::sum);after[slot]=null;
            Map<String,Object> row=new LinkedHashMap<>();row.put("type","VENTE");row.put("item",key);row.put("amount",amount);row.put("total",price);row.put("player",p.getName());row.put("time",System.currentTimeMillis());history.add(0,row);
        }
        double total=units/10000.0;
        if(sold==0||!Double.isFinite(total)||total<=0){msg(p,"Aucun objet vendable.");return;}
        if(!tradeManager.commerce(p,after,total,null,0,null,null,false,"SHOP_SELL_ALL")){msg(p,"Vente non confirmée.");return;}
        for(var entry:quantities.entrySet()){
            String k=entry.getKey();long amount=entry.getValue();
            db.set("market.window-sold."+k,db.getLong("market.window-sold."+k,0L)+amount);
            db.set("market.total-sold."+k,db.getLong("market.total-sold."+k,0L)+amount);
        }
        db.set("market.sales-total",db.getLong("market.sales-total",0L)+sold);
        db.set("market.sales-count",db.getLong("market.sales-count",0L)+piles);
        db.set("market.history",new ArrayList<>(history.subList(0,Math.min(100,history.size()))));save();
        msg(p,"Vente totale terminée : "+sold+" objet(s) pour "+formatShopPrice(total)+" Larry.");openSellMenu(p);
    }
    private boolean buyShop(Player p,String key,int amount){if(!recipePricingReady){msg(p,"Boutique temporairement indisponible.");return true;}if(!SHOP.containsKey(key)){msg(p,"Objet inconnu. Utilise /shop search <objet>.");return true;}if(amount<1||amount>64){msg(p,"Quantité autorisée: 1 à 64.");return true;}ItemStack purchase=new ItemStack(SHOP.get(key),amount);Inventory test=Bukkit.createInventory(null,36);test.setStorageContents(p.getInventory().getStorageContents());if(!test.addItem(purchase.clone()).isEmpty()){msg(p,"Inventaire insuffisant; aucun Larry débité.");return true;}double price=Math.round(shopBuyPrice(key)*amount*10000)/10000.0;if(!Double.isFinite(price)||price<=0||!tradeManager.commerce(p,test.getStorageContents(),-price,null,0,null,null,false,"SHOP_BUY")){msg(p,"Fonds Larry insuffisants. Prix: "+formatShopPrice(price)+" Larry.");return true;}List<Map<String,Object>> history=new ArrayList<>();for(Map<?,?> old:db.getMapList("market.history")){Map<String,Object> rowCopy=new LinkedHashMap<>();for(var x:old.entrySet())rowCopy.put(String.valueOf(x.getKey()),x.getValue());history.add(rowCopy);}Map<String,Object> buyRow=new LinkedHashMap<>();buyRow.put("type","ACHAT");buyRow.put("item",key);buyRow.put("amount",amount);buyRow.put("total",price);buyRow.put("player",p.getName());buyRow.put("time",System.currentTimeMillis());history.add(0,buyRow);if(history.size()>100)history=new ArrayList<>(history.subList(0,100));db.set("market.history",history);db.set("market.purchase-total",db.getLong("market.purchase-total",0L)+amount);save();msg(p,"Achat effectué: "+amount+" x "+key+" pour "+formatShopPrice(price)+" Larry.");return true;}
    private boolean shop(CommandSender sender,String[] args){if(args.length==0){Player p=player(sender);if(p!=null)openShop(p);return true;}if(args[0].equalsIgnoreCase("sell")){Player p=player(sender);if(p==null)return true;int n=p.getInventory().getItemInMainHand().getAmount();if(args.length>1)try{n=Integer.parseInt(args[1]);}catch(NumberFormatException ex){n=0;}return sellShop(p,n);}if(args.length>=2&&args[0].equalsIgnoreCase("buy")){Player p=player(sender);if(p==null)return true;int n=1;if(args.length>2)try{n=Integer.parseInt(args[2]);}catch(NumberFormatException ex){n=0;}return buyShop(p,args[1].toLowerCase(Locale.ROOT),n);}if(args.length<2||!args[0].equalsIgnoreCase("search")){msg(sender,"Usage: /shop | /shop search <objet> | /shop buy <clé> [quantité] | /shop sell [quantité]");return true;}String q=String.join(" ",Arrays.copyOfRange(args,1,args.length)).toLowerCase(Locale.ROOT);if(sender instanceof Player p){shopSearch.put(p.getUniqueId(),q);openShopSearch(p,0);return true;}List<String> found=SHOP.entrySet().stream().filter(e->e.getKey().contains(q)||e.getValue().name().toLowerCase(Locale.ROOT).contains(q)).limit(30).map(e->e.getKey()+" — Achat "+formatShopPrice(shopBuyPrice(e.getKey()))+" / vente "+formatShopPrice(shopSellPrice(e.getKey()))+" Larry/u").toList();msg(sender,found.isEmpty()?"Aucun objet correspondant.":"Résultats: "+String.join(" | ",found));return true;}
    private boolean auction(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        if(!auctionsReady){msg(p,"Hôtel des ventes indisponible.");return true;}
        if(!core.isEconomyPlayer(p)){msg(p,"Commerce réservé aux joueurs authentifiés en survie.");return true;}
        if(a.length==0||a[0].equalsIgnoreCase("list")) { openAuction(p,0);return true; }
        if(a[0].equalsIgnoreCase("search")) {
            if(a.length<2){msg(p,"Usage: /ah search <objet|vendeur> ou /ah search clear");return true;}
            if(a[1].equalsIgnoreCase("clear"))ahSearch.remove(p.getUniqueId());
            else ahSearch.put(p.getUniqueId(),String.join(" ",Arrays.copyOfRange(a,1,a.length)));
            openAuction(p,0);return true;
        }
        if(a[0].equalsIgnoreCase("sell")) {
            if(a.length!=2){msg(p,"Usage: /ah sell <prix>");return true;}
            double price=Double.parseDouble(a[1]);ItemStack hand=p.getInventory().getItemInMainHand();
            if(!Double.isFinite(price)||price<=0||hand.getType()==Material.AIR){msg(p,"Prix invalide ou main vide.");return true;}
            createAuction(p,price);return true;
        }
        if((a[0].equalsIgnoreCase("buy")||a[0].equalsIgnoreCase("cancel"))&&a.length==2) {
            if(!auctionsReady){msg(p,"Hôtel des ventes indisponible.");return true;}
            String key=a[1];try{UUID.fromString(key);}catch(IllegalArgumentException ex){msg(p,"Annonce invalide.");return true;}
            var entry=core.auctionEntry(key);
            if(entry==null){msg(p,"Annonce introuvable.");return true;}
            double price=entry.getDouble("price");ItemStack stack=entry.getItemStack("stack");
            UUID seller;try{seller=UUID.fromString(entry.getString("sellerId",""));}catch(IllegalArgumentException ex){msg(p,"Vendeur invalide.");return true;}
            boolean own=seller.equals(p.getUniqueId());
            if(a[0].equalsIgnoreCase("cancel")&&!own){msg(p,"Seul le vendeur peut retirer son annonce.");return true;}
            if(stack==null||stack.getType().isAir()||(!own&&(!Double.isFinite(price)||price<=0||forbiddenCommerce(stack.getType())))){msg(p,"Annonce invalide.");return true;}
            if(!own&&System.currentTimeMillis()-entry.getLong("created",0L)>7L*86400000L){msg(p,"Annonce expirée; le vendeur peut la récupérer.");return true;}
            Inventory test=Bukkit.createInventory(null,36);test.setStorageContents(p.getInventory().getStorageContents());
            if(!test.addItem(stack.clone()).isEmpty()){msg(p,"Inventaire plein; annonce conservée.");return true;}
            double net=own?0:saleNet(price);
            if(!tradeManager.commerce(p,test.getStorageContents(),own?0:-price,own?null:seller,net,key,null,true,own?"AH_CANCEL":"AH_BUY")){
                msg(p,"Transaction non confirmée; vérifie ton solde.");return true;
            }
            msg(p,own?"Annonce retirée; objet rendu.":"Achat effectué.");return true;
        }
        msg(p,"/ah list | /ah sell <prix> | /ah buy <id>");return true;
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if(frozen.contains(e.getPlayer().getUniqueId())&&e.getTo()!=null&&(e.getFrom().getX()!=e.getTo().getX()||e.getFrom().getY()!=e.getTo().getY()||e.getFrom().getZ()!=e.getTo().getZ()))e.setTo(e.getFrom());
    }
    boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }
    void setVanished(Player p,boolean hidden) {
        if(hidden) vanished.add(p.getUniqueId()); else vanished.remove(p.getUniqueId());
        for(Player viewer:Bukkit.getOnlinePlayers()) {
            if(hidden && !staff(viewer)) viewer.hidePlayer(plugin,p);
            else viewer.showPlayer(plugin,p);
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        for(UUID id:vanished){Player v=Bukkit.getPlayer(id);if(v!=null&&!staff(e.getPlayer()))e.getPlayer().hidePlayer(plugin,v);}
    }
    @EventHandler public void onInvClick(InventoryClickEvent e) {
        String title=e.getView().getTitle();
        if(e.getView().getTopInventory().getHolder() instanceof ClanChestHolder holder){
            boolean alreadyCancelled=e.isCancelled();e.setCancelled(true);
            if(alreadyCancelled||!(e.getWhoClicked() instanceof Player p))return;
            boolean left=e.getClick()==ClickType.LEFT;
            boolean empty=e.getCursor()==null||e.getCursor().getType().isAir();
            if(!AntiDuplication.allowedChestClick(validChest(p,holder),e.getView().getTopInventory()==holder.inventory,left,empty))return;
            int raw=e.getRawSlot();if(raw<0||raw>=e.getView().countSlots())return;
            Bukkit.getScheduler().runTask(plugin,()->transferChest(p,holder,raw));return;
        }
        if(e.isCancelled())return;
        if(title.equals("Larry | Succès")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer)) return;
            int[] slots={10,12,14,16,28,30,32,34,20,24,22};
            List<String> categories=achievementCategories();
            String category=null;
            for(int i=0;i<slots.length;i++) if(e.getRawSlot()==slots[i]) category=categories.get(i);
            if(e.getRawSlot()==49) viewer.closeInventory();
            else if(category!=null) openAchievementCategory(viewer,category);
            return;
        }
        if(title.startsWith("Larry | ") && achievementCategories().contains(title.substring("Larry | ".length()))) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer)) return;
            if(e.getRawSlot()==18) openAchievementMenu(viewer);
            else if(e.getRawSlot()==26) viewer.closeInventory();
            else openAchievementCategory(viewer,title.substring("Larry | ".length()));
            return;
        }
        if(title.equals("Larry | Classements")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer))return;
            String command=switch(e.getRawSlot()) {
                case 11 -> "baltop";
                case 13 -> "clan top";
                case 15 -> "bounty list";
                default -> null;
            };
            if(e.getRawSlot()==22){viewer.closeInventory();return;}
            if(command!=null){viewer.closeInventory();viewer.performCommand(command);}
            return;
        }
        if(title.equals("Larry | Loterie")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p)||e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
            switch(e.getRawSlot()) {
                case 10 -> {p.closeInventory();p.performCommand("lottery buy 1");Bukkit.getScheduler().runTaskLater(plugin,()->openLotteryMenu(p),2L);}
                case 12 -> {awaitingLotteryAmount.add(p.getUniqueId());p.closeInventory();msg(p,"Combien de tickets veux-tu acheter? Écris un nombre de 1 à 1 000 dans le chat (ou 'annuler').");}
                case 22 -> {
                    if(!core.isLpcAdmin(p)){msg(p,"Seuls les administrateurs peuvent démarrer ou terminer une loterie.");return;}
                    p.closeInventory();
                    if(core.lotteryActive())p.performCommand("lottery draw");else p.performCommand("lottery start 10");
                    Bukkit.getScheduler().runTaskLater(plugin,()->openLotteryMenu(p),3L);
                }
                case 26 -> p.closeInventory();
                default -> { }
            }
            return;
        }
        if(title.equals("Larry | Grades")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p) || !core.isLpcAdmin(p))return;
            String grade=switch(e.getRawSlot()){case 10->"default";case 12->"modo";case 14->"admin";case 16->"owner";default->null;};
            if(e.getRawSlot()==22){p.closeInventory();return;}
            if(grade==null)return;
            UUID id=rankTargets.remove(p.getUniqueId());
            Player target=id==null?null:Bukkit.getPlayer(id);
            p.closeInventory();
            if(target==null){msg(p,"Joueur indisponible.");return;}
            ModoGroup.assign(core,p,target,grade);
            return;
        }
        if(title.equals("Larry | Modération")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p) || !staff(p))return;
            switch(e.getRawSlot()){
                case 4 -> {p.closeInventory();p.performCommand("modo");}
                case 10,11 -> {if(watch.active(p))openPlayerSelector(p,e.getRawSlot()==10?"tp":"freeze");}
                case 12 -> {if(watch.active(p)){p.closeInventory();p.performCommand("vanish");}}
                case 14,15 -> openPlayerSelector(p,e.getRawSlot()==14?"mute":"unmute");
                case 16 -> {p.closeInventory();p.performCommand("ac log");}
                case 22 -> p.closeInventory();
                default -> { }
            }
            return;
        }
        if(title.equals("Larry | Administration")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p) || !core.isLpcAdmin(p)) return;
            int slot=e.getRawSlot();
            if(slot==49){p.closeInventory();return;}
            switch(slot) {
                case 10 -> openPlayerSelector(p,"invsee");
                case 12 -> openPlayerSelector(p,"freeze");
                case 14 -> {p.closeInventory();p.performCommand("vanish");}
                case 16 -> msg(p,"Utilise /staffchat <message> pour parler au staff.");
                case 19 -> {if(!core.isLpcAdmin(p)){msg(p,"Réinitialisation réservée aux administrateurs.");return;}openPlayerSelector(p,"reset");}
                case 21 -> {p.closeInventory();msg(p,"Économie: /eco give|take|set <joueur> <montant>.");}
                case 23 -> {p.closeInventory();p.performCommand("lottery");}
                case 25 -> {p.closeInventory();p.performCommand("top");}
                case 28 -> openPlayerSelector(p,"op/deop");
                case 30 -> {
                    if(!core.isLpcAdmin(p)){msg(p,"Seul un administrateur peut modifier l'adresse.");return;}
                    awaitingAdminIp.add(p.getUniqueId());p.closeInventory();
                    msg(p,"Écris: <adresse:port> <nom du serveur> (ex.: play.exemple.ca:25565 Larry SMP).");
                }
                case 32 -> {p.closeInventory();p.performCommand("ac status");}
                case 34 -> openAdmin(p);
                case 40 -> openPlayerSelector(p,"rank");
                default -> { }
            }
            return;
        }
        if(title.startsWith("Larry | Sélection ")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer))return;
            int slot=e.getRawSlot();
            String action=selectorAction.get(viewer.getUniqueId());
            if(!staff(viewer) || action==null || !title.equals("Larry | Sélection "+action))return;
            if(slot==45){awaitingSelectorSearch.add(viewer.getUniqueId());viewer.closeInventory();msg(viewer,"Écris le pseudo à rechercher dans le chat (ou 'annuler').");return;}
            if(slot==47){selectorSearch.remove(viewer.getUniqueId());openPlayerSelector(viewer,action);return;}
            if(slot==49){viewer.closeInventory();return;}
            if(slot==53){openPlayerSelector(viewer,action);return;}
            if(slot<0||slot>=45||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            String targetName=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Player target=Bukkit.getPlayerExact(targetName);
            if(target==null) { msg(viewer,"Ce joueur n'est plus en ligne.");viewer.closeInventory();return; }
            if(title.endsWith("invsee")) viewer.openInventory(target.getInventory());
            else if(action.equals("rank")) openRank(viewer,target);
            else if(action.equals("op/deop")) {if(core.isLpcAdmin(viewer))openOpConfirm(viewer,target);}
            else if(Set.of("tp","mute","unmute").contains(action)) {viewer.closeInventory();viewer.performCommand(action+" "+target.getName());}
            else if(title.endsWith("reset")) {
                if(!core.isLpcAdmin(viewer)){msg(viewer,"Réinitialisation réservée aux administrateurs.");return;}
                viewer.closeInventory();
                if(core.resetAuthAccount(target.getName()))msg(viewer,"Mot de passe réinitialisé pour "+target.getName()+". Le joueur a été expulsé et devra refaire /register.");
                else msg(viewer,"Aucun compte enregistré pour "+target.getName()+".");
            } else { viewer.closeInventory();viewer.performCommand("freeze "+target.getName()); }
        }
        if(title.equals("Larry | Confirmer OP")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player staffPlayer))return;
            if(e.getRawSlot()==15){pendingOpTarget.remove(staffPlayer.getUniqueId());staffPlayer.closeInventory();return;}
            if(e.getRawSlot()!=11)return;
            if(!core.isLpcAdmin(staffPlayer)){pendingOpTarget.remove(staffPlayer.getUniqueId());staffPlayer.closeInventory();msg(staffPlayer,"Permission insuffisante.");return;}
            UUID targetId=pendingOpTarget.remove(staffPlayer.getUniqueId());
            Player target=targetId==null?null:Bukkit.getPlayer(targetId);
            if(target==null){staffPlayer.closeInventory();msg(staffPlayer,"Le joueur n'est plus en ligne.");return;}
            target.setOp(!target.isOp());
            staffPlayer.closeInventory();
            msg(staffPlayer,(target.isOp()?"OP donné à ":"OP retiré à ")+target.getName()+".");
            msg(target,"Ton statut OP a été "+(target.isOp()?"activé":"retiré")+" par le staff.");
            plugin.getLogger().warning(staffPlayer.getName()+" a "+(target.isOp()?"donné":"retiré")+" OP à "+target.getName()+".");
        }
        if(title.equals("Larry | Primes")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer))return;
            int slot=e.getRawSlot();
            if(slot==49){viewer.closeInventory();return;}
            if(slot==45){openBountyTargets(viewer);return;}
            if(slot==46){awaitingBountyListSearch.add(viewer.getUniqueId());viewer.closeInventory();msg(viewer,"Écris le pseudo à rechercher dans le chat (ou 'annuler').");return;}
            if(slot==47){bountySearch.remove(viewer.getUniqueId());openBountyMenu(viewer);return;}
            if(slot==53){openBountyMenu(viewer);return;}
            return;
        }
        if(title.equals("Larry | Choisir une cible")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer))return;
            if(e.getRawSlot()==45){awaitingBountySearch.add(viewer.getUniqueId());viewer.closeInventory();msg(viewer,"Écris le pseudo à rechercher dans le chat (ou 'annuler').");return;}
            if(e.getRawSlot()==47){bountySearch.remove(viewer.getUniqueId());openBountyTargets(viewer);return;}
            if(e.getRawSlot()==49){openBountyMenu(viewer);return;}
            if(e.getRawSlot()==53){openBountyTargets(viewer);return;}
            if(e.getRawSlot()<0||e.getRawSlot()>=45||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta())return;
            String targetName=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Player target=Bukkit.getPlayerExact(targetName);
            if(target==null){msg(viewer,"Cette cible n'est plus en ligne.");openBountyTargets(viewer);return;}
            pendingBountyTarget.put(viewer.getUniqueId(),target.getUniqueId());
            viewer.closeInventory();
            msg(viewer,"Écris dans le chat le montant de la prime pour "+target.getName()+" (ou 'annuler').");
            return;
        }
        if(title.equals("Larry | Hôtel des ventes")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player buyer))return;
            int slot=e.getRawSlot();
            if(slot>=0&&slot<45) {
                String key=ahSlots.getOrDefault(buyer.getUniqueId(),Map.of()).get(slot);
                if(key==null)return;
                auction(buyer,new String[]{"buy",key});
                openAuction(buyer,ahPage.getOrDefault(buyer.getUniqueId(),0));
                return;
            }
            if(slot==45){openAuctionSell(buyer);return;}
            if(slot==46){awaitingAhSearch.add(buyer.getUniqueId());buyer.closeInventory();msg(buyer,"Écris un objet, un vendeur ou un nom d'annonce dans le chat (ou 'annuler').");return;}
            if(slot==47){ahSearch.remove(buyer.getUniqueId());openAuction(buyer,0);return;}
            if(slot==48){openAuction(buyer,ahPage.getOrDefault(buyer.getUniqueId(),0)-1);return;}
            if(slot==49){buyer.closeInventory();return;}
            if(slot==50){openAuction(buyer,ahPage.getOrDefault(buyer.getUniqueId(),0)+1);return;}
            if(slot==53){openAuction(buyer,ahPage.getOrDefault(buyer.getUniqueId(),0));return;}
        }
        if(title.equals("Larry | Prix de vente")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player seller))return;
            double price=switch(e.getRawSlot()){case 10->10.0;case 12->50.0;case 14->100.0;case 16->500.0;default->-1.0;};
            if(e.getRawSlot()==22){openAuction(seller,0);return;}
            if(price>0){seller.closeInventory();createAuction(seller,price);}
        }
        if(title.equals("Larry | Vendre")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player seller)||e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
            int slot=e.getRawSlot();if(slot==45){openSellAllConfirmation(seller);return;}if(slot==49){seller.closeInventory();return;}if(slot==53){openSellMenu(seller);return;}
            if(slot>=0&&slot<36){ItemStack stack=seller.getInventory().getItem(slot);if(isSellable(stack)){sellStack(seller,slot,stack.getAmount());openSellMenu(seller);}}return;
        }
        if(title.equals("Larry | Confirmer vente totale")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player seller))return;
            if(e.getRawSlot()==11){executeSellAll(seller);return;}if(e.getRawSlot()==15){saleSnapshots.remove(seller.getUniqueId());saleQuotes.remove(seller.getUniqueId());openSellMenu(seller);return;}return;
        }
        if(title.equals("Larry | Catégories")) {
            e.setCancelled(true);
            if(e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
            if(!(e.getWhoClicked() instanceof Player buyer)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta())return;
            if(e.getRawSlot()==45){openSellMenu(buyer);return;}
            if(e.getRawSlot()==49){awaitingShopSearch.add(buyer.getUniqueId());buyer.closeInventory();msg(buyer,"Écris le nom de l’objet à rechercher dans le chat (ou annuler).");return;}
            if(e.getRawSlot()==53){buyer.closeInventory();return;}
            String selected=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            if(shopCategories().contains(selected))openShop(buyer,selected,0);
        }
        if(title.equals("Larry | Boutique")) {
            e.setCancelled(true);
            if(e.getRawSlot()>=e.getView().getTopInventory().getSize()) return;
            if(!(e.getWhoClicked() instanceof Player buyer)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            int slot=e.getRawSlot();String category=shopCategory.getOrDefault(buyer.getUniqueId(),"Divers");int page=shopPage.getOrDefault(buyer.getUniqueId(),0);
            if(slot==0){openShopCategories(buyer);return;}
            if(slot==45){if(category.equals("__SEARCH__"))openShopSearch(buyer,page-1);else openShop(buyer,category,page-1);return;}
            if(slot==47){awaitingShopSearch.add(buyer.getUniqueId());buyer.closeInventory();msg(buyer,"Écris le nom de l’objet à rechercher dans le chat (ou annuler).");return;}
            if(slot==51){buyer.closeInventory();return;}
            if(slot==53){if(category.equals("__SEARCH__"))openShopSearch(buyer,page+1);else openShop(buyer,category,page+1);return;}
            if(slot<9||slot>44)return;
            if(!e.getCurrentItem().getItemMeta().hasDisplayName())
                openShopQuantity(buyer,e.getCurrentItem().getType());
            return;
        }
        if(title.equals("Larry | Quantité")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player buyer)) return;
            int slot=e.getRawSlot();
            if(slot==18) {
                String category=shopCategory.getOrDefault(buyer.getUniqueId(),"Divers");
                int page=shopPage.getOrDefault(buyer.getUniqueId(),0);
                if(category.equals("__SEARCH__")) openShopSearch(buyer,page);
                else openShop(buyer,category,page);
                return;
            }
            int amount=switch(slot){case 11 -> 1; case 13 -> 16; case 15 -> 64; default -> 0;};
            if(amount>0) {
                Material material=e.getView().getTopInventory().getItem(4).getType();
                buyShop(buyer,material.name().toLowerCase(Locale.ROOT),amount);
                openShopQuantity(buyer,material);
            }
            return;
        }
        if(title.equals("Larry | Quiz")) {
            e.setCancelled(true);
            if(e.getRawSlot()>=e.getView().getTopInventory().getSize()) return;
            if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            int slot=e.getRawSlot();if(!List.of(11,13,15,17,20,22).contains(slot))return;
            String expected=db.getString("quiz."+id(p)+".answer","");
            String selected=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            if(expected.isEmpty()){msg(p,"Cette question est expirée. Fais /quiz.");p.closeInventory();return;}
            if(selected.equalsIgnoreCase(expected)){
                if(!Double.isFinite(core.balance(p.getUniqueId())+50)){msg(p,"Ton solde ne peut pas recevoir la récompense.");}
                else if(core.commitReward(p.getUniqueId(),"quiz."+id(p)+"."+db.getLong("quiz."+id(p)+".time"),50,"QUIZ")){String scorePath="quiz-scores."+id(p);db.set(scorePath,db.getInt(scorePath,0)+1);msg(p,"Bonne réponse! +50 Larry. Score: "+db.getInt(scorePath)+" points.");}
            } else msg(p,"Mauvaise réponse. La bonne réponse était: "+expected+".");
            db.set("quiz."+id(p)+".answer",null);save();p.closeInventory();
            return;
        }
        if(title.equals("Larry | Clan")||title.equals("Larry | Quêtes")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer)) return;
            if(e.getRawSlot()==22) { viewer.closeInventory();return; }
            if(title.equals("Larry | Clan")) {
                String command=switch(e.getRawSlot()) { case 10 -> "clan info";case 12 -> "clan bank";case 14 -> "clan chest";case 15 -> "clan chest upgrade";case 16 -> "clan top";default -> null; };
                if(command!=null){viewer.closeInventory();viewer.performCommand(command);}
            } else {
                String command=switch(e.getRawSlot()) { case 11 -> "quests claim";case 15 -> "quests weekly claim";default -> null; };
                if(command!=null){viewer.closeInventory();viewer.performCommand(command);}
            }
        }
    }
    // Les menus LPC sont des interfaces: un glisser-déposer ne doit jamais y déposer
    // les objets du joueur (notamment sur les vitres de remplissage).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLpcMenuDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        String title = e.getView().getTitle();
        boolean lpcMenu = title.startsWith("Larry |")
            || title.startsWith("Larry I")
            ;
        if (!lpcMenu) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST) public void onClanChestDrag(InventoryDragEvent e){
        if(e.getView().getTopInventory().getHolder() instanceof ClanChestHolder)e.setCancelled(true);
    }
    @EventHandler public void onChestClose(InventoryCloseEvent e){
        if(e.getView().getTitle().equals("Larry | Grades"))rankTargets.remove(e.getPlayer().getUniqueId());
        if(e.getInventory().getHolder() instanceof ClanChestHolder holder&&clanChests.get(holder.clan)==holder.inventory){
            clanChests.remove(holder.clan);clanChestUsers.remove(holder.clan,holder.viewer);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId();
        for(String clan:new ArrayList<>(clanChestUsers.keySet()))if(id.equals(clanChestUsers.get(clan))){clanChestUsers.remove(clan);clanChests.remove(clan);}
        saleSnapshots.remove(id);saleQuotes.remove(id);
        ahSlots.remove(id);ahPage.remove(id);ahSearch.remove(id);
        shopPage.remove(id);shopSearch.remove(id);shopCategory.remove(id);
        rankTargets.remove(id);frozen.remove(id);awaitingAhSearch.remove(id);awaitingShopSearch.remove(id);pendingBountyTarget.remove(id);
        awaitingSelectorSearch.remove(id);awaitingBountySearch.remove(id);
        awaitingLotteryAmount.remove(id);awaitingAdminIp.remove(id);awaitingBountyListSearch.remove(id);
        selectorAction.remove(id);selectorSearch.remove(id);bountySearch.remove(id);
    }
    @EventHandler public void onChatSearch(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player sender=e.getPlayer();
        UUID senderId=sender.getUniqueId();
        if(awaitingLotteryAmount.remove(senderId)) {
            e.setCancelled(true);
            String raw=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(raw.equalsIgnoreCase("annuler")){msg(sender,"Achat personnalisé annulé.");openLotteryMenu(sender);return;}
                try {
                    int count=Integer.parseInt(raw);
                    if(count<1||count>1000){msg(sender,"Choisis un nombre de 1 à 1 000.");openLotteryMenu(sender);return;}
                    sender.performCommand("lottery buy "+count);
                    openLotteryMenu(sender);
                } catch(NumberFormatException ex){msg(sender,"Écris uniquement un nombre entier.");openLotteryMenu(sender);}
            });
            return;
        }
        if(awaitingAdminIp.remove(senderId)) {
            e.setCancelled(true);
            String raw=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!core.isLpcAdmin(sender)){msg(sender,"Seul un administrateur peut modifier l'adresse.");return;}
                String[] parts=raw.split("\\s+",2);
                if(parts.length<2){msg(sender,"Format: <adresse:port> <nom du serveur>.");return;}
                adminCommand(sender,new String[]{"ip",parts[0],parts[1]});
            });
            return;
        }
        if(awaitingSelectorSearch.remove(senderId)) {
            e.setCancelled(true);
            String query=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                String action=selectorAction.get(senderId);
                if(query.equalsIgnoreCase("annuler")){msg(sender,"Recherche annulée.");if(action!=null)openPlayerSelector(sender,action);return;}
                if(query.length()>64){msg(sender,"Pseudo trop long (64 caractères maximum).");if(action!=null)openPlayerSelector(sender,action);return;}
                selectorSearch.put(senderId,query);
                if(action!=null)openPlayerSelector(sender,action);
            });
            return;
        }
        if(awaitingBountyListSearch.remove(senderId)) {
            e.setCancelled(true);
            String query=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(query.equalsIgnoreCase("annuler")){msg(sender,"Recherche de primes annulée.");openBountyMenu(sender);return;}
                if(query.length()>64){msg(sender,"Pseudo trop long.");openBountyMenu(sender);return;}
                bountySearch.put(senderId,query);openBountyMenu(sender);
            });
            return;
        }
        if(awaitingBountySearch.remove(senderId)) {
            e.setCancelled(true);
            String query=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(query.equalsIgnoreCase("annuler")){msg(sender,"Recherche de prime annulée.");openBountyTargets(sender);return;}
                if(query.length()>64){msg(sender,"Pseudo trop long.");openBountyTargets(sender);return;}
                bountySearch.put(senderId,query);openBountyTargets(sender);
            });
            return;
        }
        if(awaitingShopSearch.remove(senderId)) {
            e.setCancelled(true);
            String query=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(query.equalsIgnoreCase("annuler")){msg(sender,"Recherche annulée.");openShopCategories(sender);return;}
                if(query.isBlank()||query.length()>48){msg(sender,"Recherche invalide (1 à 48 caractères).");openShopCategories(sender);return;}
                shopSearch.put(senderId,query);openShopSearch(sender,0);
            });
            return;
        }
        if(awaitingAhSearch.remove(senderId)) {
            e.setCancelled(true);
            String query=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(query.equalsIgnoreCase("annuler")){msg(sender,"Recherche annulée.");return;}
                ahSearch.put(senderId,query);openAuction(sender,0);
            });
            return;
        }
        UUID targetId=pendingBountyTarget.remove(senderId);
        if(targetId!=null) {
            e.setCancelled(true);
            String amount=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(amount.equalsIgnoreCase("annuler")){msg(sender,"Placement de prime annulé.");openBountyMenu(sender);return;}
                Player target=Bukkit.getPlayer(targetId);
                if(target==null){msg(sender,"La cible s'est déconnectée; recommence.");openBountyMenu(sender);return;}
                sender.performCommand("bounty "+target.getName()+" "+amount);
                openBountyMenu(sender);
            });
        }
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a) {
        if(c.getName().equalsIgnoreCase("trade") && a.length==1) {
            List<String> options=new ArrayList<>(List.of("accept","cancel"));
            Bukkit.getOnlinePlayers().stream().filter(p->!p.equals(s)).map(Player::getName).forEach(options::add);
            return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        if(c.getName().equalsIgnoreCase("admin") && !s.isOp() && !s.hasPermission("larpers.admin"))return List.of();
        if(c.getName().equalsIgnoreCase("admin")&&a.length==1)return List.of("reset","ip","event");
        if(c.getName().equalsIgnoreCase("admin")&&a.length==2&&a[0].equalsIgnoreCase("event"))return List.of("list","start","stop","status");
        if(c.getName().equalsIgnoreCase("admin")&&a.length==3&&a[0].equalsIgnoreCase("event")&&a[1].equalsIgnoreCase("start"))return List.of("melange","quiz","calcul","reaction");
        if(c.getName().equalsIgnoreCase("admin")&&a.length==2&&a[0].equalsIgnoreCase("reset"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if(c.getName().equalsIgnoreCase("clan")&&a.length==1)return List.of("create","invite","join","leave","transfer","disband","info","role","bank","top","allies","level","chest","audit","stats","leaderboard").stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if(c.getName().equalsIgnoreCase("clan")&&a.length==2&&a[0].equalsIgnoreCase("invite")) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(target->!target.getUniqueId().equals(s instanceof Player player?player.getUniqueId():null))
                    .filter(target->!db.contains("clan-members."+target.getUniqueId()))
                    .map(Player::getName)
                    .filter(name->name.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if(c.getName().equalsIgnoreCase("ah")&&a.length==1)return List.of("list","search","sell","buy");
        if(c.getName().equalsIgnoreCase("ah")&&a.length==2&&a[0].equalsIgnoreCase("search"))return List.of("clear");
        if(c.getName().equalsIgnoreCase("lottery")&&a.length==2&&a[0].equalsIgnoreCase("buy"))return List.of("1","10","50","100","500","1000");
        return List.of();
    }
}
