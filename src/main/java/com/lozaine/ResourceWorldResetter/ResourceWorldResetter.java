package com.lozaine.ResourceWorldResetter;

import com.lozaine.ResourceWorldResetter.config.ConfigMigration;
import com.lozaine.ResourceWorldResetter.events.PostResetEvent;
import com.lozaine.ResourceWorldResetter.events.PreResetEvent;
import com.lozaine.ResourceWorldResetter.events.RegionPostResetEvent;
import com.lozaine.ResourceWorldResetter.events.RegionPreResetEvent;
import com.lozaine.ResourceWorldResetter.commands.RwrCommand;
import com.lozaine.ResourceWorldResetter.gui.AdminGUI;
import com.lozaine.ResourceWorldResetter.gui.AdminGUIListener;
import com.lozaine.ResourceWorldResetter.gui.TeleportGUI;
import com.lozaine.ResourceWorldResetter.gui.TeleportGUIListener;
import com.lozaine.ResourceWorldResetter.utils.LogUtil;
import com.lozaine.ResourceWorldResetter.utils.TeleportationSystem;
import com.lozaine.ResourceWorldResetter.utils.WorldManagerBridge;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.lozaine.ResourceWorldResetter.metrics.Metrics;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ResourceWorldResetter extends JavaPlugin implements Listener {
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
    private TeleportGUI teleportGUI;
    private TeleportationSystem teleportationSystem;
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
    /** Current reset phase; persisted so restarts can resume from the last safe step. */
    private ResetPhase resetPhase = ResetPhase.IDLE;
    /** Next safe phase to resume if the server stops before the reset completes. */
    private ResetPhase resumePhase = ResetPhase.IDLE;
    /** Last phase that failed when the reset entered FAILED. */
    private ResetPhase failedResetPhase = null;
    /** Wall-clock timestamp when the overall reset started. */
    private long resetStartedAtMillis = 0L;
    /** Wall-clock timestamp when the current phase started. */
    private long resetPhaseStartedAtMillis = 0L;
    /** Duration spent in each phase during the current reset attempt. */
    private final Map<ResetPhase, Long> resetPhaseDurations = new EnumMap<>(ResetPhase.class);
    /** Player count captured when the reset started. */
    private int resetPlayerCountAtStart = 0;
    /** TPS captured when the reset started. */
    private double resetTpsAtStart = 20.0;
    /** Disk free space captured when the reset started. */
    private long resetDiskFreeBytesAtStart = 0L;
    /** Most recent reset failure reason for operator visibility. */
    private String lastResetFailureReason = null;
    /** Most recent reset failure detail for operator visibility. */
    private String lastResetFailureDetail = null;
    /** Full stack trace from the most recent failure. */
    private String lastResetFailureStackTrace = null;
    /** Task ID for the auto-resume task scheduled when an incomplete reset is detected on startup; -1 if not pending. */
    private int autoResumeTaskId = -1;
    private WorldManagerBridge worldManagerBridge;
    /** Last known safe standing location for each player, tracked per destination world. */
    private final Map<UUID, Map<UUID, Location>> lastSafeLocationsByPlayerWorld = new ConcurrentHashMap<>();

    public WorldManagerBridge getWorldManagerBridge() { return worldManagerBridge; }
    public TeleportationSystem getTeleportationSystem() { return teleportationSystem; }
    public TeleportGUI getTeleportGUI() { return teleportGUI; }

    public String getWorldName() { return this.worldName; }
    public String getResetType() { return this.resetMode.getConfigValue(); }
    public int getRestartTime() { return this.restartTime; }
    public int getResetWarningTime() { return this.resetWarningTime; }
    public int getResetDay() { return this.resetDay; }
    public boolean isRegionsEnabled() { return this.regionsEnabled; }
    public java.util.Set<String> getRegionsToReset() { return java.util.Collections.unmodifiableSet(regionsToReset); }
    public LocalDateTime getNextResetInstant() { return nextResetInstant; }
    public boolean isResetInProgress() { return resetInProgress; }
    public boolean isCurrentResetRegionReset() { return currentResetIsRegionReset; }
    public int getResetAttempt() { return resetAttempt; }
    public boolean isAutoResumeQueued() { return autoResumeTaskId != -1; }
    public ResetPhase getResetPhase() { return resetPhase; }
    public ResetPhase getFailedResetPhase() { return failedResetPhase; }
    public ResetPhase getIncompleteResetResumePhase() { return getSavedResumePhase(); }
    public String getLastResetFailureReason() { return lastResetFailureReason; }
    public String getLastResetFailureDetail() { return lastResetFailureDetail; }
    public String getLastResetFailureStackTrace() { return lastResetFailureStackTrace; }
    public File getResetStateFile() { return resetStateFile; }

    public void setWorldName(String name) {
        getConfig().set("worldName", name);
        saveConfig();
        validateAndApplyConfig();
        LogUtil.log(getLogger(), "World name set to " + worldName, Level.INFO);
    }

    public void setResetType(String type) {
        getConfig().set("schedule.mode", type);
        saveConfig();
        validateAndApplyConfig();
        rescheduleManager.requestReschedule();
        LogUtil.log(getLogger(), "Reset type set to " + getResetType(), Level.INFO);
    }

    public void setResetDay(int day) {
        getConfig().set("schedule.day", day);
        saveConfig();
        validateAndApplyConfig();
        rescheduleManager.requestReschedule();
        LogUtil.log(getLogger(), "Reset day set to " + resetDay + " for " + resetMode.getConfigValue() + " mode", Level.INFO);
    }

    public void setRestartTime(int hour) {
        getConfig().set("schedule.time.hour", hour);
        saveConfig();
        validateAndApplyConfig();
        rescheduleManager.requestReschedule();
        LogUtil.log(getLogger(), "Restart time set to " + restartTime + ":00", Level.INFO);
    }

    public void setResetWarningTime(int minutes) {
        getConfig().set("schedule.warningMinutes", minutes);
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

    public void openAdminGui(Player player) {
        if (adminGUI != null) {
            adminGUI.openMainMenu(player);
        }
    }

    public boolean teleportPlayerToConfiguredWorld(Player player) {
        if (player == null) {
            return false;
        }

        if (worldName == null || worldName.isBlank()) {
            player.sendMessage(ChatColor.RED + "[ResourceWorldResetter] No resource world is configured.");
            return false;
        }

        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            ensureResourceWorldExists();
            targetWorld = Bukkit.getWorld(worldName);
        }

        if (targetWorld == null) {
            player.sendMessage(ChatColor.RED + "[ResourceWorldResetter] The resource world is not available right now.");
            return false;
        }

        // Compute a safe landing location ourselves to avoid landing inside dark/unsafe spots.
        Location safeLocation = getSafeTeleportLocation(targetWorld.getSpawnLocation());

        // Use direct Bukkit teleport for /rwr tp to guarantee consistent behavior across
        // Multiverse versions/command syntaxes. Safety is handled by our own safe-location logic.
        if (worldManagerBridge != null && worldManagerBridge.isMultiverseAvailable()) {
            LogUtil.log(getLogger(), "Multiverse detected; using direct Bukkit safe teleport for /rwr tp", Level.INFO);
        }

        boolean teleported = player.teleport(safeLocation);
        if (teleported) {
            player.sendMessage(ChatColor.GREEN + "[ResourceWorldResetter] Teleported to the resource world.");
            LogUtil.log(getLogger(), "Teleported " + player.getName() + " to resource world via Bukkit fallback", Level.INFO);
        } else {
            player.sendMessage(ChatColor.RED + "[ResourceWorldResetter] Failed to teleport to the resource world.");
            LogUtil.log(getLogger(), "Failed to teleport " + player.getName() + " to resource world via Bukkit fallback", Level.SEVERE);
        }

        return teleported;
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
        
        // Run config migration from v3 to v4 if needed
        try {
            ConfigMigration migration = new ConfigMigration(getLogger(), new File(getDataFolder(), "config.yml"));
            if (migration.migrateIfNeeded()) {
                reloadConfig(); // Reload config after migration
            }
        } catch (Exception e) {
            LogUtil.log(getLogger(), "Error during config migration: " + e.getMessage(), Level.SEVERE);
            e.printStackTrace();
        }
        
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
        
        // Initialize teleportation system
        teleportationSystem = new TeleportationSystem();
        teleportGUI = new TeleportGUI(this);
        
        adminGUI = new AdminGUI(this);
        getServer().getPluginManager().registerEvents(new AdminGUIListener(this, adminGUI), this);
        getServer().getPluginManager().registerEvents(new TeleportGUIListener(this, teleportGUI), this);
        getServer().getPluginManager().registerEvents(this, this);
        RwrCommand rwrCommand = new RwrCommand(this);
        if (getCommand("rwr") != null) {
            getCommand("rwr").setExecutor(rwrCommand);
            getCommand("rwr").setTabCompleter(rwrCommand);
        } else {
            LogUtil.log(getLogger(), "Command 'rwr' was not registered in plugin.yml", Level.SEVERE);
        }

        if (worldName.isEmpty()) {
            LogUtil.log(getLogger(), "No resource world configured. Use /rwr gui in-game to select a world.", Level.INFO);
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
        if (teleportationSystem != null) {
            teleportationSystem.clearAll();
        }
        cancelScheduledTasks();
        LogUtil.log(getLogger(), "ResourceWorldResetter disabled.", Level.INFO);
    }

    private void cancelScheduledTasks() {
        rescheduleManager.cancelAll();
        nextResetInstant = null;
    }

    // Command handling moved to `RwrCommand` (registered as the executor for `/rwr`).
    // Legacy standalone aliases were removed from plugin.yml in favor of `/rwr` subcommands.

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
            LogUtil.log(getLogger(), "No resource world configured! Use /rwr gui to select a world before resetting.", Level.WARNING);
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("resourceworldresetter.admin")) {
                    admin.sendMessage(ChatColor.RED + "[RWR] No resource world configured! Open /rwr gui and use \"Change World\" to select one.");
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
        if (resetStateFile == null) {
            return;
        }

        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            YamlConfiguration state = new YamlConfiguration();
            state.set("worldName", worldName);
            state.set("resetType", getResetType());
            state.set("regionReset", currentResetIsRegionReset);
            state.set("phase", resetPhase.name());
            state.set("resumePhase", resumePhase.name());
            if (failedResetPhase != null) {
                state.set("failedPhase", failedResetPhase.name());
            }
            state.set("startedAt", resetStartedAtMillis);
            state.set("phaseStartedAt", resetPhaseStartedAtMillis);
            state.set("attempt", resetAttempt);
            state.set("playerCountAtStart", resetPlayerCountAtStart);
            state.set("tpsAtStart", resetTpsAtStart);
            state.set("diskFreeBytesAtStart", resetDiskFreeBytesAtStart);
            state.set("failureReason", lastResetFailureReason);
            state.set("failureDetail", lastResetFailureDetail);
            state.set("failureStackTrace", lastResetFailureStackTrace);
            for (Map.Entry<ResetPhase, Long> entry : resetPhaseDurations.entrySet()) {
                state.set("phaseDurations." + entry.getKey().name(), entry.getValue());
            }
            state.save(resetStateFile);
            LogUtil.log(getLogger(), "Reset state saved for world '" + worldName + "' at phase " + resetPhase,
                    Level.FINE);
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
        resetPhase = ResetPhase.IDLE;
        resumePhase = ResetPhase.IDLE;
        failedResetPhase = null;
        resetStartedAtMillis = 0L;
        resetPhaseStartedAtMillis = 0L;
        resetPhaseDurations.clear();
        resetPlayerCountAtStart = 0;
        resetTpsAtStart = 20.0;
        resetDiskFreeBytesAtStart = 0L;
    }

    public void cancelAutoResumeTask() {
        if (autoResumeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoResumeTaskId);
            autoResumeTaskId = -1;
        }
    }

    public void clearIncompleteResetState() {
        clearResetState();
    }

    private ResetPhase getSavedResumePhase() {
        if (resetStateFile == null || !resetStateFile.exists()) {
            return ResetPhase.IDLE;
        }

        YamlConfiguration state = YamlConfiguration.loadConfiguration(resetStateFile);
        ResetPhase phase = ResetPhase.fromString(state.getString("phase", ResetPhase.IDLE.name()));
        ResetPhase failedPhase = ResetPhase.fromString(state.getString("failedPhase", ResetPhase.IDLE.name()));
        ResetPhase savedResumePhase = ResetPhase.fromString(state.getString("resumePhase", null));
        return ResetPhase.resolveResumePhase(phase, failedPhase, savedResumePhase);
    }

    /**
     * Called from {@link #onEnable()} to detect a leftover {@code reset-state.yml} that indicates
     * the server was stopped in the middle of a reset. Alerts online admins and schedules an
     * auto-resume after 60 seconds (cancellable with {@code /rwr resume cancel}).
     */
    private void checkAndHandleIncompleteReset() {
        if (resetStateFile == null || !resetStateFile.exists()) {
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(resetStateFile);
        String savedWorld = state.getString("worldName", worldName);
        boolean wasRegionReset = state.getBoolean("regionReset", false);
        long startedAt = state.getLong("startedAt", 0);
        long phaseStartedAt = state.getLong("phaseStartedAt", 0);
        resetPhase = ResetPhase.fromString(state.getString("phase", ResetPhase.IDLE.name()));
        resumePhase = ResetPhase.fromString(state.getString("resumePhase", resetPhase.name()));
        failedResetPhase = ResetPhase.fromString(state.getString("failedPhase", null));
        resetStartedAtMillis = startedAt;
        resetPhaseStartedAtMillis = phaseStartedAt;
        resetAttempt = state.getInt("attempt", 0);
        resetPlayerCountAtStart = state.getInt("playerCountAtStart", 0);
        resetTpsAtStart = state.getDouble("tpsAtStart", 20.0);
        resetDiskFreeBytesAtStart = state.getLong("diskFreeBytesAtStart", 0L);
        lastResetFailureReason = state.getString("failureReason", null);
        lastResetFailureDetail = state.getString("failureDetail", null);
        lastResetFailureStackTrace = state.getString("failureStackTrace", null);
        resetPhaseDurations.clear();
        if (state.isConfigurationSection("phaseDurations")) {
            for (String key : state.getConfigurationSection("phaseDurations").getKeys(false)) {
                ResetPhase phase = ResetPhase.fromString(key);
                resetPhaseDurations.put(phase, state.getLong("phaseDurations." + key, 0L));
            }
        }

        String timeStr = startedAt > 0
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(startedAt))
                : "unknown";
        ResetPhase resumeTarget = getSavedResumePhase();
        String phaseLabel = resetPhase == ResetPhase.FAILED && failedResetPhase.isActive()
                ? resetPhase.name() + " -> " + failedResetPhase.name()
                : resetPhase.name();
        String resumeLabel = resumeTarget.isActive() && resumeTarget != resetPhase
            ? " (resume " + resumeTarget.name() + ")"
            : "";

        LogUtil.log(getLogger(), "INCOMPLETE RESET DETECTED: world='" + savedWorld + "', type=" +
            (wasRegionReset ? "region" : "full") + ", phase=" + phaseLabel + resumeLabel + ", started=" + timeStr +
                ". Auto-resuming in 60 seconds. Use /rwr resume cancel to abort.", Level.WARNING);

        String alertMsg = ChatColor.DARK_RED + "[RWR] Incomplete " + (wasRegionReset ? "region" : "full") +
            " reset detected for '" + savedWorld + "' at phase " + phaseLabel + resumeLabel +
                " (started: " + timeStr + ")." +
                ChatColor.YELLOW + " Auto-resume in 60s | /rwr resume = now | /rwr resume cancel = abort";
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("resourceworldresetter.admin")) {
                admin.sendMessage(alertMsg);
            }
        }

        final boolean finalWasRegionReset = wasRegionReset;
        final ResetPhase finalResumePhase = resumeTarget.isActive() ? resumeTarget : ResetPhase.PRECHECK;
        autoResumeTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            autoResumeTaskId = -1;
            if (resetStateFile == null || !resetStateFile.exists()) {
                return;
            }
            LogUtil.log(getLogger(), "Auto-resuming incomplete reset for world '" + savedWorld + "' at phase " +
                    finalResumePhase, Level.WARNING);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[ResourceWorldResetter] Auto-resuming incomplete world reset...");
            resumeIncompleteReset(finalWasRegionReset, finalResumePhase);
        }, 60 * 20L).getTaskId();
    }

    /**
     * Resumes an incomplete reset from the persisted phase or a manually supplied phase.
     */
    private void resumeIncompleteReset(boolean wasRegionReset, ResetPhase resumePhase) {
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
            return;
        }

        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            ensureResourceWorldExists();
            w = Bukkit.getWorld(worldName);
        }
        if (w != null) {
            performReset(w, false, resumePhase == null || !resumePhase.isActive() ? ResetPhase.PRECHECK : resumePhase, true);
        } else {
            LogUtil.log(getLogger(), "Cannot resume reset: world '" + worldName + "' is unavailable", Level.SEVERE);
        }
    }

    private void performReset(World world, boolean isScheduled) {
        performReset(world, isScheduled, ResetPhase.PRECHECK, false);
    }

    private void performReset(World world, boolean isScheduled, ResetPhase startingPhase, boolean resumeState) {
        if (!resumeState) {
            resetStartedAtMillis = System.currentTimeMillis();
            resetPhaseDurations.clear();
            resetPlayerCountAtStart = world.getPlayers().size();
            resetTpsAtStart = getServerTPS();
            resetDiskFreeBytesAtStart = getDiskFreeBytes();
            lastResetFailureReason = null;
            lastResetFailureDetail = null;
            lastResetFailureStackTrace = null;
            failedResetPhase = null;
            resetAttempt = 0;
        }

        resetInProgress = true;
        currentResetIsRegionReset = false;
        enterResetPhase(startingPhase == null || !startingPhase.isActive() ? ResetPhase.PRECHECK : startingPhase);

        LogUtil.log(getLogger(), "Starting world reset process for " + worldName + " at phase " + resetPhase +
                (resetAttempt > 0 ? " (retry " + resetAttempt + "/" + MAX_RESET_ATTEMPTS + ")" : ""), Level.INFO);
        Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                "Resource world reset in progress at phase " + resetPhase + ". Players in that world will be teleported to safety.");

        advanceFullReset(world, isScheduled, resetPhase);
    }

    private void advanceFullReset(World world, boolean isScheduled, ResetPhase phase) {
        if (!resetInProgress) {
            resetInProgress = true;
        }
        if (phase == null || !phase.isActive()) {
            phase = ResetPhase.PRECHECK;
        }
        if (resetPhase != phase) {
            enterResetPhase(phase);
        }

        switch (phase) {
            case PRECHECK -> {
                if (!runPreResetChecks(world, isScheduled)) {
                    return;
                }
                completeCurrentPhaseAndAdvance(world, isScheduled, ResetPhase.TELEPORT);
            }
            case TELEPORT -> {
                teleportPlayersSafely(world);
                completeCurrentPhaseAndAdvance(world, isScheduled, ResetPhase.UNLOAD);
            }
            case UNLOAD -> {
                if (!ensureWorldUnloaded()) {
                    failResetFlow("world unload failed", isScheduled, phase, null);
                    return;
                }
                completeCurrentPhaseAndAdvance(world, isScheduled, ResetPhase.DELETE);
            }
            case DELETE -> deleteWorldFolderAsync(isScheduled);
            case RECREATE -> {
                if (!ensureWorldRecreated()) {
                    failResetFlow("world recreation failed", isScheduled, phase, null);
                    return;
                }
                completeCurrentPhaseAndAdvance(world, isScheduled, ResetPhase.VERIFY);
            }
            case VERIFY -> {
                if (!verifyResetCompletion()) {
                    failResetFlow("verification failed", isScheduled, phase, null);
                    return;
                }
                completeResetFlow(isScheduled);
            }
            case COMPLETE -> completeResetFlow(isScheduled);
            case FAILED -> failResetFlow(lastResetFailureReason == null ? "reset failed" : lastResetFailureReason,
                    isScheduled, failedResetPhase == null ? phase : failedResetPhase, null);
            case IDLE -> advanceFullReset(world, isScheduled, ResetPhase.PRECHECK);
        }
    }

    private void enterResetPhase(ResetPhase phase) {
        if (phase == null || !phase.isActive()) {
            return;
        }

        resetPhase = phase;
        resumePhase = phase;
        resetPhaseStartedAtMillis = System.currentTimeMillis();
        saveResetState();
        onResetPhaseEnter(phase);
    }

    private void exitResetPhase(ResetPhase phase, boolean successful) {
        if (phase == null || !phase.isActive()) {
            return;
        }

        long phaseDuration = Math.max(0L, System.currentTimeMillis() - resetPhaseStartedAtMillis);
        resetPhaseDurations.merge(phase, phaseDuration, Long::sum);
        resumePhase = successful ? phase.next() : phase;
        saveResetState();
        onResetPhaseExit(phase, successful, phaseDuration);
    }

    private void completeCurrentPhaseAndAdvance(World world, boolean isScheduled, ResetPhase nextPhase) {
        ResetPhase completedPhase = resetPhase;
        exitResetPhase(completedPhase, true);
        if (nextPhase == null || !nextPhase.isActive()) {
            return;
        }

        enterResetPhase(nextPhase);
        advanceFullReset(world, isScheduled, nextPhase);
    }

    private void onResetPhaseEnter(ResetPhase phase) {
        LogUtil.log(getLogger(), "Entered reset phase " + phase + " for world '" + worldName + "'", Level.FINE);
    }

    private void onResetPhaseExit(ResetPhase phase, boolean successful, long phaseDurationMillis) {
        LogUtil.log(getLogger(), "Exited reset phase " + phase + " after " + phaseDurationMillis + "ms (" +
                (successful ? "success" : "failure") + ") for world '" + worldName + "'", Level.FINE);
    }

    private boolean runPreResetChecks(World world, boolean isScheduled) {
        LogUtil.log(getLogger(), "Running reset precheck for world '" + worldName + "'", Level.INFO);
        PreResetEvent preEvent = new PreResetEvent(worldName, getResetType());
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            LogUtil.log(getLogger(), "World reset cancelled by a PreResetEvent listener", Level.INFO);
            resetInProgress = false;
            resetPhase = ResetPhase.IDLE;
            resetAttempt = 0;
            clearResetState();
            rescheduleManager.requestReschedule();
            return false;
        }

        Bukkit.broadcastMessage(ChatColor.RED + "[ResourceWorldResetter] " +
                "Resource world reset in progress. Players in that world will be teleported to safety.");
        return true;
    }

    private void deleteWorldFolderAsync(boolean isScheduled) {
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
                if (throwable != null) {
                    LogUtil.log(getLogger(), "Async deletion threw an unexpected exception: " + throwable.getMessage(), Level.SEVERE);
                    failResetFlow("async exception: " + throwable.getMessage(), isScheduled, ResetPhase.DELETE, throwable);
                    return;
                }
                if (Boolean.TRUE.equals(deleted)) {
                    LogUtil.log(getLogger(), "World folder deleted successfully; recreating world", Level.INFO);
                    completeCurrentPhaseAndAdvance(Bukkit.getWorld(worldName), isScheduled, ResetPhase.RECREATE);
                } else {
                    LogUtil.log(getLogger(), "Failed to delete world folder: " + worldName, Level.SEVERE);
                    failResetFlow("world folder deletion failed", isScheduled, ResetPhase.DELETE, null);
                }
            });
        });
    }

    private void completeResetFlow(boolean isScheduled) {
        resetPhase = ResetPhase.COMPLETE;
        resumePhase = ResetPhase.IDLE;
        resetPhaseStartedAtMillis = System.currentTimeMillis();
        saveResetState();

        long duration = System.currentTimeMillis() - resetStartedAtMillis;
        double tpsAfter = getServerTPS();
        Bukkit.broadcastMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                "Resource world reset completed in " + (duration / 1000) + " seconds (TPS: " +
                String.format("%.2f", resetTpsAtStart) + " → " + String.format("%.2f", tpsAfter) + ").");
        LogUtil.log(getLogger(), "Resource world reset completed in " + duration + "ms", Level.INFO);
        Bukkit.getPluginManager().callEvent(new PostResetEvent(worldName, getResetType(), true));
        resetAttempt = 0;
        resetInProgress = false;
        currentResetIsRegionReset = false;
        failedResetPhase = null;
        clearResetState();
        rescheduleManager.requestReschedule();
    }

    private void failResetFlow(String reason, boolean isScheduled, ResetPhase failingPhase, Throwable throwable) {
        resetInProgress = false;
        ResetPhase effectiveFailingPhase = failingPhase;
        if (effectiveFailingPhase == null || !effectiveFailingPhase.isActive()) {
            effectiveFailingPhase = resetPhase.isActive() ? resetPhase : failedResetPhase;
        }
        exitResetPhase(effectiveFailingPhase, false);
        failedResetPhase = effectiveFailingPhase;
        resetPhase = ResetPhase.FAILED;
        resumePhase = effectiveFailingPhase == null ? ResetPhase.PRECHECK : effectiveFailingPhase;
        resetPhaseStartedAtMillis = System.currentTimeMillis();
        lastResetFailureReason = reason;
        lastResetFailureDetail = effectiveFailingPhase == null ? null : "phase=" + effectiveFailingPhase.name();
        lastResetFailureStackTrace = throwable == null ? null : stackTraceToString(throwable);
        saveResetState();

        Bukkit.getPluginManager().callEvent(new PostResetEvent(worldName, getResetType(), false));
        resetAttempt++;

        if (resetAttempt <= MAX_RESET_ATTEMPTS) {
            long delayTicks = RETRY_DELAY_TICKS[resetAttempt - 1];
            long delaySeconds = delayTicks / 20;
            final ResetPhase retryPhase = effectiveFailingPhase;
                LogUtil.log(getLogger(), "Reset failed at phase " + effectiveFailingPhase + " (" + reason + "). Scheduling retry " + resetAttempt +
                    "/" + MAX_RESET_ATTEMPTS + " in " + delaySeconds + " seconds.", Level.WARNING);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "[ResourceWorldResetter] Reset encountered an error at phase " + effectiveFailingPhase + ". " +
                    "Retrying in " + delaySeconds + " seconds (attempt " + resetAttempt + "/" + MAX_RESET_ATTEMPTS + ")...");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                LogUtil.log(getLogger(), "Executing reset retry attempt " + resetAttempt + " from phase " + failedResetPhase, Level.INFO);
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    LogUtil.log(getLogger(), "World '" + worldName + "' not found on retry; attempting to recreate before reset.", Level.WARNING);
                    ensureResourceWorldExists();
                    w = Bukkit.getWorld(worldName);
                }
                if (w != null) {
                    performReset(w, isScheduled, failedResetPhase == null ? ResetPhase.PRECHECK : failedResetPhase, true);
                } else {
                    LogUtil.log(getLogger(), "Cannot retry: world '" + worldName + "' still unavailable after recreation attempt.", Level.SEVERE);
                    failResetFlow("world unavailable for retry", isScheduled, failedResetPhase == null ? retryPhase : failedResetPhase, null);
                }
            }, delayTicks);
        } else {
            LogUtil.log(getLogger(), "Reset failed after " + MAX_RESET_ATTEMPTS + " retries at phase " + effectiveFailingPhase +
                    ". Last reason: " + reason + ". Verify: world folder permissions, Multiverse-Core status, and available disk space.", Level.SEVERE);
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

    private boolean ensureWorldUnloaded() {
        if (!worldManagerBridge.isWorldLoaded(worldName)) {
            return true;
        }
        return worldManagerBridge.unloadWorld(worldName);
    }

    private boolean ensureWorldRecreated() {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            ensureSafeWorldSpawn(world);
            return true;
        }
        return recreateWorld();
    }

    private boolean verifyResetCompletion() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            LogUtil.log(getLogger(), "Verification failed: world '" + worldName + "' is not loaded after recreation", Level.SEVERE);
            return false;
        }
        ensureSafeWorldSpawn(world);
        return true;
    }

    private long getDiskFreeBytes() {
        File container = Bukkit.getWorldContainer();
        return container == null ? 0L : container.getUsableSpace();
    }

    private String stackTraceToString(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
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
        World defaultWorld = getTeleportTargetWorld(world);
        if (defaultWorld == null) {
            LogUtil.log(getLogger(), "No alternate world available as teleport target; cannot teleport players out of regions in " + world.getName(), Level.SEVERE);
            return;
        }
        org.bukkit.Location spawn = getSafeSpawnLocation(defaultWorld);
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

    public void resumeIncompleteResetFromCommand(boolean wasRegionReset) {
        resumeIncompleteReset(wasRegionReset, getSavedResumePhase());
    }

    public void resumeIncompleteResetFromCommand(boolean wasRegionReset, ResetPhase resumePhase) {
        resumeIncompleteReset(wasRegionReset, resumePhase);
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
        World defaultWorld = getTeleportTargetWorld(world);
        if (defaultWorld == null) {
            LogUtil.log(getLogger(), "No alternate world available as teleport target; cannot teleport players out of " + world.getName(), Level.SEVERE);
            return;
        }
        Location spawn = getSafeSpawnLocation(defaultWorld);
        LogUtil.log(getLogger(), "Teleport target for reset: '" + defaultWorld.getName() + "' (spawn)", Level.INFO);

        for (Player player : world.getPlayers()) {
            if (!player.isOnline()) continue;
            player.teleport(spawn);
            player.sendMessage(ChatColor.GREEN + "[ResourceWorldResetter] " +
                    "You have been teleported to safety - the resource world is being reset.");
            LogUtil.log(getLogger(), "Teleported " + player.getName() + " to '" + defaultWorld.getName() + "' (spawn)", Level.INFO);
        }
    }

    private World getTeleportTargetWorld(World sourceWorld) {
        java.util.List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }

        if (sourceWorld != null) {
            for (World candidate : worlds) {
                if (candidate != null && !candidate.getUID().equals(sourceWorld.getUID())) {
                    return candidate;
                }
            }
            return null;
        }

        return worlds.get(0);
    }

    public boolean recreateWorld() {
        boolean success = worldManagerBridge.createWorld(worldName);

        if (success) {
            ensureSafeWorldSpawn(Bukkit.getWorld(worldName));
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

        ensureSafeWorldSpawn(Bukkit.getWorld(worldName));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }

        Location desired = event.getTo();
        Location safeLanding = getPreferredTeleportLocation(event.getPlayer(), desired);
        if (safeLanding == null) {
            return;
        }

        if (sameBlock(desired, safeLanding)) {
            return;
        }

        safeLanding.setYaw(desired.getYaw());
        safeLanding.setPitch(desired.getPitch());
        event.setTo(safeLanding);
        rememberSafeLocation(event.getPlayer(), safeLanding);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from == null || sameBlock(from, to)) {
            return;
        }

        if (isSafeStandingLocation(to)) {
            rememberSafeLocation(event.getPlayer(), to);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastSafeLocationsByPlayerWorld.remove(event.getPlayer().getUniqueId());
        if (teleportationSystem != null) {
            teleportationSystem.clearPlayerLocation(event.getPlayer());
        }
    }

    private void ensureSafeWorldSpawn(World world) {
        if (world == null) {
            return;
        }

        Location safeSpawn = getSafeSpawnLocation(world);
        world.setSpawnLocation(safeSpawn.getBlockX(), safeSpawn.getBlockY(), safeSpawn.getBlockZ());
        LogUtil.log(getLogger(), "Safe spawn set for '" + world.getName() + "' at " +
                safeSpawn.getBlockX() + "," + safeSpawn.getBlockY() + "," + safeSpawn.getBlockZ(), Level.INFO);
    }

    private Location getSafeSpawnLocation(World world) {
        return getSafeTeleportLocation(world.getSpawnLocation());
    }

    private Location getSafeTeleportLocation(Location target) {
        World world = target.getWorld();
        int baseX = target.getBlockX();
        int baseZ = target.getBlockZ();
        int minY = Math.max(world.getMinHeight() + 1, 0);
        int maxY = world.getMaxHeight() - 3;

        // Expanded search radius: up to 256 blocks to ensure we find safe ground
        for (int radius = 0; radius <= 256; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    int x = baseX + dx;
                    int z = baseZ + dz;
                    int highestY = world.getHighestBlockYAt(x, z);

                    for (int y = Math.min(highestY + 2, maxY); y >= minY; y--) {
                        if (isSafeTeleportSpot(world, x, y, z)) {
                            return new Location(world, x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
        }

        // Fallback: search wider area for any solid ground
        for (int searchRadius = 0; searchRadius <= 512; searchRadius += 32) {
            for (int dx = -searchRadius; dx <= searchRadius; dx += 32) {
                for (int dz = -searchRadius; dz <= searchRadius; dz += 32) {
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    int highestY = world.getHighestBlockYAt(x, z);
                    
                    // Search down from highest block to find a safe spot
                    for (int y = Math.min(highestY + 3, maxY); y > minY; y--) {
                        Material block = world.getBlockAt(x, y, z).getType();
                        Material above = world.getBlockAt(x, y + 1, z).getType();
                        Material below = world.getBlockAt(x, y - 1, z).getType();
                        
                        // Check if we can stand here
                        if (block.isAir() && above.isAir() && below.isSolid() && below != Material.WATER && below != Material.LAVA) {
                            return new Location(world, x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
        }

        // Final fallback: return spawn location even if not ideal; server will handle it
        return new Location(world, baseX + 0.5, Math.max(minY + 1, world.getSpawnLocation().getBlockY()), baseZ + 0.5);
    }

    private Location getPreferredTeleportLocation(Player player, Location desired) {
        Location remembered = getRememberedSafeLocation(player, desired.getWorld());
        if (remembered != null) {
            return getSafeTeleportLocation(remembered);
        }

        return getSafeTeleportLocation(desired);
    }

    private void rememberSafeLocation(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }

        lastSafeLocationsByPlayerWorld
                .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(location.getWorld().getUID(), location.clone());
    }

    private Location getRememberedSafeLocation(Player player, World world) {
        if (player == null || world == null) {
            return null;
        }

        Map<UUID, Location> byWorld = lastSafeLocationsByPlayerWorld.get(player.getUniqueId());
        if (byWorld == null) {
            return null;
        }

        Location remembered = byWorld.get(world.getUID());
        return remembered == null ? null : remembered.clone();
    }

    private boolean isSafeStandingLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return isSafeTeleportSpot(world, x, y, z);
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && b.getWorld() != null
                && a.getWorld().getUID().equals(b.getWorld().getUID())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private boolean isSafeTeleportSpot(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }

        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        Material floor = world.getBlockAt(x, y - 1, z).getType();

        if (!feet.isAir() || !head.isAir()) {
            return false;
        }

        return floor.isSolid() && floor != Material.WATER && floor != Material.LAVA;
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
        // Empty worldName is valid — it means the admin hasn't selected a world yet via /rwr gui.
        String normalizedWorldName = (rawWorldName == null) ? "" : rawWorldName.trim();
        this.worldName = normalizedWorldName;

        // Read from v4 schema: schedule.mode
        String rawResetType = config.getString("schedule.mode", ResetMode.DAILY.getConfigValue());
        ResetMode normalizedMode = ResetMode.fromConfig(rawResetType);
        if (!normalizedMode.matches(rawResetType)) {
            LogUtil.log(getLogger(), "Unknown schedule.mode '" + rawResetType + "'. Defaulting to " +
                    normalizedMode.getConfigValue(), Level.WARNING);
            config.set("schedule.mode", normalizedMode.getConfigValue());
            updated = true;
        }
        this.resetMode = normalizedMode;

        // Read from v4 schema: schedule.time.hour
        int rawRestartTime = config.getInt("schedule.time.hour", DEFAULT_RESTART_HOUR);
        int clampedRestartTime = Math.max(0, Math.min(23, rawRestartTime));
        if (clampedRestartTime != rawRestartTime) {
            LogUtil.log(getLogger(), "schedule.time.hour " + rawRestartTime + " out of range. Clamped to " + clampedRestartTime,
                    Level.WARNING);
            config.set("schedule.time.hour", clampedRestartTime);
            updated = true;
        }
        this.restartTime = clampedRestartTime;

        // Read from v4 schema: schedule.warningMinutes
        int rawWarningMinutes = config.getInt("schedule.warningMinutes", DEFAULT_WARNING_MINUTES);
        int sanitizedWarningMinutes = Math.max(0, rawWarningMinutes);
        if (sanitizedWarningMinutes != rawWarningMinutes) {
            LogUtil.log(getLogger(), "schedule.warningMinutes cannot be negative. Using " + sanitizedWarningMinutes,
                    Level.WARNING);
            config.set("schedule.warningMinutes", sanitizedWarningMinutes);
            updated = true;
        }
        this.resetWarningTime = sanitizedWarningMinutes;

        // Read from v4 schema: schedule.day
        int rawResetDay = config.getInt("schedule.day", DEFAULT_RESET_DAY);
        int sanitizedResetDay = sanitizeResetDayForMode(rawResetDay, normalizedMode);
        if (sanitizedResetDay != rawResetDay) {
            LogUtil.log(getLogger(), "schedule.day " + rawResetDay + " invalid for " + normalizedMode.getConfigValue() +
                    " resets. Using " + sanitizedResetDay, Level.WARNING);
            config.set("schedule.day", sanitizedResetDay);
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