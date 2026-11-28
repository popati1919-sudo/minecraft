package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

public final class SecureTradeManager implements Listener {
    private static final String PREFIX = ChatColor.GOLD + "[Larry] " + ChatColor.RESET;
    private static final String TITLE = ChatColor.DARK_AQUA + "Larry | Échange";
    private static final String MONEY_TITLE = ChatColor.DARK_AQUA + "Larry | Montant";
    private static final int MAX_DISTANCE_SQUARED = 100;
    private static final int[] OWN_SLOTS = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20};
    private static final int[] OTHER_SLOTS = {27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47};
    private final JavaPlugin plugin;
    private final LarpersCoin economy;
    private final Consumer<UUID> completedTrade;
    private final Map<UUID, UUID> invitations = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> opening = new HashSet<>();
    private final Set<UUID> moneyInput = new HashSet<>();
    private final Map<UUID,UUID> invitationTokens=new HashMap<>();
    private static final class TradeHolder implements org.bukkit.inventory.InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory(){return inventory;}
    }
    private boolean tradeView(InventoryView view){return view.getTopInventory().getHolder() instanceof TradeHolder;}

    private static final class Offer {
        final List<ItemStack> items = new ArrayList<>();
        final List<Integer> slots=new ArrayList<>();
        boolean confirmed;
        double money;
    }
    private static final class Session {
        final UUID first, second;
        final String id=UUID.randomUUID().toString();
        final Offer a = new Offer(), b = new Offer();
        boolean countdown;
        long generation;
        Session(UUID first, UUID second) { this.first=first; this.second=second; }
        Offer offer(UUID id) { return first.equals(id) ? a : b; }
        UUID other(UUID id) { return first.equals(id) ? second : first; }
    }

    public SecureTradeManager(JavaPlugin plugin, LarpersCoin economy, Consumer<UUID> completedTrade) {
        this.plugin=plugin; this.economy=economy; this.completedTrade=completedTrade;
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    private void msg(CommandSender sender,String message) { sender.sendMessage(PREFIX+message); }
    private Session session(UUID id) { return sessions.get(id); }
    private void clear(Session s) { sessions.remove(s.first); sessions.remove(s.second); }
    private boolean withinRange(Player a, Player b) {
        return a != null && b != null && a.getWorld().equals(b.getWorld())
                && a.getLocation().distanceSquared(b.getLocation()) <= MAX_DISTANCE_SQUARED;
    }
    private ItemStack named(Material material,String name,String... lore) {
        ItemStack item=new ItemStack(material); ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW+name);
        if(lore.length>0) meta.setLore(Arrays.stream(lore).map(x->ChatColor.GRAY+x).toList());
        item.setItemMeta(meta); return item;
    }
    private void closeViews(Session s) {
        for(UUID id:List.of(s.first,s.second)) {
            Player p=Bukkit.getPlayer(id);
            if(p!=null && (tradeView(p.getOpenInventory()) || moneyInput.contains(id))) {
                if(moneyInput.remove(id))p.getOpenInventory().getTopInventory().clear();
                p.closeInventory();
            }
            moneyInput.remove(id);
        }
    }
    private void cancel(Session s,String reason) {
        clear(s);
        Player a=Bukkit.getPlayer(s.first), b=Bukkit.getPlayer(s.second);
        // Offers are snapshots only; cancellation never transfers or destroys real items.
        closeViews(s);
        if(a!=null) msg(a,"Échange annulé : "+reason+". Tes objets sont restés dans ton inventaire.");
        if(b!=null) msg(b,"Échange annulé : "+reason+". Tes objets sont restés dans ton inventaire.");
    }
    public boolean handle(CommandSender sender,String[] args) {
        if(!(sender instanceof Player p)){msg(sender,"Cette commande doit être utilisée en jeu.");return true;}
        if(args.length==1 && args[0].equalsIgnoreCase("accept")) {
            UUID from=invitations.get(p.getUniqueId());
            if(from==null){msg(p,"Tu n'as aucune invitation. Demande à un joueur : /trade "+p.getName()+".");return true;}
            Player requester=Bukkit.getPlayer(from);
            if(requester==null || !withinRange(requester,p)) {
                invitations.remove(p.getUniqueId());
                if(requester!=null)msg(requester,"Échange annulé : vous devez être dans le même monde et à 10 blocs maximum.");
                msg(p,"Invitation expirée ou trop loin. Rapproche-toi à 10 blocs maximum.");
                return true;
            }
            if(session(from)!=null || session(p.getUniqueId())!=null) {
                invitations.remove(p.getUniqueId());
                msg(p,"Un des joueurs participe déjà à un échange."); return true;
            }
            invitations.remove(p.getUniqueId());
            Session s=new Session(from,p.getUniqueId());
            sessions.put(from,s); sessions.put(p.getUniqueId(),s);
            msg(requester,"Échange ouvert avec "+p.getName()+".");
            msg(p,"Échange ouvert avec "+requester.getName()+".");
            refresh(s); return true;
        }
        if(args.length==1 && args[0].equalsIgnoreCase("cancel")) {
            Session s=session(p.getUniqueId());
            if(s!=null){cancel(s,p.getName()+" a annulé");return true;}
            UUID from=invitations.remove(p.getUniqueId());
            if(from!=null){Player requester=Bukkit.getPlayer(from);if(requester!=null)msg(requester,p.getName()+" a refusé l'invitation.");msg(p,"Invitation refusée.");return true;}
            for(UUID target:new ArrayList<>(invitations.keySet())) if(p.getUniqueId().equals(invitations.get(target))) {
                invitations.remove(target); Player receiver=Bukkit.getPlayer(target);
                if(receiver!=null)msg(receiver,"L'invitation de "+p.getName()+" a été annulée.");
            }
            msg(p,"Aucun échange ou invitation en attente."); return true;
        }
        if(args.length==1) {
            Player target=Bukkit.getPlayerExact(args[0]);
            if(target==null || target.equals(p)){msg(p,"Joueur invalide ou hors ligne.");return true;}
            if(!withinRange(p,target)){msg(p,"Tu dois être dans le même monde que "+target.getName()+" et à 10 blocs maximum.");return true;}
            if(session(p.getUniqueId())!=null || session(target.getUniqueId())!=null
                || invitations.containsKey(target.getUniqueId()) || invitations.containsValue(p.getUniqueId()) || invitations.containsKey(p.getUniqueId()) || invitations.containsValue(target.getUniqueId())) {
                msg(p,"Un des joueurs participe déjà à un échange ou a une invitation en attente.");return true;
            }
            invitations.put(target.getUniqueId(),p.getUniqueId());
            UUID targetId=target.getUniqueId(), requesterId=p.getUniqueId(), inviteToken=UUID.randomUUID();
            invitationTokens.put(targetId,inviteToken);
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                if(inviteToken.equals(invitationTokens.get(targetId)) && requesterId.equals(invitations.get(targetId))) {
                    invitationTokens.remove(targetId);
                    invitations.remove(targetId);
                    Player receiver=Bukkit.getPlayer(targetId),requester=Bukkit.getPlayer(requesterId);
                    if(receiver!=null)msg(receiver,"Invitation expirée. Aucun échange n'a été effectué.");
                    if(requester!=null)msg(requester,"Ton invitation a expiré.");
                }
            },1200L);
            msg(p,"Invitation envoyée à "+target.getName()+". Il doit faire /trade accept (10 blocs maximum).");
            msg(target,p.getName()+" veut échanger avec toi. Fais /trade accept pour accepter ou /trade cancel pour refuser.");
            return true;
        }
        msg(p,"Commandes : /trade <joueur>, /trade accept, /trade cancel.");
        return true;
    }

    private void refresh(Session s) {
        Player a=Bukkit.getPlayer(s.first),b=Bukkit.getPlayer(s.second);
        if(a!=null)open(a,s,s.a,s.b,b==null?"Joueur":b.getName());
        if(b!=null)open(b,s,s.b,s.a,a==null?"Joueur":a.getName());
    }
    private void open(Player p,Session s,Offer own,Offer theirs,String otherName) {
        if(moneyInput.contains(p.getUniqueId()))return;
        TradeHolder holder=new TradeHolder();
        Inventory inv=Bukkit.createInventory(holder,54,TITLE);holder.inventory=inv;
        for(int i=0;i<own.items.size()&&i<OWN_SLOTS.length;i++) inv.setItem(OWN_SLOTS[i],own.items.get(i).clone());
        for(int i=0;i<theirs.items.size()&&i<OTHER_SLOTS.length;i++) inv.setItem(OTHER_SLOTS[i],theirs.items.get(i).clone());
        inv.setItem(21,named(Material.GOLD_INGOT,"ARGENT : "+own.money+" Larry","Clique ici pour saisir un montant dans l'enclume","Entre 0 pour retirer ton argent de l'offre"));
        inv.setItem(22,named(Material.BOOK,"TES OBJETS","Clique un objet de ton inventaire pour l'ajouter","Zone du haut : 21 emplacements"));
        inv.setItem(25,named(Material.GOLD_INGOT,"ARGENT DE "+otherName.toUpperCase(Locale.ROOT)+": "+theirs.money+" Larry","Offre de l'autre joueur"));
        inv.setItem(23,named(Material.NAME_TAG,"OFFRE DE "+otherName.toUpperCase(Locale.ROOT),"Objets proposés par l'autre joueur","Cette zone est en lecture seule"));
        inv.setItem(49,named(own.confirmed?Material.LIME_CONCRETE:Material.EMERALD,
            own.confirmed?"OFFRE CONFIRMÉE":"CONFIRMER L'ÉCHANGE",
            own.confirmed?"En attente de l'autre joueur":"Clique seulement quand tu es satisfait de l'offre",
            "Toute modification annule les deux confirmations"));
        inv.setItem(53,named(Material.BARRIER,"ANNULER L'ÉCHANGE","Les objets restent dans ton inventaire"));
        opening.add(p.getUniqueId());
        try{p.openInventory(inv);}finally{opening.remove(p.getUniqueId());}
    }
    private void openMoneyInput(Player p) {
        UUID id=p.getUniqueId();
        Session s=session(id);if(s==null)return;
        s.a.confirmed=false;s.b.confirmed=false;s.countdown=false;s.generation++;
        opening.add(id);
        InventoryView view=p.openAnvil(null,true);
        opening.remove(id);
        if(view==null){msg(p,"Impossible d'ouvrir la saisie du montant.");return;}
        view.setTitle(MONEY_TITLE);
        view.setItem(0,named(Material.PAPER,"0","Remplace ce texte par le montant Larry"));
        if(view instanceof AnvilView anvil)anvil.setRepairCost(0);
        moneyInput.add(id);
        msg(p,"Écris le montant Larry dans l'enclume puis clique sur le résultat pour valider. Entre 0 pour retirer l'argent.");
    }
    private int ownIndex(int slot) {
        for(int i=0;i<OWN_SLOTS.length;i++) if(OWN_SLOTS[i]==slot)return i;
        return -1;
    }
    private void changed(Session s) {
        s.a.confirmed=false;s.b.confirmed=false;s.countdown=false;s.generation++;
        Bukkit.getScheduler().runTask(plugin,()->{if(session(s.first)==s)refresh(s);});
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if(moneyInput.contains(e.getWhoClicked().getUniqueId()) && e.getView() instanceof AnvilView anvil) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player player))return;
            if(e.getRawSlot()!=2)return;
            Session active=session(player.getUniqueId());
            if(active==null){moneyInput.remove(player.getUniqueId());player.closeInventory();return;}
            String typed=anvil.getRenameText();
            double amount;
            try {
                if(typed==null||typed.isBlank())throw new NumberFormatException();
                amount=Double.parseDouble(typed.trim().replace(',','.'));
            } catch(NumberFormatException ex) {
                msg(player,"Montant invalide. Entre un nombre, par exemple 250 ou 12.5.");
                return;
            }
            if(!Double.isFinite(amount)||amount<0||amount>economy.balance(player.getUniqueId())||java.math.BigDecimal.valueOf(amount).stripTrailingZeros().scale()>4) {
                msg(player,"Montant invalide ou solde Larry insuffisant.");
                return;
            }
            e.getView().getTopInventory().clear();
            moneyInput.remove(player.getUniqueId());
            opening.add(player.getUniqueId());
            player.closeInventory();
            opening.remove(player.getUniqueId());
            active.offer(player.getUniqueId()).money=amount;
            changed(active);
            Player other=Bukkit.getPlayer(active.other(player.getUniqueId()));
            if(other!=null)msg(other,player.getName()+" a modifié son offre : "+amount+" Larry.");
            return;
        }
        if(!tradeView(e.getView()))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p))return;
        Session s=session(p.getUniqueId()); if(s==null)return;
        Player other=Bukkit.getPlayer(s.other(p.getUniqueId()));
        if(!withinRange(p,other)){cancel(s,"les joueurs doivent rester à 10 blocs maximum");return;}
        int raw=e.getRawSlot();
        if(raw==53){Bukkit.getScheduler().runTask(plugin,()->{if(session(s.first)==s)cancel(s,p.getName()+" a annulé");});return;}
        if(raw==21){Bukkit.getScheduler().runTask(plugin,()->{if(session(p.getUniqueId())==s&&tradeView(p.getOpenInventory()))openMoneyInput(p);});return;}
        if(raw==49) {
            Offer own=s.offer(p.getUniqueId()); own.confirmed=true;
            if(other!=null)msg(other,p.getName()+" a confirmé son offre.");
            if(moneyInput.contains(s.first)||moneyInput.contains(s.second)){own.confirmed=false;msg(p,"Attends la fin de la saisie du montant.");return;}
            Bukkit.getScheduler().runTask(plugin,()->{if(session(s.first)!=s)return;if(s.a.confirmed&&s.b.confirmed)startCountdown(s);else refresh(s);});
            return;
        }
        if(e.getCursor()!=null&&!e.getCursor().getType().isAir())return;
        int index=ownIndex(raw);
        Offer own=s.offer(p.getUniqueId());
        if(index>=0){
            if(index<own.items.size()){own.items.remove(index);own.slots.remove(index);changed(s);}
            return;
        }
        if(e.getClickedInventory()!=null&&e.getClickedInventory().equals(p.getInventory())){
            int slot=e.getSlot();
            if(slot<0||slot>=36)return;
            int existing=own.slots.indexOf(slot);
            if(existing>=0){own.slots.remove(existing);own.items.remove(existing);changed(s);return;}
            ItemStack clicked=p.getInventory().getItem(slot);
            if(clicked==null||clicked.getType().isAir()||own.items.size()>=OWN_SLOTS.length)return;
            own.slots.add(slot);own.items.add(clicked.clone());changed(s);
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent e) {
        if(tradeView(e.getView()) || moneyInput.contains(e.getWhoClicked().getUniqueId()))e.setCancelled(true);
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        Session s=session(e.getPlayer().getUniqueId());
        if(s==null||e.getTo()==null)return;
        Player other=Bukkit.getPlayer(s.other(e.getPlayer().getUniqueId()));
        if(other==null || !Objects.equals(e.getTo().getWorld(),other.getWorld()) || e.getTo().distanceSquared(other.getLocation())>MAX_DISTANCE_SQUARED)cancel(s,"un joueur est sorti de la zone de 10 blocs");
    }
    private void startCountdown(Session s) {
        if(s.countdown)return;
        s.countdown=true;
        refresh(s);
        countdown(s,5,++s.generation);
    }
    private void countdown(Session s,int seconds,long generation) {
        if(generation!=s.generation)return;
        if(session(s.first)!=s||session(s.second)!=s||!s.a.confirmed||!s.b.confirmed||!s.countdown)return;
        Player a=Bukkit.getPlayer(s.first),b=Bukkit.getPlayer(s.second);
        if(a==null||b==null){cancel(s,"un joueur s'est déconnecté");return;}
        if(seconds<=0){s.countdown=false;complete(s);return;}
        msg(a,"Échange validé dans "+seconds+" seconde(s)...");
        msg(b,"Échange validé dans "+seconds+" seconde(s)...");
        Bukkit.getScheduler().runTaskLater(plugin,()->countdown(s,seconds-1,generation),20L);
    }
    private ItemStack[] cloneContents(ItemStack[] contents){
        return Arrays.stream(contents).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
    }
    private ItemStack[] simulate(Player p,Offer own,Offer incoming){
        ItemStack[] before=cloneContents(p.getInventory().getStorageContents());
        if(own.slots.size()!=own.items.size())return null;
        for(int i=0;i<own.slots.size();i++){
            int slot=own.slots.get(i);
            if(slot<0||slot>=before.length||!Objects.equals(before[slot],own.items.get(i)))return null;
            before[slot]=null;
        }
        Inventory test=Bukkit.createInventory(null,36);test.setStorageContents(before);
        if(!test.addItem(incoming.items.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty())return null;
        return cloneContents(test.getStorageContents());
    }
    private java.nio.file.Path journal(Session s,Player a,Player b,ItemStack[] afterA,ItemStack[] afterB)throws java.io.IOException{
        var dir=plugin.getDataFolder().toPath().resolve("trade-journal");
        java.nio.file.Files.createDirectories(dir);
        var file=dir.resolve(s.id+".yml");
        var tmp=java.nio.file.Files.createTempFile(dir,"trade-",".tmp");
        var y=new org.bukkit.configuration.file.YamlConfiguration();
        y.set("first",s.first.toString());y.set("second",s.second.toString());
        y.set("first-money",s.a.money);y.set("second-money",s.b.money);
        y.set("first-balance-before",economy.balance(s.first));y.set("second-balance-before",economy.balance(s.second));
        y.set("first-before",Arrays.asList(cloneContents(a.getInventory().getStorageContents())));
        y.set("second-before",Arrays.asList(cloneContents(b.getInventory().getStorageContents())));
        y.set("first-after",Arrays.asList(afterA));y.set("second-after",Arrays.asList(afterB));
        try{
            y.save(tmp.toFile());
            try{java.nio.file.Files.move(tmp,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){java.nio.file.Files.move(tmp,file);}
        }finally{java.nio.file.Files.deleteIfExists(tmp);}
        return file;
    }
    private void complete(Session s){
        Player a=Bukkit.getPlayer(s.first),b=Bukkit.getPlayer(s.second);
        if(!withinRange(a,b)||a.isDead()||b.isDead()){cancel(s,"joueur indisponible ou trop loin");return;}
        if(s.a.items.isEmpty()&&s.b.items.isEmpty()&&s.a.money==0&&s.b.money==0){cancel(s,"offres vides");return;}
        ItemStack[] afterA=simulate(a,s.a,s.b),afterB=simulate(b,s.b,s.a);
        if(afterA==null||afterB==null){cancel(s,"inventaire plein ou offre modifiée");return;}
        java.nio.file.Path file;
        try{file=journal(s,a,b,afterA,afterB);}
        catch(java.io.IOException ex){cancel(s,"journal de sécurité indisponible");return;}
        if(!economy.commitTradeMoney(s.first,s.second,s.a.money,s.b.money,s.id)){
            try{java.nio.file.Files.deleteIfExists(file);}catch(java.io.IOException ignored){}
            cancel(s,"solde insuffisant ou sauvegarde impossible");return;
        }
        clear(s);
        try{
            a.getInventory().setStorageContents(afterA);b.getInventory().setStorageContents(afterB);
            a.saveData();b.saveData();
            java.nio.file.Files.deleteIfExists(file);
            economy.clearTradeCommit(s.id);
        }catch(Exception ex){
            plugin.getLogger().severe("Échange "+s.id+" à vérifier avec trade-journal et trade-commits: "+ex.getMessage());
            closeViews(s);msg(a,"Échange à vérifier par un admin.");msg(b,"Échange à vérifier par un admin.");return;
        }
        closeViews(s);
        msg(a,"Échange terminé avec "+b.getName()+".");
        msg(b,"Échange terminé avec "+a.getName()+".");
        completedTrade.accept(a.getUniqueId());completedTrade.accept(b.getUniqueId());
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        UUID closedId=e.getPlayer().getUniqueId();
        if(e.getView() instanceof AnvilView && moneyInput.remove(closedId)) {
            e.getView().getTopInventory().clear();
            Bukkit.getScheduler().runTask(plugin,()->{
                Session active=session(closedId);
                Player player=Bukkit.getPlayer(closedId);
                if(active!=null&&player!=null)refresh(active);
            });
            return;
        }
        if(!tradeView(e.getView())||opening.contains(closedId))return;
        Session s=session(e.getPlayer().getUniqueId());
        if(s!=null)cancel(s,"interface fermée");
    }
    void shutdown(){for(Session s:new HashSet<>(sessions.values()))cancel(s,"arrêt du serveur");}
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId();
        invitations.remove(id);
        Session s=session(id);
        if(s!=null)cancel(s,"un joueur s'est déconnecté");
        invitations.values().removeIf(id::equals);
        invitationTokens.keySet().retainAll(invitations.keySet());
    }
}
