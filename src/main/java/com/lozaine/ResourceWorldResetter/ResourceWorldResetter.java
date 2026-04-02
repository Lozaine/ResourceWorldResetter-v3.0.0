package com.lozaine.ResourceWorldResetter;

import com.lozaine.ResourceWorldResetter.events.PostResetEvent;
import com.lozaine.ResourceWorldResetter.events.PreResetEvent;
import com.lozaine.ResourceWorldResetter.events.RegionPostResetEvent;
import com.lozaine.ResourceWorldResetter.events.RegionPreResetEvent;
import com.lozaine.ResourceWorldResetter.gui.AdminGUI;
import com.lozaine.ResourceWorldResetter.gui.AdminGUIListener;
import com.lozaine.ResourceWorldResetter.utils.LogUtil;
import com.lozaine.ResourceWorldResetter.utils.WorldManagerBridge;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.lozaine.ResourceWorldResetter.metrics.Metrics;
import com.lozaine.ResourceWorldResetter.commands.RwrRegionCommand;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ResourceWorldResetter extends JavaPlugin {
    private static final String DEFAULT_WORLD_NAME = "Resources";
    private static final int DEFAULT_RESTART_HOUR = 3;
    private static final int DEFAULT_WARNING_MINUTES = 5;
    private static final int DEFAULT_RESET_DAY = 1;
    /** Warn if folder deletion takes longer than this many milliseconds. */
    private static final long DELETION_TIMEOUT_MS = 60_000L;
    /** Maximum number of automatic retry attempts before alerting admins (3 retries after initial failure). */
    private static final int MAX_RESET_ATTEMPTS = 3;
    /** Bukkit tick delays between consecutive retry attempts: 30 s, 60 s, 120 s. */
    private static final long[] RETRY_DELAY_TICKS = {600L, 1200L, 2400L};

    private String worldName = DEFAULT_WORLD_NAME;
    private int restartTime = DEFAULT_RESTART_HOUR;
    private int resetWarningTime = DEFAULT_WARNING_MINUTES;
    private ResetMode resetMode = ResetMode.DAILY;
    private int resetDay = DEFAULT_RESET_DAY;
    private boolean regionsEnabled;
    private boolean regionsImmediateOnAdd;
    private java.util.Set<String> regionsToReset = new java.util.HashSet<>();
    private AdminGUI adminGUI;
    private final RescheduleManager rescheduleManager = new RescheduleManager();
    private LocalDateTime nextResetInstant = null;
    /** True while a world deletion/recreation cycle is executing; prevents overlapping resets. */
    private volatile boolean resetInProgress = false;
    /** True when the in-progress reset is a region reset (as opposed to a full world reset). */
    private volatile boolean currentResetIsRegionReset = false;
    /** Tracks the current retry attempt number (0 = initial attempt, max = MAX_RESET_ATTEMPTS). */
    private int resetAttempt = 0;
    /** File used to persist reset state across server restarts for graceful shutdown recovery. */
    private File resetStateFile;
    /** Task ID for the auto-resume task scheduled when an incomplete reset is detected on startup; -1 if not pending. */
    private int autoResumeTaskId = -1;
    private WorldManagerBridge worldManagerBridge;

    public WorldManagerBridge getWorldManagerBridge() { return worldManagerBridge; }

    public String getWorldName() { return this.worldName; }
    public String getResetType() { return this.resetMode.getConfigValue(); }
    public int getRestartTime() { return this.restartTime; }
    public int getResetWarningTime() { return this.resetWarningTime; }
    public int getResetDay() { return this.resetDay; }
    public boolean isRegionsEnabled() { return this.regionsEnabled; }
    public java.util.Set<String> getRegionsToReset() { return java.util.Collections.unmodifiableSet(regionsToReset); }

    public void setWorldName(String name) {
        getConfig().set("worldName", name);
        saveConfig();
        validateAndApplyConfig();
        LogUtil.log(getLogger(), "World name set to " + worldName, Level.INFO);
    }

    public void setResetType(String type) {
        getConfig().set("resetType", type);
        saveConfig();
        validateAndApplyConfig();
        rescheduleManager.requestReschedule();
        LogUtil.log(getLogger(), "Reset type set to " + getResetType(), Level.INFO);
    }

    public void setResetDay(int day) {
        getConfig().set("resetDay", day);
        saveConfig();
        validateAndApplyConfig();
        rescheduleManager.requestReschedule();
        LogUtil.log(getLogger(), "Reset day set to " + resetDay + " for " + resetMode.getConfigValue() + " mode", Level.INFO);
    }

    public void setRestartTime(int hour) {
        getConfig().set("restartTime", hour);
        saveConfig();
        validateAndApplyConfig();
        rescheduleManager.requestReschedule();
        LogUtil.log(getLogger(), "Restart time set to " + restartTime + ":00", Level.INFO);
    }

    public void setResetWarningTime(int minutes) {
        getConfig().set("resetWarningTime", minutes);
        saveConfig();
        validateAndApplyConfig();
        // If a reset task is already active, only reschedule the warning independently
        if (nextResetInstant != null && rescheduleManager.hasResetTask()) {
            scheduleWarningTask(nextResetInstant);
            LogUtil.log(getLogger(), "Reset warning time set to " + resetWarningTime + " minutes; warning rescheduled independently", Level.INFO);
        } else {
            rescheduleManager.requestReschedule();
            LogUtil.log(getLogger(), "Reset warning time set to " + resetWarningTime + " minutes", Level.INFO);
        }
    }

    public void setRegionsEnabled(boolean enabled) {
        this.regionsEnabled = enabled;
        getConfig().set("regions.enabled", enabled);
        saveConfig();
    }

    public void addRegionToReset(int regionX, int regionZ) {
        String key = regionX + "," + regionZ;
        if (regionsToReset.add(key)) {
            java.util.List<String> list = new java.util.ArrayList<>(regionsToReset);
            getConfig().set("regions.list", list);
            saveConfig();
            if (regionsImmediateOnAdd && Bukkit.getWorld(worldName) != null) {
                World w = Bukkit.getWorld(worldName);
                java.util.Queue<int[]> queue = new java.util.LinkedList<>();
                queue.offer(new int[]{regionX, regionZ});
                scheduleRegionBatch(w, queue, 0, System.currentTimeMillis());
            }
        }
    }

    public void removeRegionToReset(int regionX, int regionZ) {
        String key = regionX + "," + regionZ;
        if (regionsToReset.remove(key)) {
            java.util.List<String> list = new java.util.ArrayList<>(regionsToReset);
            getConfig().set("regions.list", list);
            saveConfig();
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LogUtil.init(this);
        worldManagerBridge = new WorldManagerBridge(this);
        resetStateFile = new File(getDataFolder(), "reset-state.yml");

        if (getConfig().getBoolean("metrics.enabled", true)) {
            int pluginId = 25197;
            Metrics metrics = new Metrics(this, pluginId);
            metrics.addCustomChart(new Metrics.SimplePie("reset_type", this::getResetType));
            metrics.addCustomChart(new Metrics.SimplePie("reset_hour", () -> String.valueOf(this.restartTime)));
            metrics.addCustomChart(new Metrics.SimplePie("warning_time", () -> String.valueOf(this.resetWarningTime)));
            metrics.addCustomChart(new Metrics.SimplePie("server_version", () -> Bukkit.getBukkitVersion()));
            // Reports hours until the next scheduled reset; useful for bStats time-series tracking.
            metrics.addCustomChart(new Metrics.SingleLineChart("next_reset_epoch", () -> {
                LocalDateTime next = nextResetInstant;
                if (next == null) return 0;
                long hours = ChronoUnit.HOURS.between(LocalDateTime.now(), next);
                return (int) Math.max(0, hours);
            }));
            LogUtil.log(getLogger(), "bStats metrics enabled (charts: reset_type, reset_hour, warning_time, server_version, next_reset_epoch)", Level.INFO);
        } else {
            LogUtil.log(getLogger(), "bStats metrics disabled by configuration", Level.INFO);
        }

        loadConfig();
        adminGUI = new AdminGUI(this);
        getServer().getPluginManager().registerEvents(new AdminGUIListener(this, adminGUI), this);
        getCommand("rwrregion").setExecutor(new RwrRegionCommand(this));

        if (worldName.isEmpty()) {
            LogUtil.log(getLogger(), "No resource world configured. Use /rwrgui in-game to select a world.", Level.INFO);
        } else {
            ensureResourceWorldExists();
        }
        scheduleDailyReset();
        checkAndHandleIncompleteReset();
        LogUtil.log(getLogger(), "ResourcesWorldResetter v" + getDescription().getVersion() + " enabled successfully!", Level.INFO);
    }

    @Override
    public void onDisable() {
        if (autoResumeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoResumeTaskId);
            autoResumeTaskId = -1;
        }
        if (resetInProgress) {
            saveResetState();
        }
        cancelScheduledTasks();
        LogUtil.log(getLogger(), "ResourceWorldResetter disabled.", Level.INFO);
    }

    private void cancelScheduledTasks() {
        rescheduleManager.cancelAll();
        nextResetInstant = null;
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
                    rescheduleManager.requestReschedule();
                    sender.sendMessage(ChatColor.GREEN + "ResourcesWorldResetter configuration reloaded!");
                    return true;

                // Keeping resetworld for backward compatibility
                case "resetworld":
                    sender.sendMessage(ChatColor.GREEN + "Forcing resource world reset...");
                    resetResourceWorld(false);
                    return true;

                case "rwrresume":
                    if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
                        if (autoResumeTaskId != -1) {
                            Bukkit.getScheduler().cancelTask(autoResumeTaskId);
                            autoResumeTaskId = -1;
                        }
                        clearResetState();
                        sender.sendMessage(ChatColor.GREEN + "[RWR] Incomplete reset auto-resume cancelled and state cleared.");
                        LogUtil.log(getLogger(), sender.getName() + " cancelled the incomplete reset auto-resume", Level.INFO);
                    } else {
                        if (resetStateFile == null || !resetStateFile.exists()) {
                            sender.sendMessage(ChatColor.YELLOW + "[RWR] No incomplete reset state detected.");
                            return true;
                        }
                        if (autoResumeTaskId != -1) {
                            Bukkit.getScheduler().cancelTask(autoResumeTaskId);
                            autoResumeTaskId = -1;
                        }
                        YamlConfiguration savedState = YamlConfiguration.loadConfiguration(resetStateFile);
                        boolean wasRegionReset = savedState.getBoolean("regionReset", false);
                        sender.sendMessage(ChatColor.GREEN + "[RWR] Resuming incomplete " +
                                (wasRegionReset ? "region" : "full") + " reset for world '" + worldName + "'...");
                        LogUtil.log(getLogger(), sender.getName() + " manually triggered resume of incomplete reset", Level.INFO);
                        resumeIncompleteReset(wasRegionReset);
                    }
                    return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        return false;
    }

    /**
     * Pure computation: returns the next reset instant based on current config.
     * Does not schedule, cancel, or log any scheduling decisions.
     */
    private LocalDateTime computeNextResetInstant() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        LogUtil.log(getLogger(), "Computing next " + resetMode.getConfigValue() +
                " reset using server timezone: " + zone, Level.INFO);
        LocalDateTime candidate = now.withHour(restartTime).withMinute(0).withSecond(0).withNano(0);

        if (now.compareTo(candidate) >= 0) {
            candidate = candidate.plusDays(1);
        }

        switch (resetMode) {
            case WEEKLY -> {
                int currentDay = now.getDayOfWeek().getValue(); // 1 (Monday) to 7 (Sunday)
                int daysUntilReset = (resetDay - currentDay + 7) % 7;
                if (daysUntilReset == 0 && now.compareTo(candidate) >= 0) {
                    daysUntilReset = 7;
                }
                candidate = candidate.plusDays(daysUntilReset);
                LogUtil.log(getLogger(), "Next weekly reset computed for " + candidate, Level.INFO);
            }
            case MONTHLY -> {
                LocalDateTime nextMonth = now;
                if (now.getDayOfMonth() > resetDay || (now.getDayOfMonth() == resetDay && now.compareTo(candidate) >= 0)) {
                    nextMonth = now.plusMonths(1);
                }
                int maxDay = nextMonth.getMonth().length(nextMonth.toLocalDate().isLeapYear());
                int actualResetDay = Math.min(resetDay, maxDay);
                candidate = nextMonth.withDayOfMonth(actualResetDay).withHour(restartTime).withMinute(0).withSecond(0).withNano(0);
                LogUtil.log(getLogger(), "Next monthly reset computed for " + candidate, Level.INFO);
            }
            case DAILY -> LogUtil.log(getLogger(), "Next daily reset computed for " + candidate, Level.INFO);
        }

        return candidate;
    }

    /**
     * Schedules (or reschedules) only the warning broadcast task against the given reset instant.
     * Safe to call independently when {@code resetWarningTime} changes without touching the reset task.
     */
    private void scheduleWarningTask(LocalDateTime nextReset) {
        rescheduleManager.cancelWarning();

        if (resetWarningTime <= 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningTime = nextReset.minusMinutes(resetWarningTime);

        if (!now.isBefore(warningTime)) {
            LogUtil.log(getLogger(), "Warning time already passed for next reset; skipping warning task", Level.INFO);
            return;
        }

        long warningDelayTicks = Math.max(1, ChronoUnit.SECONDS.between(now, warningTime) * 20);
        LogUtil.log(getLogger(), "Warning scheduled for " + warningTime +
                " (" + (warningDelayTicks / 20 / 60) + " minutes from now)", Level.INFO);

        rescheduleManager.setWarningTask(Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[ResourceWorldResetter] " +
                    "Resource world will reset in " + resetWarningTime + " minutes!");
            LogUtil.log(getLogger(), "Broadcast reset warning to players", Level.INFO);
        }, warningDelayTicks).getTaskId());
    }

    private void scheduleDailyReset() {
        cancelScheduledTasks();

        nextResetInstant = computeNextResetInstant();
        LocalDateTime now = LocalDateTime.now();
        long resetDelayTicks = Math.max(1, ChronoUnit.SECONDS.between(now, nextResetInstant) * 20);

        LogUtil.log(getLogger(), "Next reset scheduled at " + nextResetInstant +
                " (" + (resetDelayTicks / 20 / 60) + " minutes / " +
                (resetDelayTicks / 20 / 60 / 60) + " hours from now) [timezone: " + ZoneId.systemDefault() + "]", Level.INFO);

        scheduleWarningTask(nextResetInstant);

        rescheduleManager.setResetTask(Bukkit.getScheduler().runTaskLater(this, () -> {
            rescheduleManager.clearResetTask();
            LogUtil.log(getLogger(), "Executing scheduled reset task", Level.INFO);
            resetResourceWorld(true);
        }, resetDelayTicks).getTaskId());
    }

    public void resetResourceWorld() {
        resetResourceWorld(false);
    }

    public void resetResourceWorld(boolean isScheduled) {
        if (worldName == null || worldName.isEmpty()) {
            LogUtil.log(getLogger(), "No resource world configured! Use /rwrgui to select a world before resetting.", Level.WARNING);
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("resourceworldresetter.admin")) {
                    admin.sendMessage(ChatColor.RED + "[RWR] No resource world configured! Open /rwrgui and use \"Change World\" to select one.");
                }
            }
            return;
        }
        if (resetInProgress) {
            LogUtil.log(getLogger(), "Reset already in progress; ignoring new reset request", Level.WARNING);
            return;
        }
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
            if (regionsEnabled) {
                performRegionReset(world);
            } else {
                performReset(world, isScheduled);
            }
        } else {
            // For scheduled resets, the warning was already handled
            if (regionsEnabled) {
                performRegionReset(world);
            } else {
                performReset(world, isScheduled);
            }
        }
    }

    /**
     * Saves the current reset progress to {@link #resetStateFile} so it can be detected
     * and resumed on the next startup. Called from {@link #onDisable()} when {@link #resetInProgress} is true.
     */
    private void saveResetState() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            YamlConfiguration state = new YamlConfiguration();
            state.set("worldName", worldName);
            state.set("resetType", getResetType());
            state.set("regionReset", currentResetIsRegionReset);
            state.set("startedAt", System.currentTimeMillis());
            state.save(resetStateFile);
            LogUtil.log(getLogger(), "Shutdown mid-reset detected for world '" + worldName +
                    "'; state saved to " + resetStateFile.getName() + " — recovery will trigger on next startup", Level.WARNING);
        } catch (Exception e) {
            LogUtil.log(getLogger(), "Failed to save reset state: " + e.getMessage(), Level.SEVERE);
        }
    }

    /** Deletes the reset state file once a recovery is complete (or admin dismisses it). */
    private void clearResetState() {
        if (resetStateFile != null && resetStateFile.exists()) {
            if (resetStateFile.delete()) {
                LogUtil.log(getLogger(), "Reset state file cleared (recovery complete)", Level.INFO);
            } else {
                LogUtil.log(getLogger(), "Failed to delete reset state file: " + resetStateFile.getPath(), Level.WARNING);
            }
        }
    }

    /**
     * Called from {@link #onEnable()} to detect a leftover {@code reset-state.yml} that indicates
     * the server was stopped in the middle of a reset. Alerts online admins and schedules an
     * auto-resume after 60 seconds (cancellable with {@code /rwrresume cancel}).
     */
    private void checkAndHandleIncompleteReset() {
        if (resetStateFile == null || !resetStateFile.exists()) {
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(resetStateFile);
        String savedWorld = state.getString("worldName", worldName);
        boolean wasRegionReset = state.getBoolean("regionReset", false);
        long startedAt = state.getLong("startedAt", 0);
        String timeStr = startedAt > 0
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(startedAt))
                : "unknown";

        LogUtil.log(getLogger(), "INCOMPLETE RESET DETECTED: world='" + savedWorld + "', type=" +
                (wasRegionReset ? "region" : "full") + ", started=" + timeStr +
                ". Auto-resuming in 60 seconds. Use /rwrresume cancel to abort.", Level.WARNING);

        String alertMsg = ChatColor.DARK_RED + "[RWR] Incomplete " + (wasRegionReset ? "region" : "full") +
                " reset detected for '" + savedWorld + "' (started: " + timeStr + ")." +
                ChatColor.YELLOW + " Auto-resume in 60s | /rwrresume = now | /rwrresume cancel = abort";
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("resourceworldresetter.admin")) {
                admin.sendMessage(alertMsg);
            }
        }

        final boolean finalWasRegionReset = wasRegionReset;
        autoResumeTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            autoResumeTaskId = -1;
            if (resetStateFile == null || !resetStateFile.exists()) {
                // Already handled by a manual command or normal reset
                return;
            }
            LogUtil.log(getLogger(), "Auto-resuming incomplete reset for world '" + savedWorld + "'", Level.WARNING);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[ResourceWorldResetter] Auto-resuming incomplete world reset...");
            resumeIncompleteReset(finalWasRegionReset);
        }, 60 * 20L).getTaskId();
    }

    /**
     * Clears the saved reset state and triggers a new reset of the appropriate type.
     * Called by auto-resume, {@code /rwrresume}, or indirectly by {@link #checkAndHandleIncompleteReset()}.
     */
    private void resumeIncompleteReset(boolean wasRegionReset) {
        clearResetState();
        if (wasRegionReset) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                ensureResourceWorldExists();
                w = Bukkit.getWorld(worldName);
            }
            if (w != null) {
                performRegionReset(w);
            } else {
                LogUtil.log(getLogger(), "Cannot resume region reset: world '" + worldName + "' is unavailable", Level.SEVERE);
            }
        } else {
            resetResourceWorld(false);
        }
    }

    private void performReset(World world, boolean isScheduled) {
        // Fire pre-reset event; allow other plugins to cancel.
        PreResetEvent preEvent = new PreResetEvent(worldName, getResetType());
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            LogUtil.log(getLogger(), "World reset cancelled by a PreResetEvent listener", Level.INFO);
            resetAttempt = 0;
            rescheduleManager.requestReschedule();
            return;
        }

        double tpsBefore = getServerTPS();
        long startTime = System.currentTimeMillis();
        resetInProgress = true;
        currentResetIsRegionReset = false;

        LogUtil.log(getLogger(), "Starting world reset process for " + worldName +
                (resetAttempt > 0 ? " (retry " + resetAttempt + "/" + MAX_RESET_ATTEMPTS + ")" : ""), Level.INFO);
        Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                "Resource world reset in progress. Players in that world will be teleported to safety.");

        teleportPlayersSafely(world);

        if (!worldManagerBridge.unloadWorld(worldName)) {
            LogUtil.log(getLogger(), "Failed to unload world: " + worldName + ". Aborting reset.", Level.SEVERE);
            handleResetFailure("world unload failed", isScheduled);
            return;
        }

        final File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        LogUtil.log(getLogger(), "Deleting world folder async: " + worldFolder.getAbsolutePath(), Level.INFO);

        CompletableFuture.supplyAsync(() -> {
            long asyncStart = System.currentTimeMillis();
            boolean deleted = deleteFolder(worldFolder);
            long elapsed = System.currentTimeMillis() - asyncStart;
            if (elapsed > DELETION_TIMEOUT_MS) {
                LogUtil.log(getLogger(), "World folder deletion took " + elapsed +
                        "ms, exceeding " + DELETION_TIMEOUT_MS + "ms threshold", Level.WARNING);
            } else {
                LogUtil.log(getLogger(), "World folder deletion completed in " + elapsed + "ms", Level.INFO);
            }
            return deleted;
        }).whenComplete((deleted, throwable) -> {
            if (!isEnabled()) {
                resetInProgress = false;
                LogUtil.log(getLogger(), "Plugin disabled during async reset; skipping completion callback", Level.WARNING);
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                boolean retrying = false;
                try {
                    if (throwable != null) {
                        LogUtil.log(getLogger(), "Async deletion threw an unexpected exception: " +
                                throwable.getMessage(), Level.SEVERE);
                        handleResetFailure("async exception: " + throwable.getMessage(), isScheduled);
                        retrying = true;
                        return;
                    }
                    if (Boolean.TRUE.equals(deleted)) {
                        LogUtil.log(getLogger(), "World folder deleted successfully; recreating world", Level.INFO);
                        boolean recreated = recreateWorld();
                        if (recreated) {
                            long duration = System.currentTimeMillis() - startTime;
                            double tpsAfter = getServerTPS();
                            Bukkit.broadcastMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                                    "Resource world reset completed in " + (duration / 1000) + " seconds (TPS: " +
                                    String.format("%.2f", tpsBefore) + " → " + String.format("%.2f", tpsAfter) + ").");
                            LogUtil.log(getLogger(), "Resource world reset completed in " + duration + "ms", Level.INFO);
                            resetAttempt = 0;
                            Bukkit.getPluginManager().callEvent(new PostResetEvent(worldName, getResetType(), true));
                            clearResetState();
                        } else {
                            handleResetFailure("world recreation failed", isScheduled);
                            retrying = true;
                        }
                    } else {
                        LogUtil.log(getLogger(), "Failed to delete world folder: " + worldName, Level.SEVERE);
                        handleResetFailure("world folder deletion failed", isScheduled);
                        retrying = true;
                    }
                } finally {
                    if (!retrying) {
                        resetInProgress = false;
                        rescheduleManager.requestReschedule();
                    }
                }
            });
        });
    }

    /**
     * Handles a reset failure by scheduling a retry with exponential backoff, or alerting
     * admins and rescheduling normally once {@link #MAX_RESET_ATTEMPTS} retries are exhausted.
     * Always clears {@code resetInProgress} before returning.
     */
    private void handleResetFailure(String reason, boolean isScheduled) {
        resetInProgress = false;
        Bukkit.getPluginManager().callEvent(new PostResetEvent(worldName, getResetType(), false));
        resetAttempt++;

        if (resetAttempt <= MAX_RESET_ATTEMPTS) {
            long delayTicks = RETRY_DELAY_TICKS[resetAttempt - 1];
            long delaySeconds = delayTicks / 20;
            LogUtil.log(getLogger(), "Reset failed (" + reason + "). Scheduling retry " + resetAttempt +
                    "/" + MAX_RESET_ATTEMPTS + " in " + delaySeconds + " seconds.", Level.WARNING);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[ResourceWorldResetter] Reset encountered an error. " +
                    "Retrying in " + delaySeconds + " seconds (attempt " + resetAttempt + "/" + MAX_RESET_ATTEMPTS + ")...");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                LogUtil.log(getLogger(), "Executing reset retry attempt " + resetAttempt, Level.INFO);
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    LogUtil.log(getLogger(), "World '" + worldName + "' not found on retry; attempting to recreate before reset.", Level.WARNING);
                    ensureResourceWorldExists();
                    w = Bukkit.getWorld(worldName);
                }
                if (w != null) {
                    performReset(w, isScheduled);
                } else {
                    LogUtil.log(getLogger(), "Cannot retry: world '" + worldName + "' still unavailable after recreation attempt.", Level.SEVERE);
                    handleResetFailure("world unavailable for retry", isScheduled);
                }
            }, delayTicks);
        } else {
            LogUtil.log(getLogger(), "Reset failed after " + MAX_RESET_ATTEMPTS + " retries. Last reason: " + reason +
                    ". Verify: world folder permissions, Multiverse-Core status, and available disk space.", Level.SEVERE);
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "[ResourceWorldResetter] [ADMIN ALERT] " +
                    "World reset has FAILED after " + MAX_RESET_ATTEMPTS + " attempts!");
            Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                    "Check server logs and verify: folder permissions, Multiverse-Core, and disk space.");
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("resourceworldresetter.admin")) {
                    admin.sendMessage(ChatColor.DARK_RED + "[RWR] CRITICAL: World reset failed after " +
                            MAX_RESET_ATTEMPTS + " retries! Last error: " + reason + ". Check server logs immediately.");
                }
            }
            resetAttempt = 0;
            rescheduleManager.requestReschedule();
        }
    }

    private void performRegionReset(World world) {
        resetInProgress = true;
        currentResetIsRegionReset = true;
        LogUtil.log(getLogger(), "Starting region-based reset for " + worldName, Level.INFO);
        Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] Region reset in progress for configured regions.");

        // Teleport players out of affected regions to world spawn
        teleportPlayersOutOfRegions(world);

        // Build a queue of validated regions to process
        java.util.Queue<int[]> regionQueue = new java.util.LinkedList<>();
        for (String key : regionsToReset) {
            String[] parts = key.split(",");
            try {
                int rx = Integer.parseInt(parts[0].trim());
                int rz = Integer.parseInt(parts[1].trim());
                regionQueue.offer(new int[]{rx, rz});
            } catch (Exception e) {
                LogUtil.log(getLogger(), "Invalid region entry '" + key + "'; skipping", Level.WARNING);
            }
        }

        if (regionQueue.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[ResourceWorldResetter] No valid regions to reset.");
            LogUtil.log(getLogger(), "Region reset skipped: no valid regions configured", Level.INFO);
            return;
        }

        scheduleRegionBatch(world, regionQueue, 0, System.currentTimeMillis());
    }

    /** Number of chunks to unload per tick during batched region regeneration. */
    private static final int CHUNKS_PER_TICK = 16;

    /**
     * Processes the head region in {@code regionQueue} by unloading {@link #CHUNKS_PER_TICK} chunks
     * per tick starting at {@code chunkOffset}. Once all 1 024 chunks are unloaded the .mca, POI,
     * and entities files are deleted asynchronously; then the next region is started.
     */
    private void scheduleRegionBatch(World world, java.util.Queue<int[]> regionQueue, int chunkOffset, long startTime) {
        int[] region = regionQueue.peek();
        if (region == null) {
            long elapsed = System.currentTimeMillis() - startTime;
            Bukkit.broadcastMessage(ChatColor.GREEN + "[ResourceWorldResetter] Configured regions have been regenerated.");
            LogUtil.log(getLogger(), "Region-based reset completed in " + elapsed + "ms", Level.INFO);
            resetInProgress = false;
            currentResetIsRegionReset = false;
            clearResetState();
            return;
        }

        int regionX = region[0];
        int regionZ = region[1];

        // Fire RegionPreResetEvent only at the start of a new region (not on continuation ticks).
        if (chunkOffset == 0) {
            RegionPreResetEvent preEvent = new RegionPreResetEvent(world.getName(), regionX, regionZ);
            Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) {
                LogUtil.log(getLogger(), "Region reset cancelled by RegionPreResetEvent for (" + regionX + "," + regionZ + ")", Level.INFO);
                regionQueue.poll();
                scheduleRegionBatch(world, regionQueue, 0, startTime);
                return;
            }
        }
        int startChunkX = regionX << 5;
        int startChunkZ = regionZ << 5;
        final int totalChunks = 32 * 32; // 1 024 chunks per region file

        int offset = chunkOffset;
        int processed = 0;
        while (processed < CHUNKS_PER_TICK && offset < totalChunks) {
            int cx = startChunkX + (offset >> 5);   // offset / 32
            int cz = startChunkZ + (offset & 0x1F); // offset % 32
            if (world.isChunkLoaded(cx, cz)) {
                world.unloadChunk(cx, cz, true); // save=true preserves data before file deletion
            }
            offset++;
            processed++;
        }

        if (offset < totalChunks) {
            // Schedule the next batch on the following tick
            final int nextOffset = offset;
            Bukkit.getScheduler().runTaskLater(this,
                    () -> scheduleRegionBatch(world, regionQueue, nextOffset, startTime), 1L);
        } else {
            // All 1 024 chunks unloaded; delete the data files asynchronously
            regionQueue.poll();
            final int finalRX = regionX;
            final int finalRZ = regionZ;
            CompletableFuture.runAsync(() -> deleteRegionFiles(world, finalRX, finalRZ))
                    .whenComplete((v, ex) -> {
                        boolean regionSuccess = (ex == null);
                        if (!regionSuccess) {
                            LogUtil.log(getLogger(),
                                    "Error deleting region files for (" + finalRX + "," + finalRZ + "): "
                                            + ex.getMessage(), Level.SEVERE);
                        }
                        if (!isEnabled()) return;
                        Bukkit.getScheduler().runTask(this, () -> {
                            Bukkit.getPluginManager().callEvent(
                                    new RegionPostResetEvent(world.getName(), finalRX, finalRZ, regionSuccess));
                            scheduleRegionBatch(world, regionQueue, 0, startTime);
                        });
                    });
        }
    }

    /**
     * Deletes the .mca, POI, and entities data files for a region.
     * Must be called from an async thread after the region's chunks have been unloaded.
     */
    private void deleteRegionFiles(World world, int regionX, int regionZ) {
        String fileName = "r." + regionX + "." + regionZ + ".mca";
        java.io.File worldFolder = world.getWorldFolder();
        for (String subDir : new String[]{"region", "poi", "entities"}) {
            java.io.File file = new java.io.File(new java.io.File(worldFolder, subDir), fileName);
            if (file.exists()) {
                LogUtil.log(getLogger(), "Deleting " + subDir + " file: " + file.getName(), Level.INFO);
                if (!file.delete()) {
                    LogUtil.log(getLogger(), "Failed to delete " + subDir + " file: " + file.getName(), Level.WARNING);
                }
            }
        }
        LogUtil.log(getLogger(), "Region files cleared for (" + regionX + "," + regionZ + ")", Level.INFO);
    }

    private void teleportPlayersOutOfRegions(World world) {
        java.util.List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            LogUtil.log(getLogger(), "No worlds available as teleport target; cannot teleport players out of regions in " + world.getName(), Level.SEVERE);
            return;
        }
        World defaultWorld = worlds.get(0);
        org.bukkit.Location spawn = defaultWorld.getSpawnLocation();
        LogUtil.log(getLogger(), "Region reset teleport target: '" + defaultWorld.getName() + "' (spawn)", Level.INFO);
        for (Player player : world.getPlayers()) {
            if (!player.isOnline()) continue;
            org.bukkit.Location loc = player.getLocation();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            int regionX = chunkX >> 5; // 32x32 chunks per region
            int regionZ = chunkZ >> 5;
            String key = regionX + "," + regionZ;
            if (regionsToReset.contains(key)) {
                player.teleport(spawn);
                player.sendMessage(ChatColor.GREEN + "[ResourceWorldResetter] You were moved for a region reset.");
                LogUtil.log(getLogger(), "Teleported " + player.getName() + " out of region (" + regionX + "," + regionZ + ") to '" + defaultWorld.getName() + "' (spawn)", Level.INFO);
            }
        }
    }



    public double getServerTPS() {
        try {
            // Attempt Paper API first if available
            java.lang.reflect.Method getTPSMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            Object tpsObj = getTPSMethod.invoke(Bukkit.getServer());
            if (tpsObj instanceof double[] tpsArray && tpsArray.length > 0) {
                return tpsArray[0];
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to CraftBukkit/NMS reflection
        } catch (Exception e) {
            getLogger().fine("Paper getTPS reflection failed: " + e.getMessage());
        }

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
        java.util.List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            LogUtil.log(getLogger(), "No worlds available as teleport target; cannot teleport players out of " + world.getName(), Level.SEVERE);
            return;
        }
        World defaultWorld = worlds.get(0);
        Location spawn = defaultWorld.getSpawnLocation();
        LogUtil.log(getLogger(), "Teleport target for reset: '" + defaultWorld.getName() + "' (spawn)", Level.INFO);

        for (Player player : world.getPlayers()) {
            if (!player.isOnline()) continue;
            player.teleport(spawn);
            player.sendMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                    "You have been teleported to safety - the resource world is being reset.");
            LogUtil.log(getLogger(), "Teleported " + player.getName() + " to '" + defaultWorld.getName() + "' (spawn)", Level.INFO);
        }
    }

    public boolean recreateWorld() {
        boolean success = worldManagerBridge.createWorld(worldName);

        if (success) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                    "The resource world has been reset and is ready to use!");
            LogUtil.log(getLogger(), "World recreation successful", Level.INFO);
        } else {
            Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                    "Failed to recreate the resource world!");
            LogUtil.log(getLogger(), "Failed to recreate world: " + worldName, Level.SEVERE);
        }
        return success;
    }

    public void ensureResourceWorldExists() {
        if (!worldManagerBridge.isWorldLoaded(worldName)) {
            LogUtil.log(getLogger(), "Resource world doesn't exist, creating: " + worldName, Level.INFO);
            boolean success = worldManagerBridge.createWorld(worldName);
            LogUtil.log(getLogger(), "Created resource world: " + worldName + ", Success: " + success, Level.INFO);
        } else {
            LogUtil.log(getLogger(), "Resource world exists: " + worldName, Level.INFO);
        }
    }

    private static boolean deleteFolder(File folder) {
        if (folder == null || !folder.exists()) return true;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!deleteFolder(file)) return false;
                } else {
                    if (!file.delete()) return false;
                }
            }
        }
        return folder.delete();
    }

    public void loadConfig() {
        reloadConfig();
        validateAndApplyConfig();

        regionsEnabled = getConfig().getBoolean("regions.enabled", false);
        regionsImmediateOnAdd = getConfig().getBoolean("regions.immediateRegenerationOnAdd", true);
        regionsToReset.clear();
        java.util.List<String> list = getConfig().getStringList("regions.list");
        if (list != null) {
            for (String entry : list) {
                if (entry == null || entry.trim().isEmpty()) continue;
                String[] parts = entry.trim().split(",");
                if (parts.length != 2) {
                    LogUtil.log(getLogger(), "Invalid region entry (expected 'x,z'): '" + entry + "'; skipping", Level.WARNING);
                    continue;
                }
                try {
                    int rx = Integer.parseInt(parts[0].trim());
                    int rz = Integer.parseInt(parts[1].trim());
                    regionsToReset.add(rx + "," + rz);
                } catch (NumberFormatException e) {
                    LogUtil.log(getLogger(), "Invalid region entry (non-integer coordinates): '" + entry + "'; skipping", Level.WARNING);
                }
            }
        }

        LogUtil.log(getLogger(), "Configuration loaded: worldName=" + worldName +
                ", resetType=" + getResetType() + ", restartTime=" + restartTime +
                ", resetWarningTime=" + resetWarningTime, Level.INFO);
    }

    private enum ResetMode {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly");

        private final String configValue;

        ResetMode(String configValue) {
            this.configValue = configValue;
        }

        public String getConfigValue() {
            return configValue;
        }

        public boolean matches(String candidate) {
            return candidate != null && candidate.equalsIgnoreCase(configValue);
        }

        public static ResetMode fromConfig(String candidate) {
            if (candidate == null) {
                return DAILY;
            }
            for (ResetMode mode : values()) {
                if (mode.matches(candidate)) {
                    return mode;
                }
            }
            return DAILY;
        }
    }

    private void validateAndApplyConfig() {
        FileConfiguration config = getConfig();
        boolean updated = false;

        String rawWorldName = config.getString("worldName", "");
        // Empty worldName is valid — it means the admin hasn't selected a world yet via /rwrgui.
        String normalizedWorldName = (rawWorldName == null) ? "" : rawWorldName.trim();
        this.worldName = normalizedWorldName;

        String rawResetType = config.getString("resetType", ResetMode.DAILY.getConfigValue());
        ResetMode normalizedMode = ResetMode.fromConfig(rawResetType);
        if (!normalizedMode.matches(rawResetType)) {
            LogUtil.log(getLogger(), "Unknown resetType '" + rawResetType + "'. Defaulting to " +
                    normalizedMode.getConfigValue(), Level.WARNING);
            config.set("resetType", normalizedMode.getConfigValue());
            updated = true;
        }
        this.resetMode = normalizedMode;

        int rawRestartTime = config.getInt("restartTime", DEFAULT_RESTART_HOUR);
        int clampedRestartTime = Math.max(0, Math.min(23, rawRestartTime));
        if (clampedRestartTime != rawRestartTime) {
            LogUtil.log(getLogger(), "restartTime " + rawRestartTime + " out of range. Clamped to " + clampedRestartTime,
                    Level.WARNING);
            config.set("restartTime", clampedRestartTime);
            updated = true;
        }
        this.restartTime = clampedRestartTime;

        int rawWarningMinutes = config.getInt("resetWarningTime", DEFAULT_WARNING_MINUTES);
        int sanitizedWarningMinutes = Math.max(0, rawWarningMinutes);
        if (sanitizedWarningMinutes != rawWarningMinutes) {
            LogUtil.log(getLogger(), "resetWarningTime cannot be negative. Using " + sanitizedWarningMinutes,
                    Level.WARNING);
            config.set("resetWarningTime", sanitizedWarningMinutes);
            updated = true;
        }
        this.resetWarningTime = sanitizedWarningMinutes;

        int rawResetDay = config.getInt("resetDay", DEFAULT_RESET_DAY);
        int sanitizedResetDay = sanitizeResetDayForMode(rawResetDay, normalizedMode);
        if (sanitizedResetDay != rawResetDay) {
            LogUtil.log(getLogger(), "resetDay " + rawResetDay + " invalid for " + normalizedMode.getConfigValue() +
                    " resets. Using " + sanitizedResetDay, Level.WARNING);
            config.set("resetDay", sanitizedResetDay);
            updated = true;
        }
        this.resetDay = sanitizedResetDay;

        if (updated) {
            saveConfig();
        }
    }

    private int sanitizeResetDayForMode(int candidate, ResetMode mode) {
        if (mode == ResetMode.WEEKLY) {
            return Math.max(1, Math.min(7, candidate));
        }
        if (mode == ResetMode.MONTHLY) {
            return Math.max(1, Math.min(31, candidate));
        }
        return Math.max(1, Math.min(31, candidate));
    }

    private class RescheduleManager {
        private int warningTaskId = -1;
        private int resetTaskId = -1;
        private int pendingTaskId = -1;

        void setWarningTask(int id) {
            warningTaskId = id;
        }

        void setResetTask(int id) {
            resetTaskId = id;
        }

        /** Cancel only the warning task. */
        void cancelWarning() {
            if (warningTaskId != -1) {
                Bukkit.getScheduler().cancelTask(warningTaskId);
                warningTaskId = -1;
            }
        }

        /** Returns true if a reset task is currently registered. */
        boolean hasResetTask() {
            return resetTaskId != -1;
        }

        /** Clear the reset task ID without cancelling (called when the task fires naturally). */
        void clearResetTask() {
            resetTaskId = -1;
        }

        /** Cancel the scheduled warning and reset tasks only. */
        void cancelScheduled() {
            cancelWarning();
            if (resetTaskId != -1) {
                Bukkit.getScheduler().cancelTask(resetTaskId);
                resetTaskId = -1;
            }
        }

        /** Cancel all managed tasks including any in-flight debounce task. */
        void cancelAll() {
            cancelScheduled();
            if (pendingTaskId != -1) {
                Bukkit.getScheduler().cancelTask(pendingTaskId);
                pendingTaskId = -1;
            }
        }

        /**
         * Request a reschedule, debouncing rapid successive calls so that only one
         * rescheduling operation fires (after 1 server tick).
         */
        void requestReschedule() {
            if (pendingTaskId != -1) {
                Bukkit.getScheduler().cancelTask(pendingTaskId);
                pendingTaskId = -1;
            }
            pendingTaskId = Bukkit.getScheduler().runTaskLater(ResourceWorldResetter.this, () -> {
                pendingTaskId = -1;
                cancelScheduled();
                scheduleDailyReset();
                LogUtil.log(getLogger(), "Rescheduled reset tasks after configuration change", Level.INFO);
            }, 1L).getTaskId();
        }
    }
}