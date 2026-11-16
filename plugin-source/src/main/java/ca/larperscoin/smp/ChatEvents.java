package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatEvents implements Listener {
    private static final class ActiveEvent {
        final String type, answer;
        final double reward;
        final AtomicBoolean claimed = new AtomicBoolean(false);
        ActiveEvent(String type, String answer, double reward) {
            this.type=type; this.answer=answer; this.reward=reward;
        }
    }

    private final JavaPlugin plugin;
    private final LarpersCoin core;
    private volatile ActiveEvent active;
    private BukkitTask timeout;

    public ChatEvents(JavaPlugin plugin, LarpersCoin core) {
        this.plugin=plugin;
        this.core=core;
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }

    public boolean start(String type,double reward) {
        if(active!=null)return false;
        String answer,question;
        ThreadLocalRandom random=ThreadLocalRandom.current();
        switch(type.toLowerCase(Locale.ROOT)) {
            case "melange" -> {
                String[] words={"diamant","creeper","redstone","netherite","enderdragon","pioche","village","elytra"};
                answer=words[random.nextInt(words.length)];
                List<Character> chars=new ArrayList<>();
                for(char c:answer.toCharArray())chars.add(c);
                Collections.shuffle(chars);
                if(new String(toArray(chars)).equals(answer))Collections.rotate(chars,1);
                question="Remets ce mot Minecraft dans le bon ordre: "+new String(toArray(chars));
            }
            case "quiz" -> {
                String[][] q={
                    {"Quel minerai permet de fabriquer une boussole?","fer"},
                    {"Combien de faces possède un bloc standard?","6"},
                    {"Quel mob explose près du joueur?","creeper"},
                    {"Quel objet permet de voler?","elytra"},
                    {"Dans quelle dimension trouve-t-on les blazes?","nether"},
                    {"Quel minerai sert à fabriquer une pioche en diamant?","diamant"}
                };
                String[] selected=q[random.nextInt(q.length)];
                question=selected[0]; answer=selected[1];
            }
            case "calcul" -> {
                int a=random.nextInt(4,31),b=random.nextInt(3,26),op=random.nextInt(3);
                if(op==0){answer=String.valueOf(a+b);question="Calcul éclair: "+a+" + "+b+" = ?";}
                else if(op==1){answer=String.valueOf(a*b);question="Calcul éclair: "+a+" × "+b+" = ?";}
                else {int high=Math.max(a,b),low=Math.min(a,b);answer=String.valueOf(high-low);question="Calcul éclair: "+high+" - "+low+" = ?";}
            }
            case "reaction" -> {
                String[] words={"LPC","MINECRAFT","LARPERS","DIAMANT","CREEPER"};
                answer=words[random.nextInt(words.length)].toLowerCase(Locale.ROOT);
                question="Premier joueur à écrire "+answer.toUpperCase(Locale.ROOT)+" dans le chat gagne!";
            }
            default -> { return false; }
        }
        active=new ActiveEvent(type.toLowerCase(Locale.ROOT),answer,reward);
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD+"§l✦ ÉVÉNEMENT CHAT LPC ✦");
        Bukkit.broadcastMessage(ChatColor.YELLOW+question);
        Bukkit.broadcastMessage(ChatColor.GRAY+"Récompense: "+ChatColor.GOLD+String.format(Locale.CANADA_FRENCH,"%.0f",reward)+" LPC");
        Bukkit.broadcastMessage(ChatColor.GRAY+"Tu as 45 secondes pour répondre!");
        Bukkit.broadcastMessage("");
        timeout=Bukkit.getScheduler().runTaskLater(plugin,()->{
            ActiveEvent event=active;
            if(event==null||!event.claimed.compareAndSet(false,true))return;
            active=null;timeout=null;
            Bukkit.broadcastMessage(ChatColor.RED+"Temps écoulé! La réponse était: "+ChatColor.YELLOW+event.answer);
        },20L*45L);
        return true;
    }

    private char[] toArray(List<Character> chars) {
        char[] result=new char[chars.size()];
        for(int i=0;i<chars.size();i++)result[i]=chars.get(i);
        return result;
    }

    public boolean stop() {
        ActiveEvent event=active;
        if(event==null)return false;
        active=null;
        if(timeout!=null){timeout.cancel();timeout=null;}
        Bukkit.broadcastMessage(ChatColor.RED+"[ÉVÉNEMENT] L'événement chat a été arrêté par un administrateur.");
        return true;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        ActiveEvent current=active;
        if(current==null||!event.getMessage().trim().equalsIgnoreCase(current.answer))return;
        if(!current.claimed.compareAndSet(false,true))return;
        event.setCancelled(true);
        Player winner=event.getPlayer();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(active!=current)return;
            active=null;
            if(timeout!=null){timeout.cancel();timeout=null;}
            core.credit(winner.getUniqueId(),current.reward);
            core.recordTransaction("CHAT_EVENT_"+current.type.toUpperCase(Locale.ROOT),new UUID(0L,0L),winner.getUniqueId(),current.reward);
            core.saveData();
            Bukkit.broadcastMessage(ChatColor.GREEN+"[ÉVÉNEMENT] "+winner.getName()+" a gagné "+String.format(Locale.CANADA_FRENCH,"%.0f",current.reward)+" LPC!");
        });
    }

    public String status() { return active==null?"Aucun événement chat en cours.":"Événement actif: "+active.type; }
    public void shutdown() { if(timeout!=null)timeout.cancel(); active=null; }
}
