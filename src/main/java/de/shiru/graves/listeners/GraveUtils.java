package de.shiru.graves.listeners;

import de.shiru.graves.Grave;
import de.shiru.graves.GravesManager;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

public class GraveUtils {

    protected static String convertToCounter(long remainder) {
        long totalSeconds = remainder / 20;
        final int minutesInSecs = 60;
        var counts = new StringBuilder();
        //minutes
        var minutes = totalSeconds / minutesInSecs;
        if(minutes < 10) counts.append(0);
        counts.append(minutes).append(':');
        totalSeconds -= minutes * minutesInSecs;
        //seconds
        if(totalSeconds < 10) counts.append(0);
        counts.append(totalSeconds);
        return counts.toString();
    }

    protected static void clearArmorStands(Grave grave) {
        var middleLocation = grave.getDeathLocation().clone().add(0.5, 0, 0.5);
        clearArmorStands(middleLocation);
    }

    protected static void clearArmorStands(Location loc) {
        var uuidKey = GravesManager.get().getUuidKey();
        loc.getWorld().getNearbyEntities(BoundingBox.of(loc, 2, 2, 2), entity ->
                entity instanceof ArmorStand && entity.getPersistentDataContainer().has(uuidKey)
        ).forEach(Entity::remove);
    }

    protected static ArmorStand createAssociatedArmorStand(Location loc, Grave grave) {
        var uuidKey = GravesManager.get().getUuidKey();
        var armorStand = loc.getWorld().spawn(loc, ArmorStand.class);
        armorStand.setVisible(false);
        armorStand.setCanMove(false);
        armorStand.setAI(false);
        armorStand.setInvulnerable(true);
        armorStand.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, grave.getId().toString());
        armorStand.teleport(loc);
        return armorStand;
    }

    protected static ArmorStand createCounterArmorStand(GraveListener graveListener, Location loc, Grave grave) {
        var counterKey = GravesManager.get().getCounterKey();
        var armorstand = createAssociatedArmorStand(loc, grave);
        armorstand.getPersistentDataContainer().set(counterKey, PersistentDataType.BOOLEAN, true);
        return armorstand;
    }
    
}
