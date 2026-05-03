package com.lozaine.ResourceWorldResetter.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import com.lozaine.ResourceWorldResetter.ResourceWorldResetter;
import com.lozaine.ResourceWorldResetter.gui.AdminGUI.GuiType;

public class AdminGUIListener implements Listener {
    private final ResourceWorldResetter plugin;
    private final AdminGUI adminGUI;

    public AdminGUIListener(ResourceWorldResetter plugin, AdminGUI adminGUI) {
        this.plugin = plugin;
        this.adminGUI = adminGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (!adminGUI.hasActiveGui(player.getUniqueId())) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || clickedItem.getType() == Material.AIR) return;

        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        GuiType guiType = adminGUI.getActiveGuiType(player.getUniqueId());

        switch (guiType) {
            case MAIN_MENU:
                handleMainMenuClick(player, itemName);
                break;
            case RESET_TYPE_MENU:
                handleResetTypeMenuClick(player, itemName);
                break;
            case RESET_DAY_MENU:
                handleResetDayMenuClick(player, itemName);
                break;
            case WARNING_TIME_MENU:
                handleWarningTimeMenuClick(player, itemName);
                break;
            case RESTART_TIME_MENU:
                handleRestartTimeMenuClick(player, itemName);
                break;
            case MONTHLY_DAY_MENU:
                handleMonthlyDayMenuClick(player, itemName);
                break;
            case WORLD_SELECTION_MENU:
                handleWorldSelectionMenuClick(player, itemName);
                break;
        }
    }

    // New method to handle world selection
    private void handleWorldSelectionMenuClick(Player player, String itemName) {
        if (itemName.equals("Back")) {
            adminGUI.openMainMenu(player);
            return;
        }

        // Extract the world name (remove color codes and (Current) suffix if present)
        String worldName = itemName;
        if (worldName.contains(" (Current)")) {
            worldName = worldName.substring(0, worldName.indexOf(" (Current)"));
        }

        // Update the world name in plugin config
        plugin.setWorldName(worldName);
        player.sendMessage(ChatColor.GREEN + "Resource world set to: " + worldName);
        adminGUI.openMainMenu(player);
    }

    private void handleMainMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "Enable Region Mode":
                plugin.setRegionsEnabled(true);
                player.sendMessage(ChatColor.GREEN + "Region mode enabled.");
                adminGUI.openMainMenu(player);
                break;
            case "Disable Region Mode":
                plugin.setRegionsEnabled(false);
                player.sendMessage(ChatColor.YELLOW + "Region mode disabled.");
                adminGUI.openMainMenu(player);
                break;
            case "Manage Regions":
                player.sendMessage(ChatColor.YELLOW + "Use /rwr region add <rx> <rz> or /rwr region remove <rx> <rz>.");
                break;
            case "Change World":
                adminGUI.openWorldSelectionMenu(player);
                break;
            case "Reset Type":
                adminGUI.openResetTypeMenu(player);
                break;
            case "Restart Time":
                adminGUI.openRestartTimeMenu(player);
                break;
            case "Warning Time":
                adminGUI.openWarningTimeMenu(player);
                break;
            case "Force Reset":
                player.closeInventory();
                plugin.resetResourceWorld();
                player.sendMessage(ChatColor.GREEN + "World reset initiated!");
                break;
            case "Reload Config":
                player.closeInventory();
                plugin.reloadConfig();
                plugin.loadConfig();
                player.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                break;
        }
    }

    private void handleWarningTimeMenuClick(Player player, String itemName) {
        if (itemName.equals("Back")) {
            adminGUI.openMainMenu(player);
            return;
        }

        int minutes = switch (itemName) {
            case "No Warning" -> 0;
            case "1 Minute" -> 1;
            case "5 Minutes" -> 5;
            case "10 Minutes" -> 10;
            case "15 Minutes" -> 15;
            case "30 Minutes" -> 30;
            default -> -1;
        };

        if (minutes != -1) {
            plugin.setResetWarningTime(minutes);
            player.sendMessage(ChatColor.GREEN + "Warning time set to " + minutes + " minutes!");
        }
        adminGUI.openMainMenu(player);
    }

    private void handleRestartTimeMenuClick(Player player, String itemName) {
        if (itemName.equals("Back")) {
            adminGUI.openMainMenu(player);
            return;
        }

        try {
            int hour = Integer.parseInt(itemName.split(":")[0]);
            if (hour >= 0 && hour <= 23) {
                plugin.setRestartTime(hour);
                player.sendMessage(ChatColor.GREEN + "Restart time set to " + hour + ":00!");
            }
        } catch (NumberFormatException ignored) {}

        adminGUI.openMainMenu(player);
    }

    private void handleResetTypeMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "Daily Reset":
                plugin.setResetType("daily");
                player.sendMessage(ChatColor.GREEN + "Reset type set to daily!");
                adminGUI.openMainMenu(player);
                break;
            case "Weekly Reset":
                plugin.setResetType("weekly");
                player.sendMessage(ChatColor.GREEN + "Reset type set to weekly!");
                adminGUI.openResetDayMenu(player);
                break;
            case "Monthly Reset":
                plugin.setResetType("monthly");
                player.sendMessage(ChatColor.GREEN + "Reset type set to monthly!");
                adminGUI.openMonthlyDayMenu(player);
                break;
            case "Back":
                adminGUI.openMainMenu(player);
                break;
        }
    }

    private void handleMonthlyDayMenuClick(Player player, String itemName) {
        if (itemName.equals("Back")) {
            adminGUI.openMainMenu(player);
            return;
        }

        if (itemName.startsWith("Day ")) {
            try {
                int day = Integer.parseInt(itemName.substring(4));
                if (day >= 1 && day <= 31) {
                    plugin.setResetDay(day);
                    player.sendMessage(ChatColor.GREEN + "Monthly reset day set to day " + day + "!");
                }
            } catch (NumberFormatException ignored) {}
        }

        adminGUI.openMainMenu(player);
    }

    private void handleResetDayMenuClick(Player player, String itemName) {
        if (itemName.equals("Back")) {
            adminGUI.openMainMenu(player);
            return;
        }

        int day = switch (itemName) {
            case "Monday" -> 1;
            case "Tuesday" -> 2;
            case "Wednesday" -> 3;
            case "Thursday" -> 4;
            case "Friday" -> 5;
            case "Saturday" -> 6;
            case "Sunday" -> 7;
            default -> -1;
        };

        if (day != -1) {
            plugin.setResetDay(day);
            player.sendMessage(ChatColor.GREEN + "Weekly reset day set to " + itemName + "!");
        }

        adminGUI.openMainMenu(player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        adminGUI.removeActiveGui(player.getUniqueId());
    }
}