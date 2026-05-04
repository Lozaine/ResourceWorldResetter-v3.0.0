package com.lozaine.ResourceWorldResetter.gui;

import com.lozaine.ResourceWorldResetter.ResourceWorldResetter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Set;

/**
 * Listener for TeleportGUI interactions.
 * Handles world selection and teleportation.
 */
public class TeleportGUIListener implements Listener {
    private final ResourceWorldResetter plugin;
    private final TeleportGUI teleportGui;
    private final Set<String> teleportInventoryTitles = new HashSet<>();

    public TeleportGUIListener(ResourceWorldResetter plugin, TeleportGUI teleportGui) {
        this.plugin = plugin;
        this.teleportGui = teleportGui;
        
        teleportInventoryTitles.add(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Select World to Teleport");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String inventoryTitle = event.getView().getTitle();
        if (!teleportInventoryTitles.stream().anyMatch(inventoryTitle::contains)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        String displayName = meta.getDisplayName();

        // Handle "Back" button - teleport to previous location
        if (displayName.contains("Back")) {
            handleBackButton(player);
            return;
        }

        // Handle page navigation
        if (displayName.contains("Previous Page") || displayName.contains("Next Page")) {
            handlePageNavigation(player, displayName);
            return;
        }

        // Handle world selection
        handleWorldSelection(player, clicked, displayName);
    }

    /**
     * Handles the back button to return to previous location.
     *
     * @param player the player
     */
    private void handleBackButton(Player player) {
        if (!plugin.getTeleportationSystem().hasPreviousLocation(player)) {
            player.sendMessage(ChatColor.RED + "[RWR] No previous location recorded.");
            player.closeInventory();
            return;
        }

        Location previousLocation = plugin.getTeleportationSystem().getPreviousLocation(player);
        if (previousLocation == null || previousLocation.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "[RWR] Previous location is no longer available.");
            player.closeInventory();
            return;
        }

        ResourceWorldResetter.BackTeleportDestination destination =
                plugin.resolveBackTeleportDestination(player, previousLocation);
        Location targetLocation = destination.location();

        boolean teleported = player.teleport(targetLocation);
        if (teleported) {
            if (destination.redirectedBecauseReset()) {
                if (destination.villageTargeted()) {
                    player.sendMessage(ChatColor.GREEN + "[RWR] Resource world was reset since your last location. Teleported to a nearby village instead.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "[RWR] Resource world was reset since your last location. Teleported to safe world spawn instead.");
                }
            } else {
                player.sendMessage(ChatColor.GREEN + "[RWR] Teleported back to your previous location.");
            }
            plugin.getTeleportationSystem().clearPlayerLocation(player);
        } else {
            player.sendMessage(ChatColor.RED + "[RWR] Failed to teleport back.");
        }

        player.closeInventory();
    }

    /**
     * Handles page navigation buttons.
     *
     * @param player      the player
     * @param displayName the button name
     */
    private void handlePageNavigation(Player player, String displayName) {
        int currentPage = teleportGui.getCurrentPage(player);
        int newPage = currentPage;

        if (displayName.contains("Previous")) {
            newPage = Math.max(0, currentPage - 1);
        } else if (displayName.contains("Next")) {
            newPage = currentPage + 1;
        }

        teleportGui.openWorldSelectionMenuAtPage(player, newPage);
    }

    /**
     * Handles world selection and teleportation.
     *
     * @param player      the player
     * @param item        the clicked item
     * @param displayName the display name of the item
     */
    private void handleWorldSelection(Player player, ItemStack item, String displayName) {
        // Extract world name from display name (remove color codes and environment tags)
        String worldName = ChatColor.stripColor(displayName)
                .replace(" [NETHER]", "")
                .replace(" [END]", "")
                .trim();

        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            player.sendMessage(ChatColor.RED + "[RWR] World '" + worldName + "' not found.");
            player.closeInventory();
            return;
        }

        // Record current location before teleporting
        plugin.getTeleportationSystem().recordPlayerLocation(player);

        // Teleport to the selected world's spawn location
        Location spawnLocation = targetWorld.getSpawnLocation();
        Location safeLocation = getSafeTeleportLocation(spawnLocation);

        boolean teleported = player.teleport(safeLocation);
        if (teleported) {
            plugin.getTeleportationSystem().recordTeleportedWorld(player, targetWorld);
            player.sendMessage(ChatColor.GREEN + "[RWR] Teleported to " + plugin.getTeleportationSystem().getFormattedWorldName(targetWorld));
        } else {
            player.sendMessage(ChatColor.RED + "[RWR] Failed to teleport to " + worldName);
            plugin.getTeleportationSystem().clearPlayerLocation(player);
        }

        player.closeInventory();
    }

    /**
     * Computes a safe landing location to avoid landing in unsafe spots.
     * Special handling for Nether to avoid bedrock and lava.
     *
     * @param baseLocation the base location to work from
     * @return a safe location for the player to land
     */
    private Location getSafeTeleportLocation(Location baseLocation) {
        if (baseLocation == null || baseLocation.getWorld() == null) {
            return baseLocation;
        }

        World world = baseLocation.getWorld();
        double x = baseLocation.getX();
        double z = baseLocation.getZ();

        // Different search strategy for Nether
        if (world.getEnvironment() == World.Environment.NETHER) {
            return getSafeNetherLocation(world, x, z, baseLocation);
        }

        // For regular worlds and End, search from top down
        for (int y = 255; y >= 0; y--) {
            if (isSafeBlock(world, x, y, z)) {
                Location landLoc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                landLoc.setYaw(baseLocation.getYaw());
                landLoc.setPitch(baseLocation.getPitch());
                return landLoc;
            }
        }

        // Fallback to spawn location if safe spot not found
        return baseLocation;
    }

    /**
     * Finds a safe location in the Nether, avoiding bedrock and lava.
     *
     * @param world the Nether world
     * @param x     the x coordinate
     * @param z     the z coordinate
     * @param baseLocation the base location for reference
     * @return a safe location in the Nether
     */
    private Location getSafeNetherLocation(World world, double x, double z, Location baseLocation) {
        // Search in the reasonable Nether range (y=5 to y=120, avoiding bedrock ceiling at y=127)
        // Start from middle and work outward
        for (int y = 100; y >= 5; y--) {
            if (isSafeBlock(world, x, y, z)) {
                Location landLoc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                landLoc.setYaw(baseLocation.getYaw());
                landLoc.setPitch(baseLocation.getPitch());
                return landLoc;
            }
        }

        // If spawn location is unsuitable, search in expanding square pattern around spawn
        int searchRadius = 40;
        for (int radius = 1; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check perimeter squares to avoid redundant checks
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    double searchX = x + dx;
                    double searchZ = z + dz;

                    for (int y = 100; y >= 5; y--) {
                        if (isSafeBlock(world, searchX, y, searchZ)) {
                            Location landLoc = new Location(world, searchX + 0.5, y + 1.0, searchZ + 0.5);
                            landLoc.setYaw(baseLocation.getYaw());
                            landLoc.setPitch(baseLocation.getPitch());
                            return landLoc;
                        }
                    }
                }
            }
        }

