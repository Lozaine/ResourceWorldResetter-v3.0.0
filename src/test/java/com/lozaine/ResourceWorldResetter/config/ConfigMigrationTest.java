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
}
