package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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
    private final Set<UUID> vanished = new HashSet<>();
    private final SecureTradeManager tradeManager;
    private final Map<UUID, Long> lastQuiz = new HashMap<>();
    private final Map<UUID, Long> questClaims = new HashMap<>();
    private final Map<UUID, UUID> pendingBids = new HashMap<>();
    private static final String PREFIX = ChatColor.GOLD + "[LPC] " + ChatColor.RESET;
    private static final Map<String, Material> SHOP = new LinkedHashMap<>();
    private static final Map<String, Double> SHOP_PRICES = new LinkedHashMap<>();
    private boolean saveQueued = false;

    static {
        SHOP.put("diamant", Material.DIAMOND); SHOP.put("fer", Material.IRON_INGOT);
        SHOP.put("or", Material.GOLD_INGOT); SHOP.put("émeraude", Material.EMERALD);
        SHOP.put("redstone", Material.REDSTONE); SHOP.put("lapis", Material.LAPIS_LAZULI);
        SHOP.put("obsidienne", Material.OBSIDIAN); SHOP.put("pain", Material.BREAD);
        SHOP.put("pomme", Material.APPLE); SHOP.put("bois", Material.OAK_LOG);
        SHOP.put("pierre", Material.STONE); SHOP.put("terre", Material.DIRT);
        SHOP.put("elytra", Material.ELYTRA); SHOP.put("netherite", Material.NETHERITE_INGOT);
        SHOP_PRICES.put("diamant",250.0); SHOP_PRICES.put("fer",25.0);
        SHOP_PRICES.put("or",40.0); SHOP_PRICES.put("émeraude",100.0);
        SHOP_PRICES.put("redstone",10.0); SHOP_PRICES.put("lapis",12.0);
        SHOP_PRICES.put("obsidienne",35.0); SHOP_PRICES.put("pain",5.0);
        SHOP_PRICES.put("pomme",4.0); SHOP_PRICES.put("bois",8.0);
        SHOP_PRICES.put("pierre",2.0); SHOP_PRICES.put("terre",1.0);
        SHOP_PRICES.put("elytra",5000.0); SHOP_PRICES.put("netherite",1500.0);
    }

    public LarpersFeatures(JavaPlugin plugin) {
        this.plugin = plugin;
        this.core = (LarpersCoin) plugin;
        this.tradeManager = new SecureTradeManager(plugin, core);
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "features.yml");
        db = YamlConfiguration.loadConfiguration(file);
        for (var entry : SHOP_PRICES.entrySet()) {
            String pricePath="shop.prices."+entry.getKey();
            if (!db.contains(pricePath)) db.set(pricePath,entry.getValue());
        }
        save();
        for (String cmd : List.of("clan","quest","quests","quiz","shop","admin","vanish","invsee","freeze","staffchat","ah","trade")) {
            PluginCommand c = plugin.getCommand(cmd);
            if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::save,1200L,1200L);
        plugin.getLogger().info("Modules clans, quêtes, quiz, boutique et modération chargés.");
    }

    private void save() {
        try { db.save(file); } catch (IOException e) { plugin.getLogger().severe("features.yml: " + e.getMessage()); }
    }
    public void shutdown() { save(); }
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
    private void openClanMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"LPC | Clan");
        inv.setItem(10,item(Material.BOOK,"Informations","/clan info"));
        inv.setItem(12,item(Material.GOLD_INGOT,"Banque","/clan bank"));
        inv.setItem(14,item(Material.CHEST,"Coffre partagé","/clan chest"));
        inv.setItem(16,item(Material.EMERALD,"Classement","/clan top"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        p.openInventory(inv);
    }
    private void openQuestMenu(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"LPC | Quêtes");
        inv.setItem(11,item(Material.CLOCK,"Quête quotidienne","Récompense: 150 LPC","/quests claim"));
        inv.setItem(15,item(Material.DIAMOND,"Quête hebdomadaire","Récompense: 800 LPC","/quests weekly claim"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        p.openInventory(inv);
    }
    private void openAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "LPC | Administration");
        inv.setItem(10,item(Material.BOOK,"Gestion des joueurs","/invsee <joueur>"));
        inv.setItem(12,item(Material.ENDER_EYE,"Vanish","/vanish"));
        inv.setItem(14,item(Material.ICE,"Freeze","/freeze <joueur>"));
        inv.setItem(16,item(Material.GOLD_INGOT,"Économie","/eco give|take|set"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        p.openInventory(inv);
    }
    private void openPlayerSelector(Player viewer,String action) {
        Inventory inv=Bukkit.createInventory(null,54,"LPC | Sélection "+action);
        int slot=0;
        for(Player target:Bukkit.getOnlinePlayers()) {
            if(target.equals(viewer) || slot>=54) continue;
            inv.setItem(slot++,item(Material.PAPER,target.getName(),"Clique pour "+action));
        }
        if(slot==0) msg(viewer,"Aucun autre joueur en ligne.");
        else viewer.openInventory(inv);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        try {
            switch (c) {
                case "clan" -> { return clan(sender,a); }
                case "quest", "quests" -> { return quests(sender,a); }
                case "quiz" -> { return quiz(sender,a); }
                case "shop" -> { return shop(sender,a); }
                case "admin" -> { Player p=player(sender); if(p!=null){if(!staff(p))msg(p,"Permission insuffisante.");else openAdmin(p);} return true; }
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
                case "trade" -> { return tradeManager.handle(sender,a); }
                default -> { return false; }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Erreur /"+c+": "+ex.getMessage()); msg(sender,"Erreur de commande. Consulte la console."); return true;
        }
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
                String path=clanPath(own); if(!id(p).equals(db.getString(path+".owner"))){msg(p,"Seul le chef peut inviter.");return true;}
                Player t=Bukkit.getPlayerExact(a[1]); if(t==null){msg(p,"Joueur introuvable.");return true;}
                db.set("invites."+id(t),own); save(); msg(t,"Invitation au clan "+db.getString(path+".name")+". Fais /clan join "+own); msg(p,"Invitation envoyée."); return true;
            }
            case "join" -> {
                if(a.length!=2||own!=null){msg(p,"Usage : /clan join <nom> (sans clan)");return true;}
                String n=a[1].toLowerCase(Locale.ROOT);
                if(!n.equals(db.getString("invites."+id(p)))||!db.contains(clanPath(n))){msg(p,"Aucune invitation valide pour ce clan.");return true;}
                List<String> m=db.getStringList(clanPath(n)+".members");m.add(id(p));db.set(clanPath(n)+".members",m);db.set("clan-members."+id(p),n);db.set(clanPath(n)+".roles."+id(p),"member");db.set("invites."+id(p),null);save();msg(p,"Tu as rejoint le clan.");return true;
            }
            case "leave" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own); if(id(p).equals(db.getString(path+".owner"))){msg(p,"Le chef doit transférer le rôle ou supprimer le clan (fonction staff).");return true;}
                List<String> m=db.getStringList(path+".members");m.remove(id(p));db.set(path+".members",m);db.set(path+".roles."+id(p),null);db.set("clan-members."+id(p),null);save();msg(p,"Tu as quitté le clan.");return true;
            }
            case "info","stats","level" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own);int level=Math.max(1,db.getInt(path+".level",1));
                msg(p,db.getString(path+".name")+" | membres: "+db.getStringList(path+".members").size()+" | niveau "+level+" | XP "+db.getInt(path+".xp",0)+"/"+(level*100)+" | banque "+db.getDouble(path+".bank",0)+" LPC");
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
                if(a.length==1){msg(p,"Banque du clan: "+db.getDouble(path)+" LPC. /clan bank deposit|withdraw <montant>");return true;}
                if(a.length!=3){msg(p,"Usage: /clan bank deposit|withdraw <montant>");return true;}
                double v=Double.parseDouble(a[2]);if(v<=0){msg(p,"Montant invalide.");return true;}
                if(a[1].equalsIgnoreCase("deposit")){if(!charge(p,v)){msg(p,"Fonds insuffisants.");return true;}db.set(path,db.getDouble(path)+v);}
                else if(a[1].equalsIgnoreCase("withdraw")){if(!clanLeaderOrOfficer(p,own)||db.getDouble(path)<v){msg(p,"Retrait refusé (chef/officier seulement ou fonds insuffisants).");return true;}db.set(path,db.getDouble(path)-v);give(p,v);}
                else {msg(p,"Choisis deposit ou withdraw.");return true;} save();msg(p,"Opération effectuée.");return true;
            }
            case "chest" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                Inventory inv=Bukkit.createInventory(null,54,"Coffre clan: "+own);
                List<ItemStack> contents=(List<ItemStack>)db.getList(clanPath(own)+".chest");
                if(contents!=null)for(int i=0;i<Math.min(54,contents.size());i++)inv.setItem(i,contents.get(i));
                p.openInventory(inv);msg(p,"Coffre partagé: sauvegarde du contenu à la fermeture.");return true;
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
                msg(p,"Classement des clans (banque):");int i=1;for(String n:names){if(i>10)break;msg(p,i+". "+db.getString(clanPath(n)+".name")+" — "+db.getDouble(clanPath(n)+".bank")+" LPC");i++;}return true;
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
        int[] goals=weekly?new int[]{100,50,25,15}:new int[]{20,10,5,3};
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
            db.set(claimPath,true);
            double reward=isWeekly?800.0:150.0;
            give(p,reward);save();
            msg(p,"Quête "+(isWeekly?"hebdomadaire":"quotidienne")+" terminée : +"+reward+" LPC.");
            return true;
        }
        String period=weekly?weeklyQuestKey():dailyQuestKey();
        int[] goals=weekly?new int[]{100,50,25,15}:new int[]{20,10,5,3};
        String[] names={"blocks","crops","kills","fish"};
        String[] labels={"blocs cassés","cultures récoltées","créatures vaincues","poissons pêchés"};
        msg(p,(weekly?"Quêtes hebdomadaires ":"Quêtes quotidiennes ")+period+":");
        for(int i=0;i<names.length;i++) msg(p,labels[i]+": "+Math.min(db.getInt(questPath(weekly,p,names[i]),0),goals[i])+"/"+goals[i]);
        msg(p,"Récompense: "+(weekly?"800":"150")+" LPC — valide un objectif pour réclamer.");
        msg(p,weekly?"/quests weekly claim":"/quests claim");
        return true;
    }

    @EventHandler public void onQuestBlockPlace(BlockPlaceEvent e) {
        var b=e.getBlockPlaced();
        playerPlacedBlocks.add(b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ());
    }
    @EventHandler public void onQuestBlockBreak(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.getGameMode()==org.bukkit.GameMode.CREATIVE) return;
        var b=e.getBlock();
        String loc=b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();
        if(playerPlacedBlocks.remove(loc)) return;
        if(b.getBlockData() instanceof Ageable age && age.getAge()>=age.getMaximumAge()) {
            progressQuest(p,"crops");
        } else if(!b.getType().isAir() && b.getType()!=Material.BEDROCK) {
            progressQuest(p,"blocks");
        }
    }
    @EventHandler public void onQuestMobKill(EntityDeathEvent e) {
        Player killer=e.getEntity().getKiller();
        if(killer!=null) progressQuest(killer,"kills");
    }
    @EventHandler public void onQuestFish(PlayerFishEvent e) {
        if(e.getState()==PlayerFishEvent.State.CAUGHT_FISH) progressQuest(e.getPlayer(),"fish");
    }
    private void openQuiz(Player p,String question,String[] choices) {
        Inventory inv=Bukkit.createInventory(null,27,"LPC | Quiz");
        inv.setItem(4,item(Material.BOOK,"Question",question,"Choisis une réponse ci-dessous."));
        inv.setItem(11,item(Material.PAPER,choices[0],"Réponse A"));
        inv.setItem(13,item(Material.PAPER,choices[1],"Réponse B"));
        inv.setItem(15,item(Material.PAPER,choices[2],"Réponse C"));
        p.openInventory(inv);
    }
    private boolean quiz(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        long now=System.currentTimeMillis();
        long previous=db.getLong("quiz."+id(p)+".time",0L);
        if(a.length==0&&now-previous<60_000L){msg(p,"Attends une minute avant un autre quiz.");return true;}
        String[] q={"Quel minerai permet de fabriquer une boussole?","Combien de faces possède un bloc standard?","Quel mob explose près du joueur?"};
        String[] answers={"fer","6","creeper"};
        String[][] choices={{"fer","cuivre","or"},{"4","6","8"},{"creeper","zombie","squelette"}};
        if(a.length==0) {
            int n=new Random().nextInt(q.length);
            db.set("quiz."+id(p)+".answer",answers[n]);db.set("quiz."+id(p)+".time",now);save();
            openQuiz(p,q[n],choices[n]);return true;
        }
        String expected=db.getString("quiz."+id(p)+".answer","");
        if(expected.isEmpty()){msg(p,"Commence par /quiz.");return true;}
        if(String.join(" ",a).equalsIgnoreCase(expected)){give(p,50);msg(p,"Bonne réponse! +50 LPC.");}
        else msg(p,"Pas cette fois. Réponse: "+expected);
        db.set("quiz."+id(p)+".answer",null);save();return true;
    }
    private void openShop(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,"LPC | Boutique");
        int slot=0;
        for(var entry:SHOP.entrySet()) {
            if(slot>=54) break;
            double price=db.getDouble("shop.prices."+entry.getKey(),SHOP_PRICES.get(entry.getKey()));
            inv.setItem(slot++,item(entry.getValue(),entry.getKey(),"Prix: "+price+" LPC","Clique pour acheter 1"));
        }
        p.openInventory(inv);
    }
    private boolean buyShop(Player p,String key,int amount) {
        if(!SHOP.containsKey(key)) { msg(p,"Objet inconnu. Utilise /shop search <objet>.");return true; }
        if(amount<1||amount>64) { msg(p,"Quantité autorisée: 1 à 64.");return true; }
        Material material=SHOP.get(key);
        ItemStack purchase=new ItemStack(material,amount);
        Inventory simulated=Bukkit.createInventory(null,36);
        simulated.setStorageContents(p.getInventory().getStorageContents());
        if(!simulated.addItem(purchase.clone()).isEmpty()) { msg(p,"Inventaire insuffisant; aucun LPC débité.");return true; }
        double price=db.getDouble("shop.prices."+key,SHOP_PRICES.getOrDefault(key,0.0))*amount;
        if(!Double.isFinite(price)||price<=0||!charge(p,price)) { msg(p,"Fonds LPC insuffisants. Prix: "+price+" LPC.");return true; }
        p.getInventory().addItem(purchase);
        msg(p,"Achat effectué: "+amount+" x "+material.name()+" pour "+price+" LPC.");
        return true;
    }
    private boolean shop(CommandSender s,String[] a) {
        if(a.length==0) { Player p=player(s);if(p!=null)openShop(p);return true; }
        if(a.length>=2&&a[0].equalsIgnoreCase("buy")) {
            Player p=player(s);if(p==null)return true;
            int amount=1;
            if(a.length>=3) try { amount=Integer.parseInt(a[2]); } catch(NumberFormatException ex) { amount=0; }
            return buyShop(p,a[1].toLowerCase(Locale.ROOT),amount);
        }
        if(a.length<2||!a[0].equalsIgnoreCase("search")){msg(s,"Usage: /shop | /shop search <objet> | /shop buy <clé> [quantité]");return true;}
        String query=String.join(" ",Arrays.copyOfRange(a,1,a.length)).toLowerCase(Locale.ROOT);
        List<String> found=SHOP.entrySet().stream().filter(e->e.getKey().contains(query)||e.getValue().name().toLowerCase(Locale.ROOT).contains(query)).map(e->e.getKey()+" — "+e.getValue().name()+" ("+db.getDouble("shop.prices."+e.getKey(),SHOP_PRICES.get(e.getKey()))+" LPC/u)").toList();
        msg(s,found.isEmpty()?"Aucun objet correspondant dans le catalogue.":"Résultats: "+String.join(" | ",found));return true;
    }
    private boolean auction(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        if(a.length==0||a[0].equalsIgnoreCase("list")) {
            var sec=db.getConfigurationSection("ah");if(sec==null){msg(p,"Aucune annonce. /ah sell <prix>");return true;}
            msg(p,"Hôtel des ventes:");for(String key:sec.getKeys(false)){String path="ah."+key;msg(p,key+": "+db.getString(path+".item")+" x"+db.getInt(path+".amount")+" — "+db.getDouble(path+".price")+" LPC par "+db.getString(path+".seller"));}return true;
        }
        if(a[0].equalsIgnoreCase("sell")) {
            if(a.length!=2){msg(p,"Usage: /ah sell <prix>");return true;}
            double price=Double.parseDouble(a[1]);ItemStack hand=p.getInventory().getItemInMainHand();
            if(price<=0||hand.getType()==Material.AIR){msg(p,"Prix invalide ou main vide.");return true;}
            String key=UUID.randomUUID().toString().substring(0,8);db.set("ah."+key+".seller",p.getName());db.set("ah."+key+".sellerId",id(p));db.set("ah."+key+".item",hand.getType().name());db.set("ah."+key+".amount",hand.getAmount());db.set("ah."+key+".price",price);db.set("ah."+key+".stack",hand.clone());p.getInventory().setItemInMainHand(null);save();msg(p,"Annonce créée: /ah buy "+key);return true;
        }
        if(a[0].equalsIgnoreCase("buy")&&a.length==2) {
            String path="ah."+a[1];if(!db.contains(path)){msg(p,"Annonce introuvable.");return true;}
            double price=db.getDouble(path+".price");if(!charge(p,price)){msg(p,"Fonds insuffisants.");return true;}
            ItemStack stack=db.getItemStack(path+".stack");int empty=p.getInventory().firstEmpty();
            if(stack==null||empty<0){give(p,price);msg(p,"Il faut un emplacement libre; achat annulé.");return true;}
            p.getInventory().setItem(empty,stack);
            UUID sellerId=UUID.fromString(db.getString(path+".sellerId"));core.credit(sellerId,price);core.saveData();db.set(path,null);save();msg(p,"Achat effectué.");return true;
        }
        msg(p,"/ah list | /ah sell <prix> | /ah buy <id>");return true;
    }
    private boolean trade(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        if(a.length!=1){msg(p,"Usage: /trade <joueur> — demande d'échange (confirmation des deux joueurs requise).");return true;}
        Player t=Bukkit.getPlayerExact(a[0]);if(t==null||t.equals(p)){msg(p,"Joueur invalide.");return true;}
        tradeInvites.put(t.getUniqueId(),p.getUniqueId());msg(t,p.getName()+" souhaite échanger. Réponds avec /trade "+p.getName()+" pour indiquer ton intérêt.");msg(p,"Demande envoyée. Le transfert sécurisé n'est pas encore disponible dans cette version.");return true;
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if(frozen.contains(e.getPlayer().getUniqueId())&&e.getTo()!=null&&(e.getFrom().getX()!=e.getTo().getX()||e.getFrom().getZ()!=e.getTo().getZ()))e.setTo(e.getFrom());
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        for(UUID id:vanished){Player v=Bukkit.getPlayer(id);if(v!=null&&!staff(e.getPlayer()))e.getPlayer().hidePlayer(plugin,v);}
    }
    @EventHandler public void onInvClick(InventoryClickEvent e) {
        String title=e.getView().getTitle();
        if(title.equals("LPC | Administration")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p)) return;
            switch(e.getRawSlot()) {
                case 10 -> openPlayerSelector(p,"invsee");
                case 12 -> { p.closeInventory(); p.performCommand("vanish"); }
                case 14 -> openPlayerSelector(p,"freeze");
                case 16 -> msg(p,"Économie: /eco give <joueur> <montant>, /eco take <joueur> <montant>, /eco set <joueur> <montant>.");
                case 22 -> p.closeInventory();
                default -> { }
            }
            return;
        }
        if(title.equals("LPC | Sélection invsee")||title.equals("LPC | Sélection freeze")) {
            e.setCancelled(true);
            if(e.getRawSlot()>=e.getView().getTopInventory().getSize()) return;
            if(!(e.getWhoClicked() instanceof Player viewer)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            String targetName=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Player target=Bukkit.getPlayerExact(targetName);
            if(target==null) { msg(viewer,"Ce joueur n'est plus en ligne.");viewer.closeInventory();return; }
            if(title.endsWith("invsee")) viewer.openInventory(target.getInventory());
            else { viewer.closeInventory();viewer.performCommand("freeze "+target.getName()); }
        }
        if(title.equals("LPC | Boutique")) {
            e.setCancelled(true);
            if(e.getRawSlot()>=e.getView().getTopInventory().getSize()) return;
            if(!(e.getWhoClicked() instanceof Player buyer)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            String key=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            buyShop(buyer,key,1);
        }
        if(title.equals("LPC | Quiz")) {
            e.setCancelled(true);
            if(e.getRawSlot()>=e.getView().getTopInventory().getSize()) return;
            if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta()) return;
            if(e.getRawSlot()!=11&&e.getRawSlot()!=13&&e.getRawSlot()!=15) return;
            String expected=db.getString("quiz."+id(p)+".answer","");
            String selected=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            if(expected.isEmpty()){msg(p,"Cette question est expirée. Fais /quiz.");p.closeInventory();return;}
            if(selected.equalsIgnoreCase(expected)){give(p,50);msg(p,"Bonne réponse! +50 LPC.");}
            else msg(p,"Mauvaise réponse. La bonne réponse était: "+expected+".");
            db.set("quiz."+id(p)+".answer",null);save();p.closeInventory();
            return;
        }
        if(title.equals("LPC | Clan")||title.equals("LPC | Quêtes")) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player viewer)) return;
            if(e.getRawSlot()==22) { viewer.closeInventory();return; }
            if(title.equals("LPC | Clan")) {
                String command=switch(e.getRawSlot()) { case 10 -> "clan info";case 12 -> "clan bank";case 14 -> "clan chest";case 16 -> "clan top";default -> null; };
                if(command!=null){viewer.closeInventory();viewer.performCommand(command);}
            } else {
                String command=switch(e.getRawSlot()) { case 11 -> "quests claim";case 15 -> "quests weekly claim";default -> null; };
                if(command!=null){viewer.closeInventory();viewer.performCommand(command);}
            }
        }
    }
    @EventHandler public void onChestClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        String title=e.getView().getTitle();
        if(!title.startsWith("Coffre clan:"))return;
        String clan=title.substring("Coffre clan:".length());
        db.set(clanPath(clan)+".chest",Arrays.asList(e.getInventory().getContents()));save();
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { frozen.remove(e.getPlayer().getUniqueId()); }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a) {
        if(c.getName().equalsIgnoreCase("clan")&&a.length==1)return List.of("create","invite","join","leave","info","role","bank","top","allies","level","chest","stats","leaderboard");
        if(c.getName().equalsIgnoreCase("ah")&&a.length==1)return List.of("list","sell","buy");
        if(c.getName().equalsIgnoreCase("shop")&&a.length==1)return List.of("search","buy");
        return List.of();
    }
}
