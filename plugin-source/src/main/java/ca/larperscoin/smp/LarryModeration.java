package ca.larperscoin.smp;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

final class LarryModeration implements Listener, CommandExecutor {
    private final LarryServiceData store;
    private final Map<UUID,String> muted=new ConcurrentHashMap<>();

    LarryModeration(LarpersCoin plugin) {
        store=new LarryServiceData(plugin);
        for(String id:store.ids()) {
            try { String name=store.name(id); if(name!=null && !name.isBlank()) muted.put(UUID.fromString(id),name); }
            catch(IllegalArgumentException ex) { plugin.getLogger().warning("Invalid mute UUID: "+id); }
        }
        for(String command: new String[]{"mute","unmute","banip","listban"})
            plugin.getCommand(command).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        if(Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) ModoGroup.install(plugin);
    }

    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("larpers.staff") && !sender.hasPermission("larpers.admin")) { sender.sendMessage("Permission refusée."); return true; }
        String name=command.getName();
        if(name.equals("listban") || name.equals("banip")) {
            String nativeName=name.equals("banip") ? "ban-ip" : "banlist";
            return Bukkit.dispatchCommand(sender,"minecraft:"+nativeName+" "+String.join(" ",args));
        }
        if(args.length==0) return false;
        Player target=Bukkit.getPlayerExact(args[0]);
        if(name.equals("mute")) {
            if(target==null) { sender.sendMessage("Le joueur doit être connecté."); return true; }
            if(target.isOp() || target.hasPermission("larpers.admin")) {
                sender.sendMessage("Ce joueur est protégé."); return true;
            }
            if(!store.set(target.getUniqueId(),target.getName())) { sender.sendMessage("Sauvegarde impossible."); return true; }
            muted.put(target.getUniqueId(),target.getName());
            target.sendMessage("Chat public désactivé. "+String.join(" ",Arrays.copyOfRange(args,1,args.length)));
            sender.sendMessage("Mute appliqué à "+target.getName()+".");
        } else {
            UUID id=target!=null ? target.getUniqueId() : muted.entrySet().stream()
                .filter(entry->entry.getValue().equalsIgnoreCase(args[0])).map(Map.Entry::getKey).findFirst().orElse(null);
            if(id==null || !muted.containsKey(id)) { sender.sendMessage("Aucun mute trouvé."); return true; }
            if(!store.set(id,null)) { sender.sendMessage("Sauvegarde impossible."); return true; }
            muted.remove(id);
            sender.sendMessage("Mute retiré.");
        }
        return true;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void chat(AsyncPlayerChatEvent event) {
        if(muted.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Ton chat public est désactivé.");
        }
    }
}
