package de.shiru.graves;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.shiru.graves.listeners.GraveListener;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class GravesPlugin extends JavaPlugin {
    private static GravesPlugin instance;
    private GraveSaveFile graveSaveFile;
    private int updateTask;

    @Override
    public void onEnable() {
        instance = this;
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
        GraveListener.create();
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

        private static void sendUsageMessage(CommandContext<CommandSourceStack> ctx) {

        }

        public static LiteralCommandNode<CommandSourceStack> createCommand() {
            return Commands.literal("graves")
                    .then(Commands.argument("option", StringArgumentType.word())
                            .suggests(((context, builder) -> {
                                var arg = context.getArgument("option", String.class);
                                if("reload".startsWith(arg.toLowerCase()))
                                    builder.suggest("reload");
                                return builder.buildFuture();
                            }))
                            .executes(ctx -> {
                                if (!ctx.getArgument("option", String.class).equalsIgnoreCase("reload"))
                                    return 0;
                                GravesPlugin.get().reloadConfig();
                                var sender = ctx.getSource().getSender();
                                var reloadText = Component.text("Reloaded the Graves config!");
                                var noticeText = Component.text("Notice that this doesn't save the current graves!");
                                if (sender instanceof Player player) {
                                    reloadText = reloadText.color(NamedTextColor.GREEN);
                                    noticeText = noticeText.color(NamedTextColor.RED);
                                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                                }
                                sender.sendMessage(
                                        reloadText.appendNewline().append(noticeText)
                                );
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build();
        }

    }

}
