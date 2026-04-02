package com.lozaine.ResourceWorldResetter.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread after a single region has finished being regenerated
 * (or attempted) during a region-based reset.
 *
 * <p>Region coordinates follow Minecraft's region file convention: each unit
 * covers a 32×32 chunk (512×512 block) area.
 */
public class RegionPostResetEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final int regionX;
    private final int regionZ;
    private final boolean success;

    /**
     * @param worldName the name of the world containing the region
     * @param regionX   the region X coordinate (chunk X / 32)
     * @param regionZ   the region Z coordinate (chunk Z / 32)
     * @param success   {@code true} if the region files were deleted without errors
     */
    public RegionPostResetEvent(String worldName, int regionX, int regionZ, boolean success) {
        this.worldName = worldName;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.success = success;
    }

    /** Returns the name of the world containing the region. */
    public String getWorldName() {
        return worldName;
    }

    /** Returns the region X coordinate (chunk X / 32). */
    public int getRegionX() {
        return regionX;
    }

    /** Returns the region Z coordinate (chunk Z / 32). */
    public int getRegionZ() {
        return regionZ;
    }

    /** Returns {@code true} if the region files were reset without errors. */
    public boolean isSuccess() {
        return success;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
