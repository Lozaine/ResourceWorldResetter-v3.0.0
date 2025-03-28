package com.lozaine.ResourceWorldResetter;

import com.lozaine.ResourceWorldResetter.gui.AdminGUI;
import com.lozaine.ResourceWorldResetter.gui.AdminGUIListener;
import com.lozaine.ResourceWorldResetter.utils.LogUtil;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.lozaine.ResourceWorldResetter.metrics.Metrics;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static com.onarandombox.MultiverseCore.utils.FileUtils.deleteFolder;

public class ResourceWorldResetter extends JavaPlugin {
    private String worldName;
    private MultiverseCore core;
    private int restartTime;
    private int resetWarningTime;
    private String resetType;
    private int resetDay;
    private AdminGUI adminGUI;
    private int warningTaskId = -1;
    private int resetTaskId = -1;

    public String getWorldName() { return this.worldName; }
    public String getResetType() { return this.resetType; }
    public int getRestartTime() { return this.restartTime; }
    public int getResetWarningTime() { return this.resetWarningTime; }
    public int getResetDay() { return this.resetDay; }

    public void setWorldName(String name) {
        this.worldName = name;
        getConfig().set("worldName", name);
        saveConfig();
        ensureResourceWorldExists();
    }

    public void setResetType(String type) {
        this.resetType = type;
        getConfig().set("resetType", type);
        saveConfig();
        scheduleDailyReset(); // Reschedule after changing type
    }

    public void setResetDay(int day) {
        this.resetDay = day;
        getConfig().set("resetDay", day);
        saveConfig();
        scheduleDailyReset(); // Reschedule after changing day
    }

    public void setRestartTime(int hour) {
        if (hour >= 0 && hour <= 23) {
            this.restartTime = hour;
            getConfig().set("restartTime", hour);
            saveConfig();
            scheduleDailyReset(); // Reschedule after changing time

            LogUtil.log(getLogger(), "Restart time set to " + hour + ":00", Level.INFO);
        }
    }

