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

    private static final class Offer {
        final List<ItemStack> items = new ArrayList<>();
        boolean confirmed;
        double money;
    }
    private static final class Session {
        final UUID first, second;
        final Offer a = new Offer(), b = new Offer();
        boolean countdown;
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
    private void returnItems(Player p,Offer offer) {
        for(ItemStack item:offer.items) {
            Map<Integer,ItemStack> left=p.getInventory().addItem(item.clone());
            for(ItemStack rest:left.values()) p.getWorld().dropItemNaturally(p.getLocation(),rest);
        }
        offer.items.clear();
    }
    private void closeViews(Session s) {
        for(UUID id:List.of(s.first,s.second)) {
            Player p=Bukkit.getPlayer(id);
            if(p!=null && p.getOpenInventory().getTitle().equals(TITLE)) p.closeInventory();
        }
    }
    private void cancel(Session s,String reason) {
        clear(s);
        Player a=Bukkit.getPlayer(s.first), b=Bukkit.getPlayer(s.second);
        if(a!=null) returnItems(a,s.a);
        if(b!=null) returnItems(b,s.b);
        closeViews(s);
        if(a!=null) msg(a,"Échange annulé : "+reason+". Tes objets t'ont été rendus.");
        if(b!=null) msg(b,"Échange annulé : "+reason+". Tes objets t'ont été rendus.");
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
                || invitations.containsKey(target.getUniqueId()) || invitations.containsValue(p.getUniqueId())) {
                msg(p,"Un des joueurs participe déjà à un échange ou a une invitation en attente.");return true;
            }
            invitations.put(target.getUniqueId(),p.getUniqueId());
            UUID targetId=target.getUniqueId(), requesterId=p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                if(requesterId.equals(invitations.get(targetId))) {
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
        Inventory inv=Bukkit.createInventory(null,54,TITLE);
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
        inv.setItem(53,named(Material.BARRIER,"ANNULER L'ÉCHANGE","Les objets déposés te seront rendus"));
        opening.add(p.getUniqueId());
        p.openInventory(inv);
        opening.remove(p.getUniqueId());
    }
    private void openMoneyInput(Player p) {
        UUID id=p.getUniqueId();
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
        s.a.confirmed=false;s.b.confirmed=false;s.countdown=false;refresh(s);
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if(e.getView().getTitle().equals(MONEY_TITLE)) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player player))return;
            if(e.getRawSlot()!=2)return;
            Session active=session(player.getUniqueId());
            if(active==null){moneyInput.remove(player.getUniqueId());player.closeInventory();return;}
            String typed=((AnvilView)e.getView()).getRenameText();
            double amount;
            try {
                if(typed==null||typed.isBlank())throw new NumberFormatException();
                amount=Double.parseDouble(typed.trim().replace(',','.'));
            } catch(NumberFormatException ex) {
                msg(player,"Montant invalide. Entre un nombre, par exemple 250 ou 12.5.");
                return;
            }
            if(!Double.isFinite(amount)||amount<0||amount>economy.balance(player.getUniqueId())) {
                msg(player,"Montant invalide ou solde Larry insuffisant.");
                return;
            }
            moneyInput.remove(player.getUniqueId());
            player.closeInventory();
            active.offer(player.getUniqueId()).money=amount;
            changed(active);
            Player other=Bukkit.getPlayer(active.other(player.getUniqueId()));
            if(other!=null)msg(other,player.getName()+" a modifié son offre : "+amount+" Larry.");
            return;
        }
        if(!e.getView().getTitle().equals(TITLE))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p))return;
        Session s=session(p.getUniqueId()); if(s==null)return;
        Player other=Bukkit.getPlayer(s.other(p.getUniqueId()));
        if(!withinRange(p,other)){cancel(s,"les joueurs doivent rester à 10 blocs maximum");return;}
        int raw=e.getRawSlot();
        if(raw==53){cancel(s,p.getName()+" a annulé");return;}
        if(raw==21){openMoneyInput(p);return;}
        if(raw==49) {
            Offer own=s.offer(p.getUniqueId()); own.confirmed=true;
            if(other!=null)msg(other,p.getName()+" a confirmé son offre.");
            if(s.a.confirmed&&s.b.confirmed)startCountdown(s);else refresh(s);
            return;
        }
        int index=ownIndex(raw);
        if(index>=0) {
            Offer own=s.offer(p.getUniqueId());
            ItemStack cursor=e.getCursor(), existing=index<own.items.size()?own.items.get(index):null;
            if(cursor!=null&&!cursor.getType().isAir()) {
                if(index>own.items.size() || (index==own.items.size()&&own.items.size()>=OWN_SLOTS.length))return;
                if(index==own.items.size()) own.items.add(cursor.clone()); else own.items.set(index,cursor.clone());
                e.setCursor(existing==null?null:existing.clone());
                changed(s);
            } else if(existing!=null) {
                own.items.remove(index); e.setCursor(existing.clone()); changed(s);
            }
            return;
        }
        if(raw>=e.getView().getTopInventory().getSize()) {
            ItemStack clicked=e.getCurrentItem();
            if(clicked==null||clicked.getType().isAir())return;
            Offer own=s.offer(p.getUniqueId());
            if(own.items.size()>=OWN_SLOTS.length)return;
            own.items.add(clicked.clone());
            e.setCurrentItem(null);
            changed(s);
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent e) {
        if(e.getView().getTitle().equals(TITLE))e.setCancelled(true);
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        Session s=session(e.getPlayer().getUniqueId());
        if(s==null||e.getTo()==null)return;
        Player other=Bukkit.getPlayer(s.other(e.getPlayer().getUniqueId()));
        if(!withinRange(e.getPlayer(),other))cancel(s,"un joueur est sorti de la zone de 10 blocs");
    }
    private void startCountdown(Session s) {
        if(s.countdown)return;
        s.countdown=true;
        refresh(s);
        countdown(s,5);
    }
    private void countdown(Session s,int seconds) {
        if(session(s.first)!=s||session(s.second)!=s||!s.a.confirmed||!s.b.confirmed||!s.countdown)return;
        Player a=Bukkit.getPlayer(s.first),b=Bukkit.getPlayer(s.second);
        if(a==null||b==null){cancel(s,"un joueur s'est déconnecté");return;}
        if(seconds<=0){s.countdown=false;complete(s);return;}
        msg(a,"Échange validé dans "+seconds+" seconde(s)...");
        msg(b,"Échange validé dans "+seconds+" seconde(s)...");
        Bukkit.getScheduler().runTaskLater(plugin,()->countdown(s,seconds-1),20L);
    }
    private boolean canReceive(Player receiver,Offer incoming) {
        Inventory test=Bukkit.createInventory(null,36);
        test.setStorageContents(receiver.getInventory().getStorageContents());
        return test.addItem(incoming.items.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty();
    }
    private void complete(Session s) {
        Player a=Bukkit.getPlayer(s.first),b=Bukkit.getPlayer(s.second);
        if(a==null||b==null){cancel(s,"un joueur s'est déconnecté");return;}
        if(!withinRange(a,b)){cancel(s,"les joueurs sont trop éloignés");return;}
        if(!canReceive(a,s.b)||!canReceive(b,s.a)){cancel(s,"inventaire plein; objets rendus sans échange");return;}
        if(!Double.isFinite(s.a.money)||!Double.isFinite(s.b.money)||s.a.money<0||s.b.money<0
            ||economy.balance(a.getUniqueId())<s.a.money||economy.balance(b.getUniqueId())<s.b.money) {
            cancel(s,"solde Larry insuffisant; aucun échange effectué");return;
        }
        if(s.a.money>0&&!economy.debit(a.getUniqueId(),s.a.money)){cancel(s,"paiement Larry impossible");return;}
        if(s.b.money>0&&!economy.debit(b.getUniqueId(),s.b.money)){
            if(s.a.money>0)economy.credit(a.getUniqueId(),s.a.money);
            cancel(s,"paiement Larry impossible");return;
        }
        if(s.a.money>0)economy.credit(b.getUniqueId(),s.a.money);
        if(s.b.money>0)economy.credit(a.getUniqueId(),s.b.money);
        economy.saveData();
        if(s.a.money>0)economy.recordTransaction("TRADE",a.getUniqueId(),b.getUniqueId(),s.a.money);
        if(s.b.money>0)economy.recordTransaction("TRADE",b.getUniqueId(),a.getUniqueId(),s.b.money);
        clear(s);
        for(ItemStack item:s.a.items)b.getInventory().addItem(item.clone());
        for(ItemStack item:s.b.items)a.getInventory().addItem(item.clone());
        s.a.items.clear();s.b.items.clear();
        closeViews(s);
        msg(a,"Échange terminé avec "+b.getName()+". Argent reçu : "+s.b.money+" Larry, envoyé : "+s.a.money+" Larry.");
        msg(b,"Échange terminé avec "+a.getName()+". Argent reçu : "+s.a.money+" Larry, envoyé : "+s.b.money+" Larry.");
        completedTrade.accept(a.getUniqueId());
        completedTrade.accept(b.getUniqueId());
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        UUID closedId=e.getPlayer().getUniqueId();
        if(e.getView().getTitle().equals(MONEY_TITLE) && moneyInput.remove(closedId)) {
            Bukkit.getScheduler().runTask(plugin,()->{
                Session active=session(closedId);
                Player player=Bukkit.getPlayer(closedId);
                if(active!=null&&player!=null)refresh(active);
            });
            return;
        }
        if(!e.getView().getTitle().equals(TITLE)||opening.contains(closedId))return;
        Session s=session(e.getPlayer().getUniqueId());
        if(s!=null)cancel(s,"interface fermée");
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId();
        invitations.remove(id);
        Session s=session(id);
        if(s!=null)cancel(s,"un joueur s'est déconnecté");
        invitations.values().removeIf(id::equals);
    }
}
