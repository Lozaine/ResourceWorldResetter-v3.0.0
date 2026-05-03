package com.lozaine.ResourceWorldResetter.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Handles migration of configuration from v3 to v4 format.
 * 
 * v3 Config Structure:
 *   - worldName
 *   - resetWarningTime
 *   - restartTime
 *   - resetType
 *   - resetDay
 *   - regions.*
 *
 * v4 Config Structure:
 *   - configVersion: 4
 *   - worldName
 *   - schedule.mode (daily|weekly|monthly)
 *   - schedule.time.hour
 *   - schedule.warningMinutes
 *   - schedule.day
 *   - regions.*
 */
public class ConfigMigration {
    private static final int V4_CONFIG_VERSION = 4;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Logger logger;
    private final File configFile;
    private final File backupFile;
    private final StringBuilder migrationReport = new StringBuilder();

    public ConfigMigration(Logger logger, File configFile) {
        this.logger = logger;
        this.configFile = configFile;
        this.backupFile = new File(configFile.getParent(), "config.v3.backup.yml");
    }

    /**
     * Performs migration if necessary. Returns true if migration occurred.
     */
    public boolean migrateIfNeeded() throws IOException {
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        // Check if already v4
        if (config.contains("configVersion") && config.getInt("configVersion") == V4_CONFIG_VERSION) {
            logger.log(Level.INFO, "Config already at v4 schema. Skipping migration.");
            return false;
        }

        migrationReport.setLength(0);
        migrationReport.append("\n==================== CONFIG MIGRATION REPORT ====================\n");
        migrationReport.append("Detected v3 config format. Migrating to v4 schema...\n\n");

        // Backup old config before migration
        backupOldConfig(config);

        // Perform migration
        migrateScheduleKeys(config);
        migrateRegionKeys(config);
        
        // Set version marker
        config.set("configVersion", V4_CONFIG_VERSION);
        migrationReport.append(String.format("Set configVersion to %d\n", V4_CONFIG_VERSION));

        // Save migrated config
        config.save(configFile);

        migrationReport.append("\n=== Migration completed successfully ===\n");
        migrationReport.append("Old config backed up to: ").append(backupFile.getName()).append("\n");
        
        logger.log(Level.INFO, migrationReport.toString());
        return true;
    }

    /**
     * Backs up the old v3 config as config.v3.backup.yml with timestamp.
     */
    private void backupOldConfig(FileConfiguration oldConfig) throws IOException {
        if (backupFile.exists()) {
            String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
            File timestampedBackup = new File(configFile.getParent(), 
                "config.v3.backup." + timestamp + ".yml");
            oldConfig.save(timestampedBackup);
            migrationReport.append(String.format("Timestamped backup created: %s\n", timestampedBackup.getName()));
        }
        
        oldConfig.save(backupFile);
        migrationReport.append(String.format("Backup created: %s\n\n", backupFile.getName()));
    }

    /**
     * Migrates flat scheduling keys into structured v4 schedule block.
     */
    private void migrateScheduleKeys(FileConfiguration config) {
        migrationReport.append("--- Schedule Migration ---\n");

        // Migrate resetType -> schedule.mode
        String resetType = config.getString("resetType", "daily");
        config.set("schedule.mode", resetType);
        migrationReport.append(String.format("Migrated resetType '%s' -> schedule.mode\n", resetType));
        config.set("resetType", null); // Remove old key

        // Migrate restartTime -> schedule.time.hour
        int restartTime = config.getInt("restartTime", 3);
        config.set("schedule.time.hour", restartTime);
        migrationReport.append(String.format("Migrated restartTime %d -> schedule.time.hour\n", restartTime));
        config.set("restartTime", null);

        // Migrate resetWarningTime -> schedule.warningMinutes
        int warningTime = config.getInt("resetWarningTime", 5);
        config.set("schedule.warningMinutes", warningTime);
        migrationReport.append(String.format("Migrated resetWarningTime %d -> schedule.warningMinutes\n", warningTime));
        config.set("resetWarningTime", null);

        // Migrate resetDay -> schedule.day
        int resetDay = config.getInt("resetDay", 1);
        config.set("schedule.day", resetDay);
        migrationReport.append(String.format("Migrated resetDay %d -> schedule.day\n", resetDay));
        config.set("resetDay", null);

        migrationReport.append("\n");
    }

    /**
     * Ensures region keys are preserved with proper defaults.
     */
    private void migrateRegionKeys(FileConfiguration config) {
        migrationReport.append("--- Region Configuration ---\n");

        if (!config.contains("regions")) {
            config.set("regions.enabled", false);
            config.set("regions.immediateRegenerationOnAdd", true);
            config.set("regions.list", java.util.Collections.emptyList());
            migrationReport.append("Created default regions block\n");
        } else {
            boolean regionsEnabled = config.getBoolean("regions.enabled", false);
            migrationReport.append(String.format("Preserved regions.enabled: %b\n", regionsEnabled));
        }

        migrationReport.append("\n");
    }

    /**
     * Returns the migration report for logging.
     */
    public String getMigrationReport() {
        return migrationReport.toString();
    }

    /**
     * Checks if a v3 backup file exists.
     */
    public boolean hasBackup() {
        return backupFile.exists();
    }

    /**
     * Gets the backup file path (if exists).
     */
    public File getBackupFile() {
        return backupFile;
    }
}
