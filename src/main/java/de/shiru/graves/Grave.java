package de.shiru.graves;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Grave implements ConfigurationSerializable {
    private final UUID id;
    private final UUID player;
    private final Location deathLocation;
    private final long timestamp;
    private final ItemStack[] inventory;
    private final int level;
    private final float exp;

    public Grave(Player player) {
        id = UUID.randomUUID();
        this.player = player.getUniqueId();
        deathLocation = player.getLocation().getBlock().getLocation();
        timestamp = System.currentTimeMillis();
        var invContent = player.getInventory().getContents();
        inventory = Arrays.stream(invContent)
                .filter(Objects::nonNull).toArray(ItemStack[]::new);
        level = player.getLevel();
        exp = player.getExp();
    }

    public Grave(Map<String, Object> map) {
        id = UUID.fromString((String) map.get("id"));
        player = UUID.fromString((String) map.get("player"));
        deathLocation = (Location) map.get("deathLocation");
        timestamp = (Long) map.get("timestamp");
        inventory = ((List<ItemStack>) map.get("inventory")).toArray(ItemStack[]::new);
        level = (Integer) map.get("level");
        exp = (Float) map.get("exp");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayer() {
        return player;
    }

    public Location getDeathLocation() {
        return deathLocation;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getLevel() {
        return level;
    }

    public float getExp() {
        return exp;
    }

    public ItemStack[] getInventory() {
        return inventory;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        var map = new HashMap<String, Object>();
        map.put("id", id.toString());
        map.put("player", player.toString());
        map.put("deathLocation", deathLocation);
        map.put("timestamp", timestamp);
        map.put("inventory", inventory);
        map.put("level", level);
        map.put("exp", exp);
        return map;
    }

}
