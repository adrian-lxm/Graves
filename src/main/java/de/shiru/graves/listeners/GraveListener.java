package de.shiru.graves.listeners;

import de.shiru.graves.Grave;
import de.shiru.graves.GravesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public abstract class GraveListener implements Listener {
    private static NamespacedKey uuidKey;
    private static NamespacedKey counterKey;
    private static List<Grave> graves;
    private static boolean created = false;

    private static final List<Class<? extends GraveListener>> listeners = List.of(
            GraveCreationListener.class,
            CompassListener.class
    );

    protected abstract void initialize();
    protected abstract void destroy();

    public static void create() {
        if(created) return;
        var plugin = GravesPlugin.get();
        uuidKey = new NamespacedKey(plugin, "grave_uuid");
        counterKey = new NamespacedKey(plugin, "grave_counter");
        var config = plugin.getSaveFile();
        var list = (List<Grave>) config.getList("graves");
        graves = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
        for(var clazz : listeners) {
            try {
                var listenerInstance = clazz.getDeclaredConstructor().newInstance();
                listenerInstance.initialize();
                Bukkit.getPluginManager().registerEvents(listenerInstance, plugin);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, clazz.getName() + " couldn't be created!", e);
            }
        }
        created = true;
    }

    public static void close() {
        var plugin = GravesPlugin.get();
        var registeredListeners = HandlerList.getRegisteredListeners(plugin);
        registeredListeners.stream().map(RegisteredListener::getListener).forEach(listener -> {
            if(listener instanceof GraveListener graveListener) {
                graveListener.destroy();
            } else plugin.getLogger().severe("GravePlugin listener " + listener.getClass().getName() + " wasn't a GraveListener!");
        });
    }

    //to make the usage of these methods clear, method calls will be made with super.method() in the subclasses
    protected NamespacedKey getUuidKey() {
        return uuidKey;
    }

    protected NamespacedKey getCounterKey() {
        return counterKey;
    }

    protected List<Grave> getGraves() {
        return graves;
    }
}
