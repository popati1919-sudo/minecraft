package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Échanges synchrones et atomiques sur le thread serveur.
 * Les objets restent dans l'inventaire jusqu'à la confirmation des deux joueurs.
 */
public final class SecureTradeManager implements Listener {
    private static final String PREFIX = ChatColor.GOLD + "[LPC] " + ChatColor.RESET;
    private final JavaPlugin plugin;
    private final LarpersCoin economy;
    private final Map<UUID, UUID> invitations = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Offer {
        double money;
        final Map<Integer, ItemStack> items = new LinkedHashMap<>();
        boolean confirmed;
    }

    private static final class Session {
        final UUID first;
        final UUID second;
        final Offer a = new Offer();
        final Offer b = new Offer();
        Session(UUID first, UUID second) { this.first = first; this.second = second; }
        Offer offer(UUID id) { return first.equals(id) ? a : b; }
        UUID other(UUID id) { return first.equals(id) ? second : first; }
    }

    public SecureTradeManager(JavaPlugin plugin, LarpersCoin economy) {
        this.plugin = plugin;
        this.economy = economy;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void msg(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + message);
    }

    private Session session(UUID id) { return sessions.get(id); }

    private void clear(Session s) {
        sessions.remove(s.first);
        sessions.remove(s.second);
    }