        // Final fallback: find ANY solid block in Nether range and put them on top
        for (int y = 120; y >= 5; y--) {
            org.bukkit.block.Block block = world.getBlockAt((int) x, y, (int) z);
            if (block.getType().isSolid()) {
                Location fallback = new Location(world, x + 0.5, Math.min(y + 1.0, 127.0), z + 0.5);
                fallback.setYaw(baseLocation.getYaw());
                fallback.setPitch(baseLocation.getPitch());
                return fallback;
            }
        }

        // Last resort: place at y=64 in open space (middle of Nether)
        Location lastResort = new Location(world, x + 0.5, 64.0, z + 0.5);
        lastResort.setYaw(baseLocation.getYaw());
        lastResort.setPitch(baseLocation.getPitch());
        return lastResort;
    }

    /**
     * Checks if a block is safe to stand on.
     * Avoids dangerous blocks like lava, fire, bedrock, magma, etc.
     *
     * @param world the world
     * @param x     x coordinate
     * @param y     y coordinate
     * @param z     z coordinate
     * @return true if the block is solid and safe to stand on
     */
    private boolean isSafeBlock(World world, double x, int y, double z) {
        if (world == null || y < 0 || y > 255) {
            return false;
        }

        org.bukkit.block.Block block = world.getBlockAt((int) x, y, (int) z);
        Material blockType = block.getType();

        // Air and transparent blocks are not safe to stand on
        if (!blockType.isSolid()) {
            return false;
        }

        String blockName = blockType.toString();

        // Never stand on bedrock (top and bottom of Nether/End)
        if (blockName.contains("BEDROCK")) {
            return false;
        }

        // Avoid dangerous blocks
        if (blockName.contains("LAVA") || 
            blockName.contains("MAGMA") || 
            blockName.contains("FIRE") ||
            blockName.contains("CACTUS") ||
            blockName.contains("SWEET_BERRY") ||
            blockName.contains("POWDER_SNOW")) {
            return false;
        }

        // Check block above to make sure there's space to stand
        org.bukkit.block.Block blockAbove = world.getBlockAt((int) x, y + 1, (int) z);
        if (blockAbove.getType().isSolid()) {
            return false;
        }

        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String inventoryTitle = event.getView().getTitle();
        if (teleportInventoryTitles.stream().anyMatch(inventoryTitle::contains)) {
            teleportGui.clearPageTracker(player);
        }
    }
}
