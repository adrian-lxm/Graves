package de.shiru.graves;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

public class DeathListener implements Listener {
    private final NamespacedKey hashCodeKey;
    private final NamespacedKey counterKey;
    private final List<Grave> graves;
    private final BlockFace[] faces = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    public DeathListener() {
        var plugin = GravesPlugin.get();
        hashCodeKey = new NamespacedKey(plugin, "grave_code");
        counterKey = new NamespacedKey(plugin, "grave_counter");
        var config = plugin.getSaveFile();
        var list = (List<Grave>) config.getList("graves");
        graves = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
    }

    public int createUpdateTask() {
        return Bukkit.getScheduler()
                .scheduleSyncRepeatingTask(GravesPlugin.get(), this::updateCounters, 20, 1);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var inventory = player.getInventory();
        var items = Arrays.stream(inventory.getContents())
                .filter(Objects::nonNull)
                .filter(item -> {
                    if(!item.hasItemMeta()) return false;
                    return item.getItemMeta().getPersistentDataContainer().has(hashCodeKey);
                }).toArray(ItemStack[]::new);
        for(var item : items) {
            var id = UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(hashCodeKey, PersistentDataType.STRING));
            var graveOpt = graves.stream().filter(grave -> grave.getId().equals(id)).findAny();
            if(graveOpt.isPresent()) continue;
            inventory.remove(item);
        }
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
        GravesPlugin.get().getSaveFile().set("graves", Collections.unmodifiableList(graves));
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
            var uuid = entity.getPersistentDataContainer().get(hashCodeKey, PersistentDataType.STRING);
            var graveOpt = graves.stream().filter(grave -> grave.getId().toString().equals(uuid)).findFirst();
            if(graveOpt.isEmpty()) {
                entity.getLocation().getBlock().setType(Material.AIR);
                clearArmorStands(entity.getLocation());
            }
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        var item = event.getItem();
        if(!(item != null && isLockedItem(item))) return;
        var compassMeta = (CompassMeta) item.getItemMeta();
        var location = compassMeta.getLodestone().toVector().add(new Vector(0.5, 2, 0.5));
        var playerLocation = player.getLocation().toVector().add(new Vector(0.5, 2, 0.5));
        location.subtract(playerLocation).normalize().multiply(0.1);
        var particleVector = playerLocation.clone();
        var world = player.getWorld();
        for(int i = 0; i < 30; i++) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, particleVector.toLocation(world), 1);
            particleVector.add(location);
        }
    }

    private void updateCounters() {
        var lifetime = (long) GravesPlugin.get().getConfig().getInt("grave-lifetime");
        lifetime *= 60 * 1000;
        var currentTime = System.currentTimeMillis();
        var changed = false;
        var updateArmorstands = Bukkit.getCurrentTick() % 20 == 0;
        for(var grave : List.copyOf(graves)) {
            if(grave.reduceTick() <= 0) {
                graves.remove(grave);
                changed = true;
                if(!grave.getDeathLocation().getChunk().isLoaded())
                    continue;
                clearArmorStands(grave);
                grave.getDeathLocation().getBlock().setType(Material.AIR);
                continue;
            }
            if(!updateArmorstands) continue;
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
            var counters = convertToCounter(grave.getRemainder());
            var newDisplayName = Component.text("Expires in ", NamedTextColor.GREEN)
                    .append(Component.text(counters, NamedTextColor.YELLOW));
            armorStand.customName(newDisplayName);
        }
        if(changed) {
            GravesPlugin.get().getSaveFile().set("graves", Collections.unmodifiableList(graves));
        }
    }

    private String convertToCounter(long remainder) {
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

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        var player = event.getPlayer();
        var graveOpt = graves.stream()
                .filter(grave -> grave.getPlayer().equals(player.getUniqueId())).findAny();
        if(graveOpt.isEmpty()) return;
        createGraveCompass(player, graveOpt.get());
    }

    private void createGraveCompass(Player player, Grave grave) {
        var compass = new ItemStack(Material.COMPASS);
        if(compass.getItemMeta() instanceof CompassMeta meta) {
            meta.getPersistentDataContainer().set(hashCodeKey, PersistentDataType.STRING, grave.getId().toString());
            var location = grave.getDeathLocation();
            meta.setLodestone(location);
            meta.setLodestoneTracked(true);
            meta.addEnchant(Enchantment.LOYALTY, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.itemName(Component.text(player.getName() + "'s Grave Compass", NamedTextColor.RED));
            meta.lore(Arrays.asList(
                    Component.text("X: ", NamedTextColor.GREEN).append(Component.text(location.getBlockX(), NamedTextColor.YELLOW)),
                    Component.text("Y: ", NamedTextColor.GREEN).append(Component.text(location.getBlockY(), NamedTextColor.YELLOW)),
                    Component.text("Z: ", NamedTextColor.GREEN).append(Component.text(location.getBlockZ(), NamedTextColor.YELLOW))
            ));
            compass.setItemMeta(meta);
            player.getInventory().addItem(compass);
            return;
        }
        GravesPlugin.get().getLogger().severe("Couldn't create grave compass for " + player.getName());
    }

    private boolean isLockedItem(ItemStack item) {
        if(item.getType() == Material.COMPASS && item.getItemMeta() instanceof CompassMeta meta) {
            return meta.getPersistentDataContainer().has(hashCodeKey);
        }
        return false;
    }

    @EventHandler
    public void onInventoryChange(InventoryClickEvent event) {
        if(event.getCurrentItem() == null) return;
        event.setCancelled(isLockedItem(event.getCurrentItem()));
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        event.setCancelled(isLockedItem(event.getItemDrop().getItemStack()));
    }

    @EventHandler
    public void onItemSwap(PlayerSwapHandItemsEvent event) {
        event.setCancelled(isLockedItem(event.getMainHandItem()));
    }

}
