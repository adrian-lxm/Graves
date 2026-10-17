package de.shiru.graves.listeners;

import de.shiru.graves.Grave;
import de.shiru.graves.GravesManager;
import de.shiru.graves.GravesPlugin;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.*;

public class GraveCreationListener extends GraveListener {
    private int updateTask;
    private final BlockFace[] faces = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    @Override
    public void initialize() {
        updateTask = Bukkit.getScheduler()
                .scheduleSyncRepeatingTask(GravesPlugin.get(), this::updateCounters, 20, 1);
    }

    @Override
    public void destroy() {
        Bukkit.getScheduler().cancelTask(updateTask);
    }

    private void updateCounters() {
        var changed = false;
        var updateArmorstands = Bukkit.getCurrentTick() % 20 == 0;
        var graves = GravesManager.get().getGraves();
        for(var grave : List.copyOf(graves)) {
            if(grave.reduceTick() <= 0) {
                graves.remove(grave);
                changed = true;
                if(!grave.getDeathLocation().getChunk().isLoaded())
                    continue;
                GraveUtils.clearArmorStands(grave);
                grave.getDeathLocation().getBlock().setType(Material.AIR);
                var player = Bukkit.getPlayer(grave.getPlayer());
                if(player == null) continue;
                Arrays.stream(player.getInventory().getContents()).filter(item  -> {
                    if(item != null && item.getType() == Material.COMPASS && item.hasItemMeta())
                        return item.getItemMeta().getPersistentDataContainer().has(GravesManager.get().getUuidKey());
                    return false;
                }).forEach(item -> player.getInventory().removeItem(item));
                continue;
            }
            if(!updateArmorstands) continue;
            var chunk = grave.getDeathLocation().getChunk();
            if(!(chunk.isLoaded() && chunk.isEntitiesLoaded()))
                continue;
            var middleLocation = grave.getDeathLocation().clone().add(0.5, 0, 0.5);
            var counterKey = GravesManager.get().getCounterKey();
            var counterOpt = chunk.getWorld().getNearbyEntities(BoundingBox.of(middleLocation, 0, 2, 0), entity ->
                    entity instanceof ArmorStand && entity.getPersistentDataContainer().has(counterKey)
            ).stream().findFirst();
            if(counterOpt.isEmpty()) {
                var playerName = Bukkit.getOfflinePlayer(grave.getPlayer()).getName();
                GravesPlugin.get().getLogger().info(playerName + "'s Grave has no detectable counter armor stand!!!");
                continue;
            }
            var armorStand = counterOpt.get();
            var counters = GraveUtils.convertToCounter(grave.getRemainder());
            var newDisplayName = Component.text("Expires in ", NamedTextColor.GREEN)
                    .append(Component.text(counters, NamedTextColor.YELLOW));
            armorStand.customName(newDisplayName);
        }
        if(changed) {
            GravesManager.get().getSaveConfig().set("graves", Collections.unmodifiableList(graves));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var inventory = player.getInventory();
        var uuidKey = GravesManager.get().getUuidKey();
        Arrays.stream(inventory.getContents()).filter(item -> {
            if(item != null && item.getType() == Material.COMPASS && item.hasItemMeta())
                return item.getItemMeta().getPersistentDataContainer().has(uuidKey);
            return false;
        }).forEach(item -> {
            var id = UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING));
            var graveOpt = GravesManager.get().getGraves().stream()
                    .filter(grave -> grave.getId().equals(id)).findAny();
            if(graveOpt.isEmpty())
                inventory.remove(item);
        });
    }


    private final List<EntityDamageEvent.DamageCause> illegalCauses = List.of(
            EntityDamageEvent.DamageCause.CUSTOM, EntityDamageEvent.DamageCause.WORLD_BORDER
    );
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        var damageCause = player.getLastDamageCause();
        if(damageCause == null || illegalCauses.contains(damageCause.getCause()))
            return;
        var grave = new Grave(player, damageCause.getCause() == EntityDamageEvent.DamageCause.VOID);
        var graves = GravesManager.get().getGraves();
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
            skull.getPersistentDataContainer().set(GravesManager.get().getUuidKey(), PersistentDataType.STRING, grave.getId().toString());
            skull.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            skull.update(true);
        }
        GravesManager.get().getSaveConfig().set("graves", Collections.unmodifiableList(graves));
        var nameStandLocation = location.clone().add(0.5, 0, 0.5);
        var nameStand = GraveUtils.createAssociatedArmorStand(nameStandLocation, grave);
        var component = Component.text(player.getName() + "'s", NamedTextColor.YELLOW)
                        .appendSpace()
                        .append(Component.text("Grave", NamedTextColor.GREEN));
        nameStand.customName(component);
        nameStand.setCustomNameVisible(true);
        var counterStandLocation = location.clone().add(0.5, -0.5, 0.5);
        GraveUtils.createCounterArmorStand(this, counterStandLocation, grave)
                .setCustomNameVisible(true);
        event.getDrops().clear();
        event.setShouldDropExperience(false);
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if(event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        var player = event.getPlayer();
        var block = event.getClickedBlock();
        if(block.getType() != Material.PLAYER_HEAD) return;
        if(!(block.getState() instanceof Skull skull)) return;
        var uuidKey = GravesManager.get().getUuidKey();
        var graves = GravesManager.get().getGraves();
        if(!skull.getPersistentDataContainer().has(uuidKey)) return;
        var graveId = skull.getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
        var graveOpt = graves.stream().filter(grave -> grave.getId().toString().equals(graveId)).findFirst();
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
        GraveUtils.clearArmorStands(grave);
        graves.remove(grave);
        GravesManager.get().getSaveConfig().set("graves", Collections.unmodifiableList(graves));
        block.setType(Material.AIR);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        Arrays.stream(player.getInventory().getContents()).filter(item  -> {
            if(item != null && item.getType() == Material.COMPASS && item.hasItemMeta())
                return item.getItemMeta().getPersistentDataContainer().has(GravesManager.get().getUuidKey());
            return false;
        }).forEach(item -> player.getInventory().removeItem(item));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if(block.getType() != Material.PLAYER_HEAD) return;
        if(block.getState() instanceof Skull skull && skull.getPersistentDataContainer().has(GravesManager.get().getUuidKey())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityLoad(EntitiesLoadEvent event) {
        var uuidKey = GravesManager.get().getUuidKey();
        var entities = event.getEntities().stream().filter(entity ->
                entity instanceof ArmorStand && entity.getPersistentDataContainer().has(uuidKey)
        ).toArray(Entity[]::new);
        if(entities.length == 0) return;
        Arrays.stream(entities).filter(entity ->
                !entity.getPersistentDataContainer().has(GravesManager.get().getCounterKey())
        ).forEach(entity -> {
            var uuid = entity.getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
            var graveOpt = GravesManager.get().getGraves().stream().filter(grave -> grave.getId().toString().equals(uuid)).findFirst();
            if(graveOpt.isEmpty()) {
                entity.getLocation().getBlock().setType(Material.AIR);
                GraveUtils.clearArmorStands(entity.getLocation());
            }
        });
    }

}
