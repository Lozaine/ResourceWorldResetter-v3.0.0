package com.lozaine.ResourceWorldResetter.gui;

import com.lozaine.ResourceWorldResetter.ResourceWorldResetter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for players to select which world to teleport to.
 * Shows all available worlds including Nether and End variants.
 */
public class TeleportGUI implements Listener {
    private final ResourceWorldResetter plugin;
    private final Map<UUID, Integer> pageTracker = new HashMap<>();

    public TeleportGUI(ResourceWorldResetter plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the world selection menu for a player to teleport.
     *
     * @param player the player opening the menu
     */
    public void openWorldSelectionMenu(Player player) {
        openWorldSelectionMenuAtPage(player, 0);
    }

    /**
     * Opens the world selection menu at a specific page.
     *
     * @param player the player
     * @param page   the page number (0-indexed)
     */
    public void openWorldSelectionMenuAtPage(Player player, int page) {
        openWorldSelectionMenuPage(player, page);
    }

    /**
     * Internal method for opening the world selection menu at a specific page.
     *
     * @param player the player
     * @param page   the page number (0-indexed)
     */
    private void openWorldSelectionMenuPage(Player player, int page) {
        World[] allWorlds = plugin.getTeleportationSystem().getAvailableWorlds();

        // Filter to only include normal worlds (no Nether or End)
        java.util.List<World> filteredWorlds = new java.util.ArrayList<>();
        for (World world : allWorlds) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                filteredWorlds.add(world);
            }
        }

        if (filteredWorlds.isEmpty()) {
            player.sendMessage(ChatColor.RED + "[RWR] No worlds available for teleportation.");
            return;
        }

        // Calculate pagination (9 slots per page, minus 1 for back button)
        int worldsPerPage = 8;
        int totalPages = (int) Math.ceil((double) filteredWorlds.size() / worldsPerPage);

        if (page < 0 || page >= totalPages) {
            page = 0;
        }

        Inventory gui = Bukkit.createInventory(player, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Select World to Teleport");

        int startIdx = page * worldsPerPage;
        int endIdx = Math.min(startIdx + worldsPerPage, filteredWorlds.size());

        int slot = 0;
        for (int i = startIdx; i < endIdx; i++) {
            World world = filteredWorlds.get(i);
            Material icon = getWorldIcon(world);
            String displayName = plugin.getTeleportationSystem().getFormattedWorldName(world);

            gui.setItem(slot, createWorldItem(icon, displayName, world));
            slot++;
        }

        // Add "Back" button at the end
        gui.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "Back", "Return to previous location (if available)"));

        // Add page navigation if multiple pages
        if (totalPages > 1) {
            if (page > 0) {
                gui.setItem(48, createGuiItem(Material.ARROW, ChatColor.GRAY + "Previous Page", ""));
            }
            if (page < totalPages - 1) {
                gui.setItem(50, createGuiItem(Material.ARROW, ChatColor.GRAY + "Next Page", ""));
            }
            gui.setItem(49, createGuiItem(Material.BOOK, ChatColor.GRAY + "Page " + (page + 1) + "/" + totalPages, ""));
        }

        player.openInventory(gui);
        pageTracker.put(player.getUniqueId(), page);
    }

    /**
     * Gets the appropriate icon for a world based on its environment.
     *
     * @param world the world
     * @return the material to use as an icon
     */
    private Material getWorldIcon(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    /**
     * Creates an item representing a world for teleportation.
     *
     * @param material the material/icon
     * @param name     the display name
     * @param world    the world
     * @return an ItemStack representing the world
     */
    private ItemStack createWorldItem(Material material, String name, World world) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to teleport");
            lore.add(ChatColor.GRAY + "Players: " + ChatColor.YELLOW + world.getPlayers().size());

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        // Store world name in item metadata for retrieval
        item.setData(item.getData());

        return item;
    }

    /**
     * Creates a generic GUI item with name and lore.
     *
     * @param material   the material
     * @param name       the display name
     * @param lore       the lore text
     * @return an ItemStack
     */
    private ItemStack createGuiItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                List<String> loreList = new ArrayList<>();
                loreList.add(lore);
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Gets the current page for a player's teleport menu.
     *
     * @param player the player
     * @return the current page
     */
    public int getCurrentPage(Player player) {
        return pageTracker.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Clears the page tracker for a player.
     *
     * @param player the player
     */
    public void clearPageTracker(Player player) {
        pageTracker.remove(player.getUniqueId());
    }
}
