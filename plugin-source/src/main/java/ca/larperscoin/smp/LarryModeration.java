package ca.larperscoin.smp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

final class LarryModeration implements Listener, CommandExecutor {
    private final LarryServiceData store;
    private final LarpersCoin plugin;
    private final Set<UUID> pending=ConcurrentHashMap.newKeySet();

    LarryModeration(LarpersCoin plugin) {
        this.plugin=plugin;
        store=new LarryServiceData(plugin);
        for(String command: new String[]{"mute","unmute","banip","listban"}) plugin.getCommand(command).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,store::refresh,20L,40L);
        if(Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) ModoGroup.install(plugin);
    }
    private void persist(CommandSender sender,UUID id,LarryServiceData.Mute mute){
        if(!pending.add(id)){sender.sendMessage("Une modification est déjà en cours.");return;}
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            boolean ok=store.set(id,mute);
            pending.remove(id);
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!ok){sender.sendMessage("Sauvegarde impossible.");return;}
                sender.sendMessage(mute==null?"Mute retiré.":"Mute appliqué à "+mute.name()+".");
                Player p=Bukkit.getPlayer(id);
                if(p!=null)p.sendMessage(mute==null?"Ton chat est réactivé.":"Chat public désactivé. "+mute.reason());
            });
        });
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String name=command.getName();
        if(!sender.hasPermission("larpers.moderate") && !sender.hasPermission("larpers.admin")){
            sender.sendMessage("Permission refusée.");return true;
        }
        if(name.equals("listban") || name.equals("banip")) {
            String nativeName=name.equals("banip")?"ban-ip":"banlist";
            return Bukkit.dispatchCommand(sender,"minecraft:"+nativeName+" "+String.join(" ",args));
        }
        if(args.length==0)return false;
        Player target=Bukkit.getPlayerExact(args[0]);
        if(name.equals("unmute")){
            UUID id=target!=null?target.getUniqueId():null;
            if(id==null){
                var matches=store.snapshot().entrySet().stream().filter(e->e.getValue().name().equalsIgnoreCase(args[0])||e.getKey().toString().equalsIgnoreCase(args[0])).map(Map.Entry::getKey).toList();
                if(matches.size()==1)id=matches.getFirst();
            }
            if(id==null||!store.snapshot().containsKey(id)){sender.sendMessage("Aucun mute unique trouvé; utilise le UUID ou le pseudo actuel.");return true;}
            persist(sender,id,null);return true;
        }
        long now=System.currentTimeMillis(), expires=0;
        int start=1;
        if(args.length>1 && args[1].matches("(?i)[0-9]+[mhd]")){
            try {
                String d=args[1].toLowerCase(Locale.ROOT);
                long value=Long.parseLong(d.substring(0,d.length()-1));
                long factor=switch(d.charAt(d.length()-1)){case 'm'->60000L;case 'h'->3600000L;default->86400000L;};
                if(value<1||value>365L*86400000/factor)throw new IllegalArgumentException();
                expires=now+value*factor;start=2;
            }catch(IllegalArgumentException ex){sender.sendMessage("Durée invalide (1 minute à 365 jours).");return true;}
        }
        String reason=String.join(" ",Arrays.copyOfRange(args,start,args.length));
        if(reason.length()>500){sender.sendMessage("Raison trop longue.");return true;}
        if(target!=null){
            if(target.isOp()||target.hasPermission("larpers.admin")){sender.sendMessage("Ce joueur est protégé.");return true;}
            persist(sender,target.getUniqueId(),new LarryServiceData.Mute(target.getName(),reason,sender.getName(),now,expires));return true;
        }
        // Offline sanctions require a known account and an administrator. Verify its LuckPerms data first.
        if(!sender.hasPermission("larpers.admin")||!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")){
            sender.sendMessage("Mute hors ligne réservé aux administrateurs avec LuckPerms.");return true;
        }
        OfflinePlayer offline=Bukkit.getOfflinePlayerIfCached(args[0]);
        if(offline==null||!offline.hasPlayedBefore()||offline.isOp()){
            sender.sendMessage("Compte inconnu ou protégé.");return true;
        }
        UUID id=offline.getUniqueId();
        var mute=new LarryServiceData.Mute(offline.getName()==null?args[0]:offline.getName(),reason,sender.getName(),now,expires);
        net.luckperms.api.LuckPermsProvider.get().getUserManager().loadUser(id).whenComplete((user,error)->
            Bukkit.getScheduler().runTask(plugin,()->{
                if(error!=null||user==null||!sender.hasPermission("larpers.admin")){sender.sendMessage("Vérification du compte impossible.");return;}
                if(user.getNodes().stream().anyMatch(node->node.getValue() && (node.getKey().equalsIgnoreCase("larpers.admin")||node.getKey().equalsIgnoreCase("group.admin")||node.getKey().equalsIgnoreCase("group.owner")))||
                   user.getCachedData().getPermissionData().checkPermission("larpers.admin").asBoolean()){
                    sender.sendMessage("Ce joueur est protégé.");return;
                }
                Player current=Bukkit.getPlayer(id);
                if(current!=null&&(current.isOp()||current.hasPermission("larpers.admin"))){sender.sendMessage("Ce joueur est protégé.");return;}
                persist(sender,id,mute);
            }));
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void chat(AsyncPlayerChatEvent event){
        var mute=store.snapshot().get(event.getPlayer().getUniqueId());
        if(mute!=null&&mute.active()){
            event.setCancelled(true);
            event.getPlayer().sendMessage("Ton chat public est désactivé.");
        }
    }
}
