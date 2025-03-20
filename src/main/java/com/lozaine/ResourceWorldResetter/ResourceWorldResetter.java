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

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static com.onarandombox.MultiverseCore.utils.FileUtils.deleteFolder;

public class ResourceWorldResetter extends JavaPlugin {
    private String worldName;
    private MultiverseCore core;
    private long resetInterval;
    private int restartTime;
    private int resetWarningTime;
    private String resetType;
    private int resetDay;
    private AdminGUI adminGUI;

    public String getWorldName() { return this.worldName; }
    public String getResetType() { return this.resetType; }
    public long getResetInterval() { return this.resetInterval; }
    public int getRestartTime() { return this.restartTime; }
    public int getResetWarningTime() { return this.resetWarningTime; }

    public void setResetType(String type) { this.resetType = type; }
    public void setResetInterval(int interval) { this.resetInterval = interval; }
    public void setResetDay(int day) { this.resetDay = day; }


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

        loadConfig();
        adminGUI = new AdminGUI(this);
        getServer().getPluginManager().registerEvents(new AdminGUIListener(this, adminGUI), this);

        ensureResourceWorldExists();
        scheduleDailyReset();
        LogUtil.log(getLogger(), "ResourcesWorldResetter enabled successfully!", Level.INFO);
    }


    @Override
    public void onDisable() {
        LogUtil.log(getLogger(), "ResourceWorldResetter disabled.", Level.INFO);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("resourceworldresetter.admin")) {
            switch (command.getName().toLowerCase()) {
                case "rwadmin":
                    if (sender instanceof Player player) {
                        adminGUI.openMainMenu(player);
                    } else {
                        sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                    }
                    return true;
                case "setworld":
                    if (args.length > 0) {
                        worldName = args[0];
                        getConfig().set("worldName", worldName);
                        saveConfig();
                        sender.sendMessage(ChatColor.GREEN + "Resource world set to: " + worldName);
                        ensureResourceWorldExists();
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /setworld <worldname>");
                    }
                    return true;
                case "resetworld":
                    sender.sendMessage(ChatColor.GREEN + "Forcing resource world reset...");
                    resetResourceWorld();
                    return true;
                case "reloadworldresetter":
                    reloadConfig();
                    loadConfig();
                    sender.sendMessage(ChatColor.GREEN + "ResourcesWorldResetter configuration reloaded!");
                    return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        }
        return false;
    }

    private void scheduleDailyReset() {
        Bukkit.getScheduler().cancelTasks(this);
        resetType = getConfig().getString("resetType", "daily");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReset = now.withHour(restartTime).withMinute(0).withSecond(0);

        if (resetInterval > 0 && resetInterval < 86400) {
            long intervalTicks = resetInterval * 20;
            Bukkit.getScheduler().runTaskTimer(this, this::resetResourceWorld, intervalTicks, intervalTicks);
            return;
        }

        long initialDelayTicks = ChronoUnit.MILLIS.between(now, nextReset) / 50;
        Bukkit.getScheduler().runTaskLater(this, this::resetResourceWorld, initialDelayTicks);
    }

    public void resetResourceWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        double tpsBefore = getServerTPS();
        long startTime = System.currentTimeMillis();
        teleportPlayersSafely(world);

        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.unloadWorld(worldName)) return;

        CompletableFuture.runAsync(() -> {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (deleteFolder(worldFolder)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    recreateWorld(worldManager);
                    long duration = System.currentTimeMillis() - startTime;
                    double tpsAfter = getServerTPS();
                    Bukkit.broadcastMessage(ChatColor.GREEN + "Resource world reset completed in " + duration + "ms (TPS: " + tpsBefore + " -> " + tpsAfter + ").");
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
        Location spawn = world.getSpawnLocation();
        for (Player player : world.getPlayers()) {
            player.teleport(spawn);
            player.sendMessage(ChatColor.GREEN + "Teleported safely out of resource world.");
        }
    }

    public void recreateWorld(MVWorldManager worldManager) {
        boolean success = worldManager.addWorld(worldName, World.Environment.NORMAL, null, WorldType.NORMAL, true, "DEFAULT");
        Bukkit.broadcastMessage(success ? ChatColor.GREEN + "The resource world has been reset!" : ChatColor.RED + "Failed to recreate the resource world!");
    }

    public void ensureResourceWorldExists() {
        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.isMVWorld(worldName)) {
            worldManager.addWorld(worldName, World.Environment.NORMAL, null, WorldType.NORMAL, true, "DEFAULT");
        }
    }

    public void loadConfig() {
        reloadConfig();
        worldName = getConfig().getString("worldName", "Resources");
        resetInterval = getConfig().getLong("resetInterval", 86400);
        restartTime = getConfig().getInt("restartTime", 3);
        resetWarningTime = getConfig().getInt("resetWarningTime", 5);
        resetType = getConfig().getString("resetType", "daily");
        resetDay = getConfig().getInt("resetDay", 1);
    }
}
