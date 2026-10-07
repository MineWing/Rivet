package dev.rivet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModuleCatalogTest {
    @Test
    public void everyCatalogCommandIsDeclaredInPluginYml() {
        var pluginResource = getClass().getResourceAsStream("/plugin.yml");
        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(
            new InputStreamReader(pluginResource, StandardCharsets.UTF_8));
        Set<String> declared = plugin.getConfigurationSection("commands").getKeys(false);
        Set<String> owned = ModuleCatalog.DEFINITIONS.stream()
            .flatMap(definition -> definition.commandNames().stream()).collect(Collectors.toSet());
        assertTrue(declared.containsAll(owned));
    }

    @Test
    public void definitionsSharingASwitchAgreeOnItsDefault() {
        Map<String, Boolean> defaults = new HashMap<>();
        ModuleCatalog.DEFINITIONS.stream().filter(ModuleDefinition::switchedInModulesFile)
            .forEach(definition -> {
                Boolean previous = defaults.putIfAbsent(definition.id(), definition.enabledByDefault());
                if (previous != null) {
                    assertEquals(definition.id(), previous, definition.enabledByDefault());
                }
            });
    }

    @Test
    public void gameplaySwitchedFeaturesStayOutOfModulesYml() {
        List<String> switches = ModuleCatalog.switches();
        assertTrue(switches.contains("inventory"));
        assertEquals(1, switches.stream().filter("inventory"::equals).count());
        assertTrue(!switches.contains("autocrafter") && !switches.contains("beacon-tools")
            && !switches.contains("hoppers"));
    }

    @Test
    public void definitionsRejectAmbiguousBindings() {
        assertThrows(IllegalArgumentException.class, () -> ModuleDefinition.hosted("homes", true)
            .pluginCommands("home", "home"));
        assertThrows(IllegalArgumentException.class, () -> ModuleDefinition.module("trash", true, TrashModule::new)
            .completer("trash", (trash, sender, args) -> List.of()));
    }
}
