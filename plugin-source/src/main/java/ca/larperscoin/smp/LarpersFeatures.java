package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private final Map<String, ItemStack[]> clanChestSnapshots = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final SecureTradeManager tradeManager;
    private final ChatEvents chatEvents;
    private final Map<UUID, Long> lastQuiz = new HashMap<>();
    private final Map<UUID, Long> questClaims = new HashMap<>();
    private final Map<UUID, UUID> pendingBids = new HashMap<>();
    private final Map<UUID, String> shopCategory = new HashMap<>();
    private final Map<UUID, Integer> shopPage = new HashMap<>();
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
    static {for(Material m:Material.values()){String n=m.name();if(!m.isItem()||m.isAir()||SHOP_BLOCKED.contains(m)||SHOP_BLOCKED_NAMES.contains(n)||n.endsWith("_SPAWN_EGG"))continue;String k=n.toLowerCase(Locale.ROOT);SHOP.put(k,m);SHOP_PRICES.put(k,baseShopPrice(m));}}
    private static double baseShopPrice(Material m){String n=m.name();if(n.contains("DIAMOND"))return 2.50;if(n.contains("EMERALD"))return 1.80;if(n.contains("NETHER_STAR")||n.contains("DRAGON_EGG"))return 8;if(n.contains("TRIDENT")||n.contains("HEART_OF_THE_SEA")||n.contains("TOTEM"))return 2;if(n.contains("IRON_INGOT")||n.contains("IRON_NUGGET"))return .55;if(n.contains("GOLD_INGOT")||n.contains("GOLD_NUGGET"))return .45;if(n.contains("COPPER_INGOT"))return .18;if(n.contains("LAPIS"))return .12;if(n.contains("REDSTONE"))return .10;if(n.contains("COAL"))return .16;if(n.contains("OBSIDIAN"))return .25;if(n.contains("BREAD")||n.contains("COOKED"))return .18;if(n.contains("LOG")||n.contains("WOOD")||n.contains("PLANKS")||n.contains("STEM")||n.contains("HYPHAE"))return .08;if(n.contains("WHEAT")||n.contains("CARROT")||n.contains("POTATO")||n.contains("BEETROOT")||n.contains("PUMPKIN")||n.contains("MELON"))return .08;if(n.contains("STONE")||n.contains("COBBLESTONE")||n.contains("DEEPSLATE")||n.contains("TUFF")||n.contains("GRANITE")||n.contains("DIORITE")||n.contains("ANDESITE"))return .025;if(n.contains("DIRT")||n.contains("SAND")||n.contains("GRAVEL")||n.contains("CLAY")||n.contains("NETHERRACK"))return .015;if(n.contains("BOOK")||n.contains("POTION")||n.contains("ENCHANTED"))return .30;if(n.contains("SWORD")||n.contains("PICKAXE")||n.contains("AXE")||n.contains("HOE")||n.contains("SHOVEL")||n.contains("HELMET")||n.contains("CHESTPLATE")||n.contains("LEGGINGS")||n.contains("BOOTS"))return .35;return .05;}

    public LarpersFeatures(JavaPlugin plugin) {
        this.plugin = plugin;
        this.core = (LarpersCoin) plugin;
        this.tradeManager = new SecureTradeManager(plugin, core, this::recordTradeCompleted);
        this.chatEvents = new ChatEvents(plugin, core);
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "features.yml");
        db = YamlConfiguration.loadConfiguration(file);
        for(var e:SHOP_PRICES.entrySet()){String k=e.getKey();if(!db.contains("shop.sell-prices."+k))db.set("shop.sell-prices."+k,e.getValue());if(!db.contains("shop.buy-prices."+k))db.set("shop.buy-prices."+k,Math.max(.02,e.getValue()*2.8));}
        if(!db.contains("market.next-refresh"))db.set("market.next-refresh",System.currentTimeMillis()+MARKET_REFRESH_MILLIS);
        long delay=Math.max(1,(db.getLong("market.next-refresh")-System.currentTimeMillis())/50L);
        Bukkit.getScheduler().runTaskLater(plugin,()->{recalculateMarketPrices();Bukkit.getScheduler().runTaskTimer(plugin,this::recalculateMarketPrices,MARKET_REFRESH_MILLIS/50L,MARKET_REFRESH_MILLIS/50L);},delay);
        seedQuizQuestion("compass","Quel minerai permet de fabriquer une boussole?","fer",List.of("fer","cuivre","or"));
        seedQuizQuestion("faces","Combien de faces possède un bloc standard?","6",List.of("4","6","8"));
        seedQuizQuestion("creeper","Quel mob explose près du joueur?","creeper",List.of("creeper","zombie","squelette"));
        save();
        for (String cmd : List.of("clan","quest","quests","succes","top","admin","vanish","invsee","freeze","staffchat","ah","trade","check","shop")) {
            PluginCommand c = plugin.getCommand(cmd);
            if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::save,1200L,1200L);
        plugin.getLogger().info("Modules clans, quêtes, classements, commerce entre joueurs et modération chargés.");
    }

    private void save() {
        for(var entry:clanChests.entrySet())db.set(clanPath(entry.getKey())+".chest",Arrays.asList(entry.getValue().getContents()));
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
        } catch(IOException ex) {
            plugin.getLogger().severe("Échec de sauvegarde atomique de features.yml: "+ex.getMessage());
            try { Files.deleteIfExists(temporary); } catch(IOException ignored) { }
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
    public void shutdown() { chatEvents.shutdown(); save(); }
    private void msg(CommandSender s, String m) { s.sendMessage(PREFIX + m); }
    private boolean staff(CommandSender s) { return s.isOp() || s.hasPermission("larpers.staff") || s.hasPermission("larpers.admin"); }
    private Player player(CommandSender s) { if (s instanceof Player p) return p; msg(s,"Commande réservée aux joueurs."); return null; }
    private String id(Player p) { return p.getUniqueId().toString(); }
    private String clanOf(UUID u) { return db.getString("clan-members."+u); }
    private String clanPath(String clan) { return "clans."+clan.toLowerCase(Locale.ROOT); }
    private boolean member(Player p) { return clanOf(p.getUniqueId()) != null; }
    private void give(Player p, double amount) {
        core.credit(p.getUniqueId(), amount);
        core.saveData();
    }
    private boolean charge(Player p, double amount) {
        boolean ok = core.debit(p.getUniqueId(), amount);
        if (ok) core.saveData();
        return ok;
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
        int chestLevel=own==null?1:Math.max(1,db.getInt(clanPath(own)+".chest-level",1));
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
        inv.setItem(34,item(Material.SUNFLOWER,"Actualiser le menu"));
        inv.setItem(49,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private void openAuction(Player p,int page) {
        List<String> keys=new ArrayList<>();
        var section=db.getConfigurationSection("ah");
        if(section!=null) keys.addAll(section.getKeys(false));
        String query=ahSearch.getOrDefault(p.getUniqueId(),"").toLowerCase(Locale.ROOT).trim();
        if(!query.isEmpty()) keys.removeIf(k -> {
            String path="ah."+k;
            ItemStack stack=db.getItemStack(path+".stack");
            String seller=db.getString(path+".seller","");
            String material=stack==null?"":stack.getType().name();
            String display=stack!=null&&stack.hasItemMeta()&&stack.getItemMeta().hasDisplayName()?ChatColor.stripColor(stack.getItemMeta().getDisplayName()):"";
            return !(k.toLowerCase(Locale.ROOT).contains(query)||seller.toLowerCase(Locale.ROOT).contains(query)
                ||material.toLowerCase(Locale.ROOT).contains(query)||display.toLowerCase(Locale.ROOT).contains(query));
        });
        keys.sort(Comparator.comparingLong(k->db.getLong("ah."+k+".created",0L)));
        Collections.reverse(keys);
        int maxPage=Math.max(0,(keys.size()-1)/45);
        page=Math.max(0,Math.min(page,maxPage));
        ahPage.put(p.getUniqueId(),page);
        Map<Integer,String> slots=new HashMap<>();
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Hôtel des ventes");
        int start=page*45;
        for(int n=start;n<Math.min(keys.size(),start+45);n++) {
            String key=keys.get(n),path="ah."+key;
            ItemStack stack=db.getItemStack(path+".stack");
            if(stack==null||stack.getType()==Material.AIR) continue;
            ItemStack display=stack.clone();
            ItemMeta meta=display.getItemMeta();
            if(meta!=null) {
                List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
                lore.add(ChatColor.GRAY+"Vendeur: "+db.getString(path+".seller","inconnu"));
                lore.add(ChatColor.GOLD+"Prix: "+db.getDouble(path+".price")+" Larry");
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
    private void createAuction(Player p,double price) {
        ItemStack hand=p.getInventory().getItemInMainHand();
        if(!Double.isFinite(price)||price<=0||hand.getType()==Material.AIR){msg(p,"Prix invalide ou main vide.");return;}
        String key=UUID.randomUUID().toString().substring(0,8),path="ah."+key;
        db.set(path+".seller",p.getName());db.set(path+".sellerId",id(p));
        db.set(path+".item",hand.getType().name());db.set(path+".amount",hand.getAmount());
        db.set(path+".price",price);db.set(path+".stack",hand.clone());db.set(path+".created",System.currentTimeMillis());
        p.getInventory().setItemInMainHand(null);save();msg(p,"Annonce créée pour "+price+" Larry.");
        openAuction(p,0);
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
                case "admin" -> { return adminCommand(sender,a); }
                case "vanish" -> {
                    Player p=player(sender); if(p==null)return true; if(!staff(p)){msg(p,"Permission insuffisante.");return true;}
                    if(vanished.remove(p.getUniqueId())) { for(Player x:Bukkit.getOnlinePlayers())x.showPlayer(plugin,p); msg(p,"Vanish désactivé."); }
                    else { vanished.add(p.getUniqueId()); for(Player x:Bukkit.getOnlinePlayers())if(!staff(x))x.hidePlayer(plugin,p); msg(p,"Vanish activé."); }
                    return true;
                }
                case "freeze" -> {
                    if(!staff(sender)){msg(sender,"Permission insuffisante.");return true;}
                    if(a.length!=1){msg(sender,"Usage : /freeze <joueur>");return true;}
                    Player t=Bukkit.getPlayerExact(a[0]); if(t==null){msg(sender,"Joueur introuvable.");return true;}
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
            if(p!=null){if(!core.isLpcAdmin(p))msg(p,"Le menu /admin est réservé aux administrateurs.");else openAdmin(p);}
            return true;
        }
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
        if(a.length==0){openClanMenu(p);return true;}
        String sub=a[0].toLowerCase(Locale.ROOT), own=clanOf(p.getUniqueId());
        switch(sub) {
            case "create" -> {
                if(a.length<2||!a[1].matches("[A-Za-z0-9_]{3,16}")){msg(p,"Nom: 3 à 16 caractères alphanumériques.");return true;}
                if(own!=null){msg(p,"Tu es déjà dans un clan.");return true;}
                String n=a[1].toLowerCase(Locale.ROOT), path=clanPath(n);
                if(db.contains(path)){msg(p,"Ce clan existe déjà.");return true;}
                db.set(path+".name",a[1]); db.set(path+".owner",id(p)); db.set(path+".members",List.of(id(p))); db.set(path+".roles."+id(p),"leader");
                db.set(path+".bank",0.0); db.set(path+".level",1); db.set(path+".xp",0); db.set(path+".allies",new ArrayList<String>());
                db.set("clan-members."+id(p),n); save(); msg(p,"Clan "+a[1]+" créé."); return true;
            }
            case "invite" -> {
                if(a.length!=2||own==null){msg(p,"Usage : /clan invite <joueur> (dans un clan)");return true;}
                String path=clanPath(own);
                if(!clanLeaderOrOfficer(p,own)){msg(p,"Chef ou officier requis pour inviter.");return true;}
                OfflinePlayer target=Bukkit.getOfflinePlayer(a[1]);
                String targetClan=db.getString("clan-members."+target.getUniqueId());
                if(targetClan!=null){msg(p,"Ce joueur appartient déjà à un clan.");return true;}
                db.set("invites."+target.getUniqueId(),own);save();
                if(target.isOnline())msg(target.getPlayer(),"Invitation au clan "+db.getString(path+".name")+". Fais /clan join "+own);
                msg(p,"Invitation enregistrée pour "+a[1]+". Le joueur pourra la rejoindre avec /clan join "+own+".");return true;
            }
            case "join" -> {
                if(a.length!=2||own!=null){msg(p,"Usage : /clan join <nom> (sans clan)");return true;}
                String n=a[1].toLowerCase(Locale.ROOT);
                if(!n.equals(db.getString("invites."+id(p)))||!db.contains(clanPath(n))){msg(p,"Aucune invitation valide pour ce clan.");return true;}
                List<String> m=db.getStringList(clanPath(n)+".members");m.add(id(p));db.set(clanPath(n)+".members",m);db.set("clan-members."+id(p),n);db.set(clanPath(n)+".roles."+id(p),"member");db.set("invites."+id(p),null);save();msg(p,"Tu as rejoint le clan.");return true;
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
                String path=clanPath(own);List<String> members=db.getStringList(path+".members");
                for(String member:members)db.set("clan-members."+member,null);
                for(String ally:db.getStringList(path+".allies")){List<String> list=db.getStringList(clanPath(ally)+".allies");list.remove(own);db.set(clanPath(ally)+".allies",list);}
                var invites=db.getConfigurationSection("invites");if(invites!=null)for(String key:new ArrayList<>(invites.getKeys(false)))if(own.equals(db.getString("invites."+key)))db.set("invites."+key,null);
                var requests=db.getConfigurationSection("alliance-requests");
                if(requests!=null)for(String key:new ArrayList<>(requests.getKeys(false))) {
                    db.set("alliance-requests."+key+"."+own,null);
                    db.set("alliance-requests."+own+"."+key,null);
                }
                db.set(path,null);clanChests.remove(own);clanChestUsers.remove(own);clanChestSnapshots.remove(own);save();msg(p,"Clan dissous. Les membres ont été retirés.");return true;
            }
            case "info","stats","level" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own);int level=Math.max(1,db.getInt(path+".level",1));
                msg(p,db.getString(path+".name")+" | membres: "+db.getStringList(path+".members").size()+" | niveau "+level+" | XP "+db.getInt(path+".xp",0)+"/"+(level*100)+" | banque "+db.getDouble(path+".bank",0)+" Larry");
                msg(p,"Ton rôle: "+db.getString(path+".roles."+id(p),"member"));return true;
            }
            case "role" -> {
                if(own==null||a.length!=3||!id(p).equals(db.getString(clanPath(own)+".owner"))){msg(p,"Chef seulement: /clan role <joueur> <officer|member>");return true;}
                Player target=Bukkit.getPlayerExact(a[1]);
                String role=a[2].toLowerCase(Locale.ROOT);
                if(target==null||!own.equals(clanOf(target.getUniqueId()))||!(role.equals("officer")||role.equals("member"))){msg(p,"Joueur du clan en ligne et rôle valide requis.");return true;}
                db.set(clanPath(own)+".roles."+id(target),role);save();msg(p,target.getName()+" est maintenant "+role+".");msg(target,"Ton rôle de clan est maintenant "+role+".");return true;
            }
            case "bank" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own)+".bank";
                if(a.length==1){msg(p,"Banque du clan: "+db.getDouble(path)+" Larry. /clan bank deposit|withdraw <montant>");return true;}
                if(a.length!=3){msg(p,"Usage: /clan bank deposit|withdraw <montant>");return true;}
                double v=Double.parseDouble(a[2]);if(!Double.isFinite(v)||v<=0){msg(p,"Montant invalide.");return true;}
                if(a[1].equalsIgnoreCase("deposit")){
                    double nextBank=db.getDouble(path)+v;
                    if(!Double.isFinite(nextBank)){msg(p,"La banque dépasserait la limite numérique.");return true;}
                    if(!charge(p,v)){msg(p,"Fonds insuffisants.");return true;}
                    db.set(path,nextBank);core.recordTransaction("CLAN_DEPOSIT",p.getUniqueId(),new UUID(0L,0L),v);
                }
                else if(a[1].equalsIgnoreCase("withdraw")){
                    if(!clanLeaderOrOfficer(p,own)||db.getDouble(path)<v||!Double.isFinite(core.balance(p.getUniqueId())+v)){msg(p,"Retrait refusé (chef/officier seulement ou fonds insuffisants).");return true;}
                    db.set(path,db.getDouble(path)-v);give(p,v);core.recordTransaction("CLAN_WITHDRAW",new UUID(0L,0L),p.getUniqueId(),v);
                }
                else {msg(p,"Choisis deposit ou withdraw.");return true;} save();msg(p,"Opération effectuée.");return true;
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
                if(a.length>1 && a[1].equalsIgnoreCase("upgrade")) {
                    String cp=clanPath(own);
                    int level=Math.max(1,db.getInt(cp+".chest-level",1));
                    if(level>=7){msg(p,"Ton coffre a déjà sa capacité maximale (54 emplacements).");return true;}
                    double price=250.0*Math.pow(2,level-1);
                    if(!charge(p,price)){msg(p,"Amélioration du coffre: "+price+" Larry. Fonds insuffisants.");return true;}
                    db.set(cp+".chest-level",level+1);
                    Inventory cached=clanChests.get(own);
                    if(cached!=null) {
                        int oldSlots=level==1?1:(level-1)*9;
                        int newSlots=level*9;
                        for(int slot=oldSlots;slot<newSlots;slot++) {
                            ItemStack existing=cached.getItem(slot);
                            if(existing!=null && existing.getType()==Material.GRAY_STAINED_GLASS_PANE
                                    && existing.hasItemMeta() && "Emplacement verrouillé".equals(ChatColor.stripColor(existing.getItemMeta().getDisplayName()))) cached.clear(slot);
                        }
                    }
                    save();
                    msg(p,"Coffre amélioré à "+(level*9)+" emplacements. Coût: "+price+" Larry.");
                    return true;
                }
                UUID user=clanChestUsers.get(own);
                if(user!=null&&!user.equals(p.getUniqueId())){msg(p,"Le coffre du clan est déjà utilisé par un autre membre.");return true;}
                if(!db.getStringList(clanPath(own)+".members").contains(id(p))){msg(p,"Accès réservé aux membres du clan.");return true;}
                Inventory inv=clanChests.get(own);
                if(inv==null) {
                    inv=Bukkit.createInventory(null,54,"Coffre clan: "+own);
                    List<ItemStack> contents=(List<ItemStack>)db.getList(clanPath(own)+".chest");
                    if(contents!=null)for(int i=0;i<Math.min(54,contents.size());i++)inv.setItem(i,contents.get(i));
                    int storedLevel=db.getInt(clanPath(own)+".chest-level",0);
                    if(storedLevel<=0) {
                        // Un coffre déjà sauvegardé avant ce système conserve son ancienne capacité.
                        storedLevel=db.contains(clanPath(own)+".chest")?7:1;
                        db.set(clanPath(own)+".chest-level",storedLevel);
                    }
                    int loadedLevel=Math.max(1,Math.min(7,storedLevel));
                    int loadedUnlocked=loadedLevel==1?1:(loadedLevel-1)*9;
                    for(int slot=0;slot<loadedUnlocked;slot++) {
                        ItemStack existing=inv.getItem(slot);
                        if(existing!=null&&existing.getType()==Material.GRAY_STAINED_GLASS_PANE&&existing.hasItemMeta()
                                &&"Emplacement verrouillé".equals(ChatColor.stripColor(existing.getItemMeta().getDisplayName()))) inv.clear(slot);
                    }
                    clanChests.put(own,inv);
                }
                int chestLevel=Math.max(1,db.getInt(clanPath(own)+".chest-level",1));
                int unlocked=Math.max(1,Math.min(54,chestLevel==1?1:(chestLevel-1)*9));
                for(int slot=unlocked;slot<54;slot++) inv.setItem(slot,item(Material.GRAY_STAINED_GLASS_PANE,"Emplacement verrouillé","/clan chest upgrade"));
                clanChestUsers.put(own,p.getUniqueId());
                clanChestSnapshots.put(own,Arrays.stream(inv.getContents()).map(x->x==null?null:x.clone()).toArray(ItemStack[]::new));
                p.openInventory(inv);msg(p,"Coffre partagé: "+unlocked+"/54 emplacements. Améliore avec /clan chest upgrade.");return true;
            }
            case "allies" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String ap=clanPath(own)+".allies";
                if(a.length==1){msg(p,"Alliés: "+String.join(", ",db.getStringList(ap)));return true;}
                if(!id(p).equals(db.getString(clanPath(own)+".owner"))||a.length!=3){msg(p,"Chef: /clan allies request|accept|remove <clan>");return true;}
                String other=a[2].toLowerCase(Locale.ROOT);if(!db.contains(clanPath(other))||other.equals(own)){msg(p,"Clan invalide.");return true;}
                if(a[1].equalsIgnoreCase("request")) {
                    db.set("alliance-requests."+other+"."+own,true);save();
                    msg(p,"Demande d'alliance envoyée à "+db.getString(clanPath(other)+".name")+". Le chef adverse doit accepter.");
                    for(Player online:Bukkit.getOnlinePlayers()) if(id(online).equals(db.getString(clanPath(other)+".owner"))) msg(online,"Demande d'alliance de "+db.getString(clanPath(own)+".name")+". /clan allies accept "+own);
                    return true;
                }
                if(a[1].equalsIgnoreCase("accept")) {
                    if(!db.getBoolean("alliance-requests."+own+"."+other,false)){msg(p,"Aucune demande de ce clan.");return true;}
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
                List<String> names=new ArrayList<>(sec.getKeys(false));names.sort(Comparator.comparingDouble((String n)->db.getDouble(clanPath(n)+".bank")).reversed());
                msg(p,"Classement des clans (banque):");int i=1;for(String n:names){if(i>10)break;msg(p,i+". "+db.getString(clanPath(n)+".name")+" — "+db.getDouble(clanPath(n)+".bank")+" Larry");i++;}return true;
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
        String path=achievementPath(p)+".stats."+stat;
        db.set(path,Math.max(0L,db.getLong(path,0L)+amount));
        checkAchievements(p);
        saveSoon();
    }
    private boolean achievementClaimed(Player p,String key) {
        return db.getBoolean(achievementPath(p)+".claimed."+key,false);
    }
    private void unlockAchievement(Player p,String key,String label,String tier,double money,Material rewardMaterial,int rewardAmount) {
        if(achievementClaimed(p,key)) return;
        db.set(achievementPath(p)+".claimed."+key,true);
        String rewardText="";
        if(money>0 && Double.isFinite(core.balance(p.getUniqueId())+money)) {
            double safeMoney=Math.min(1000.0,money);
            give(p,safeMoney);
            core.recordTransaction("ACHIEVEMENT_"+key.toUpperCase(Locale.ROOT),new UUID(0L,0L),p.getUniqueId(),safeMoney);
            rewardText=" — +"+safeMoney+" LPC";
        } else if(rewardMaterial!=null && rewardAmount>0) {
            ItemStack reward=new ItemStack(rewardMaterial,rewardAmount);
            Map<Integer,ItemStack> leftovers=p.getInventory().addItem(reward);
            leftovers.values().forEach(stack->p.getWorld().dropItemNaturally(p.getLocation(),stack));
            rewardText=" — récompense: "+rewardAmount+" "+rewardMaterial.name();
        }
        msg(p,ChatColor.GOLD+"Succès débloqué: "+label+" ["+tier+"]"+rewardText+"!");
        if(tier.equalsIgnoreCase("EXTRÊME") || tier.equalsIgnoreCase("MYTHIQUE")) {
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE+"✦ "+ChatColor.LIGHT_PURPLE+p.getName()
                +ChatColor.GOLD+" a débloqué le succès "+ChatColor.YELLOW+label
                +ChatColor.LIGHT_PURPLE+" ["+tier+"]"+ChatColor.DARK_PURPLE+" ✦");
        }
        saveSoon();
    }
    private record AchievementTier(String key,String name,String stat,long goal,double reward,Material icon) { }
    private List<List<AchievementTier>> achievementLanes(String category) {
        return switch(category) {
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
    private List<String> achievementCategories() { return List.of("Mineur","Fermier","PvP","Chasseur","Divers","Constructeur","Alchimiste","Aventurier","Collectionneur","Marchand"); }
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
            "Récompense: "+next.reward()+" LPC",
            progress>=next.goal()?"Succès atteint — récompense en cours de validation.":"Continue pour débloquer le prochain palier.",
            "Après validation, ce succès sera remplacé ici par le suivant.");
    }
    private void openAchievementMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,"Larry | Succès");
        String[] categories={"Mineur","Fermier","PvP","Chasseur","Divers","Constructeur","Alchimiste","Aventurier","Collectionneur","Marchand"};
        Material[] icons={Material.IRON_PICKAXE,Material.WHEAT,Material.DIAMOND_SWORD,Material.ZOMBIE_HEAD,Material.NETHER_STAR,
            Material.BRICKS,Material.BREWING_STAND,Material.COMPASS,Material.CHEST,Material.EMERALD};
        int[] slots={10,12,14,16,28,30,32,34,20,24};
        for(int i=0;i<categories.length;i++) inv.setItem(slots[i],item(icons[i],categories[i],"Voir les 7 paliers de cette catégorie"));
        inv.setItem(49,item(Material.BARRIER,"Fermer"));
        LpcMenuTheme.frame(inv);
        p.openInventory(inv);
    }
    private void openAchievementCategory(Player p,String category) {
        List<List<AchievementTier>> lanes=achievementLanes(category);
        Inventory inv=Bukkit.createInventory(null,27,"Larry | "+category);
        int[] slots={10,12,14,16,22};
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
        int xp=db.getInt(path+".xp",0)+amount;
        while(xp>=level*100) { xp-=level*100; level++; }
        db.set(path+".xp",xp);
        db.set(path+".level",level);
    }
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
            if(db.getBoolean(claimPath,false)){msg(p,"Cette récompense a déjà été réclamée pour cette période.");return true;}
            if(!questReady(p,isWeekly)){msg(p,"Objectifs incomplets. Fais /quests pour voir ta progression.");return true;}
            double reward=isWeekly?400.0:100.0;
            if(!Double.isFinite(core.balance(p.getUniqueId())+reward)){msg(p,"Ton solde ne peut pas recevoir cette récompense.");return true;}
            db.set(claimPath,true);
            give(p,reward);core.recordTransaction(isWeekly?"QUEST_WEEKLY":"QUEST_DAILY",new UUID(0L,0L),p.getUniqueId(),reward);save();
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

    @EventHandler public void onQuestBlockPlace(BlockPlaceEvent e) {
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

    @EventHandler public void onQuestBlockBreak(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.getGameMode()==org.bukkit.GameMode.CREATIVE) return;
        var b=e.getBlock();
        String loc=b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();
        if(playerPlacedBlocks.remove(loc) || db.getBoolean("player-placed-blocks."+loc,false)) {
            db.set("player-placed-blocks."+loc,null);
            // Un bloc posé est un bloc supplémentaire à casser: on poursuit son comptage.
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
        if(killer!=null) {
            progressQuest(killer,"kills");
            if(e.getEntity() instanceof org.bukkit.entity.Monster) addAchievementProgress(killer,"monster-kills",1);
            if(e.getEntity() instanceof org.bukkit.entity.Wither) addAchievementProgress(killer,"wither-kills",1);
            if(e.getEntity() instanceof org.bukkit.entity.EnderDragon) addAchievementProgress(killer,"dragon-kills",1);
        }
    }
    @EventHandler(ignoreCancelled=true)
    public void onExploreBiome(PlayerMoveEvent e) {
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
    @EventHandler public void onItemPickup(EntityPickupItemEvent e) {
        if(e.getEntity() instanceof Player p)
            Bukkit.getScheduler().runTask(plugin,()->recordCollection(p));
    }
    @EventHandler public void onItemCraft(CraftItemEvent e) {
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
    @EventHandler public void onQuestFish(PlayerFishEvent e) {
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

    private String shopCategoryFor(String k){String n=k.toUpperCase(Locale.ROOT);if(n.matches(".*(DIAMOND|EMERALD|IRON|GOLD|COPPER|COAL|REDSTONE|LAPIS|QUARTZ|AMETHYST).*"))return "Minerais";if(n.matches(".*(LOG|WOOD|PLANK|STONE|DIRT|SAND|BRICK|GLASS|WOOL|CONCRETE|TERRACOTTA).*"))return "Construction";if(n.matches(".*(BREAD|APPLE|CARROT|POTATO|WHEAT|COOKED|FISH|MEAT|BEEF|PORK|CHICKEN|MELON|BERRY|COOKIE|CAKE).*"))return "Nourriture";if(n.matches(".*(SWORD|PICKAXE|AXE|SHOVEL|HOE|ARMOR|HELMET|CHESTPLATE|LEGGINGS|BOOTS|BOW|CROSSBOW|SHIELD).*"))return "Équipement";return "Divers";}
    private double shopSellPrice(String k){return Math.max(.01,db.getDouble("shop.sell-prices."+k,SHOP_PRICES.getOrDefault(k,.05)));}
    private double shopBuyPrice(String k){return Math.max(shopSellPrice(k)*1.5,db.getDouble("shop.buy-prices."+k,shopSellPrice(k)*2.8));}
    private void recalculateMarketPrices(){for(var e:SHOP.entrySet()){String k=e.getKey();double base=SHOP_PRICES.getOrDefault(k,.05);long sold=Math.max(0,db.getLong("market.window-sold."+k,0));double sell=Math.max(.01,base*(1-Math.min(.65,sold/512.0*.12)));db.set("shop.sell-prices."+k,Math.round(sell*100)/100.0);db.set("shop.buy-prices."+k,Math.round(sell*2.8*100)/100.0);List<Double> trend=db.getDoubleList("market.price-history."+k);trend.add(Math.round(sell*100)/100.0);if(trend.size()>24)trend=new ArrayList<>(trend.subList(trend.size()-24,trend.size()));db.set("market.price-history."+k,trend);db.set("market.window-sold."+k,0L);}long now=System.currentTimeMillis();db.set("market.last-refresh",now);db.set("market.next-refresh",now+MARKET_REFRESH_MILLIS);save();plugin.getLogger().info("Marché Larry recalculé; prochaine actualisation dans 6 heures.");}
    private String marketTimeLeft(){long ms=Math.max(0,db.getLong("market.next-refresh",System.currentTimeMillis())-System.currentTimeMillis());return(ms/3600000)+" h "+((ms/60000)%60)+" min";}
    private boolean sellShop(Player p,int amount){ItemStack hand=p.getInventory().getItemInMainHand();if(hand==null||hand.getType().isAir()||amount<1||amount>hand.getAmount()){msg(p,"Quantité invalide ou aucun objet vendable dans ta main.");return true;}String k=hand.getType().name().toLowerCase(Locale.ROOT);if(!SHOP.containsKey(k)||SHOP_BLOCKED.contains(hand.getType())){msg(p,"Cet objet ne peut pas être vendu.");return true;}if(hand.hasItemMeta()){msg(p,"Les objets renommés ou enchantés ne sont pas acceptés.");return true;}double total=Math.round(shopSellPrice(k)*amount*100)/100.0;if(total<=0||!Double.isFinite(core.balance(p.getUniqueId())+total)){msg(p,"Vente impossible.");return true;}int left=hand.getAmount()-amount;if(left<=0)p.getInventory().setItemInMainHand(null);else hand.setAmount(left);give(p,total);db.set("market.window-sold."+k,db.getLong("market.window-sold."+k,0L)+amount);db.set("market.sales-total",db.getLong("market.sales-total",0L)+amount);db.set("market.sales-count",db.getLong("market.sales-count",0L)+1);List<Map<String,Object>> h=new ArrayList<>();for(Map<?,?> old:db.getMapList("market.history")){Map<String,Object> r=new LinkedHashMap<>();for(var x:old.entrySet())r.put(String.valueOf(x.getKey()),x.getValue());h.add(r);}Map<String,Object> row=new LinkedHashMap<>();row.put("type","VENTE");row.put("item",k);row.put("amount",amount);row.put("total",total);row.put("player",p.getName());row.put("time",System.currentTimeMillis());h.add(0,row);if(h.size()>100)h=new ArrayList<>(h.subList(0,100));db.set("market.history",h);save();core.recordTransaction("SHOP_SELL",new UUID(0L,0L),p.getUniqueId(),total);msg(p,"Vente: "+amount+" x "+k+" pour "+total+" Larry. Prix mis à jour dans "+marketTimeLeft()+".");return true;}
    private void openShop(Player p){openShop(p,"Tous",0);}
    private void openShop(Player p,String category,int page){List<String> cats=List.of("Tous","Minerais","Construction","Nourriture","Équipement","Divers");if(!cats.contains(category))category="Tous";final String selected=category;List<Map.Entry<String,Material>> entries=SHOP.entrySet().stream().filter(e->selected.equals("Tous")||shopCategoryFor(e.getKey()).equals(selected)).toList();int pages=Math.max(1,(entries.size()+35)/36);page=Math.max(0,Math.min(page,pages-1));shopCategory.put(p.getUniqueId(),category);shopPage.put(p.getUniqueId(),page);Inventory inv=Bukkit.createInventory(null,54,"Larry | Boutique");for(int i=0;i<cats.size();i++)inv.setItem(i,item(Material.BOOK,cats.get(i),category.equals(cats.get(i))?"Sélectionnée":"Filtrer"));int from=page*36;for(int i=from;i<Math.min(entries.size(),from+36);i++){String k=entries.get(i).getKey();inv.setItem(9+i-from,item(entries.get(i).getValue(),k,"Achat: "+shopBuyPrice(k)+" Larry/u","Revente: "+shopSellPrice(k)+" Larry/u","Clic gauche: acheter 1","Clic droit: acheter 16","Maj-clic: acheter 64"));}inv.setItem(45,item(Material.ARROW,"Page précédente"));inv.setItem(47,item(Material.GOLD_INGOT,"Vendre l'objet en main","Clique pour vendre la pile tenue"));inv.setItem(49,item(Material.PAPER,"Page "+(page+1)+"/"+pages,"Catalogue: "+SHOP.size()+" objets","Prix recalculés toutes les 6 heures","Prochaine mise à jour: "+marketTimeLeft()));inv.setItem(51,item(Material.BARRIER,"Fermer"));inv.setItem(53,item(Material.ARROW,"Page suivante"));p.openInventory(inv);}
    private boolean buyShop(Player p,String key,int amount){if(!SHOP.containsKey(key)){msg(p,"Objet inconnu. Utilise /shop search <objet>.");return true;}if(amount<1||amount>64){msg(p,"Quantité autorisée: 1 à 64.");return true;}ItemStack purchase=new ItemStack(SHOP.get(key),amount);Inventory test=Bukkit.createInventory(null,36);test.setStorageContents(p.getInventory().getStorageContents());if(!test.addItem(purchase.clone()).isEmpty()){msg(p,"Inventaire insuffisant; aucun Larry débité.");return true;}double price=Math.round(shopBuyPrice(key)*amount*100)/100.0;if(!Double.isFinite(price)||price<=0||!charge(p,price)){msg(p,"Fonds Larry insuffisants. Prix: "+price+" Larry.");return true;}p.getInventory().addItem(purchase);List<Map<String,Object>> history=new ArrayList<>();for(Map<?,?> old:db.getMapList("market.history")){Map<String,Object> rowCopy=new LinkedHashMap<>();for(var x:old.entrySet())rowCopy.put(String.valueOf(x.getKey()),x.getValue());history.add(rowCopy);}Map<String,Object> buyRow=new LinkedHashMap<>();buyRow.put("type","ACHAT");buyRow.put("item",key);buyRow.put("amount",amount);buyRow.put("total",price);buyRow.put("player",p.getName());buyRow.put("time",System.currentTimeMillis());history.add(0,buyRow);if(history.size()>100)history=new ArrayList<>(history.subList(0,100));db.set("market.history",history);db.set("market.purchase-total",db.getLong("market.purchase-total",0L)+amount);save();core.recordTransaction("SHOP_BUY",p.getUniqueId(),new UUID(0L,0L),price);msg(p,"Achat effectué: "+amount+" x "+key+" pour "+price+" Larry.");return true;}
    private boolean shop(CommandSender sender,String[] args){if(args.length==0){Player p=player(sender);if(p!=null)openShop(p);return true;}if(args[0].equalsIgnoreCase("sell")){Player p=player(sender);if(p==null)return true;int n=p.getInventory().getItemInMainHand().getAmount();if(args.length>1)try{n=Integer.parseInt(args[1]);}catch(NumberFormatException ex){n=0;}return sellShop(p,n);}if(args.length>=2&&args[0].equalsIgnoreCase("buy")){Player p=player(sender);if(p==null)return true;int n=1;if(args.length>2)try{n=Integer.parseInt(args[2]);}catch(NumberFormatException ex){n=0;}return buyShop(p,args[1].toLowerCase(Locale.ROOT),n);}if(args.length<2||!args[0].equalsIgnoreCase("search")){msg(sender,"Usage: /shop | /shop search <objet> | /shop buy <clé> [quantité] | /shop sell [quantité]");return true;}String q=String.join(" ",Arrays.copyOfRange(args,1,args.length)).toLowerCase(Locale.ROOT);List<String> found=SHOP.entrySet().stream().filter(e->e.getKey().contains(q)||e.getValue().name().toLowerCase(Locale.ROOT).contains(q)).limit(30).map(e->e.getKey()+" — Achat "+shopBuyPrice(e.getKey())+" / vente "+shopSellPrice(e.getKey())+" Larry/u").toList();msg(sender,found.isEmpty()?"Aucun objet correspondant.":"Résultats: "+String.join(" | ",found));return true;}
    private boolean auction(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
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
        if(a[0].equalsIgnoreCase("buy")&&a.length==2) {
            String path="ah."+a[1];if(!db.contains(path)){msg(p,"Annonce introuvable.");return true;}
            double price=db.getDouble(path+".price");
            ItemStack stack=db.getItemStack(path+".stack");
            if(!Double.isFinite(price)||price<=0||stack==null){msg(p,"Annonce invalide; achat refusé.");return true;}
            Inventory simulated=Bukkit.createInventory(null,36);
            simulated.setStorageContents(p.getInventory().getStorageContents());
            if(!simulated.addItem(stack.clone()).isEmpty()){msg(p,"Inventaire insuffisant; aucun Larry débité.");return true;}
            UUID sellerId=UUID.fromString(db.getString(path+".sellerId"));
            if(sellerId.equals(p.getUniqueId())){msg(p,"Tu ne peux pas acheter ta propre annonce.");return true;}
            if(!Double.isFinite(core.balance(sellerId)+price)){msg(p,"Le solde du vendeur est saturé; achat refusé.");return true;}
            if(!charge(p,price)){msg(p,"Fonds insuffisants.");return true;}
            p.getInventory().addItem(stack);
            core.credit(sellerId,price);core.saveData();
            core.recordTransaction("AH_BUY",p.getUniqueId(),sellerId,price);
            db.set(path,null);save();msg(p,"Achat effectué.");return true;
        }
        msg(p,"/ah list | /ah sell <prix> | /ah buy <id>");return true;
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if(frozen.contains(e.getPlayer().getUniqueId())&&e.getTo()!=null&&(e.getFrom().getX()!=e.getTo().getX()||e.getFrom().getY()!=e.getTo().getY()||e.getFrom().getZ()!=e.getTo().getZ()))e.setTo(e.getFrom());
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        for(UUID id:vanished){Player v=Bukkit.getPlayer(id);if(v!=null&&!staff(e.getPlayer()))e.getPlayer().hidePlayer(plugin,v);}
    }
    @EventHandler public void onInvClick(InventoryClickEvent e) {
        String title=e.getView().getTitle();
        if(title.startsWith("Coffre clan:") && e.isShiftClick() && e.getClickedInventory()!=null
                && e.getClickedInventory().equals(e.getView().getBottomInventory())) { e.setCancelled(true); return; }
        if(title.startsWith("Coffre clan:") && e.getRawSlot()>=0 && e.getRawSlot()<e.getView().getTopInventory().getSize()) {
            String clan=title.substring("Coffre clan:".length());
            int level=Math.max(1,db.getInt(clanPath(clan)+".chest-level",1));
            int unlocked=Math.max(1,Math.min(54,level==1?1:(level-1)*9));
            if(e.getRawSlot()>=unlocked){e.setCancelled(true);if(e.getWhoClicked() instanceof Player p)msg(p,"Emplacement verrouillé. Utilise /clan chest upgrade.");}
            return;
        }
        if(title.equals("Larry | Succès")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer)) return;
            int[] slots={10,12,14,16,28,30,32,34,20,24};
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
        if(title.equals("Larry | Administration")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p)) return;
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
                default -> { }
            }
            return;
        }
        if(title.startsWith("Larry | Sélection ")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer))return;
            int slot=e.getRawSlot();
            String action=selectorAction.getOrDefault(viewer.getUniqueId(),title.substring("Larry | Sélection ".length()));
            if(slot==45){awaitingSelectorSearch.add(viewer.getUniqueId());viewer.closeInventory();msg(viewer,"Écris le pseudo à rechercher dans le chat (ou 'annuler').");return;}
            if(slot==47){selectorSearch.remove(viewer.getUniqueId());openPlayerSelector(viewer,action);return;}
            if(slot==49){viewer.closeInventory();return;}
            if(slot==53){openPlayerSelector(viewer,action);return;}
            if(slot<0||slot>=45||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            String targetName=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Player target=Bukkit.getPlayerExact(targetName);
            if(target==null) { msg(viewer,"Ce joueur n'est plus en ligne.");viewer.closeInventory();return; }
            if(title.endsWith("invsee")) viewer.openInventory(target.getInventory());
            else if(title.endsWith("op/deop")) openOpConfirm(viewer,target);
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
            if(!staff(staffPlayer)){pendingOpTarget.remove(staffPlayer.getUniqueId());staffPlayer.closeInventory();msg(staffPlayer,"Permission insuffisante.");return;}
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
                String path="ah."+key;
                if(!db.contains(path)){msg(buyer,"Cette annonce n'existe plus.");openAuction(buyer,ahPage.getOrDefault(buyer.getUniqueId(),0));return;}
                if(id(buyer).equals(db.getString(path+".sellerId"))) {
                    ItemStack stack=db.getItemStack(path+".stack");
                    if(stack!=null) {
                        Inventory simulated=Bukkit.createInventory(null,36);
                        simulated.setStorageContents(buyer.getInventory().getStorageContents());
                        if(!simulated.addItem(stack.clone()).isEmpty()){msg(buyer,"Inventaire plein; annonce conservée.");return;}
                        buyer.getInventory().addItem(stack);
                        db.set(path,null);save();msg(buyer,"Ton annonce a été retirée et l'objet rendu.");openAuction(buyer,ahPage.getOrDefault(buyer.getUniqueId(),0));return;
                    }
                }
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
        if(title.equals("Larry | Boutique")) {
            e.setCancelled(true);
            if(e.getRawSlot()>=e.getView().getTopInventory().getSize()) return;
            if(!(e.getWhoClicked() instanceof Player buyer)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            int slot=e.getRawSlot();String category=shopCategory.getOrDefault(buyer.getUniqueId(),"Tous");int page=shopPage.getOrDefault(buyer.getUniqueId(),0);
            if(slot>=0&&slot<=5){String next=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());if(List.of("Tous","Minerais","Construction","Nourriture","Équipement","Divers").contains(next))openShop(buyer,next,0);return;}
            if(slot==45){openShop(buyer,category,page-1);return;}
            if(slot==47){sellShop(buyer,buyer.getInventory().getItemInMainHand().getAmount());openShop(buyer,category,page);return;}
            if(slot==51){buyer.closeInventory();return;}
            if(slot==53){openShop(buyer,category,page+1);return;}
            if(slot<9||slot>44)return;
            String key=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            int amount=e.isShiftClick()?64:(e.isRightClick()?16:1);
            buyShop(buyer,key,amount);
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
                else {give(p,50);core.recordTransaction("QUIZ",new UUID(0L,0L),p.getUniqueId(),50);String scorePath="quiz-scores."+id(p);db.set(scorePath,db.getInt(scorePath,0)+1);msg(p,"Bonne réponse! +50 Larry. Score: "+db.getInt(scorePath)+" points.");}
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
            || title.startsWith("Coffre clan:");
        if (!lpcMenu) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler public void onClanChestDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        String title=e.getView().getTitle();
        if(!title.startsWith("Coffre clan:"))return;
        String clan=title.substring("Coffre clan:".length());
        int level=Math.max(1,db.getInt(clanPath(clan)+".chest-level",1));
        int unlocked=Math.max(1,Math.min(54,level==1?1:(level-1)*9));
        for(int rawSlot:e.getRawSlots()) {
            if(rawSlot>=0 && rawSlot<e.getView().getTopInventory().getSize() && rawSlot>=unlocked) {
                e.setCancelled(true);
                if(e.getWhoClicked() instanceof Player p)msg(p,"Emplacement verrouillé. Utilise /clan chest upgrade.");
                return;
            }
        }
    }
    @EventHandler public void onChestClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        String title=e.getView().getTitle();
        if(!title.startsWith("Coffre clan:"))return;
        String clan=title.substring("Coffre clan:".length());
        if(!Objects.equals(clanChestUsers.get(clan),e.getPlayer().getUniqueId()))return;
        ItemStack[] before=clanChestSnapshots.remove(clan),after=e.getInventory().getContents();
        db.set(clanPath(clan)+".chest",Arrays.asList(after));
        if(before!=null) {
            List<String> audit=db.getStringList(clanPath(clan)+".chest-audit");
            for(int slot=0;slot<after.length;slot++) {
                ItemStack a=before[slot],b=after[slot];
                if(Objects.equals(a,b))continue;
                audit.add(System.currentTimeMillis()+"|"+e.getPlayer().getUniqueId()+"|slot="+slot+"|"+(a==null?"vide":a.serialize().toString())+" -> "+(b==null?"vide":b.serialize().toString()));
            }
            if(audit.size()>500)audit=new ArrayList<>(audit.subList(audit.size()-500,audit.size()));
            db.set(clanPath(clan)+".chest-audit",audit);
        }
        clanChestUsers.remove(clan);save();
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId();
        frozen.remove(id);awaitingAhSearch.remove(id);pendingBountyTarget.remove(id);
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
                if(query.length()>16){msg(sender,"Pseudo trop long (16 caractères maximum).");if(action!=null)openPlayerSelector(sender,action);return;}
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
                if(query.length()>16){msg(sender,"Pseudo trop long.");openBountyMenu(sender);return;}
                bountySearch.put(senderId,query);openBountyMenu(sender);
            });
            return;
        }
        if(awaitingBountySearch.remove(senderId)) {
            e.setCancelled(true);
            String query=e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(query.equalsIgnoreCase("annuler")){msg(sender,"Recherche de prime annulée.");openBountyTargets(sender);return;}
                if(query.length()>16){msg(sender,"Pseudo trop long.");openBountyTargets(sender);return;}
                bountySearch.put(senderId,query);openBountyTargets(sender);
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
