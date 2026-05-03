package com.lozaine.ResourceWorldResetter.commands;

import com.lozaine.ResourceWorldResetter.ResourceWorldResetter;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RwrRegionCommand implements CommandExecutor {
    private final ResourceWorldResetter plugin;

    public RwrRegionCommand(ResourceWorldResetter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, args);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("resourceworldresetter.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/rwr region enable|disable|list|add <rx> <rz>|remove <rx> <rz>|addhere");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "enable":
                plugin.setRegionsEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Region mode enabled.");
                return true;
            case "disable":
                plugin.setRegionsEnabled(false);
                sender.sendMessage(ChatColor.YELLOW + "Region mode disabled.");
                return true;
            case "list":
                sender.sendMessage(ChatColor.AQUA + "Regions: " + String.join(", ", plugin.getRegionsToReset()));
                return true;
            case "add":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rwr region add <regionX> <regionZ>");
                    return true;
                }
                try {
                    int rx = Integer.parseInt(args[1]);
                    int rz = Integer.parseInt(args[2]);
                    plugin.addRegionToReset(rx, rz);
                    sender.sendMessage(ChatColor.GREEN + "Added region " + rx + "," + rz + " to reset list.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "regionX and regionZ must be integers.");
                }
                return true;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rwr region remove <regionX> <regionZ>");
                    return true;
                }
                try {
                    int rx = Integer.parseInt(args[1]);
                    int rz = Integer.parseInt(args[2]);
                    plugin.removeRegionToReset(rx, rz);
                    sender.sendMessage(ChatColor.YELLOW + "Removed region " + rx + "," + rz + ".");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "regionX and regionZ must be integers.");
                }
                return true;
            case "addhere":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Players only for addhere.");
                    return true;
                }
                Player player = (Player) sender;
                World world = player.getWorld();
                if (!world.getName().equalsIgnoreCase(plugin.getWorldName())) {
                    sender.sendMessage(ChatColor.RED + "You must be in the resource world: " + plugin.getWorldName());
                    return true;
                }
                int chunkX = player.getLocation().getBlockX() >> 4;
                int chunkZ = player.getLocation().getBlockZ() >> 4;
                int regionX = chunkX >> 5;
                int regionZ = chunkZ >> 5;
                plugin.addRegionToReset(regionX, regionZ);
                sender.sendMessage(ChatColor.GREEN + "Added current region " + regionX + "," + regionZ + ".");
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                return true;
        }
    }
}

