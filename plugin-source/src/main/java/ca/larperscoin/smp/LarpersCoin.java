package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LarpersCoin extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor, TabCompleter {
    private YamlConfiguration data;
    private LarpersFeatures features;
    private File dataFile;
    private final DecimalFormat money = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.CANADA_FRENCH));

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Impossible de créer le dossier de données.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String name : getDescription().getCommands().keySet()) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        features = new LarpersFeatures(this);
        long week = 20L * 60L * 60L * 24L * 7L;
        getServer().getScheduler().runTaskTimer(this, this::drawLottery, week, week);
        getLogger().info("LarpersCoin 0.8.0 actif. Aucun claim ni protection de parcelle n'est enregistré.");
    }

    @Override
    public void onDisable() {
        if (features != null) features.shutdown();
        saveData();
    }

    void saveData() {
        Path target=dataFile.toPath();
        Path temporary=target.resolveSibling(target.getFileName()+".tmp");
        try {
            byte[] bytes=data.saveToString().getBytes(StandardCharsets.UTF_8);
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException ex) {
            getLogger().severe("Échec de sauvegarde atomique de data.yml: "+ex.getMessage());
            try { Files.deleteIfExists(temporary); } catch(IOException ignored) { }
        }
    }

    private String key(UUID uuid) {
        return uuid.toString();
    }

    double balance(UUID uuid) {
        double value=data.getDouble("balances." + key(uuid),0.0);
        return Double.isFinite(value)&&value>0.0?value:0.0;
    }

    private void setBalance(UUID uuid, double amount) {
        if (!Double.isFinite(amount)) return;
        data.set("balances." + key(uuid), Math.max(0.0, amount));
        saveData();
    }

    boolean debit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || balance(uuid) + 1.0e-9 < amount) return false;
        data.set("balances." + key(uuid), Math.max(0.0,balance(uuid) - amount));
        return true;
    }

    void credit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        double next = balance(uuid) + amount;
        if (!Double.isFinite(next)) {
            getLogger().warning("Crédit refusé: dépassement numérique pour " + uuid);
            return;
        }
        data.set("balances." + key(uuid), next);
    }
    void recordTransaction(String type, UUID from, UUID to, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        List<String> records = data.getStringList("transactions");
        records.add(System.currentTimeMillis() + "|" + type + "|" + from + "|" + to + "|" + amount);
        if (records.size() > 2000) records = new ArrayList<>(records.subList(records.size() - 2000, records.size()));
        data.set("transactions", records);
        saveData();
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[LPC] " + ChatColor.RESET + message);
    }

    private String format(double amount) {
        return money.format(amount) + " LPC";
    }

    private boolean isStaff(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("larpers.staff") || sender.hasPermission("larpers.admin");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        reply(sender, "Cette commande doit être utilisée en jeu.");
        return null;
    }

    private Double parseAmount(String raw) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            return Double.isFinite(value) && value > 0.0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        try {
            switch (name) {
                case "balance" -> {
                    if (args.length == 0) {
                        Player player = requirePlayer(sender);
                        if (player == null) return true;
                        reply(sender, "Ton solde : " + format(balance(player.getUniqueId())));
                    } else {
                        if (!isStaff(sender)) {
                            reply(sender, "Permission insuffisante.");
                            return true;
                        }
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                        reply(sender, "Solde de " + args[0] + " : " + format(balance(target.getUniqueId())));
                    }
                    return true;
                }
                case "pay" -> {
                    Player player = requirePlayer(sender);
                    if (player == null) return true;
                    if (args.length != 2) {
                        reply(sender, "Usage : /pay <joueur> <montant>");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    Double amount = parseAmount(args[1]);
                    if (target == null || target.equals(player) || amount == null) {
                        reply(sender, "Joueur en ligne ou montant invalide.");
                        return true;
                    }
                    if (!Double.isFinite(balance(target.getUniqueId()) + amount)) {
                        reply(sender, "Le solde du destinataire dépasserait la limite numérique.");
                        return true;
                    }
                    if (!debit(player.getUniqueId(), amount)) {
                        reply(sender, "Fonds insuffisants.");
                        return true;
                    }
                    credit(target.getUniqueId(), amount);
                    saveData();
                    recordTransaction("PAY", player.getUniqueId(), target.getUniqueId(), amount);
                    reply(player, "Tu as envoyé " + format(amount) + " à " + target.getName() + ".");
                    reply(target, "Tu as reçu " + format(amount) + " de " + player.getName() + ".");
                    return true;
                }
                case "history" -> {
                    if(args.length>1){reply(sender,"Usage : /history [joueur]");return true;}
                    UUID targetId;
                    String targetName;
                    if (args.length == 0) {
                        Player player = requirePlayer(sender);
                        if (player == null) return true;
                        targetId = player.getUniqueId();
                        targetName = player.getName();
                    } else {
                        if (!isStaff(sender)) { reply(sender, "Permission insuffisante."); return true; }
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                        targetId = target.getUniqueId();
                        targetName = args[0];
                    }
                    List<String> records = data.getStringList("transactions");
                    int shown = 0;
                    reply(sender, "Historique LPC de " + targetName + " (10 dernières opérations) :");
                    for (int i = records.size() - 1; i >= 0 && shown < 10; i--) {
                        String[] parts = records.get(i).split("\\|");
                        if (parts.length != 5) continue;
                        if (!parts[2].equals(targetId.toString()) && !parts[3].equals(targetId.toString())) continue;
                        long stamp;
                        try { stamp = Long.parseLong(parts[0]); } catch (NumberFormatException ex) { continue; }
                        String direction = parts[2].equals(targetId.toString()) ? "-" : "+";
                        String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(stamp));
                        reply(sender, date + " " + parts[1] + " " + direction + parts[4] + " LPC");
                        shown++;
                    }
                    if (shown == 0) reply(sender, "Aucune transaction enregistrée.");
                    return true;
                }
                case "baltop" -> {
                    var section = data.getConfigurationSection("balances");
                    if (section == null || section.getKeys(false).isEmpty()) {
                        reply(sender, "Aucun solde enregistré.");
                        return true;
                    }
                    List<String> ids = new ArrayList<>(section.getKeys(false));
                    ids.sort(Comparator.comparingDouble((String id) -> data.getDouble("balances." + id)).reversed());
                    reply(sender, "§6--- Classement des fortunes LPC ---");
                    int rank = 1;
                    for (String id : ids) {
                        if (rank > 10) break;
                        OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(id));
                        String playerName = offline.getName() == null ? id.substring(0, 8) : offline.getName();
                        reply(sender, rank + ". " + playerName + " : " + format(data.getDouble("balances." + id)));
                        rank++;
                    }
                    return true;
                }
                case "daily" -> {
                    Player player = requirePlayer(sender);
                    if (player == null) return true;
                    String id = key(player.getUniqueId());
                    long now = System.currentTimeMillis();
                    long previous = data.getLong("daily." + id, 0L);
                    long cooldown = 86_400_000L;
                    if (now - previous < cooldown) {
                        long hours = (cooldown - (now - previous) + 3_599_999L) / 3_600_000L;
                        reply(sender, "Tu as déjà réclamé ta récompense. Reviens dans environ " + hours + " h.");
                        return true;
                    }
                    long previousDay = previous;
                    int previousStreak = data.getInt("streak." + id, 0);
                    int streak = previousDay > 0 && now - previousDay <= 172_800_000L
                            ? previousStreak + 1 : 1;
                    double reward = 100.0 + Math.min(streak, 7) * 20.0;
                    if (!Double.isFinite(balance(player.getUniqueId()) + reward)) {
                        reply(sender, "Ton solde ne peut pas recevoir cette récompense.");
                        return true;
                    }
                    data.set("daily." + id, now);
                    data.set("streak." + id, streak);
                    credit(player.getUniqueId(), reward);
                    saveData();
                    recordTransaction("DAILY", new UUID(0L,0L), player.getUniqueId(), reward);
                    reply(sender, "Récompense quotidienne : +" + format(reward) + " (série " + streak + ").");
                    return true;
                }
                case "bounty" -> {
                    Player player = requirePlayer(sender);
                    if (player == null) return true;
                    if (args.length != 2) {
                        reply(sender, "Usage : /bounty <joueur> <montant>");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    Double amount = parseAmount(args[1]);
                    if (target == null || target.equals(player) || amount == null) {
                        reply(sender, "Cible ou montant invalide.");
                        return true;
                    }
                    String targetId = key(target.getUniqueId());
                    double nextBounty = data.getDouble("bounties." + targetId) + amount;
                    if (!Double.isFinite(nextBounty)) {
                        reply(sender, "La prime totale dépasserait la limite numérique.");
                        return true;
                    }
                    if (!debit(player.getUniqueId(), amount)) {
                        reply(sender, "Fonds insuffisants.");
                        return true;
                    }
                    data.set("bounties." + targetId, nextBounty);
                    saveData();
                    recordTransaction("BOUNTY_PLACE", player.getUniqueId(), new UUID(0L,0L), amount);
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[LPC] " + player.getName() + " place une prime de " + format(amount) + " sur " + target.getName() + " !");
                    return true;
                }
                case "eco" -> {
                    if (!sender.hasPermission("larpers.admin")) {
                        reply(sender, "Permission insuffisante.");
                        return true;
                    }
                    if (args.length != 3) {
                        reply(sender, "Usage : /eco <give|take|set> <joueur> <montant>");
                        return true;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    Double amount = parseAmount(args[2]);
                    if (amount == null) {
                        reply(sender, "Montant invalide.");
                        return true;
                    }
                    double oldBalance = balance(target.getUniqueId());
                    if (args[0].equalsIgnoreCase("give") && !Double.isFinite(oldBalance + amount)) {
                        reply(sender, "Le solde du joueur dépasserait la limite numérique.");
                        return true;
                    }
                    double recordedAmount = amount;
                    UUID systemAccount = new UUID(0L,0L);
                    switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "give" -> credit(target.getUniqueId(), amount);
                        case "take" -> {
                            recordedAmount = Math.min(oldBalance, amount);
                            data.set("balances." + key(target.getUniqueId()), oldBalance - recordedAmount);
                        }
                        case "set" -> {
                            recordedAmount = Math.abs(amount - oldBalance);
                            data.set("balances." + key(target.getUniqueId()), amount);
                        }
                        default -> {
                            reply(sender, "Action invalide : give, take ou set.");
                            return true;
                        }
                    }
                    saveData();
                    if (recordedAmount > 0) {
                        String action = args[0].toUpperCase(Locale.ROOT);
                        if (action.equals("TAKE") || action.equals("SET") && amount < oldBalance)
                            recordTransaction("ECO_" + action, target.getUniqueId(), systemAccount, recordedAmount);
                        else recordTransaction("ECO_" + action, systemAccount, target.getUniqueId(), recordedAmount);
                    }
                    reply(sender, "Solde de " + args[1] + " : " + format(balance(target.getUniqueId())));
                    return true;
                }
                case "lottery" -> {
                    if (args.length == 0 || args[0].equalsIgnoreCase("pot")) {
                        reply(sender, "Jackpot actuel : " + format(data.getDouble("lottery.pot", 0.0)));
                        reply(sender, "Un ticket coûte 25 LPC. /lottery buy [1-10]");
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("buy")) {
                        Player player = requirePlayer(sender);
                        if (player == null) return true;
                        int count = args.length >= 2 ? Integer.parseInt(args[1]) : 1;
                        if (count < 1 || count > 10) {
                            reply(sender, "Tu peux acheter de 1 à 10 tickets.");
                            return true;
                        }
                        double cost = count * 25.0;
                        double nextPot = data.getDouble("lottery.pot",0.0) + cost;
                        if (!Double.isFinite(nextPot)) {
                            reply(sender, "Le jackpot dépasserait la limite numérique.");
                            return true;
                        }
                        if (!debit(player.getUniqueId(), cost)) {
                            reply(sender, "Fonds insuffisants : " + format(cost) + ".");
                            return true;
                        }
                        List<String> tickets = data.getStringList("lottery.tickets");
                        for (int i = 0; i < count; i++) tickets.add(key(player.getUniqueId()));
                        data.set("lottery.tickets", tickets);
                        data.set("lottery.pot", nextPot);
                        saveData();
                        recordTransaction("LOTTERY_BUY", player.getUniqueId(), new UUID(0L,0L), cost);
                        reply(sender, "Tickets achetés : " + count + ". Jackpot : " + format(data.getDouble("lottery.pot")));
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("draw") && isStaff(sender)) {
                        drawLottery();
                        reply(sender, "Tirage effectué.");
                        return true;
                    }
                    reply(sender, "Usage : /lottery buy [1-10] | pot | draw (staff)");
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (NumberFormatException ex) {
            reply(sender, "Nombre invalide.");
            return true;
        } catch (RuntimeException ex) {
            getLogger().warning("Erreur commande /" + name + " : " + ex.getMessage());
            reply(sender, "Une erreur est survenue. Consulte la console.");
            return true;
        }
    }

    private void drawLottery() {
        List<String> tickets = data.getStringList("lottery.tickets");
        if (tickets.isEmpty()) return;
        String winnerId = tickets.get(ThreadLocalRandom.current().nextInt(tickets.size()));
        double jackpot = data.getDouble("lottery.pot", 0.0);
        UUID winnerUuid = UUID.fromString(winnerId);
        if (!Double.isFinite(jackpot) || jackpot <= 0.0 || !Double.isFinite(balance(winnerUuid) + jackpot)) {
            getLogger().severe("Tirage annulé: jackpot ou solde du gagnant invalide.");
            return;
        }
        credit(winnerUuid, jackpot);
        data.set("lottery.tickets", new ArrayList<String>());
        data.set("lottery.pot", 0.0);
        saveData();
        recordTransaction("LOTTERY_WIN", new UUID(0L,0L), winnerUuid, jackpot);
        OfflinePlayer winner = Bukkit.getOfflinePlayer(UUID.fromString(winnerId));
        Bukkit.broadcastMessage(ChatColor.GOLD + "[LPC] " + (winner.getName() == null ? "Un joueur" : winner.getName()) + " remporte la loterie : " + format(jackpot) + " !");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        String victimId = key(event.getEntity().getUniqueId());
        double bounty = data.getDouble("bounties." + victimId, 0.0);
        if (!Double.isFinite(bounty) || bounty <= 0.0) return;
        if (!Double.isFinite(balance(killer.getUniqueId()) + bounty)) {
            getLogger().warning("Prime conservée: solde maximal atteint pour " + killer.getName());
            return;
        }
        data.set("bounties." + victimId, 0.0);
        credit(killer.getUniqueId(), bounty);
        saveData();
        recordTransaction("BOUNTY_WIN", new UUID(0L,0L), killer.getUniqueId(), bounty);
        Bukkit.broadcastMessage(ChatColor.GOLD + "[LPC] " + killer.getName() + " a récupéré la prime de " + format(bounty) + " sur " + event.getEntity().getName() + " !");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("eco") && args.length == 1 && sender.hasPermission("larpers.admin")) {
            return List.of("give", "take", "set").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (command.getName().equalsIgnoreCase("lottery") && args.length == 1) {
            return List.of("buy", "pot", "draw").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
