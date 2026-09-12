package de.shiru.graves;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DeathListener implements Listener {
    private List<Grave> graves;
    private NamespacedKey hashCodeKey;
    private NamespacedKey counterKey;
    private final BlockFace[] faces = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    public void loadData() {
        var plugin = GravesPlugin.get();
        hashCodeKey = new NamespacedKey(plugin, "grave_code");
        counterKey = new NamespacedKey(plugin, "grave_counter");
        var config = plugin.getSaveFile();
        var list = (List<Grave>) config.getList("graves");
        graves = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
    }

    public int createUpdateTask() {
        return Bukkit.getScheduler()
                .scheduleSyncRepeatingTask(GravesPlugin.get(), this::updateCounters, 20, 20);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        var grave = new Grave(player);
        graves.add(grave);
        var block = player.getLocation().getBlock();
        var location = block.getLocation();
        block.setType(Material.PLAYER_HEAD);
        float yaw = player.getLocation().getYaw();
        int index = Math.round((yaw + 180) / 22.5f);
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(faces[index]);
            block.setBlockData(rotatable);
        }
        if(block.getState() instanceof Skull skull) {
            skull.getPersistentDataContainer().set(hashCodeKey, PersistentDataType.STRING, grave.getId().toString());
            skull.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            skull.update(true);
        }
        GravesPlugin.get().getSaveFile().set("graves", Collections.unmodifiableList(graves));
        var nameStandLocation = location.clone().add(0.5, 0, 0.5);
        var nameStand = createAssociatedArmorStand(nameStandLocation, grave);
        var component = Component.text(player.getName() + "'s", NamedTextColor.YELLOW)
                        .appendSpace()
                        .append(Component.text("Grave", NamedTextColor.GREEN));
        nameStand.customName(component);
        nameStand.setCustomNameVisible(true);
        var counterStandLocation = location.clone().add(0.5, -0.5, 0.5);
        createCounterArmorStand(counterStandLocation, grave)
                .setCustomNameVisible(true);
        event.getDrops().clear();
        event.setShouldDropExperience(false);
    }

    private ArmorStand createAssociatedArmorStand(Location loc, Grave grave) {
        var armorStand = loc.getWorld().spawn(loc, ArmorStand.class);
        armorStand.setVisible(false);
        armorStand.setCanMove(false);
        armorStand.setAI(false);
        armorStand.setInvulnerable(true);
        armorStand.getPersistentDataContainer().set(hashCodeKey, PersistentDataType.STRING, grave.getId().toString());
        armorStand.teleport(loc);
        return armorStand;
    }

    private ArmorStand createCounterArmorStand(Location loc, Grave grave) {
        var armorstand = createAssociatedArmorStand(loc, grave);
        armorstand.getPersistentDataContainer().set(counterKey, PersistentDataType.BOOLEAN, true);
        return armorstand;
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if(event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        var player = event.getPlayer();
        var block = event.getClickedBlock();
        if(block.getType() != Material.PLAYER_HEAD) return;
        if(!(block.getState() instanceof Skull skull)) return;
        if(!skull.getPersistentDataContainer().has(hashCodeKey)) return;
        var graveId = skull.getPersistentDataContainer().get(hashCodeKey, PersistentDataType.STRING);
        var graveOpt = graves.stream().filter(graves -> graves.getId().toString().equals(graveId)).findFirst();
        if(graveOpt.isEmpty()) return;
        var grave = graveOpt.get();
        if(!grave.getPlayer().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("This grave doesn't belong to you!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.5f);
            return;
        }
        player.setLevel(player.getLevel() + grave.getLevel());
        player.setExp(player.getExp() + grave.getExp());
        var remainingItems = player.getInventory().addItem(grave.getInventory());
        if(!remainingItems.isEmpty()) {
            remainingItems.values().forEach(
                    item -> block.getWorld().dropItemNaturally(block.getLocation(), item)
            );
        }
        clearArmorStands(grave);
        graves.remove(grave);
        block.setType(Material.AIR);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if(block.getType() != Material.PLAYER_HEAD) return;
        if(block.getState() instanceof Skull skull && skull.getPersistentDataContainer().has(hashCodeKey)) {
            event.setCancelled(true);
        }
    }

    private void clearArmorStands(Grave grave) {
        var middleLocation = grave.getDeathLocation().clone().add(0.5, 0, 0.5);
        clearArmorStands(middleLocation);
    }

    private void clearArmorStands(Location loc) {
        loc.getWorld().getNearbyEntities(BoundingBox.of(loc, 2, 2, 2), entity ->
                entity instanceof ArmorStand && entity.getPersistentDataContainer().has(hashCodeKey)
        ).forEach(Entity::remove);
    }

    @EventHandler
    public void onEntityLoad(EntitiesLoadEvent event) {
        var entities = event.getEntities().stream().filter(entity ->
                entity instanceof ArmorStand && entity.getPersistentDataContainer().has(hashCodeKey)
        ).toArray(Entity[]::new);
        if(entities.length == 0) return;
        Arrays.stream(entities).filter(entity ->
                !entity.getPersistentDataContainer().has(counterKey)
        ).forEach(entity -> {
            var graveOpt = graves.stream().filter(grave -> grave.getDeathLocation().equals(entity.getLocation())).findFirst();
            if(graveOpt.isEmpty()) {
                entity.getLocation().getBlock().setType(Material.AIR);
                clearArmorStands(entity.getLocation());
            }
        });
    }

    private void updateCounters() {
        var lifetime = (long) GravesPlugin.get().getConfig().getInt("grave-lifetime");
        lifetime *= 60 * 1000;
        var currentTime = System.currentTimeMillis();
        for(var grave : List.copyOf(graves)) {
            if(currentTime - grave.getTimestamp() > lifetime) {
                graves.remove(grave);
                if(!grave.getDeathLocation().getChunk().isLoaded())
                    continue;
                clearArmorStands(grave);
                grave.getDeathLocation().getBlock().setType(Material.AIR);
                continue;
            }
            var chunk = grave.getDeathLocation().getChunk();
            if(!(chunk.isLoaded() && chunk.isEntitiesLoaded()))
                continue;
            var middleLocation = grave.getDeathLocation().clone().add(0.5, 0, 0.5);
            var counterOpt = chunk.getWorld().getNearbyEntities(BoundingBox.of(middleLocation, 0, 2, 0), entity ->
                    entity instanceof ArmorStand && entity.getPersistentDataContainer().has(counterKey)
            ).stream().findFirst();
            if(counterOpt.isEmpty()) {
                var playerName = Bukkit.getOfflinePlayer(grave.getPlayer()).getName();
                GravesPlugin.get().getLogger().info(playerName + "'s Grave has no detectable counter armor stand!!!");
                continue;
            }
            var armorStand = counterOpt.get();
            var counters = convertDifferenceToCounter(lifetime - (currentTime - grave.getTimestamp()));
            var newDisplayName = Component.text("Expires in ", NamedTextColor.GREEN)
                    .append(Component.text(counters, NamedTextColor.YELLOW));
            armorStand.customName(newDisplayName);
        }
    }

    private String convertDifferenceToCounter(long difference) {
        final long minutesMs = 60 * 1000;
        final long secondsMs = 1000;
        var counts = new StringBuilder();
        //minutes
        var minutes = (difference - (difference % minutesMs)) / minutesMs;
        if(minutes < 10) counts.append(0);
        counts.append(minutes).append(':');
        difference -= minutes * minutesMs;
        //seconds
        var seconds = (difference - (difference % secondsMs)) / secondsMs;
        if(seconds < 10) counts.append(0);
        counts.append(seconds);
        return counts.toString();
    }

}
