package ca.larperscoin.smp;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

final class ModoGroup {
    static void install(LarpersCoin plugin) {
        var groups=LuckPermsProvider.get().getGroupManager();
        groups.createAndLoadGroup("modo").thenCompose(group -> {
            group.data().add(Node.builder("larpers.modo").build());
            return groups.saveGroup(group);
        }).exceptionally(error->{plugin.getLogger().warning("Groupe modo : "+error.getMessage());return null;});
    }
}
