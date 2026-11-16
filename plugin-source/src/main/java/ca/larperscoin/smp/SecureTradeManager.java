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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class SecureTradeManager implements Listener {
    private static final String PREFIX = ChatColor.GOLD + "[LPC] " + ChatColor.RESET;
    private static final String TITLE = ChatColor.DARK_AQUA + "Échange sécurisé";
    private static final int[] OWN_SLOTS = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20};
    private static final int[] OTHER_SLOTS = {27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47};
    private final JavaPlugin plugin;
    private final LarpersCoin economy;
    private final Map<UUID, UUID> invitations = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> opening = new HashSet<>();

    private static final class Offer {
        final List<ItemStack> items = new ArrayList<>();
        boolean confirmed;
    }
    private static final class Session {
        final UUID first, second;
        final Offer a = new Offer(), b = new Offer();
        Session(UUID first, UUID second) { this.first=first; this.second=second; }
        Offer offer(UUID id) { return first.equals(id) ? a : b; }
        Offer otherOffer(UUID id) { return first.equals(id) ? b : a; }
        UUID other(UUID id) { return first.equals(id) ? second : first; }
    }

    public SecureTradeManager(JavaPlugin plugin, LarpersCoin economy) {
        this.plugin=plugin; this.economy=economy;
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    private void msg(CommandSender sender,String message) { sender.sendMessage(PREFIX+message); }
    private Session session(UUID id) { return sessions.get(id); }
    private void clear(Session s) { sessions.remove(s.first); sessions.remove(s.second); }
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
        if(a!=null) msg(a,"Échange annulé : "+reason);
        if(b!=null) msg(b,"Échange annulé : "+reason);
    }
    public boolean handle(CommandSender sender,String[] args) {
        if(!(sender instanceof Player p)){msg(sender,"Cette commande doit être utilisée en jeu.");return true;}
        if(args.length==1 && args[0].equalsIgnoreCase("accept")) {
            UUID from=invitations.remove(p.getUniqueId());
            if(from==null){msg(p,"Aucune invitation en attente.");return true;}
            Player requester=Bukkit.getPlayer(from);
            if(requester==null || session(from)!=null || session(p.getUniqueId())!=null) {
                msg(p,"Invitation expirée ou joueur déjà occupé."); return true;
            }
            Session s=new Session(from,p.getUniqueId());
            sessions.put(from,s); sessions.put(p.getUniqueId(),s);
            msg(requester,"Échange ouvert avec "+p.getName()+". Dépose tes objets dans la zone du haut.");
            msg(p,"Échange ouvert avec "+requester.getName()+". Dépose tes objets dans la zone du haut.");
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
            if(session(p.getUniqueId())!=null || session(target.getUniqueId())!=null
                || invitations.containsKey(target.getUniqueId())) {
                msg(p,"Un des joueurs participe déjà à un échange ou a une invitation en attente.");return true;
            }
            invitations.put(target.getUniqueId(),p.getUniqueId());
            UUID targetId=target.getUniqueId(), requesterId=p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                if(requesterId.equals(invitations.get(targetId))) {
                    invitations.remove(targetId);
                    Player receiver=Bukkit.getPlayer(targetId),requester=Bukkit.getPlayer(requesterId);
                    if(receiver!=null)msg(receiver,"L'invitation d'échange a expiré.");
                    if(requester!=null)msg(requester,"Ton invitation d'échange a expiré.");
                }
            },1200L);
            msg(p,"Invitation envoyée à "+target.getName()+".");
            msg(target,p.getName()+" te propose un échange. Fais /trade accept.");
            return true;
        }
        msg(p,"Utilisation : /trade <joueur>, /trade accept ou /trade cancel.");
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
        inv.setItem(22,named(Material.LIME_STAINED_GLASS_PANE,"Ton offre"));
        inv.setItem(23,named(Material.GRAY_STAINED_GLASS_PANE,"Offre de "+otherName));
        inv.setItem(49,named(own.confirmed?Material.LIME_CONCRETE:Material.YELLOW_CONCRETE,
            own.confirmed?"Confirmé":"Confirmer l'échange",
            own.confirmed?"En attente de l'autre joueur":"Clique lorsque ton offre est finale"));
        inv.setItem(53,named(Material.BARRIER,"Annuler l'échange","Les objets te seront rendus"));
        opening.add(p.getUniqueId());
        p.openInventory(inv);
        opening.remove(p.getUniqueId());
    }
    private int ownIndex(int slot) {
        for(int i=0;i<OWN_SLOTS.length;i++) if(OWN_SLOTS[i]==slot)return i;
        return -1;
    }
    private void changed(Session s) {
        s.a.confirmed=false;s.b.confirmed=false;refresh(s);
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if(!e.getView().getTitle().equals(TITLE))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p))return;
        Session s=session(p.getUniqueId()); if(s==null)return;
        int raw=e.getRawSlot();
        if(raw==53){cancel(s,p.getName()+" a annulé");return;}
        if(raw==49) {
            Offer own=s.offer(p.getUniqueId()); own.confirmed=true;
            Player other=Bukkit.getPlayer(s.other(p.getUniqueId()));
            if(other!=null)msg(other,p.getName()+" a confirmé son offre.");
            if(s.a.confirmed&&s.b.confirmed)complete(s);else refresh(s);
            return;
        }
        int index=ownIndex(raw);
        if(index>=0) {
            Offer own=s.offer(p.getUniqueId());
            ItemStack cursor=e.getCursor(), existing=index<own.items.size()?own.items.get(index):null;
            if(cursor!=null&&!cursor.getType().isAir()) {
                if(index>own.items.size() || (index==own.items.size()&&own.items.size()>=OWN_SLOTS.length))return;
                if(existing!=null) e.setCursor(existing.clone());
                else e.setCursor(null);
                if(index==own.items.size())own.items.add(cursor.clone());else own.items.set(index,cursor.clone());
                if(existing==null)e.setCursor(null);
                changed(s);
            } else if(existing!=null) {
                own.items.remove(index); e.setCursor(existing.clone()); changed(s);
            }
            return;
        }
        if(raw>=e.getView().getTopInventory().getSize()) {
            if(e.isShiftClick()) {
                ItemStack clicked=e.getCurrentItem();
                if(clicked==null||clicked.getType().isAir())return;
                Offer own=s.offer(p.getUniqueId());
                if(own.items.size()>=OWN_SLOTS.length)return;
                own.items.add(clicked.clone()); e.setCurrentItem(null); changed(s);
            }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent e) {
        if(e.getView().getTitle().equals(TITLE))e.setCancelled(true);
    }
    private boolean canReceive(Player receiver,Offer incoming) {
        Inventory test=Bukkit.createInventory(null,36);
        test.setStorageContents(receiver.getInventory().getStorageContents());
        return test.addItem(incoming.items.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty();
    }
    private void complete(Session s) {
        Player a=Bukkit.getPlayer(s.first),b=Bukkit.getPlayer(s.second);
        if(a==null||b==null){cancel(s,"un joueur s'est déconnecté");return;}
        if(!canReceive(a,s.b)||!canReceive(b,s.a)){cancel(s,"inventaire plein; objets rendus sans échange");return;}
        clear(s);
        for(ItemStack item:s.a.items)b.getInventory().addItem(item.clone());
        for(ItemStack item:s.b.items)a.getInventory().addItem(item.clone());
        s.a.items.clear();s.b.items.clear();
        closeViews(s);
        msg(a,"Échange terminé avec "+b.getName()+".");
        msg(b,"Échange terminé avec "+a.getName()+".");
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if(!e.getView().getTitle().equals(TITLE)||opening.contains(e.getPlayer().getUniqueId()))return;
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
