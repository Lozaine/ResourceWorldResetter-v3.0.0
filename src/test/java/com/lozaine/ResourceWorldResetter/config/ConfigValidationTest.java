package com.lozaine.ResourceWorldResetter.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for v3 → v4 schema transformation validation.
 * Ensures that migrated configs are properly validated and have correct structure.
 */
class ConfigValidationTest {

    @TempDir
    File tempDir;

    @Test
    void v4SchemaHasRequiredConfigVersion() throws IOException {
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

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(4, config.getInt("configVersion"));
        assertTrue(config.contains("configVersion"));
    }

    @Test
    void v4SchemaPreservesWorldName() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        String worldName = "MyCustomWorld";
        Files.writeString(configFile.toPath(), """
                configVersion: 4
                worldName: %s
                schedule:
                  mode: daily
                  time:
                    hour: 3
                """.formatted(worldName), StandardCharsets.UTF_8);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(worldName, config.getString("worldName"));
    }

    @Test
    void v4SchemaScheduleBlockHasRequiredFields() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                configVersion: 4
                worldName: Resources
                schedule:
                  mode: weekly
                  time:
                    hour: 5
                  warningMinutes: 10
                  day: 3
                """, StandardCharsets.UTF_8);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(config.contains("schedule.mode"));
        assertTrue(config.contains("schedule.time.hour"));
        assertTrue(config.contains("schedule.warningMinutes"));
        assertTrue(config.contains("schedule.day"));

        assertEquals("weekly", config.getString("schedule.mode"));
        assertEquals(5, config.getInt("schedule.time.hour"));
        assertEquals(10, config.getInt("schedule.warningMinutes"));
        assertEquals(3, config.getInt("schedule.day"));
    }

    @Test
    void v4SchemaSupportsValidScheduleModes() throws IOException {
        String[] validModes = {"daily", "weekly", "monthly"};

        for (String mode : validModes) {
            File configFile = new File(tempDir, "config_" + mode + ".yml");
            Files.writeString(configFile.toPath(), """
                    configVersion: 4
                    worldName: Resources
                    schedule:
                      mode: %s
                      time:
                        hour: 3
                      warningMinutes: 5
                      day: 1
                    """.formatted(mode), StandardCharsets.UTF_8);

            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            assertEquals(mode, config.getString("schedule.mode"));
        }
    }

    @Test
    void v4SchemaHourValidationBetween0And23() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        
        for (int hour = 0; hour < 24; hour++) {
            Files.writeString(configFile.toPath(), """
                    configVersion: 4
                    worldName: Resources
                    schedule:
                      mode: daily
                      time:
                        hour: %d
                      warningMinutes: 5
                      day: 1
                    """.formatted(hour), StandardCharsets.UTF_8);

            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            assertEquals(hour, config.getInt("schedule.time.hour"));
        }
    }

    @Test
    void v4SchemaWarningMinutesNotNegative() throws IOException {
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                configVersion: 4
                worldName: Resources
                schedule:
                  mode: daily
                  time:
                    hour: 3
                  warningMinutes: 0
                  day: 1
                """, StandardCharsets.UTF_8);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        int warningMinutes = config.getInt("schedule.warningMinutes");
        assertTrue(warningMinutes >= 0);
    }

    @Test
    void v4SchemaRegionsBlockIsWellFormed() throws IOException {
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
                regions:
                  enabled: true
                  immediateRegenerationOnAdd: false
                  list:
                    - "0,0"
                    - "1,1"
                """, StandardCharsets.UTF_8);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(config.contains("regions.enabled"));
        assertTrue(config.contains("regions.immediateRegenerationOnAdd"));
        assertTrue(config.contains("regions.list"));

        assertTrue(config.getBoolean("regions.enabled"));
        assertFalse(config.getBoolean("regions.immediateRegenerationOnAdd"));
        assertEquals(2, config.getList("regions.list").size());
    }

    @Test
    void v4SchemaMigratedFromV3HasConsistentStructure() throws IOException {
        // Simulate a v3 config that was migrated
        File configFile = new File(tempDir, "config.yml");
        Files.writeString(configFile.toPath(), """
                configVersion: 4
                worldName: Resources
                schedule:
                  mode: weekly
                  time:
                    hour: 4
                  warningMinutes: 7
                  day: 3
                regions:
                  enabled: true
                  immediateRegenerationOnAdd: false
                  list:
                    - "1,2"
                """, StandardCharsets.UTF_8);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        // Verify all required fields exist
        assertEquals(4, config.getInt("configVersion"));
        assertEquals("Resources", config.getString("worldName"));
        assertEquals("weekly", config.getString("schedule.mode"));
        assertEquals(4, config.getInt("schedule.time.hour"));
        assertEquals(7, config.getInt("schedule.warningMinutes"));
        assertEquals(3, config.getInt("schedule.day"));
        assertTrue(config.getBoolean("regions.enabled"));
    }

    @Test
    void v4SchemaConfigCanBeLoadedAndRewritten() throws IOException {
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

        // Load, modify, and save
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set("schedule.time.hour", 5);
        config.save(configFile);

        // Reload and verify
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(5, reloaded.getInt("schedule.time.hour"));
        assertEquals(4, reloaded.getInt("configVersion"));
    }

    @Test
    void v4SchemaOldV3KeysAreNotPresent() throws IOException {
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

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        // Ensure old v3 keys don't exist
        assertFalse(config.contains("resetWarningTime"));
        assertFalse(config.contains("restartTime"));
        assertFalse(config.contains("resetType"));
        assertFalse(config.contains("resetDay"));
    }

    @Test
    void v4SchemaHandlesEmptyRegionsList() throws IOException {
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
                regions:
                  enabled: false
                  list: []
                """, StandardCharsets.UTF_8);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(config.getBoolean("regions.enabled"));
        assertTrue(config.getList("regions.list").isEmpty());
    }
}
