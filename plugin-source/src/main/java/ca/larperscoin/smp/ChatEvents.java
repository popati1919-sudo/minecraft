package ca.larperscoin.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.Normalizer;
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

    private static final String[] WORDS = {
        "diamant","creeper","redstone","netherite","enderdragon","pioche","village","elytra",
        "obsidienne","emeraude","squelette","zombie","araignee","enderman","blaze","ghast",
        "phantom","warden","allay","axolotl","tortue","dauphin","renard","lama","chameau",
        "cheval","cochon","mouton","vache","poulet","abeille","villageois","pillager",
        "ravageur","sorcier","noyé","gardien","poisson","saumon","morue","calmar",
        "glowstone","redstone","piston","levier","bouton","comparateur","repeteur","entonnoir",
        "distributeur","observateur","detecteur","rail","minecart","bateau","trident","bouclier",
        "arbalete","arc","fleche","epee","hache","pelle","houe","enclume","forge","fourneau",
        "fumoir","hautfourneau","bibliotheque","etable","portail","forteresse","bastion","ruine",
        "monument","manoir","temple","donjon","mineshaft","geode","amethyste","cuivre",
        "fer","or","charbon","lapis","quartz","prismarine","sculk","crafter","craie",
        "citrouille","pastèque","melon","carotte","patate","betterave","ble","canneasucre",
        "bambou","cactus","chorus","nenuphar","champignon","fleur","tulipe","orchidee",
        "coquelicot","bleuet","tournesol","lilas","pivoine","mangrove","cerisier","acacia",
        "bouleau","chene","sapin","jungle","darkoak","poudre","potion","breuvage","experience",
        "enchantement","bibliothecaire","cartographe","forgeron","armurier","boucher","clerc",
        "pecheur","berger","flecheur","maçon","composteur","cloche","lanterne","torche",
        "feuxdartifice","elytre","perle","oeuf","totem","boussole","horloge","carte","banniere",
        "coffre","baril","shulker","balise","conduit","ancre","respawn","lit","laine"
    };

    private static final String[][] QUIZ = {
        {"Quel minerai faut-il pour fabriquer une boussole?","fer"},
        {"Quel mob explose près du joueur?","creeper"},
        {"Quel objet permet de voler dans l'End?","elytra"},
        {"Dans quelle dimension trouve-t-on les blazes?","nether"},
        {"Quel objet active un portail de l'End?","oeil de l'end"},
        {"Combien de côtés possède un bloc cubique?","6"},
        {"Quel animal donne du cuir?","vache"},
        {"Quel mob téléporte?","enderman"},
        {"Quel minerai bleu sert aux enchantements?","lapis-lazuli"},
        {"Quel boss se trouve dans l'End?","enderdragon"},
        {"Quel boss apparaît dans les océans avec trois têtes?","aucun"},
        {"Quel objet permet de respirer sous l'eau?","conduit"},
        {"Quel minerai est nécessaire pour fabriquer une armure en netherite?","lingot de netherite"},
        {"Quel mob garde les forteresses du Nether et lance des boules de feu?","blaze"},
        {"Quel bloc sert à enchanter les objets?","table d'enchantement"},
        {"Quel outil récolte la pierre plus vite?","pioche"},
        {"Quel objet permet de repousser les projectiles?","bouclier"},
        {"Quel animal peut être monté avec une selle?","cheval"},
        {"Quel mob laisse tomber des os?","squelette"},
        {"Quel mob apparaît dans les villages la nuit?","zombie"},
        {"Quel bloc fait pousser les cultures?","terre labourée"},
        {"Quel objet permet de revenir à son point d'apparition dans le Nether?","ancre de réapparition"},
        {"Quel minerai donne des émeraudes au villageois?","aucun"},
        {"Quel bloc fait fondre les minerais?","fourneau"},
        {"Quel bloc cuisine la nourriture rapidement?","fumoir"},
        {"Quel bloc fond les minerais rapidement?","haut fourneau"},
        {"Quel objet permet de stocker de l'eau?","seau"},
        {"Quel mob protège les villages?","golem de fer"},
        {"Quel objet attire les éclairs?","paratonnerre"},
        {"Quel bloc permet de faire du commerce avec un cartographe?","table de cartographie"},
        {"Quel objet permet de voir les coordonnées?","écran de débogage"},
        {"Quel enchantement permet de marcher sur l'eau?","marcheur des glaces"},
        {"Quel enchantement augmente les dégâts sous l'eau?","empalement"},
        {"Quel enchantement permet de réparer avec l'expérience?","raccommodage"},
        {"Quel objet invoque un éclair lorsqu'il est lancé?","trident"},
        {"Quel mob nage dans les océans et attaque en groupe?","gardien"},
        {"Quel animal donne de la laine?","mouton"},
        {"Quel animal donne des plumes?","poulet"},
        {"Quel animal produit du miel?","abeille"},
        {"Quel animal peut transporter un coffre?","lama"},
        {"Quel mob danse avec un disque musical?","allay"},
        {"Quel mob bleu peut ramasser des objets?","allay"},
        {"Quel mob ancien détecte les vibrations?","warden"},
        {"Quel bloc est nécessaire pour fabriquer un portail du Nether?","obsidienne"},
        {"Combien d'obsidiennes minimum faut-il pour un portail du Nether?","10"},
        {"Quel objet allume un portail du Nether?","briquet"},
        {"Quel minerai se trouve principalement dans le Nether?","quartz"},
        {"Quel bloc lumineux se trouve dans le Nether?","glowstone"},
        {"Quel mob lance des boules de feu dans le Nether?","ghast"},
        {"Quel objet permet de récupérer les ruches sans casser le miel?","cisaille"},
        {"Quel outil récolte les feuilles?","cisaille"},
        {"Quel bloc permet de faire pousser les champignons géants?","mycelium"},
        {"Quel aliment apprivoise les loups?","os"},
        {"Quel aliment attire les cochons?","carotte"},
        {"Quel aliment attire les vaches?","blé"},
        {"Quel aliment attire les poulets?","graines"},
        {"Quel aliment attire les tortues?","herbe aquatique"},
        {"Quel objet permet de pêcher?","canne à pêche"},
        {"Quel objet permet de se téléporter en le lançant?","perle de l'Ender"},
        {"Quel objet protège de la mort une fois?","totem d'immortalité"},
        {"Quel bloc permet de réapparaître dans le monde normal?","lit"},
        {"Quel bloc permet de stocker des objets comme un coffre?","baril"},
        {"Quel coffre peut être transporté avec son contenu?","coffre de shulker"},
        {"Quel bloc donne un effet de puissance dans une zone?","balise"},
        {"Quel objet permet de localiser le manoir?","carte d'exploration"},
        {"Quel villageois échange des livres enchantés?","bibliothécaire"},
        {"Quel villageois échange des cartes d'exploration?","cartographe"},
        {"Quel villageois travaille avec une meule?","forgeron d'armes"},
        {"Quel bloc transforme un villageois en pêcheur?","tonneau"},
        {"Quel bloc transforme un villageois en fermier?","composteur"},
        {"Quel bloc transforme un villageois en bibliothécaire?","pupitre"},
        {"Quel bloc transforme un villageois en clerc?","alambic"},
        {"Quel bloc transforme un villageois en armurier?","haut fourneau"},
        {"Quel bloc transforme un villageois en boucher?","fumoir"},
        {"Quel bloc transforme un villageois en fléchier?","table d'archerie"},
        {"Quel bloc permet de réparer et combiner des objets?","enclume"},
        {"Quel bloc retire les enchantements?","meule"},
        {"Quel objet permet de copier une carte?","carte vierge"},
        {"Quel objet indique le nord et le point d'apparition?","boussole"},
        {"Quel objet indique l'heure?","horloge"},
        {"Quel bloc pousse les autres blocs?","piston"},
        {"Quel bloc détecte les changements devant lui?","observateur"},
        {"Quel composant transmet un signal redstone?","poudre de redstone"},
        {"Quel composant retarde un signal redstone?","répéteur"},
        {"Quel composant compare les signaux redstone?","comparateur"},
        {"Quel bloc aspire les objets?","entonnoir"},
        {"Quel bloc lance les objets?","distributeur"},
        {"Quel bloc détecte les joueurs et entités?","plaque de pression"},
        {"Quel matériau sert à fabriquer une torche?","charbon"},
        {"Quel minerai donne du cuivre?","cuivre"},
        {"Quel minerai donne du lapis-lazuli?","lapis-lazuli"},
        {"Quel minerai donne du diamant?","diamant"},
        {"Quel minerai donne de la redstone?","redstone"},
        {"Quel minerai donne de l'or?","or"},
        {"Quel minerai donne du fer?","fer"},
        {"Quel bloc violet se trouve dans les géodes?","améthyste"},
        {"Quel fruit pousse sur les arbres de l'End?","fruit chorus"},
        {"Quel objet permet de grimper sur les murs?","échelle"},
        {"Quel bloc ralentit les entités avec de la toile?","toile d'araignée"},
        {"Quel mob laisse tomber de la poudre à canon?","creeper"},
        {"Quel mob laisse tomber des ficelles?","araignée"},
        {"Quel mob laisse tomber des perles de l'Ender?","enderman"},
        {"Quel mob laisse tomber des membranes?","phantom"},
        {"Quel mob laisse tomber des larmes?","ghast"},
        {"Quel mob laisse tomber des bâtons de blaze?","blaze"},
        {"Quel mob laisse tomber des coquilles de nautile?","noyé"},
        {"Quel mob laisse tomber des sacs d'encre?","calmar"},
        {"Quel mob laisse tomber des sacs lumineux?","calmar lumineux"},
        {"Quel mob peut être apprivoisé avec du poisson?","chat"},
        {"Quel mob peut être apprivoisé avec des os?","loup"},
        {"Quel mob peut être apprivoisé avec des graines?","perroquet"},
        {"Quel mob peut être élevé avec du bambou?","panda"},
        {"Quel biome contient des cactus?","désert"},
        {"Quel biome contient des champignons géants?","champs de champignons"},
        {"Quel biome contient des pandas?","jungle"},
        {"Quel biome contient des ours polaires?","banquise"},
        {"Quel biome contient des mangroves?","marais de mangrove"},
        {"Quel biome contient des cerisiers roses?","bosquet de cerisiers"},
        {"Quel bloc permet de fabriquer des potions?","alambic"},
        {"Quel ingrédient rend une potion persistante?","souffle du dragon"},
        {"Quel ingrédient rend une potion jetable?","poudre à canon"},
        {"Quel ingrédient donne une potion de vision nocturne?","carotte dorée"},
        {"Quel ingrédient donne une potion de force?","poudre de blaze"},
        {"Quel ingrédient donne une potion de rapidité?","sucre"},
        {"Quel objet sert à nourrir un cheval?","carotte dorée"},
        {"Quel objet sert à guider un bateau sur la glace?","aucun"},
        {"Quel bloc fait rebondir le joueur?","bloc de slime"},
        {"Quel bloc réduit les dégâts de chute?","bloc de miel"},
        {"Quel bloc permet de faire des sons de basse?","bois"},
        {"Quel objet permet de faire un feu d'artifice?","fusée"},
        {"Quel objet permet de personnaliser une bannière?","motif de bannière"},
        {"Quel bloc permet de tailler la pierre?","tailleur de pierre"}
    };

    private static final String[] REACTION_WORDS = {
        "Larry","MINECRAFT","LARPERS","DIAMANT","CREEPER","REDSTONE","NETHERITE","ELYTRA",
        "VILLAGE","ENDERMAN","SQUELETTE","ZOMBIE","BLAZE","GHAST","WARDEN","ALLAY",
        "AXOLOTL","TORTUE","DAUPHIN","RENARD","CHEVAL","MOUTON","ABEILLE","VILLAGEOIS",
        "OBSIDienne","EMERAUDE","AMETHYSTE","CUIVRE","QUARTZ","PRISMARINE","SCULK","PIoche",
        "EPEE","HACHE","PELLE","HOUE","BOUCLIER","TRIDENT","ARBALLETE","ENCLUME",
        "FOURNEAU","FUMOIR","ENTONNOIR","PISTON","LEVIER","BOUTON","OBSERVATEUR","COMPARATEUR",
        "REPETEUR","DISTRIBUTEUR","PORTAIL","FORTERESSE","BASTION","MONUMENT","MANOIR","TEMPLE",
        "DONJON","GEODE","CITROUILLE","PASTÈQUE","CAROTTE","PATATE","BETTERAVE","BAMBOU",
        "CACTUS","CHORUS","NENUPHAR","CHAMPIGNON","TOURNESOL","ORCHIDEE","COQUELICOT","TULIPE",
        "MANGROVE","CERISIER","ACACIA","BOULEAU","CHENE","SAPIN","JUNGLE","BIBLIOTHECAIRE",
        "CARTOGRAPHE","FORGERON","ARMURIER","BOUCHER","CLERC","PECHEUR","BERGER","FLECHEUR",
        "COMPOSTEUR","CLOCHE","LANTERNE","TORCHE","BOUSSOLE","HORLOGE","BANNIERE","COFFRE",
        "BARIL","SHULKER","BALISE","CONDUIT","ANCRE","TOTEM","PERLE","POTION",
        "EXPERIENCE","ENCHANTEMENT","FANTOME","RAVAGEUR","PILLARD","SORCIER","NOYE","GARDIEN",
        "CALMAR","SAUMON","MORUE","POISSON","COCHON","POULET","VACHE","LAMA",
        "CHAMEAU","PANDA","PERROQUET","CHAT","LOUP","GOLEM","ARAIGNEE","PHANTOM",
        "NETHER","END","OVERWORLD","BIOME","DESERT","SAVANE","TAIGA","TUNDRA",
        "MINESHAFT","RUINES","EPAVE","TRESOR","PORTAIL","CRAFTING","SURVIE","AVENTURE"
    };

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
                answer=WORDS[random.nextInt(WORDS.length)].toLowerCase(Locale.ROOT);
                List<Character> chars=new ArrayList<>();
                for(char c:answer.toCharArray())chars.add(c);
                Collections.shuffle(chars);
                if(new String(toArray(chars)).equals(answer))Collections.rotate(chars,1);
                question="Remets ce mot Minecraft dans le bon ordre: "+new String(toArray(chars));
            }
            case "quiz" -> {
                String[] selected=QUIZ[random.nextInt(QUIZ.length)];
                question=selected[0]; answer=normalizeAnswer(selected[1]);
            }
            case "calcul" -> {
                int a=random.nextInt(4,101),b=random.nextInt(3,101),op=random.nextInt(4);
                if(op==0){answer=String.valueOf(a+b);question="Calcul éclair: "+a+" + "+b+" = ?";}
                else if(op==1){answer=String.valueOf(a*b);question="Calcul éclair: "+a+" × "+b+" = ?";}
                else if(op==2){int high=Math.max(a,b),low=Math.min(a,b);answer=String.valueOf(high-low);question="Calcul éclair: "+high+" - "+low+" = ?";}
                else {int multiplier=random.nextInt(2,13),result=multiplier*random.nextInt(2,13);answer=String.valueOf(result/multiplier);question="Calcul éclair: "+result+" ÷ "+multiplier+" = ?";}
            }
            case "reaction" -> {
                answer=REACTION_WORDS[random.nextInt(REACTION_WORDS.length)].toLowerCase(Locale.ROOT);
                question="Premier joueur à écrire "+answer.toUpperCase(Locale.ROOT)+" dans le chat gagne!";
            }
            default -> { return false; }
        }
        active=new ActiveEvent(type.toLowerCase(Locale.ROOT),answer,reward);
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD+"§l✦ ÉVÉNEMENT CHAT Larry ✦");
        Bukkit.broadcastMessage(ChatColor.YELLOW+question);
        Bukkit.broadcastMessage(ChatColor.GRAY+"Récompense: "+ChatColor.GOLD+String.format(Locale.CANADA_FRENCH,"%.0f",reward)+" Larry");
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

    private String normalizeAnswer(String value) {
        String normalized=Normalizer.normalize(value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","");
        return normalized.toLowerCase(Locale.ROOT).trim();
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
        if(current==null||!normalizeAnswer(event.getMessage()).equalsIgnoreCase(current.answer))return;
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
            Bukkit.broadcastMessage(ChatColor.GREEN+"[ÉVÉNEMENT] "+winner.getName()+" a trouvé la bonne réponse : "+ChatColor.YELLOW+current.answer+ChatColor.GREEN+" et remporte "+String.format(Locale.CANADA_FRENCH,"%.0f",current.reward)+" Larry !");
        });
    }

    public String status() { return active==null?"Aucun événement chat en cours.":"Événement actif: "+active.type; }
    public void shutdown() { if(timeout!=null)timeout.cancel(); active=null; }
}
