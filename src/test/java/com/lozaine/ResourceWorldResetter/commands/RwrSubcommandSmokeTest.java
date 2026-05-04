package com.lozaine.ResourceWorldResetter.commands;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RwrSubcommandSmokeTest {

    @Test
    void pluginYmlDeclaresUnifiedRwrCommandAndAllSubcommands() {
        File pluginYml = new File("src/main/resources/plugin.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(pluginYml);

        assertTrue(yaml.contains("commands.rwr"));
        String usage = yaml.getString("commands.rwr.usage", "");

        assertTrue(usage.contains("help"));
        assertTrue(usage.contains("gui"));
        assertTrue(usage.contains("reload"));
        assertTrue(usage.contains("reset now"));
        assertTrue(usage.contains("resume [cancel]"));
        assertTrue(usage.contains("tp"));
        assertTrue(usage.contains("back"));
        assertTrue(usage.contains("region <enable|disable|list|add|remove|addhere>"));
        assertTrue(usage.contains("status"));
        assertTrue(usage.contains("next"));
    }

    @Test
    void pluginYmlDoesNotRegisterLegacyAliasCommands() {
        File pluginYml = new File("src/main/resources/plugin.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(pluginYml);

        assertFalse(yaml.contains("commands.rwrgui"));
        assertFalse(yaml.contains("commands.reloadrwr"));
        assertFalse(yaml.contains("commands.resetworld"));
        assertFalse(yaml.contains("commands.rwrregion"));
        assertFalse(yaml.contains("commands.rwrresume"));
    }

    @Test
    void rwrCommandDispatchContainsAllRootSubcommandCases() throws IOException {
        String source = Files.readString(
                new File("src/main/java/com/lozaine/ResourceWorldResetter/commands/RwrCommand.java").toPath(),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("case \"gui\""));
        assertTrue(source.contains("case \"reload\""));
        assertTrue(source.contains("case \"reset\""));
        assertTrue(source.contains("case \"resume\""));
        assertTrue(source.contains("case \"tp\""));
        assertTrue(source.contains("case \"back\""));
        assertTrue(source.contains("case \"region\""));
        assertTrue(source.contains("case \"status\""));
        assertTrue(source.contains("case \"next\""));
    }
}