    public void setResetWarningTime(int minutes) {
        if (minutes >= 0) {
            this.resetWarningTime = minutes;
            getConfig().set("resetWarningTime", minutes);
            saveConfig();
            scheduleDailyReset(); // Reschedule warning after changing time

            LogUtil.log(getLogger(), "Reset warning time set to " + minutes + " minutes", Level.INFO);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LogUtil.init(this);
        core = (MultiverseCore) Bukkit.getPluginManager().getPlugin("Multiverse-Core");

        if (core == null) {
            LogUtil.log(getLogger(), "Multiverse-Core not found! Disabling plugin.", Level.SEVERE);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        int pluginId = 25197;
        Metrics metrics = new Metrics(this, pluginId);
        // Track reset type (daily, weekly, monthly)
        metrics.addCustomChart(new Metrics.SimplePie("reset_type", () -> this.resetType));

        // Track what time users schedule resets
        metrics.addCustomChart(new Metrics.SimplePie("reset_hour", () -> String.valueOf(this.restartTime)));

        // Track warning time
        metrics.addCustomChart(new Metrics.SimplePie("warning_time", () -> String.valueOf(this.resetWarningTime)));

        // Track Minecraft version
        metrics.addCustomChart(new Metrics.SimplePie("server_version", () -> Bukkit.getBukkitVersion()));

        if (getConfig().getBoolean("metrics.enabled", true)) {
            metrics = new Metrics(this, pluginId);
            // Add custom charts...
            LogUtil.log(getLogger(), "bStats metrics enabled", Level.INFO);
        } else {
            LogUtil.log(getLogger(), "bStats metrics disabled by configuration", Level.INFO);
        }

        loadConfig();
        adminGUI = new AdminGUI(this);
        getServer().getPluginManager().registerEvents(new AdminGUIListener(this, adminGUI), this);

        ensureResourceWorldExists();
        scheduleDailyReset();
        LogUtil.log(getLogger(), "ResourcesWorldResetter v" + getDescription().getVersion() + " enabled successfully!", Level.INFO);
    }

    @Override
    public void onDisable() {
        cancelScheduledTasks();
        LogUtil.log(getLogger(), "ResourceWorldResetter disabled.", Level.INFO);
    }

    private void cancelScheduledTasks() {
        if (warningTaskId != -1) {
            Bukkit.getScheduler().cancelTask(warningTaskId);
            warningTaskId = -1;
        }
        if (resetTaskId != -1) {
            Bukkit.getScheduler().cancelTask(resetTaskId);
            resetTaskId = -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("resourceworldresetter.admin")) {
            switch (command.getName().toLowerCase()) {
                case "rwrgui":
                    if (sender instanceof Player player) {
                        adminGUI.openMainMenu(player);
                        return true;
                    } else {
                        sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                        return true;
                    }

                case "reloadrwr":
                    reloadConfig();
                    loadConfig();
                    scheduleDailyReset(); // Re-schedule resets after reload
                    sender.sendMessage(ChatColor.GREEN + "ResourcesWorldResetter configuration reloaded!");
                    return true;

                // Keeping resetworld for backward compatibility
                case "resetworld":
                    sender.sendMessage(ChatColor.GREEN + "Forcing resource world reset...");
                    resetResourceWorld(false);
                    return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        return false;
    }

    private void scheduleDailyReset() {
        // Cancel any existing scheduled tasks
        cancelScheduledTasks();

        // For daily, weekly, monthly resets
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReset = now.withHour(restartTime).withMinute(0).withSecond(0);

        // If current time is past reset time, schedule for next occurrence
        if (now.compareTo(nextReset) >= 0) {
            nextReset = nextReset.plusDays(1);
        }

        // Handle weekly resets
        if ("weekly".equals(resetType)) {
            int currentDay = now.getDayOfWeek().getValue(); // 1 (Monday) to 7 (Sunday)
            int daysUntilReset = (resetDay - currentDay + 7) % 7;
            if (daysUntilReset == 0 && now.compareTo(nextReset) >= 0) {
                daysUntilReset = 7;
            }
            nextReset = nextReset.plusDays(daysUntilReset);
            LogUtil.log(getLogger(), "Scheduled weekly reset for " + nextReset, Level.INFO);
        }
        // Handle monthly resets
        else if ("monthly".equals(resetType)) {
            LocalDateTime nextMonth = now;
            if (now.getDayOfMonth() > resetDay || (now.getDayOfMonth() == resetDay && now.compareTo(nextReset) >= 0)) {
                nextMonth = now.plusMonths(1);
            }

            int maxDay = nextMonth.getMonth().length(nextMonth.toLocalDate().isLeapYear());
            int actualResetDay = Math.min(resetDay, maxDay);
            nextReset = nextMonth.withDayOfMonth(actualResetDay).withHour(restartTime).withMinute(0).withSecond(0);
            LogUtil.log(getLogger(), "Scheduled monthly reset for " + nextReset, Level.INFO);
        }
        // Default to daily reset
        else {
            LogUtil.log(getLogger(), "Scheduled daily reset for " + nextReset, Level.INFO);
        }

        // Calculate delay in ticks for reset
        long resetDelayTicks = Math.max(1, ChronoUnit.SECONDS.between(now, nextReset) * 20);

        // Calculate delay for warning (if applicable)
        if (resetWarningTime > 0) {
            LocalDateTime warningTime = nextReset.minusMinutes(resetWarningTime);

            // If warning time is already passed, don't schedule warning
            if (now.isBefore(warningTime)) {
                long warningDelayTicks = Math.max(1, ChronoUnit.SECONDS.between(now, warningTime) * 20);

                LogUtil.log(getLogger(), "Warning scheduled for " + warningTime +
                        " (" + (warningDelayTicks/20/60) + " minutes from now)", Level.INFO);

                warningTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "[ResourceWorldResetter] " +
                            "Resource world will reset in " + resetWarningTime + " minutes!");
                    LogUtil.log(getLogger(), "Broadcast reset warning to players", Level.INFO);
                }, warningDelayTicks).getTaskId();
            }
        }

        // Schedule the actual reset
        LogUtil.log(getLogger(), "Next reset scheduled in " + (resetDelayTicks/20/60) + " minutes (" +
                (resetDelayTicks/20/60/60) + " hours)", Level.INFO);

        resetTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            LogUtil.log(getLogger(), "Executing scheduled reset task", Level.INFO);
            resetResourceWorld(true);
        }, resetDelayTicks).getTaskId();
    }

    public void resetResourceWorld() {
        resetResourceWorld(false);
    }

    public void resetResourceWorld(boolean isScheduled) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            LogUtil.log(getLogger(), "World '" + worldName + "' not found! Attempting to create it...", Level.WARNING);
            ensureResourceWorldExists();
            world = Bukkit.getWorld(worldName);

            if (world == null) {
                LogUtil.log(getLogger(), "Failed to create world '" + worldName + "'! Reset aborted.", Level.SEVERE);
                return;
            }
        }

        // Immediate reset for manual resets or if warning time is 0
        if (!isScheduled || resetWarningTime <= 0) {
            performReset(world);
        } else {
            // For scheduled resets, the warning was already handled in scheduleDailyReset()
            // So we just do the reset directly
            performReset(world);
        }
    }

    private void performReset(World world) {
        double tpsBefore = getServerTPS();
        long startTime = System.currentTimeMillis();

        LogUtil.log(getLogger(), "Starting world reset process for " + worldName, Level.INFO);
        Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                "Resource world reset in progress. Players in that world will be teleported to safety.");

        teleportPlayersSafely(world);

        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.unloadWorld(worldName)) {
            LogUtil.log(getLogger(), "Failed to unload world: " + worldName + ". Retrying with forced unload.", Level.WARNING);

            // Try forcing world unload if normal unload fails
            if (!worldManager.unloadWorld(worldName, true)) {
                LogUtil.log(getLogger(), "Forced unload also failed. Aborting reset.", Level.SEVERE);
                Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                        "Failed to reset resource world. Please notify an administrator.");
                return;
            }
        }

        CompletableFuture.runAsync(() -> {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            LogUtil.log(getLogger(), "Deleting world folder: " + worldFolder.getAbsolutePath(), Level.INFO);

            if (deleteFolder(worldFolder)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    LogUtil.log(getLogger(), "World folder deleted, recreating world", Level.INFO);
                    recreateWorld(worldManager);
                    long duration = System.currentTimeMillis() - startTime;
                    double tpsAfter = getServerTPS();
                    Bukkit.broadcastMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                            "Resource world reset completed in " + (duration/1000) + " seconds (TPS: " +
                            String.format("%.2f", tpsBefore) + " → " + String.format("%.2f", tpsAfter) + ").");
                    LogUtil.log(getLogger(), "Resource world reset completed in " + duration + "ms", Level.INFO);

                    // Reschedule for next time
                    if (resetTaskId != -1) {
                        Bukkit.getScheduler().runTaskLater(this, this::scheduleDailyReset, 20);
                    }
                });
            } else {
                LogUtil.log(getLogger(), "Failed to delete world folder: " + worldName, Level.SEVERE);
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                            "Resource world reset failed! Check server logs for details.");
                });
            }
        });
    }

    public double getServerTPS() {
        try {
            Object mcServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] recentTps = (double[]) mcServer.getClass().getField("recentTps").get(mcServer);
            return recentTps[0];
        } catch (Exception e) {
            getLogger().warning("Failed to get server TPS. Defaulting to 20.0");
            return 20.0;
        }
    }

    public void teleportPlayersSafely(World world) {
        World defaultWorld = Bukkit.getWorlds().get(0); // Get server's default world
        Location spawn = defaultWorld.getSpawnLocation();

        for (Player player : world.getPlayers()) {
            player.teleport(spawn);
            player.sendMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                    "You have been teleported to safety - the resource world is being reset.");
            LogUtil.log(getLogger(), "Teleported " + player.getName() + " out of resource world", Level.INFO);
        }
    }

    public void recreateWorld(MVWorldManager worldManager) {
        boolean success = worldManager.addWorld(
                worldName,
                World.Environment.NORMAL,
                null,
                WorldType.NORMAL,
                true,
                "DEFAULT"
        );

        if (success) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                    "The resource world has been reset and is ready to use!");
            LogUtil.log(getLogger(), "World recreation successful", Level.INFO);
        } else {
            Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                    "Failed to recreate the resource world!");
            LogUtil.log(getLogger(), "Failed to recreate world: " + worldName, Level.SEVERE);
        }
    }

    public void ensureResourceWorldExists() {
        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.isMVWorld(worldName)) {
            LogUtil.log(getLogger(), "Resource world doesn't exist, creating: " + worldName, Level.INFO);
            boolean success = worldManager.addWorld(
                    worldName,
                    World.Environment.NORMAL,
                    null,
                    WorldType.NORMAL,
                    true,
                    "DEFAULT"
            );
            LogUtil.log(getLogger(), "Created resource world: " + worldName + ", Success: " + success, Level.INFO);
        } else {
            LogUtil.log(getLogger(), "Resource world exists: " + worldName, Level.INFO);
        }
    }

    public void loadConfig() {
        reloadConfig();
        worldName = getConfig().getString("worldName", "Resources");
        restartTime = getConfig().getInt("restartTime", 3);
        resetWarningTime = getConfig().getInt("resetWarningTime", 5);
        resetType = getConfig().getString("resetType", "daily");
        resetDay = getConfig().getInt("resetDay", 1);

        LogUtil.log(getLogger(), "Configuration loaded: worldName=" + worldName +
                ", resetType=" + resetType + ", restartTime=" + restartTime +
                ", resetWarningTime=" + resetWarningTime, Level.INFO);
    }
}