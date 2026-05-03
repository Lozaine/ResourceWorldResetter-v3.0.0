package com.lozaine.ResourceWorldResetter.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Provides access to v4 configuration with proper defaults and validation.
 * Abstracts away the underlying YAML structure from the rest of the plugin.
 */
public class ConfigManager {
    private static final String DEFAULT_WORLD_NAME = "Resources";
    private static final int DEFAULT_RESTART_HOUR = 3;
    private static final int DEFAULT_WARNING_MINUTES = 5;
    private static final int DEFAULT_RESET_DAY = 1;

    private final JavaPlugin plugin;
    private final Logger logger;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Gets the world name to reset. Empty string means not configured.
     */
    public String getWorldName() {
        return plugin.getConfig().getString("worldName", "");
    }

    /**
     * Sets the world name and saves config.
     */
    public void setWorldName(String worldName) {
        plugin.getConfig().set("worldName", worldName);
        plugin.saveConfig();
    }

    /**
     * Gets the schedule mode: daily, weekly, or monthly.
     */
    public String getScheduleMode() {
        return plugin.getConfig().getString("schedule.mode", "daily");
    }

    /**
     * Sets the schedule mode and saves config.
     */
    public void setScheduleMode(String mode) {
        plugin.getConfig().set("schedule.mode", mode);
        plugin.saveConfig();
    }

    /**
     * Gets the reset hour (0-23).
     */
    public int getScheduleHour() {
        int hour = plugin.getConfig().getInt("schedule.time.hour", DEFAULT_RESTART_HOUR);
        // Clamp to valid 24-hour range
        return Math.max(0, Math.min(23, hour));
    }

    /**
     * Sets the reset hour and saves config.
     */
    public void setScheduleHour(int hour) {
        plugin.getConfig().set("schedule.time.hour", Math.max(0, Math.min(23, hour)));
        plugin.saveConfig();
    }

    /**
     * Gets the warning time in minutes before reset.
     */
    public int getWarningMinutes() {
        return Math.max(1, plugin.getConfig().getInt("schedule.warningMinutes", DEFAULT_WARNING_MINUTES));
    }

    /**
     * Sets the warning minutes and saves config.
     */
    public void setWarningMinutes(int minutes) {
        plugin.getConfig().set("schedule.warningMinutes", Math.max(1, minutes));
        plugin.saveConfig();
    }

    /**
     * Gets the reset day (for weekly: 1-7 where 1=Monday; for monthly: 1-31).
     */
    public int getScheduleDay() {
        return plugin.getConfig().getInt("schedule.day", DEFAULT_RESET_DAY);
    }

    /**
     * Sets the reset day and saves config.
     */
    public void setScheduleDay(int day) {
        plugin.getConfig().set("schedule.day", day);
        plugin.saveConfig();
    }

    /**
     * Gets whether region-based resets are enabled.
     */
    public boolean isRegionsEnabled() {
        return plugin.getConfig().getBoolean("regions.enabled", false);
    }

    /**
     * Sets whether region-based resets are enabled.
     */
    public void setRegionsEnabled(boolean enabled) {
        plugin.getConfig().set("regions.enabled", enabled);
        plugin.saveConfig();
    }

    /**
     * Gets whether regions regenerate immediately when added via command.
     */
    public boolean isRegionsImmediateOnAdd() {
        return plugin.getConfig().getBoolean("regions.immediateRegenerationOnAdd", true);
    }

    /**
     * Gets the list of regions to reset (format: "x,z").
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRegionsList() {
        Set<String> regions = new HashSet<>();
        Object list = plugin.getConfig().get("regions.list");
        
        if (list instanceof java.util.List) {
            for (Object item : (java.util.List<?>) list) {
                if (item instanceof String) {
                    regions.add((String) item);
                }
            }
        }
        
        return regions;
    }

    /**
     * Adds a region to the list and saves config.
     */
    public void addRegion(String region) {
        Set<String> regions = getRegionsList();
        regions.add(region);
        plugin.getConfig().set("regions.list", new java.util.ArrayList<>(regions));
        plugin.saveConfig();
    }

    /**
     * Removes a region from the list and saves config.
     */
    public void removeRegion(String region) {
        Set<String> regions = getRegionsList();
        regions.remove(region);
        plugin.getConfig().set("regions.list", new java.util.ArrayList<>(regions));
        plugin.saveConfig();
    }

    /**
     * Clears all regions and saves config.
     */
    public void clearRegions() {
        plugin.getConfig().set("regions.list", new java.util.ArrayList<>());
        plugin.saveConfig();
    }

    /**
     * Gets the config version.
     */
    public int getConfigVersion() {
        return plugin.getConfig().getInt("configVersion", 0);
    }
}
