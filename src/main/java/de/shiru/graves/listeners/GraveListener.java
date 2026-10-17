package de.shiru.graves.listeners;

import org.bukkit.event.Listener;
import java.util.List;

public abstract class GraveListener implements Listener {
    public static final List<Class<? extends GraveListener>> CLASSES = List.of(
            GraveCreationListener.class,
            GraveRemovalListener.class,
            CompassListener.class
    );

    public abstract void initialize();
    public abstract void destroy();

}
