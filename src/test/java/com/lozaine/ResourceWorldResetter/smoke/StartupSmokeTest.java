package com.lozaine.ResourceWorldResetter.smoke;

import com.lozaine.ResourceWorldResetter.config.ConfigMigration;
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

class StartupSmokeTest {

    @TempDir
    File tempDir;

    @Test
    void startupWithBundledV4ConfigSkipsMigration() throws IOException {
        File sourceConfig = new File("src/main/resources/config.yml");
        String bundledConfig = Files.readString(sourceConfig.toPath(), StandardCharsets.UTF_8);

        File runtimeConfig = new File(tempDir, "config.yml");
        Files.writeString(runtimeConfig.toPath(), bundledConfig, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), runtimeConfig);

        assertFalse(migration.migrateIfNeeded());
        assertFalse(new File(tempDir, "config.v3.backup.yml").exists());

        YamlConfiguration config = YamlConfiguration.loadConfiguration(runtimeConfig);
        assertEquals(4, config.getInt("configVersion"));
        assertTrue(config.contains("schedule.mode"));
        assertTrue(config.contains("schedule.time.hour"));
        assertTrue(config.contains("schedule.warningMinutes"));
        assertTrue(config.contains("schedule.day"));
    }

    @Test
    void startupWithV3ConfigMigratesAndCreatesBackup() throws IOException {
        File runtimeConfig = new File(tempDir, "config.yml");
        Files.writeString(runtimeConfig.toPath(), """
                worldName: Resources
                resetWarningTime: 7
                restartTime: 4
                resetType: weekly
                resetDay: 3
                regions:
                  enabled: true
                  immediateRegenerationOnAdd: false
                  list:
                    - \"1,2\"
                """, StandardCharsets.UTF_8);

        ConfigMigration migration = new ConfigMigration(Logger.getLogger("test"), runtimeConfig);

        assertTrue(migration.migrateIfNeeded());
        assertTrue(new File(tempDir, "config.v3.backup.yml").exists());

        YamlConfiguration config = YamlConfiguration.loadConfiguration(runtimeConfig);
        assertEquals(4, config.getInt("configVersion"));
        assertEquals("weekly", config.getString("schedule.mode"));
        assertEquals(4, config.getInt("schedule.time.hour"));
        assertEquals(7, config.getInt("schedule.warningMinutes"));
        assertEquals(3, config.getInt("schedule.day"));
        assertTrue(config.getBoolean("regions.enabled"));
    }
}
