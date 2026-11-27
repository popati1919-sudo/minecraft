package ca.larperscoin.smp;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

final class ModoGroup {
    private static final java.util.Set<String> RANKS=java.util.Set.of("default","joueur","modo","moderator","mod","admin","owner");
    static void assign(LarpersCoin plugin,org.bukkit.entity.Player admin,org.bukkit.entity.Player player,String rank) {
        if(!plugin.isLpcAdmin(admin))return;
        if(!admin.isOp() && (player.isOp() || player.hasPermission("group.owner") || rank.equals("owner"))) {
            admin.sendMessage("Seul un opérateur peut gérer le grade Owner ou celui d’un opérateur.");return;
        }
        var lp=LuckPermsProvider.get();
        var groups=lp.getGroupManager();
        var group=groups.getGroup(rank);
        if(group==null){admin.sendMessage("Groupe LuckPerms indisponible : "+rank);return;}
        var user=lp.getUserManager().getUser(player.getUniqueId());
        if(user==null){admin.sendMessage("Profil LuckPerms indisponible.");return;}
        user.data().clear(node -> node instanceof net.luckperms.api.node.types.InheritanceNode parent &&
            node.getContexts().isEmpty() && RANKS.contains(parent.getGroupName()));
        user.data().add(net.luckperms.api.node.types.InheritanceNode.builder(rank).build());
        user.setPrimaryGroup(rank);
        String label=rank.equals("default")?"joueur":rank;
        lp.getUserManager().saveUser(user).whenComplete((unused,error)->
            org.bukkit.Bukkit.getScheduler().runTask(plugin,()->{
                if(error!=null){admin.sendMessage("Échec de sauvegarde du grade.");return;}
                admin.sendMessage(player.getName()+" : grade "+label+".");
                player.sendMessage("Ton grade sur le SMP est maintenant "+label+".");
                plugin.getLogger().info(admin.getName()+" attribue le grade "+label+" à "+player.getName());
            }));
    }

    static void install(LarpersCoin plugin) {
        var groups=LuckPermsProvider.get().getGroupManager();
        for(String rank:java.util.List.of("admin","owner")) {
            groups.createAndLoadGroup(rank).thenCompose(group->{
                group.data().add(Node.builder("larpers.admin").build());
                return groups.saveGroup(group);
            }).exceptionally(error->{plugin.getLogger().warning("Groupe "+rank+" : "+error.getMessage());return null;});
        }
        groups.createAndLoadGroup("modo").thenCompose(group -> {
            group.data().add(Node.builder("larpers.modo").build());
            return groups.saveGroup(group);
        }).exceptionally(error->{plugin.getLogger().warning("Groupe modo : "+error.getMessage());return null;});
    }
}
