package com.lozaine.ResourceWorldResetter.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread immediately before a single region file is scheduled
 * for regeneration during a region-based reset.
 *
 * <p>Cancelling this event skips the reset for the specific region while
 * allowing the remaining queued regions to continue processing.
 *
 * <p>Region coordinates follow Minecraft's region file convention: each unit
 * covers a 32×32 chunk (512×512 block) area.
 */
public class RegionPreResetEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final int regionX;
    private final int regionZ;
    private boolean cancelled;

    /**
     * @param worldName the name of the world containing the region
     * @param regionX   the region X coordinate (chunk X / 32)
     * @param regionZ   the region Z coordinate (chunk Z / 32)
     */
    public RegionPreResetEvent(String worldName, int regionX, int regionZ) {
        this.worldName = worldName;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.cancelled = false;
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

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
