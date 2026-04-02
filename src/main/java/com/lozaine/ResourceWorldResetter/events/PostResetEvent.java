package com.lozaine.ResourceWorldResetter.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread after a full resource-world reset attempt finishes,
 * whether it succeeded or failed.
 *
 * <p>Listen to this event to perform post-reset setup, announce results to
 * an external system, or log audit information.
 */
public class PostResetEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final String resetType;
    private final boolean success;

    /**
     * @param worldName the name of the world that was reset
     * @param resetType the configured reset type ("daily", "weekly", or "monthly")
     * @param success   {@code true} if the reset completed successfully
     */
    public PostResetEvent(String worldName, String resetType, boolean success) {
        this.worldName = worldName;
        this.resetType = resetType;
        this.success = success;
    }

    /** Returns the name of the world that was reset. */
    public String getWorldName() {
        return worldName;
    }

    /** Returns the reset mode string ("daily", "weekly", or "monthly"). */
    public String getResetType() {
        return resetType;
    }

    /** Returns {@code true} if the reset completed without errors. */
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
