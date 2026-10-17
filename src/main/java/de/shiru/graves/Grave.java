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
    private final boolean voidDeath;
    private long remainder;
    private final ItemStack[] inventory;
    private final int level;
    private final float exp;

    public Grave(Player player, boolean voidDeath) {
        id = UUID.randomUUID();
        this.player = player.getUniqueId();
        deathLocation = player.getLocation().getBlock().getLocation();
        this.voidDeath = voidDeath;
        remainder = GravesPlugin.get().getConfig().getInt("grave-lifetime") * 60L * 20;
        var invContent = player.getInventory().getContents();
        inventory = Arrays.stream(invContent)
                .filter(Objects::nonNull).map(ItemStack::clone).toArray(ItemStack[]::new);
        level = player.getLevel();
        exp = player.getExp();
    }

    public Grave(Map<String, Object> map) {
        id = UUID.fromString((String) map.get("id"));
        player = UUID.fromString((String) map.get("player"));
        deathLocation = (Location) map.get("deathLocation");
        voidDeath = (Boolean) map.getOrDefault("voidDeath", false);
        remainder = (Long) map.get("remainder");
        inventory = ((List<ItemStack>) map.get("inventory")).toArray(ItemStack[]::new);
        level = (Integer) map.get("level");
        exp = (Float) map.get("exp");
    }

    public long reduceTick() {
        remainder--;
        return remainder;
    }

    public long getRemainder() {
        return remainder;
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
        if(voidDeath) map.put("voidDeath", true);
        map.put("remainder", remainder);
        map.put("inventory", inventory);
        map.put("level", level);
        map.put("exp", exp);
        return map;
    }

}
