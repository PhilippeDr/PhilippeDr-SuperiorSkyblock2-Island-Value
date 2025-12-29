package dev.philippedr.islandvalue;

import dev.philippedr.islandvalue.commands.IsValueCommand;
import dev.philippedr.islandvalue.hologram.HolographicValue;
import dev.philippedr.islandvalue.superiorskyblock.SuperiorSkyblock2Connector;
import dev.philippedr.islandvalue.superiorskyblock.SuperiorSkyblock2IslandValueCheck;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PhilippeDrIslandValuePlugin extends JavaPlugin {

    private HolographicValue holographicValue;

    @Override
    public void onEnable() {
        SuperiorSkyblock2Connector connector = new SuperiorSkyblock2Connector();
        SuperiorSkyblock2IslandValueCheck islandValueCheck = new SuperiorSkyblock2IslandValueCheck(connector);
        IsValueCommand isValueCommand = new IsValueCommand(this, islandValueCheck);

        PluginCommand command = getCommand("isvalue");
        if (command == null) {
            getLogger().severe("Command 'isvalue' missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        command.setExecutor(isValueCommand);
        command.setTabCompleter(isValueCommand);

        holographicValue = new HolographicValue(this, connector);
        holographicValue.start();

        getLogger().info("philippedr_island_value enabled.");
    }

    @Override
    public void onDisable() {
        if (holographicValue != null) {
            holographicValue.stop();
            holographicValue = null;
        }
        getLogger().info("philippedr_island_value disabled.");
    }
}
