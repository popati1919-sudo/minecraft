package ca.larperscoin.smp;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class LarryServiceData {
    record Mute(String name, String reason, String staff, long created, long expires) {
        boolean active() { return expires==0 || expires>System.currentTimeMillis(); }
    }
    private final Path file;
    private final LarpersCoin plugin;
    private volatile Map<UUID,Mute> snapshot=Map.of();
    private boolean warned;
    LarryServiceData(LarpersCoin plugin) {
        this.plugin=plugin;
        Path local=plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        // Both network backends use the SMP file; standalone keeps its local file.
        Path defaultFile=System.getProperty("larpers.server","").equalsIgnoreCase("Lobby")
            ? local.resolve("../../../SMP/plugins/LarpersCoin/mutes.yml").normalize()
            : local.resolve("mutes.yml");
        String configured=plugin.getConfig().getString("moderation.mutes-file","");
        file=configured==null || configured.isBlank()?defaultFile:Path.of(configured).toAbsolutePath().normalize();
        refresh();
    }
    Map<UUID,Mute> snapshot(){ return snapshot; }
    private Map<UUID,Mute> read(Path path) throws Exception {
        var result=new HashMap<UUID,Mute>();
        if(!Files.exists(path)) return result;
        var y=new YamlConfiguration();
        y.load(path.toFile());
        for(String key:y.getKeys(false)) {
            try {
                UUID id=UUID.fromString(key);
                String name=y.isString(key)?y.getString(key):y.getString(key+".name");
                if(name==null || name.isBlank()) continue;
                result.put(id,new Mute(name,y.getString(key+".reason",""),y.getString(key+".staff",""),y.getLong(key+".created"),y.getLong(key+".expires")));
            } catch(IllegalArgumentException ignored) { }
        }
        return result;
    }
    synchronized void refresh() {
        try { snapshot=Map.copyOf(read(file)); warned=false; }
        catch(Exception ex) {
            if(!warned) plugin.getLogger().warning("Mutes illisibles; dernier état conservé: "+ex.getMessage());
            warned=true;
        }
    }
    synchronized boolean set(UUID id,Mute mute) {
        Path tmp=null;
        try {
            Files.createDirectories(file.getParent());
            try(var channel=FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lock=channel.lock()) {
                var next=read(file);
                if(mute==null) next.remove(id); else next.put(id,mute);
                var y=new YamlConfiguration();
                for(var entry:next.entrySet()){
                    String key=entry.getKey().toString(); Mute m=entry.getValue();
                    y.set(key+".name",m.name()); y.set(key+".reason",m.reason());
                    y.set(key+".staff",m.staff()); y.set(key+".created",m.created()); y.set(key+".expires",m.expires());
                }
                tmp=Files.createTempFile(file.getParent(),"mutes-",".tmp");
                y.save(tmp.toFile());
                if(Files.exists(file)) Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
                try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException ex){ Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
                snapshot=Map.copyOf(next);
                return true;
            }
        } catch(Exception ex){plugin.getLogger().warning("Sauvegarde mute impossible: "+ex.getMessage());return false;}
        finally {if(tmp!=null) try{Files.deleteIfExists(tmp);}catch(IOException ignored){}}
    }
}