    private void cancel(Session s, String reason) {
        clear(s);
        Player a = Bukkit.getPlayer(s.first), b = Bukkit.getPlayer(s.second);
        if (a != null) msg(a, "Échange annulé : " + reason);
        if (b != null) msg(b, "Échange annulé : " + reason);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Cette commande doit être utilisée en jeu.");
            return true;
        }
        if (args.length == 0) {
            help(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accept" -> {
                UUID from = invitations.remove(p.getUniqueId());
                if (from == null) { msg(p, "Aucune invitation en attente."); return true; }
                Player requester = Bukkit.getPlayer(from);
                if (requester == null || session(from) != null || session(p.getUniqueId()) != null) {
                    msg(p, "Invitation expirée ou joueur déjà occupé."); return true;
                }
                Session s = new Session(from, p.getUniqueId());
                sessions.put(from, s); sessions.put(p.getUniqueId(), s);
                msg(requester, "Échange ouvert avec " + p.getName() + ". Ajoute des objets/LP puis /trade confirm.");
                msg(p, "Échange ouvert avec " + requester.getName() + ". Ajoute des objets/LP puis /trade confirm.");
                show(s);
                return true;
            }
            case "item" -> {
                Session s = session(p.getUniqueId());
                if (s == null) { msg(p, "Aucun échange actif."); return true; }
                Offer o = s.offer(p.getUniqueId());
                int slot = p.getInventory().getHeldItemSlot();
                ItemStack hand = p.getInventory().getItem(slot);
                if (hand == null || hand.getType().isAir()) { msg(p, "Tiens l'objet à proposer dans ta main."); return true; }
                if (o.items.containsKey(slot)) { msg(p, "Cet emplacement est déjà proposé."); return true; }
                o.items.put(slot, hand.clone());
                changed(s);
                msg(p, "Objet proposé : " + hand.getAmount() + " x " + hand.getType() + ". L'objet reste dans ton inventaire jusqu'à validation.");
                show(s);
                return true;
            }
            case "money" -> {
                Session s = session(p.getUniqueId());
                if (s == null) { msg(p, "Aucun échange actif."); return true; }
                if (args.length != 2) { msg(p, "Usage : /trade money <montant>"); return true; }
                double amount;
                try { amount = Double.parseDouble(args[1].replace(',', '.')); }
                catch (NumberFormatException ex) { msg(p, "Montant invalide."); return true; }
                if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000_000) {
                    msg(p, "Montant invalide."); return true;
                }
                Offer o = s.offer(p.getUniqueId());
                double next = o.money + amount;
                if (!Double.isFinite(next) || economy.balance(p.getUniqueId()) + 1e-9 < next) {
                    msg(p, "Solde LPC insuffisant pour cette offre."); return true;
                }
                o.money = next;
                changed(s);
                msg(p, "Offre monétaire : " + o.money + " LPC.");
                show(s);
                return true;
            }
            case "confirm" -> {
                Session s = session(p.getUniqueId());
                if (s == null) { msg(p, "Aucun échange actif."); return true; }
                Offer o = s.offer(p.getUniqueId());
                o.confirmed = true;
                Player other = Bukkit.getPlayer(s.other(p.getUniqueId()));
                msg(p, "Confirmation enregistrée. Toute modification annulera les deux confirmations.");
                if (other != null) msg(other, p.getName() + " a confirmé. Vérifie ton offre puis /trade confirm.");
                if (s.a.confirmed && s.b.confirmed) complete(s);
                return true;
            }
            case "cancel" -> {
                Session s = session(p.getUniqueId());
                if (s != null) { cancel(s, p.getName() + " a annulé"); return true; }
                boolean removed=false;
                UUID incoming=invitations.remove(p.getUniqueId());
                if(incoming!=null){Player requester=Bukkit.getPlayer(incoming);if(requester!=null)msg(requester,p.getName()+" a refusé/annulé l'invitation.");removed=true;}
                for(UUID target:new ArrayList<>(invitations.keySet())) {
                    if(p.getUniqueId().equals(invitations.get(target))) {
                        invitations.remove(target);Player receiver=Bukkit.getPlayer(target);
                        if(receiver!=null)msg(receiver,"L'invitation de "+p.getName()+" a été annulée.");
                        removed=true;
                    }
                }
                msg(p,removed?"Invitation annulée.":"Aucun échange ou invitation en attente.");
                return true;
            }
            default -> {
                if (args.length == 1) {
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null || target.equals(p)) { msg(p, "Joueur invalide ou hors ligne."); return true; }
                    if (session(p.getUniqueId()) != null || session(target.getUniqueId()) != null) {
                        msg(p, "Un des joueurs participe déjà à un échange."); return true;
                    }
                    invitations.put(target.getUniqueId(), p.getUniqueId());
                    msg(p, "Invitation envoyée à " + target.getName() + ".");
                    msg(target, p.getName() + " te propose un échange. Fais /trade accept ou /trade cancel.");
                    return true;
                }
                help(p);
                return true;
            }
        }
    }

    private void changed(Session s) {
        s.a.confirmed = false;
        s.b.confirmed = false;
        Player a = Bukkit.getPlayer(s.first), b = Bukkit.getPlayer(s.second);
        if (a != null) msg(a, "Offre modifiée : les confirmations ont été réinitialisées.");
        if (b != null) msg(b, "Offre modifiée : les confirmations ont été réinitialisées.");
    }

    private boolean validOffer(Player p, Offer offer) {
        if (economy.balance(p.getUniqueId()) + 1e-9 < offer.money) return false;
        for (var entry : offer.items.entrySet()) {
            ItemStack current = p.getInventory().getItem(entry.getKey());
            ItemStack expected = entry.getValue();
            if (current == null || !current.isSimilar(expected) || current.getAmount() != expected.getAmount()) return false;
        }
        return true;
    }

    private boolean canReceive(Player receiver, Offer senderOffer, Offer receiverOffer) {
        Inventory simulated = Bukkit.createInventory(null, 36);
        simulated.setStorageContents(receiver.getInventory().getStorageContents());
        for (Integer slot : receiverOffer.items.keySet()) simulated.setItem(slot, null);
        Map<Integer, ItemStack> leftovers = simulated.addItem(senderOffer.items.values().stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        return leftovers.isEmpty();
    }

    private void complete(Session s) {
        Player a = Bukkit.getPlayer(s.first), b = Bukkit.getPlayer(s.second);
        if (a == null || b == null) { cancel(s, "un joueur est déconnecté"); return; }
        if (!validOffer(a, s.a) || !validOffer(b, s.b)) {
            cancel(s, "une offre n'est plus valide (objet déplacé ou solde modifié)");
            return;
        }
        if (!Double.isFinite(economy.balance(a.getUniqueId()) + s.b.money)
                || !Double.isFinite(economy.balance(b.getUniqueId()) + s.a.money)) {
            cancel(s, "le solde du destinataire dépasserait la limite numérique");
            return;
        }
        if (!canReceive(a, s.b, s.a) || !canReceive(b, s.a, s.b)) {
            cancel(s, "inventaire plein; aucun objet ni LPC n'a été transféré");
            return;
        }

        // Toutes les validations sont faites avant la première mutation. Tout se déroule
        // sur le thread principal Bukkit, sans possibilité d'interleaving entre les étapes.
        if (s.a.money > 0 && !economy.debit(a.getUniqueId(), s.a.money)) { cancel(s, "solde de " + a.getName() + " insuffisant"); return; }
        if (s.b.money > 0 && !economy.debit(b.getUniqueId(), s.b.money)) {
            if (s.a.money > 0) economy.credit(a.getUniqueId(), s.a.money);
            economy.saveData();
            cancel(s, "solde de " + b.getName() + " insuffisant");
            return;
        }

        for (Integer slot : s.a.items.keySet()) a.getInventory().setItem(slot, null);
        for (Integer slot : s.b.items.keySet()) b.getInventory().setItem(slot, null);
        if (s.a.money > 0) economy.credit(b.getUniqueId(), s.a.money);
        if (s.b.money > 0) economy.credit(a.getUniqueId(), s.b.money);
        for (ItemStack item : s.a.items.values()) b.getInventory().addItem(item.clone());
        for (ItemStack item : s.b.items.values()) a.getInventory().addItem(item.clone());
        economy.saveData();
        if (s.a.money > 0) economy.recordTransaction("TRADE",a.getUniqueId(),b.getUniqueId(),s.a.money);
        if (s.b.money > 0) economy.recordTransaction("TRADE",b.getUniqueId(),a.getUniqueId(),s.b.money);
        clear(s);
        msg(a, "Échange terminé avec " + b.getName() + ".");
        msg(b, "Échange terminé avec " + a.getName() + ".");
    }

    private void show(Session s) {
        Player a = Bukkit.getPlayer(s.first), b = Bukkit.getPlayer(s.second);
        if (a != null) msg(a, "Ton offre : " + s.a.money + " LPC, " + s.a.items.size() + " emplacement(s). Confirmation : " + s.a.confirmed);
        if (b != null) msg(b, "Ton offre : " + s.b.money + " LPC, " + s.b.items.size() + " emplacement(s). Confirmation : " + s.b.confirmed);
    }

    private void help(Player p) {
        msg(p, "/trade <joueur> | /trade accept | /trade item | /trade money <montant> | /trade confirm | /trade cancel");
        msg(p, "Les objets restent dans l'inventaire jusqu'à la double confirmation.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        invitations.remove(id);
        Session s = session(id);
        if (s != null) cancel(s, "un joueur s'est déconnecté");
        invitations.values().removeIf(id::equals);
    }
}
