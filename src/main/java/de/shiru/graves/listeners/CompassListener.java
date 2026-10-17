package de.shiru.graves.listeners;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import de.shiru.graves.Grave;
import de.shiru.graves.GravesManager;
import de.shiru.graves.GravesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class CompassListener extends GraveListener {

    @Override
    public void initialize() {}
    @Override
    public void destroy() {}

    private boolean isLockedItem(ItemStack item) {
        var uuidKey = GravesManager.get().getUuidKey();
        if(item.getType() == Material.COMPASS && item.getItemMeta() instanceof CompassMeta meta) {
            return meta.getPersistentDataContainer().has(uuidKey);
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        var item = event.getItem();
        if(!(item != null && isLockedItem(item))) return;
        var compassMeta = (CompassMeta) item.getItemMeta();
        var particleVelocity = compassMeta.getLodestone().toVector().add(new Vector(0.5, 2, 0.5));
        var playerLocation = player.getLocation().clone().add(0.5, 2, 0.5);
        particleVelocity.subtract(playerLocation.toVector()).multiply(1 / (particleVelocity.length() * 10));
        var i = new AtomicInteger(1);
        var dustOption = new Particle.DustOptions(Color.RED, 2);
        Bukkit.getScheduler().runTaskTimer(GravesPlugin.get(), (task) -> {
            player.spawnParticle(Particle.DUST, playerLocation.add(particleVelocity), 1, dustOption);
            if(i.getAndIncrement() >= 30) task.cancel();
        }, 1, 3);
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

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        var player = event.getPlayer();
        var graveOpt = GravesManager.get().getGraves().stream()
                .filter(grave -> grave.getPlayer().equals(player.getUniqueId())).findAny();
        if(graveOpt.isEmpty()) return;
        createGraveCompass(player, graveOpt.get());
    }

    private void createGraveCompass(Player player, Grave grave) {
        var compass = new ItemStack(Material.COMPASS);
        if(compass.getItemMeta() instanceof CompassMeta meta) {
            meta.getPersistentDataContainer().set(GravesManager.get().getUuidKey(), PersistentDataType.STRING, grave.getId().toString());
            var location = grave.getDeathLocation();
            meta.setLodestone(location);
            meta.setLodestoneTracked(false);
            meta.addEnchant(Enchantment.LOYALTY, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            var customName = Component.text(player.getName(), NamedTextColor.YELLOW)
                            .append(Component.text("'s Grave Compass", NamedTextColor.RED));
            meta.customName(customName);
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

}
