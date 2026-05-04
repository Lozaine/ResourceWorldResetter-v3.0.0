package com.lozaine.ResourceWorldResetter.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player teleportation tracking and location recording.
 * Keeps track of where players came from before teleporting to resource worlds.
 */
public class TeleportationSystem {
    /** Stores the previous location for each player before they teleport. */
    private final Map<UUID, Location> previousLocations = new HashMap<>();

    /** Stores when a previous location was recorded for each player (epoch millis). */
    private final Map<UUID, Long> previousLocationRecordedAt = new HashMap<>();
    
    /** Tracks the world a player teleported to for back-navigation purposes. */
    private final Map<UUID, World> teleportedToWorld = new HashMap<>();

    /**
     * Records a player's current location before teleporting them.
     *
     * @param player the player to record
     */
    public void recordPlayerLocation(Player player) {
        if (player != null && player.isOnline()) {
            previousLocations.put(player.getUniqueId(), player.getLocation().clone());
            previousLocationRecordedAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Records which world a player just teleported to.
     *
     * @param player the player
     * @param world  the destination world
     */
    public void recordTeleportedWorld(Player player, World world) {
        if (player != null) {
            teleportedToWorld.put(player.getUniqueId(), world);
        }
    }

    /**
     * Retrieves the previously recorded location for a player.
     *
     * @param player the player
     * @return the previous location, or null if not recorded
     */
    public Location getPreviousLocation(Player player) {
        if (player == null) {
            return null;
        }
        return previousLocations.get(player.getUniqueId());
    }

    /**
     * Returns when the player's previous location was recorded.
     *
     * @param player the player
     * @return epoch millis, or 0 if unknown
     */
    public long getPreviousLocationRecordedAt(Player player) {
        if (player == null) {
            return 0L;
        }
        return previousLocationRecordedAt.getOrDefault(player.getUniqueId(), 0L);
    }

    /**
     * Checks if a player has a recorded previous location.
     *
     * @param player the player
     * @return true if a previous location is recorded
     */
    public boolean hasPreviousLocation(Player player) {
        if (player == null) {
            return false;
        }
        Location previous = previousLocations.get(player.getUniqueId());
        return previous != null && previous.getWorld() != null && Bukkit.getWorld(previous.getWorld().getUID()) != null;
    }

    /**
     * Clears the recorded location for a player.
     *
     * @param player the player
     */
    public void clearPlayerLocation(Player player) {
        if (player != null) {
            previousLocations.remove(player.getUniqueId());
            previousLocationRecordedAt.remove(player.getUniqueId());
            teleportedToWorld.remove(player.getUniqueId());
        }
    }

    /**
     * Clears all tracked locations (e.g., on plugin disable).
     */
    public void clearAll() {
        previousLocations.clear();
        previousLocationRecordedAt.clear();
        teleportedToWorld.clear();
    }

    /**
     * Gets the list of all available worlds including regular, Nether, and End variants.
     *
     * @return array of all worlds on the server
     */
    public World[] getAvailableWorlds() {
        return Bukkit.getWorlds().toArray(new World[0]);
    }

    /**
     * Gets a formatted name for a world based on its environment.
     *
     * @param world the world
     * @return formatted world name with environment indicator
     */
    public String getFormattedWorldName(World world) {
        String baseName = world.getName();
        String environment = switch (world.getEnvironment()) {
            case NETHER -> " [NETHER]";
            case THE_END -> " [END]";
            default -> "";
        };
        return baseName + environment;
    }
}
