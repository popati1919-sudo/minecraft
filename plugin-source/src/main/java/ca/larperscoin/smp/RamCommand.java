package ca.larperscoin.smp;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

final class RamCommand implements CommandExecutor, Listener {
    private static final long GIB=1L<<30;
    private static final List<Integer> PRESETS=List.of(1,2,3,4,6,8,12,16,24);
    private final Path file=Path.of("larry-heap.args");
    private final JavaPlugin plugin;
    private static final class RamMenu implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final String TITLE="Larry | RAM";

    RamCommand(JavaPlugin plugin) {
        this.plugin=plugin;
        plugin.getCommand("ram").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }

    private long siblingReserve(){
        String server=System.getProperty("larpers.server","");
        if(!server.equalsIgnoreCase("SMP")&&!server.equalsIgnoreCase("Lobby"))return 0;
        String other=server.equalsIgnoreCase("SMP")?"Lobby":"SMP";
        try {
            String arg=Files.readString(Path.of("..",other,"larry-heap.args")).trim();
            if(arg.matches("-Xmx[1-9][0-9]*G"))return 1+Long.parseLong(arg.substring(4,arg.length()-1));
        } catch(IOException|NumberFormatException ignored){}
        return 1+(other.equals("Lobby")?1:4);
    }
    private long limit() {
        var os=ManagementFactory.getOperatingSystemMXBean();
        if(os instanceof com.sun.management.OperatingSystemMXBean machine && machine.getTotalMemorySize()>0)
            return Math.max(0, machine.getTotalMemorySize()/GIB - 3 - siblingReserve());
        return 0;
    }

    private void set(CommandSender sender,String input) {
        if(!sender.hasPermission("larpers.admin"))return;
        long max=limit();
        if(max<1) { sender.sendMessage("Mémoire disponible insuffisante ou inconnue."); return; }
        int amount;
        try { amount=Integer.parseInt(input); } catch(NumberFormatException ex) { amount=0; }
        if(amount<1 || amount>max || amount*GIB<ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit()) {
            sender.sendMessage("Choisis un nombre entier de GiB entre 1 et "+max+".");return;
        }
        try {
            Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"larry-heap-",".tmp");
            try {
                Files.writeString(tmp,"-Xmx"+amount+"G\n");
                try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
            } finally {Files.deleteIfExists(tmp);}
            sender.sendMessage("Limite enregistrée : "+amount+" GiB au prochain démarrage avec le lanceur Larry.");
        } catch(IOException ex){sender.sendMessage("Impossible de sauvegarder la limite RAM.");}
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(!sender.hasPermission("larpers.admin")){sender.sendMessage("Permission refusée.");return true;}
        if(args.length==1){set(sender,args[0]);return true;}
        if(args.length!=0)return false;
        String saved="non configurée";
        try {if(Files.exists(file))saved=Files.readString(file).trim().replace("-Xmx","");}
        catch(IOException ex){saved="illisible";}
        sender.sendMessage("Maximum JVM actuel : "+Runtime.getRuntime().maxMemory()/(1L<<20)+" Mio. Prochain démarrage : "+saved+". /ram <GiB>");
        if(sender instanceof Player p) {
            RamMenu menu=new RamMenu();
            Inventory inv=Bukkit.createInventory(menu,9,TITLE);
            menu.inventory=inv;
            for(int i=0;i<PRESETS.size();i++){
                int value=PRESETS.get(i);
                if(value>limit())break;
                ItemStack icon=new ItemStack(Material.REDSTONE,value);
                var meta=icon.getItemMeta();meta.setDisplayName(value+" GiB");icon.setItemMeta(meta);
                inv.setItem(i,icon);
            }
            p.openInventory(inv);
        }
        return true;
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if(e.getView().getTopInventory().getHolder() instanceof RamMenu) e.setCancelled(true);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if(!TITLE.equals(e.getView().getTitle()))return;
        e.setCancelled(true);
        int slot=e.getRawSlot();
        if(e.getWhoClicked() instanceof Player p && p.hasPermission("larpers.admin") && slot>=0 && slot<PRESETS.size() && e.getCurrentItem()!=null){
            Bukkit.getScheduler().runTask(plugin,()->{ if(p.isOnline() && p.hasPermission("larpers.admin")){p.closeInventory();set(p,PRESETS.get(slot).toString());} });
        }
    }
}
