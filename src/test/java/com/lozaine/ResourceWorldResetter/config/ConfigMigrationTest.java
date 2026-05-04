package com.lozaine.ResourceWorldResetter.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTest {

    @TempDir
    File tempDir;

    @Test
    void migrateIfNeededCreatesBackupAndUpgradesV3Config() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                worldName: Resources
                resetWarningTime: 7
                restartTime: 4
                resetType: weekly
                resetDay: 3
                regions:
                  enabled: true
                  immediateRegenerationOnAdd: false
                  list:
                    - 1,2
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);

        assertTrue(migration.migrateIfNeeded());
        assertTrue(new File(tempDir, "config.v3.backup.yml").exists());

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(4, migratedConfig.getInt("configVersion"));
        assertEquals("weekly", migratedConfig.getString("schedule.mode"));
        assertEquals(4, migratedConfig.getInt("schedule.time.hour"));
        assertEquals(7, migratedConfig.getInt("schedule.warningMinutes"));
        assertEquals(3, migratedConfig.getInt("schedule.day"));

        assertFalse(migration.migrateIfNeeded());
        assertEquals(4, YamlConfiguration.loadConfiguration(configFile).getInt("configVersion"));
    }

    @Test
    void migrateIfNeededReturnsFalseForAlreadyMigratedConfig() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                configVersion: 4
                worldName: Resources
                schedule:
                  mode: daily
                  time:
                    hour: 3
                  warningMinutes: 5
                  day: 1
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);

        assertFalse(migration.migrateIfNeeded());
        assertFalse(new File(tempDir, "config.v3.backup.yml").exists());
        assertEquals(4, YamlConfiguration.loadConfiguration(configFile).getInt("configVersion"));
    }

    @Test
    void migrateIfNeededHandlesMissingOptionalFieldsWithDefaults() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                worldName: TestWorld
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);

        assertTrue(migration.migrateIfNeeded());

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(4, migratedConfig.getInt("configVersion"));
        assertEquals("daily", migratedConfig.getString("schedule.mode"));
        assertEquals(3, migratedConfig.getInt("schedule.time.hour"));
        assertEquals(5, migratedConfig.getInt("schedule.warningMinutes"));
        assertEquals(1, migratedConfig.getInt("schedule.day"));
    }

    @Test
    void migrateIfNeededRemovesOldV3Keys() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                worldName: Resources
                resetWarningTime: 7
                restartTime: 4
                resetType: weekly
                resetDay: 3
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);
        assertTrue(migration.migrateIfNeeded());

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(migratedConfig.contains("resetWarningTime"));
        assertFalse(migratedConfig.contains("restartTime"));
        assertFalse(migratedConfig.contains("resetType"));
        assertFalse(migratedConfig.contains("resetDay"));
    }

    @Test
    void migrateIfNeededPreservesRegionConfiguration() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                worldName: Resources
                regions:
                  enabled: true
                  immediateRegenerationOnAdd: false
                  list:
                    - "0,0"
                    - "1,1"
                    - "2,2"
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);
        assertTrue(migration.migrateIfNeeded());

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(migratedConfig.getBoolean("regions.enabled"));
        assertFalse(migratedConfig.getBoolean("regions.immediateRegenerationOnAdd"));
        assertEquals(3, migratedConfig.getList("regions.list").size());
    }

    @Test
    void migrateIfNeededCreatesDefaultRegionsBlockIfMissing() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                worldName: Resources
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);
        assertTrue(migration.migrateIfNeeded());

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(migratedConfig.getBoolean("regions.enabled"));
        assertTrue(migratedConfig.getBoolean("regions.immediateRegenerationOnAdd"));
        assertTrue(migratedConfig.getList("regions.list").isEmpty());
    }

    @Test
    void migrateIfNeededHandlesEdgeCaseScheduleValues() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                worldName: Resources
                resetWarningTime: 1
                restartTime: 23
                resetType: monthly
                resetDay: 31
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);
        assertTrue(migration.migrateIfNeeded());

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertEquals("monthly", migratedConfig.getString("schedule.mode"));
        assertEquals(23, migratedConfig.getInt("schedule.time.hour"));
        assertEquals(1, migratedConfig.getInt("schedule.warningMinutes"));
        assertEquals(31, migratedConfig.getInt("schedule.day"));
    }

    @Test
    void migrationReportContainsRelevantInfo() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                worldName: Resources
                resetWarningTime: 7
                restartTime: 4
                resetType: weekly
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), configFile);
        assertTrue(migration.migrateIfNeeded());

        String report = migration.getMigrationReport();
        assertTrue(report.contains("CONFIG MIGRATION REPORT"));
        assertTrue(report.contains("v3 config format"));
        assertTrue(report.contains("Migration completed successfully"));
        assertTrue(report.contains("config.v3.backup.yml"));
    }
}
