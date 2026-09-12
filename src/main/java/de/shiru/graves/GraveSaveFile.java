package de.shiru.graves;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

public class GraveSaveFile {
    private final File gravesFile;
    private final int saveCycleTask;
    private YamlConfiguration config;

    public GraveSaveFile() {
        var plugin = GravesPlugin.get();
        gravesFile = new File(plugin.getDataFolder(), "graves.yml");
        saveCycleTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int size = config.getList("graves", List.of()).size();
            String saved = config.saveToString();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Files.writeString(gravesFile.toPath(), saved);
                    plugin.getLogger().info(size + " Graves were saved during automatic save cycle.");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Grave file saving failed", e);
                }
            });
        }, 20 * 60 * 5, 20 * 60 * 5);
        if(!gravesFile.exists()) {
            try {
                gravesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Save file creation failed", e);
                Bukkit.getScheduler().cancelTask(saveCycleTask);
                return;
            }
        }
        config = YamlConfiguration.loadConfiguration(gravesFile);
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public void close() {
        Bukkit.getScheduler().cancelTask(saveCycleTask);
        try {
            config.save(gravesFile);
        } catch (IOException e) {
            GravesPlugin.get().getLogger().log(Level.SEVERE, "Grave file saving failed", e);
        }
    }

}
