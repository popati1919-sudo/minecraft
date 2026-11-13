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
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Modules sociaux, progression, commerce et modération de LarpersCoin. */
public final class LarpersFeatures implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final LarpersCoin core;
    private final YamlConfiguration db;
    private final File file;
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final SecureTradeManager tradeManager;
    private final Map<UUID, Long> lastQuiz = new HashMap<>();
    private final Map<UUID, Long> questClaims = new HashMap<>();
    private final Map<UUID, UUID> pendingBids = new HashMap<>();
    private static final String PREFIX = ChatColor.GOLD + "[LPC] " + ChatColor.RESET;
    private static final Map<String, Material> SHOP = new LinkedHashMap<>();

    static {
        SHOP.put("diamant", Material.DIAMOND); SHOP.put("fer", Material.IRON_INGOT);
        SHOP.put("or", Material.GOLD_INGOT); SHOP.put("émeraude", Material.EMERALD);
        SHOP.put("redstone", Material.REDSTONE); SHOP.put("lapis", Material.LAPIS_LAZULI);
        SHOP.put("obsidienne", Material.OBSIDIAN); SHOP.put("pain", Material.BREAD);
        SHOP.put("pomme", Material.APPLE); SHOP.put("bois", Material.OAK_LOG);
        SHOP.put("pierre", Material.STONE); SHOP.put("terre", Material.DIRT);
        SHOP.put("elytra", Material.ELYTRA); SHOP.put("netherite", Material.NETHERITE_INGOT);
    }

    public LarpersFeatures(JavaPlugin plugin) {
        this.plugin = plugin;
        this.core = (LarpersCoin) plugin;
        this.tradeManager = new SecureTradeManager(plugin, core);
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "features.yml");
        db = YamlConfiguration.loadConfiguration(file);
        for (String cmd : List.of("clan","quest","quests","quiz","shop","admin","vanish","invsee","freeze","staffchat","ah","trade")) {
            PluginCommand c = plugin.getCommand(cmd);
            if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Modules clans, quêtes, quiz, boutique et modération chargés.");
    }

    private void save() {
        try { db.save(file); } catch (IOException e) { plugin.getLogger().severe("features.yml: " + e.getMessage()); }
    }
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
    private void openAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "LPC | Administration");
        inv.setItem(10,item(Material.BOOK,"Gestion des joueurs","/invsee <joueur>"));
        inv.setItem(12,item(Material.ENDER_EYE,"Vanish","/vanish"));
        inv.setItem(14,item(Material.ICE,"Freeze","/freeze <joueur>"));
        inv.setItem(16,item(Material.GOLD_INGOT,"Économie","/eco give|take|set"));
        inv.setItem(22,item(Material.BARRIER,"Fermer"));
        p.openInventory(inv);
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
        if(a.length==0){msg(p,"/clan create <nom>, invite <joueur>, join <nom>, leave, info, bank, top, allies, level, chest, stats, leaderboard");return true;}
        String sub=a[0].toLowerCase(Locale.ROOT), own=clanOf(p.getUniqueId());
        switch(sub) {
            case "create" -> {
                if(a.length<2||!a[1].matches("[A-Za-z0-9_]{3,16}")){msg(p,"Nom: 3 à 16 caractères alphanumériques.");return true;}
                if(own!=null){msg(p,"Tu es déjà dans un clan.");return true;}
                String n=a[1].toLowerCase(Locale.ROOT), path=clanPath(n);
                if(db.contains(path)){msg(p,"Ce clan existe déjà.");return true;}
                db.set(path+".name",a[1]); db.set(path+".owner",id(p)); db.set(path+".members",List.of(id(p)));
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
                List<String> m=db.getStringList(clanPath(n)+".members");m.add(id(p));db.set(clanPath(n)+".members",m);db.set("clan-members."+id(p),n);db.set("invites."+id(p),null);save();msg(p,"Tu as rejoint le clan.");return true;
            }
            case "leave" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own); if(id(p).equals(db.getString(path+".owner"))){msg(p,"Le chef doit transférer le rôle ou supprimer le clan (fonction staff).");return true;}
                List<String> m=db.getStringList(path+".members");m.remove(id(p));db.set(path+".members",m);db.set("clan-members."+id(p),null);save();msg(p,"Tu as quitté le clan.");return true;
            }
            case "info","stats","level" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own);msg(p,db.getString(path+".name")+" | membres: "+db.getStringList(path+".members").size()+" | niveau "+db.getInt(path+".level",1)+" | XP "+db.getInt(path+".xp",0)+" | banque "+db.getDouble(path+".bank",0)+" LPC");return true;
            }
            case "bank" -> {
                if(own==null){msg(p,"Tu n'as pas de clan.");return true;}
                String path=clanPath(own)+".bank";
                if(a.length==1){msg(p,"Banque du clan: "+db.getDouble(path)+" LPC. /clan bank deposit|withdraw <montant>");return true;}
                if(a.length!=3){msg(p,"Usage: /clan bank deposit|withdraw <montant>");return true;}
                double v=Double.parseDouble(a[2]);if(v<=0){msg(p,"Montant invalide.");return true;}
                if(a[1].equalsIgnoreCase("deposit")){if(!charge(p,v)){msg(p,"Fonds insuffisants.");return true;}db.set(path,db.getDouble(path)+v);}
                else if(a[1].equalsIgnoreCase("withdraw")){if(!id(p).equals(db.getString(clanPath(own)+".owner"))||db.getDouble(path)<v){msg(p,"Retrait refusé (chef seulement ou fonds insuffisants).");return true;}db.set(path,db.getDouble(path)-v);give(p,v);}
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
                if(!id(p).equals(db.getString(clanPath(own)+".owner"))||a.length!=3){msg(p,"Chef: /clan allies add|remove <clan>");return true;}
                String other=a[2].toLowerCase(Locale.ROOT);if(!db.contains(clanPath(other))||other.equals(own)){msg(p,"Clan invalide.");return true;}
                List<String> allies=db.getStringList(ap);
                if(a[1].equalsIgnoreCase("add")){if(!allies.contains(other))allies.add(other);}
                else if(a[1].equalsIgnoreCase("remove"))allies.remove(other);
                else {msg(p,"Choisis add ou remove.");return true;}
                db.set(ap,allies);save();msg(p,"Alliances mises à jour.");return true;
            }
            case "top","leaderboard" -> {
                var sec=db.getConfigurationSection("clans");if(sec==null){msg(p,"Aucun clan.");return true;}
                List<String> names=new ArrayList<>(sec.getKeys(false));names.sort(Comparator.comparingDouble((String n)->db.getDouble(clanPath(n)+".bank")).reversed());
                msg(p,"Classement des clans (banque):");int i=1;for(String n:names){if(i>10)break;msg(p,i+". "+db.getString(clanPath(n)+".name")+" — "+db.getDouble(clanPath(n)+".bank")+" LPC");i++;}return true;
            }
            default -> {msg(p,"Sous-commande inconnue. /clan pour l'aide.");return true;}
        }
    }

    private boolean quests(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        if(a.length>0&&a[0].equalsIgnoreCase("claim")) {
            long now=System.currentTimeMillis(), prev=db.getLong("quest-daily."+id(p),0L);
            if(now-prev<86_400_000L){msg(p,"Récompense déjà réclamée aujourd'hui.");return true;}
            db.set("quest-daily."+id(p),now);give(p,150);save();msg(p,"Quête quotidienne validée: +150 LPC. (Les objectifs détaillés seront suivis en jeu dans une prochaine version.)");return true;
        }
        msg(p,"Quêtes quotidiennes et hebdomadaires: /quests claim — récompense quotidienne 150 LPC; reset 24 h.");msg(p,"Les objectifs de récolte/combats et le suivi hebdomadaire ne sont pas encore activés.");return true;
    }
    private boolean quiz(CommandSender s,String[] a) {
        Player p=player(s);if(p==null)return true;
        long now=System.currentTimeMillis();if(a.length==0&&now-lastQuiz.getOrDefault(p.getUniqueId(),0L)<60_000L){msg(p,"Attends une minute avant un autre quiz.");return true;}
        String[] q={"Quel minerai permet de fabriquer une boussole?","Combien de côtés possède un bloc standard?","Quel mob explose près du joueur?"};
        String[] answers={"fer","6","creeper"};int n=new Random().nextInt(q.length);
        if(a.length==0){msg(p,"Quiz Minecraft: "+q[n]);msg(p,"Réponds avec /quiz <réponse>.");db.set("quiz."+id(p)+".answer",answers[n]);db.set("quiz."+id(p)+".time",now);save();lastQuiz.put(p.getUniqueId(),now);return true;}
        String expected=db.getString("quiz."+id(p)+".answer","");
        if(expected.isEmpty()){msg(p,"Commence par /quiz.");return true;}
        if(String.join(" ",a).equalsIgnoreCase(expected)){give(p,50);msg(p,"Bonne réponse! +50 LPC.");}else msg(p,"Pas cette fois. Réponse: "+expected);
        db.set("quiz."+id(p)+".answer",null);save();return true;
    }
    private boolean shop(CommandSender s,String[] a) {
        if(a.length<2||!a[0].equalsIgnoreCase("search")){msg(s,"Usage: /shop search <objet>");return true;}
        String query=String.join(" ",Arrays.copyOfRange(a,1,a.length)).toLowerCase(Locale.ROOT);
        List<String> found=SHOP.entrySet().stream().filter(e->e.getKey().contains(query)||e.getValue().name().toLowerCase(Locale.ROOT).contains(query)).map(e->e.getKey()+" — "+e.getValue().name()).toList();
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
        if(e.getView().getTitle().equals("LPC | Administration")) {
            e.setCancelled(true);
            if(e.getCurrentItem()!=null&&e.getCurrentItem().getType()==Material.BARRIER)e.getWhoClicked().closeInventory();
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
        if(c.getName().equalsIgnoreCase("clan")&&a.length==1)return List.of("create","invite","join","leave","info","bank","top","allies","level","chest","stats","leaderboard");
        if(c.getName().equalsIgnoreCase("ah")&&a.length==1)return List.of("list","sell","buy");
        if(c.getName().equalsIgnoreCase("shop")&&a.length==1)return List.of("search");
        return List.of();
    }
}
