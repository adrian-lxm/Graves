package de.shiru.graves;

import de.shiru.graves.listeners.GraveListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class GravesManager {
    private static GravesManager instance;
    private NamespacedKey uuidKey;
    private NamespacedKey counterKey;
    private GraveSaveFile graveSaveFile;
    private List<Grave> graves;

    private GravesManager() {}

    public void initialize(GravesPlugin plugin) {
        uuidKey = new NamespacedKey(plugin, "grave_uuid");
        counterKey = new NamespacedKey(plugin, "grave_counter");
        graveSaveFile = new GraveSaveFile();
        var config = graveSaveFile.getConfig();
        var list = (List<Grave>) config.getList("graves");
        graves = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
        for(var clazz : GraveListener.CLASSES) {
            try {
                var listenerInstance = clazz.getConstructor().newInstance();
                listenerInstance.initialize();
                Bukkit.getPluginManager().registerEvents(listenerInstance, plugin);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, clazz.getName() + " couldn't be initialized!", e);
            }
        }
    }

    public void close(GravesPlugin plugin) {
        HandlerList.getRegisteredListeners(plugin).stream().filter(listener -> {
            if(listener.getListener() instanceof GraveListener) return true;
            plugin.getLogger().severe(listener.getListener().getClass().getName() + " isn't a GraveLister, but is a registered listener!");
            return false;
        }).map(listener -> (GraveListener) listener.getListener()).forEach(GraveListener::destroy);
        graveSaveFile.close();
    }

    public NamespacedKey getUuidKey() {
        return uuidKey;
    }

    public List<Grave> getGraves() {
        return graves;
    }

    public NamespacedKey getCounterKey() {
        return counterKey;
    }

    public YamlConfiguration getSaveConfig() {
        return graveSaveFile.getConfig();
    }

    public static GravesManager get() {
        return (instance != null) ? instance : (instance = new GravesManager());
    }

}
