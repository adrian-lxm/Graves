package de.shiru.graves;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

public class GravesPlugin extends JavaPlugin {
    private static GravesPlugin instance;
    private GraveSaveFile graveSaveFile;
    private int updateTask;

    @Override
    public void onEnable() {
        instance = this;
        var listener = new DeathListener();
        ConfigurationSerialization.registerClass(Grave.class);
        graveSaveFile = new GraveSaveFile();
        if(!getConfig().contains("grave-lifetime")) {
            getConfig().set("grave-lifetime", 5);
            saveConfig();
        }
        int lifetime = getConfig().getInt("grave-lifetime");
        if(lifetime <= 0 || lifetime >= 60) {
            getLogger().severe("Grave lifetime must be between 0 and 60 minutes !");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(GraveCommand.createCommand());
        });
        listener.loadData();
        Bukkit.getPluginManager().registerEvents(listener, this);
        updateTask = listener.createUpdateTask();
    }

    @Override
    public void onDisable() {
        graveSaveFile.close();
        Bukkit.getScheduler().cancelTask(updateTask);
    }

    public YamlConfiguration getSaveFile() {
        return graveSaveFile.getConfig();
    }

    public static GravesPlugin get() {
        return instance;
    }

    private static class GraveCommand {

        public static LiteralCommandNode<CommandSourceStack> createCommand() {
            return Commands.literal("graves")
                    .then(Commands.argument("option", StringArgumentType.word()))
                    .executes(ctx -> {
                        if(!ctx.getArgument("option", String.class).equals("reload"))
                            return 0;
                        GravesPlugin.get().reloadConfig();
                        return Command.SINGLE_SUCCESS;
                    }).build();
        }

    }

}
