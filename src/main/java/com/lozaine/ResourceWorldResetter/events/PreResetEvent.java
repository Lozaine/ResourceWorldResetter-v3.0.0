package com.lozaine.ResourceWorldResetter.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread immediately before a full resource-world reset begins.
 * Cancelling this event prevents the reset from executing.
 *
 * <p>Listen to this event in another plugin to perform pre-reset cleanup or to
 * conditionally block the reset (e.g., if a boss fight is in progress).
 */
public class PreResetEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final String resetType;
    private boolean cancelled;

    /**
     * @param worldName the name of the world that is about to be reset
     * @param resetType the configured reset type ("daily", "weekly", or "monthly")
     */
    public PreResetEvent(String worldName, String resetType) {
        this.worldName = worldName;
        this.resetType = resetType;
        this.cancelled = false;
    }

    /** Returns the name of the world that is about to be reset. */
    public String getWorldName() {
        return worldName;
    }

    /** Returns the reset mode string ("daily", "weekly", or "monthly"). */
    public String getResetType() {
        return resetType;
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
