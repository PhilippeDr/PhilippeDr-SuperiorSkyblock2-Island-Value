package dev.philippedr.islandvalue.commands;

import dev.philippedr.islandvalue.PhilippeDrIslandValuePlugin;
import dev.philippedr.islandvalue.superiorskyblock.SuperiorSkyblock2IslandValueCheck;
import dev.philippedr.islandvalue.superiorskyblock.SuperiorSkyblock2IslandValueCheck.BlockWorthLine;
import dev.philippedr.islandvalue.superiorskyblock.SuperiorSkyblock2IslandValueCheck.IslandWorthDetails;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class IsValueCommand implements CommandExecutor, TabCompleter {

    private final PhilippeDrIslandValuePlugin plugin;
    private final SuperiorSkyblock2IslandValueCheck islandValueCheck;

    public IsValueCommand(PhilippeDrIslandValuePlugin plugin, SuperiorSkyblock2IslandValueCheck islandValueCheck) {
        this.plugin = plugin;
        this.islandValueCheck = islandValueCheck;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " info");
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(ChatColor.AQUA + "philippedr_island_value" + ChatColor.GRAY + " v" + plugin.getDescription().getVersion());
            sender.sendMessage(ChatColor.GRAY + "Paper: " + ChatColor.WHITE + plugin.getServer().getName());
            sender.sendMessage(ChatColor.GRAY + "Command: " + ChatColor.WHITE + "/" + label + " info");
            return true;
        }

        if (args.length == 1) {
            String targetName = args[0];

            Optional<IslandWorthDetails> detailsOpt = islandValueCheck.getIslandWorthDetailsByUsername(targetName);
            if (detailsOpt.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Couldn't get island value for '" + targetName + "'. (No island / player not found / SuperiorSkyblock2 not installed)");
                return true;
            }

            IslandWorthDetails details = detailsOpt.get();
            sender.sendMessage(ChatColor.GRAY + "Island value for " + ChatColor.AQUA + targetName + ChatColor.GRAY + ": " + ChatColor.GREEN + SuperiorSkyblock2IslandValueCheck.formatDecimal(details.worth()));

            Optional<List<BlockWorthLine>> topBlocksOpt = islandValueCheck.getTopWorthBlocksByUsername(targetName, 10);
            if (topBlocksOpt.isPresent()) {
                List<BlockWorthLine> topBlocks = topBlocksOpt.get();
                if (topBlocks.isEmpty()) {
                    sender.sendMessage(ChatColor.DARK_GRAY + "  (No block breakdown available)");
                } else {
                    sender.sendMessage(ChatColor.GRAY + "Top blocks:" );
                    int idx = 1;
                    for (BlockWorthLine line : topBlocks) {
                        String star = (idx == 1) ? (ChatColor.YELLOW + "★ " + ChatColor.RESET) : "";
                        sender.sendMessage(
                                ChatColor.DARK_GRAY + "  " + idx + ") " +
                                        star + ChatColor.WHITE + line.key() +
                                        ChatColor.GRAY + " x" + ChatColor.WHITE + line.amount() +
                                        ChatColor.GRAY + " @ " + ChatColor.WHITE + SuperiorSkyblock2IslandValueCheck.formatDecimal(line.worthEach()) +
                                        ChatColor.GRAY + " = " + ChatColor.GREEN + SuperiorSkyblock2IslandValueCheck.formatDecimal(line.worthTotal())
                        );
                        idx++;
                    }
                }
            }

            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Try: /" + label + " info");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("info");
            suggestions.addAll(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            return filterPrefix(suggestions, args[0]);
        }

        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) return options;
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(p)) out.add(opt);
        }
        return out;
    }
}
