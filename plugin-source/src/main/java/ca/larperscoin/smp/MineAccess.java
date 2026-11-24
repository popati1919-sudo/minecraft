package ca.larperscoin.smp;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class MineAccess {
    static final Path directory = Path.of("../Event");
    static final Path session = directory.resolve("mine-active");
    static Path ticket(UUID id) { return directory.resolve("mine-entry-" + id); }

    static void control(CommandSender sender, String[] args) {
        try {
            if (!Files.isDirectory(directory)) { sender.sendMessage("Serveur Event absent."); return; }
            if (args.length == 2 && args[0].equalsIgnoreCase("start") && args[1].equalsIgnoreCase("mine")) {
                Files.writeString(session, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
                sender.sendMessage("Mine ouverte. Rejoins-la avec /serveur event.");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
                sender.sendMessage(Files.deleteIfExists(session) ? "Mine fermée." : "La mine est déjà fermée.");
            } else sender.sendMessage("/admin start mine | /admin stop");
        } catch (FileAlreadyExistsException e) { sender.sendMessage("La mine est déjà ouverte."); }
        catch (IOException e) { sender.sendMessage("Impossible de modifier la session Event."); }
    }

    static void join(JavaPlugin plugin, Player player) {
        try {
            String round = Files.readString(session);
            if (round.isBlank()) { player.sendMessage("Ouverture en cours."); return; }
            Files.writeString(ticket(player.getUniqueId()), round + " " + (System.currentTimeMillis() + 15000));
            transfer(plugin,player,"event");
        } catch (IOException e) { player.sendMessage("La mine est fermée ou indisponible."); }
    }

    static void transfer(JavaPlugin plugin, Player player, String server) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin,"BungeeCord",bytes.toByteArray());
        } catch (IOException e) { player.sendMessage("Transfert impossible."); }
    }
}
