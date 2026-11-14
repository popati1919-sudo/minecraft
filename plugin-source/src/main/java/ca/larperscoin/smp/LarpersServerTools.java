package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LarpersServerTools implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final LarpersCoin core;
    private final File file;
    private final YamlConfiguration settings;
    private String activeAnswer;
    private String activeEvent;
    private long eventEndsAt;

    public LarpersServerTools(JavaPlugin plugin, LarpersCoin core) {
        this.plugin = plugin;
        this.core = core;
        file = new File(plugin.getDataFolder(), "server-display.yml");
        settings = YamlConfiguration.loadConfiguration(file);
        settings.addDefault("display.name", "Larpers Coin SMP");
        settings.addDefault("display.ip", "À configurer");
        settings.options().copyDefaults(true);
        save();
        for (String name : List.of("event", "modif")) {
            if (plugin.getCommand(name) != null) {
                plugin.getCommand(name).setExecutor(this);
                plugin.getCommand(name).setTabCompleter(this);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTab, 40L, 40L);
        Bukkit.getWorlds().forEach(w -> {
            try { w.setGameRule(org.bukkit.GameRule.LOCATOR_BAR, false); }
            catch (IllegalArgumentException ignored) { plugin.getLogger().fine("Règle locator_bar indisponible sur cette version."); }
        });
    }

    private void save() {
        try { settings.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Impossible d'enregistrer server-display.yml: " + ex.getMessage()); }
    }

    private boolean admin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("larpers.admin");
    }

    private void tell(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[LPC] " + ChatColor.RESET + message);
    }

    private void refreshTab() {
        String serverName = settings.getString("display.name", "Larpers Coin SMP");
        String ip = settings.getString("display.ip", "À configurer");
        for (Player player : Bukkit.getOnlinePlayers()) {
            String header = ChatColor.GOLD + "✦ " + serverName + " ✦\n" +
                    ChatColor.GRAY + "IP: " + ChatColor.AQUA + ip;
            String footer = ChatColor.YELLOW + "Solde: " + ChatColor.GOLD +
                    String.format(Locale.CANADA_FRENCH, "%,.2f", core.balance(player.getUniqueId())) + " LPC" +
                    ChatColor.DARK_GRAY + "  •  " + ChatColor.GREEN + "Ping: " + player.getPing() + " ms";
            player.setPlayerListHeaderFooter(header, footer);
        }
    }

    @EventHandler public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setGameRule(org.bukkit.GameRule.LOCATOR_BAR, false);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::refreshTab);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        // Aucun état individuel à conserver.
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("modif")) {
            if (!admin(sender)) { tell(sender, "Commande réservée aux administrateurs."); return true; }
            if (args.length < 2) { tell(sender, "Usage: /modif ip <adresse> ou /modif nom <nom>"); return true; }
            String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            if (value.length() > 64 || value.isBlank() || value.contains("\n") || value.contains("\r")) {
                tell(sender, "Valeur invalide (1 à 64 caractères)."); return true;
            }
            if (args[0].equalsIgnoreCase("ip")) {
                if (!value.matches("[A-Za-z0-9._:-]+")) { tell(sender, "Adresse invalide."); return true; }
                settings.set("display.ip", value);
            } else if (args[0].equalsIgnoreCase("nom")) {
                settings.set("display.name", value);
            } else {
                tell(sender, "Choisis ip ou nom."); return true;
            }
            save();
            refreshTab();
            tell(sender, "Affichage du serveur mis à jour.");
            return true;
        }

        if (!admin(sender)) { tell(sender, "Seuls les administrateurs peuvent lancer un événement."); return true; }
        if (args.length != 1) { tell(sender, "Usage: /event <calcul|motmelange|devine>"); return true; }
        if (activeAnswer != null && System.currentTimeMillis() < eventEndsAt) {
            tell(sender, "Un événement est déjà en cours."); return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        String question;
        if (type.equals("calcul")) {
            int a = ThreadLocalRandom.current().nextInt(2, 21);
            int b = ThreadLocalRandom.current().nextInt(2, 21);
            activeAnswer = Integer.toString(a * b);
            question = "Combien font " + a + " × " + b + " ?";
        } else if (type.equals("motmelange")) {
            String[] words = {"diamant", "creeper", "redstone", "squelette", "village"};
            String word = words[ThreadLocalRandom.current().nextInt(words.length)];
            activeAnswer = word;
            question = "Remets les lettres dans l'ordre: " + shuffle(word);
        } else if (type.equals("devine")) {
            int number = ThreadLocalRandom.current().nextInt(1, 21);
            activeAnswer = Integer.toString(number);
            question = "Devine le nombre entre 1 et 20!";
        } else {
            tell(sender, "Événement inconnu. Choisis calcul, motmelange ou devine."); return true;
        }
        activeEvent = type;
        eventEndsAt = System.currentTimeMillis() + 60_000L;
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "✦ ÉVÉNEMENT LPC ✦ " + ChatColor.YELLOW + question);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Réponds dans le chat! Récompense: 100 LPC. Durée maximale: 60 secondes.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeAnswer != null && activeEvent != null && System.currentTimeMillis() >= eventEndsAt) {
                Bukkit.broadcastMessage(ChatColor.RED + "Fin de l'événement! Réponse: " + activeAnswer);
                activeAnswer = null; activeEvent = null;
            }
        }, 1800L);
        return true;
    }

    private String shuffle(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        Collections.shuffle(chars);
        StringBuilder result = new StringBuilder();
        for (char c : chars) result.append(c);
        return result.toString();
    }

    @EventHandler public void onAnswer(AsyncPlayerChatEvent event) {
        if (activeAnswer == null || System.currentTimeMillis() >= eventEndsAt) return;
        if (!event.getMessage().trim().equalsIgnoreCase(activeAnswer)) return;
        event.setCancelled(true);
        Player winner = event.getPlayer();
        synchronized (this) {
            if (activeAnswer == null) return;
            activeAnswer = null; activeEvent = null;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            core.credit(winner.getUniqueId(), 100.0);
            core.saveData();
            core.recordTransaction("CHAT_EVENT", new UUID(0L, 0L), winner.getUniqueId(), 100.0);
            Bukkit.broadcastMessage(ChatColor.GREEN + winner.getName() + " a gagné l'événement et remporte 100 LPC!");
        });
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!admin(sender)) return List.of();
        if (command.getName().equalsIgnoreCase("modif") && args.length == 1)
            return List.of("ip", "nom").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (command.getName().equalsIgnoreCase("event") && args.length == 1)
            return List.of("calcul", "motmelange", "devine").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
