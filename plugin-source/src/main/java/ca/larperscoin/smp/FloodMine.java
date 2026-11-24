package ca.larperscoin.smp;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class FloodMine extends JavaPlugin implements Listener {
    private static final int BASE = 200, DECK = 216, RADIUS = 6;
    private final Map<UUID,Integer> bags = new HashMap<>(), scores = new HashMap<>();
    private World world;
    private String round = "";
    private int seconds, water;

    @Override public void onEnable() {
        world = Bukkit.getWorlds().get(0);
        try { Files.deleteIfExists(MineAccess.session); }
        catch (IOException ex) { throw new IllegalStateException("Impossible de fermer la mine au démarrage",ex); }
        Bukkit.getMessenger().registerOutgoingPluginChannel(this,"BungeeCord");
        Bukkit.getPluginManager().registerEvents(this,this);
        getCommand("serveur").setExecutor((sender,cmd,label,args) -> {
            if (sender instanceof Player p && args.length==1 && args[0].equalsIgnoreCase("lobby"))
                MineAccess.transfer(this,p,"lobby");
            else sender.sendMessage("Utilise /serveur lobby.");
            return true;
        });
        getCommand("admin").setExecutor((sender,cmd,label,args) -> {
            if (sender.isOp() || sender.hasPermission("larpers.admin")) MineAccess.control(sender,args);
            else sender.sendMessage("Commande réservée aux administrateurs.");
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(this,this::tick,1,20);
    }

    private String requestedRound() {
        try { return Files.readString(MineAccess.session); }
        catch (IOException ex) { return ""; }
    }

    private void tick() {
        String requested = requestedRound();
        if (!requested.equals(round)) {
            for (Player p : Bukkit.getOnlinePlayers()) MineAccess.transfer(this,p,"lobby");
            bags.clear();
            scores.clear();
            round = requested;
            if (!round.isEmpty()) build();
            return;
        }
        if (round.isEmpty()) {
            for (Player p : Bukkit.getOnlinePlayers()) MineAccess.transfer(this,p,"lobby");
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (++seconds % 15 == 0) {
            if (++water >= DECK) {
                scores.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(winner ->
                    Bukkit.broadcastMessage("Meilleur dépôt : " + Bukkit.getOfflinePlayer(winner.getKey()).getName() + " avec " + winner.getValue() + " points."));
                try { if (round.equals(requestedRound())) Files.deleteIfExists(MineAccess.session); }
                catch (IOException e) { getLogger().warning("Fermeture de la mine impossible."); }
                return;
            }
            for (int x=-5;x<=5;x++) for (int z=-5;z<=5;z++) {
                Block b=world.getBlockAt(x,water,z);
                if (b.getType().isAir()) b.setType(Material.WATER,false);
            }
            Bukkit.broadcastMessage("L’eau monte à la hauteur " + water + " !");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getEyeLocation().getY() < water+1 || p.getLocation().getY()<BASE ||
                Math.abs(p.getLocation().getX())>8 || Math.abs(p.getLocation().getZ())>8) {
                bags.remove(p.getUniqueId());
                p.teleport(surface());
                p.sendMessage("Secouru ! Ton sac est perdu, tes dépôts sont conservés.");
            }
        }
    }

    private Location surface() { return new Location(world,3.5,DECK+1,0.5); }

    private void build() {
        water=BASE; seconds=0;
        for (int y=BASE;y<=DECK+3;y++) for(int x=-RADIUS;x<=RADIUS;x++) for(int z=-RADIUS;z<=RADIUS;z++) {
            boolean wall=Math.abs(x)==RADIUS || Math.abs(z)==RADIUS;
            boolean shaft=x==0 && z==0;
            Material m=Material.AIR;
            if (wall || y==BASE || ((y-BASE)%4==0 && y<=DECK && !shaft)) m=Material.STONE_BRICKS;
            else if (shaft && y<=DECK+1) m=Material.SCAFFOLDING;
            else if (y<DECK && (y-BASE)%4==1 && Math.abs(x)==4 && z%2==0)
                m=y<BASE+5 ? Material.DIAMOND_ORE : y<BASE+10 ? Material.GOLD_ORE : Material.IRON_ORE;
            if (wall && (y-BASE)%4==2 && (x==0 || z==0)) m=Material.SEA_LANTERN;
            world.getBlockAt(x,y,z).setType(m,false);
        }
        world.getBlockAt(4,DECK+1,0).setType(Material.BARREL,false);
    }

    @EventHandler public void login(PlayerLoginEvent e) {
        Path ticket=MineAccess.ticket(e.getPlayer().getUniqueId());
        try {
            String[] parts=Files.readString(ticket).split(" ");
            Files.delete(ticket);
            if (parts.length==2 && !round.isEmpty() && round.equals(parts[0]) &&
                round.equals(requestedRound()) && Long.parseLong(parts[1])>=System.currentTimeMillis()) return;
        } catch (IOException | NumberFormatException ignored) { }
        e.disallow(PlayerLoginEvent.Result.KICK_OTHER,"Entre depuis Larry SMP avec /serveur event.");
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p=e.getPlayer();
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(surface());
        p.sendMessage("Descends par l’échafaudage. Touche les minerais, puis touche le tonneau en surface pour déposer tes points avant la crue !");
    }

    @EventHandler public void damage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler public void interact(PlayerInteractEvent e) {
        Block block=e.getClickedBlock();
        if(block==null || e.getHand()!=EquipmentSlot.HAND || round.isEmpty()) return;
        Player p=e.getPlayer();
        UUID id=p.getUniqueId();
        Material m=block.getType();
        if (m==Material.BARREL) {
            e.setCancelled(true);
            int points=bags.getOrDefault(id,0);
            bags.remove(id);
            p.sendMessage("Dépôt : "+points+" points. Total : "+scores.merge(id,points,Integer::sum));
            return;
        }
        int value=switch(m) { case DIAMOND_ORE -> 5; case GOLD_ORE -> 3; case IRON_ORE -> 1; default -> 0; };
        if(value==0 || block.getY()<=water || Math.abs(block.getX())>5 || Math.abs(block.getZ())>5) return;
        e.setCancelled(true);
        block.setType(Material.AIR,false);
        p.sendMessage("Sac : "+bags.merge(id,value,Integer::sum)+" points.");
    }
}
