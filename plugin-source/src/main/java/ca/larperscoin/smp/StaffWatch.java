package ca.larperscoin.smp;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class StaffWatch implements Listener, CommandExecutor {
    private final NamespacedKey key;
    private final JavaPlugin plugin;
    private final LarpersFeatures features;
    private static final Set<String> WATCH_COMMANDS=Set.of("admin","rank","modo","tp","freeze","vanish","staffchat","ac","mute","unmute","ban","banip","ban-ip","kick","listban","banlist","pardon","pardon-ip");

    StaffWatch(JavaPlugin plugin,LarpersFeatures features) {
        this.plugin=plugin;
        this.features=features;
        key=new NamespacedKey(plugin,"staff_watch_return");
        for(String name:List.of("modo","tp")) plugin.getCommand(name).setExecutor(this);
        plugin.getCommand("modo").setTabCompleter((sender,cmd,alias,args)->
            sender.hasPermission("larpers.staff") && args.length==1 && "help".startsWith(args[0].toLowerCase(Locale.ROOT))
                ? List.of("help") : List.of());
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player p:Bukkit.getOnlinePlayers()) if(active(p)) restore(p);
    }
    boolean active(Player p) { return p.getPersistentDataContainer().has(key,PersistentDataType.STRING); }

    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        Player p=sender instanceof Player player ? player : null;
        if(cmd.getName().equals("tp")) {
            if(p!=null && active(p)) { follow(p,args);return true; }
            if(sender.hasPermission("minecraft.command.teleport"))
                return Bukkit.dispatchCommand(sender,"minecraft:teleport "+String.join(" ",args));
            sender.sendMessage("Active /modo pour rejoindre un joueur.");return true;
        }
        if(p==null) { sender.sendMessage("Commande en jeu.");return true; }
        if(args.length>0) {
            if(!p.hasPermission("larpers.staff")) {p.sendMessage("Guide réservé au staff.");return true;}
            if(args.length==1 && args[0].equalsIgnoreCase("help")) openGuide(p);
            else p.sendMessage("Utilise /modo ou /modo help.");
            return true;
        }
        if(active(p)) { restore(p);return true; }
        if(!p.hasPermission("larpers.staff") || plugin.getConfig().getBoolean("network.auth-only",false)) {
            p.sendMessage("Surveillance réservée au staff sur le SMP.");return true;
        }
        if(p.isDead() || p.isInsideVehicle() || p.isSleeping()) {
            p.sendMessage("Place-toi debout avant d’activer le mode.");return true;
        }
        p.closeInventory();
        var state=new YamlConfiguration();
        state.set("position",p.getLocation());
        state.set("mode",p.getGameMode().name());
        state.set("flight",p.getAllowFlight());
        state.set("flying",p.isFlying());
        state.set("hidden",features.isVanished(p));
        state.set("slot",p.getInventory().getHeldItemSlot());
        var items=p.getInventory().getContents();
        for(int i=0;i<items.length;i++)
            if(items[i]!=null && !items[i].getType().isAir())
                state.set("item."+i,Base64.getEncoder().encodeToString(items[i].serializeAsBytes()));
        p.getPersistentDataContainer().set(key,PersistentDataType.STRING,state.saveToString());
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        features.setVanished(p,true);
        p.saveData();
        p.sendMessage("Surveillance active. /tp <joueur>, /freeze <joueur>, /vanish. Retape /modo pour revenir.");
        return true;
    }
    private void openGuide(Player p) {
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);
        var meta=(org.bukkit.inventory.meta.BookMeta)book.getItemMeta();
        meta.setTitle("Guide modo");
        meta.setAuthor("Larry SMP");
        meta.addPage(
            "§2Surveiller un joueur§r\n\n1. Tape /modo.\n2. Tape /tp Alex pour observer Alex.\n3. Retape /modo pour rentrer.\n\nRemplace Alex par le pseudo exact du joueur.",
            "§2Entrer en service§r\n\nSur le SMP, /modo garde ta position, ton armure, ta main gauche et ton inventaire. Tu passes en spectateur avec le vanish actif.\n\n/admin ouvre ton menu.",
            "§2Observer§r\n\n/tp Alex te rapproche du joueur connecté.\n\n/vanish change ta visibilité dans le TAB des joueurs. Tu restes en spectateur.\n\n/staffchat <message> parle au staff.",
            "§2Immobiliser§r\n\n/freeze Alex bloque ses déplacements. Retape /freeze Alex pour le libérer.\n\nLibère le joueur à la fin de la vérification.",
            "§2Lire les alertes§r\n\n/ac alerts active ou coupe tes alertes.\n/ac info Alex montre son compteur.\n/ac log montre les alertes récentes.\n/ac top affiche le classement.\n/ac status affiche l’état.",
            "§2Modérer le chat§r\n\n/mute Alex spam coupe son chat public. Il doit être connecté.\n/unmute Alex lui rend la parole, même hors ligne.\n/kick Alex <raison> le déconnecte du SMP.",
            "§2Bannir du SMP§r\n\n/ban Alex <raison> bannit le joueur.\n/pardon Alex le débannit.\n/listban players liste les bans de joueurs.",
            "§2Bannir une IP§r\n\n/banip Alex <raison> bannit son adresse. Plusieurs joueurs peuvent la partager.\n/listban ips liste ces bans.\n/pardon-ip <IP> retire le ban.",
            "§2Fin de service§r\n\nRetape /modo pour retrouver ta position et ton inventaire. La déconnexion lance aussi le retour.\n\nHors surveillance, /invsee Alex ouvre son inventaire.\n/modo help rouvre ce guide."
        );
        book.setItemMeta(meta);
        p.openBook(book);
    }

    private void follow(Player p,String[] args) {
        if(!p.hasPermission("larpers.staff")) {restore(p);return;}
        Player target=args.length==1 ? Bukkit.getPlayerExact(args[0]) : null;
        if(target==null || target==p) {p.sendMessage("Utilise /tp <joueur connecté>.");return;}
        if(p.teleport(target.getLocation())) p.sendMessage("Surveillance de "+target.getName()+".");
    }
    private void restore(Player p) {
        try {
            var state=new YamlConfiguration();
            state.loadFromString(p.getPersistentDataContainer().get(key,PersistentDataType.STRING));
            Location location=state.getLocation("position");
            GameMode mode=GameMode.valueOf(state.getString("mode"));
            ItemStack[] items=new ItemStack[p.getInventory().getSize()];
            for(int i=0;i<items.length;i++) {
                String bytes=state.getString("item."+i);
                if(bytes!=null) items[i]=ItemStack.deserializeBytes(Base64.getDecoder().decode(bytes));
            }
            if(location==null || !p.teleport(location)) throw new IllegalStateException("Téléportation refusée");
            p.setGameMode(mode);
            p.setAllowFlight(state.getBoolean("flight"));
            p.setFlying(state.getBoolean("flying") && p.getAllowFlight());
            p.getInventory().setContents(items);
            p.getInventory().setHeldItemSlot(state.getInt("slot"));
            features.setVanished(p,state.getBoolean("hidden"));
            p.getPersistentDataContainer().remove(key);
            p.saveData();
            p.sendMessage("Retour à ta position. Inventaire restauré.");
        } catch(Exception ex) {
            plugin.getLogger().warning("Retour modo de "+p.getName()+" : "+ex.getMessage());
            p.sendMessage("Retour impossible. Contacte un admin pour vérifier ton point de retour.");
        }
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent e) {
        if(!active(e.getPlayer())) return;
        String[] parts=e.getMessage().substring(1).trim().split("\\s+");
        String name=parts[0].toLowerCase(Locale.ROOT);
        if(!e.getPlayer().hasPermission("larpers.staff")) {
            e.setCancelled(true);
            restore(e.getPlayer());
            return;
        }
        if(name.equals("tp")) {
            e.setCancelled(true);
            follow(e.getPlayer(),Arrays.copyOfRange(parts,1,parts.length));
        } else if(!WATCH_COMMANDS.contains(name)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("Quitte la surveillance avec /modo pour utiliser cette commande.");
        }
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        if(active(e.getPlayer())) restore(e.getPlayer());
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        if(active(e.getPlayer())) restore(e.getPlayer());
    }
    void shutdown() {
        for(Player p:Bukkit.getOnlinePlayers()) if(active(p)) restore(p);
    }
}
