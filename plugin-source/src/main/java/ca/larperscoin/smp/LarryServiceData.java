package ca.larperscoin.smp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class LarryServiceData {
    private final File file;
    private final YamlConfiguration data=new YamlConfiguration();

    LarryServiceData(LarpersCoin plugin) {
        file=new File(plugin.getDataFolder(),"mutes.yml");
        if(file.exists()) try { data.load(file); }
        catch(Exception ex) { throw new IllegalStateException("Lecture de mutes.yml impossible",ex); }
    }
    Set<String> ids() { return data.getKeys(false); }
    String name(String id) { return data.getString(id); }
    boolean set(UUID id,String name) {
        String key=id.toString(),previous=data.getString(key);
        data.set(key,name);
        try {
            Path tmp=file.toPath().resolveSibling("mutes.yml.tmp");
            try {
            data.save(tmp.toFile());
            try { Files.move(tmp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,file.toPath(),StandardCopyOption.REPLACE_EXISTING); }
            } finally { try { Files.deleteIfExists(tmp); } catch(IOException ignored) {} }
            return true;
        } catch(IOException ex) { data.set(key,previous);return false; }
    }
}
