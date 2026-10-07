package dev.rivet;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class SettingsMergeTest {
    private static YamlConfiguration bundled(String module) throws IOException, InvalidConfigurationException {
        try (InputStream stream = SettingsMergeTest.class.getResourceAsStream("/settings/" + module + ".yml")) {
            assertNotNull("bundled settings/" + module + ".yml", stream);
            YamlConfiguration configuration = new YamlConfiguration();
            if (module.equals("permissions")) {
                configuration.options().pathSeparator('/');
            }
            configuration.loadFromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return configuration;
        }
    }

    private static YamlConfiguration copy(YamlConfiguration source) throws InvalidConfigurationException {
        YamlConfiguration copy = new YamlConfiguration();
        copy.options().pathSeparator(source.options().pathSeparator());
        copy.loadFromString(source.saveToString());
        return copy;
    }

    @Test
    public void deletedKitStaysDeleted() throws Exception {
        YamlConfiguration defaults = bundled("kits");
        YamlConfiguration configured = copy(defaults);
        configured.set("kits.starter", null);
        configured.set("kits.vip.cooldown-seconds", 60);

        assertFalse(RivetConfig.mergeDefaults(defaults, configured, RivetConfig.USER_COLLECTIONS.get("kits")));
        assertFalse(configured.contains("kits.starter"));
        assertEquals(60, configured.getInt("kits.vip.cooldown-seconds"));
    }

    @Test
    public void entriesInsideAKeptKitAreNotReAdded() throws Exception {
        YamlConfiguration defaults = bundled("kits");
        YamlConfiguration configured = copy(defaults);
        configured.set("kits.starter.armor", null);

        RivetConfig.mergeDefaults(defaults, configured, RivetConfig.USER_COLLECTIONS.get("kits"));
        assertFalse(configured.contains("kits.starter.armor"));
    }

    @Test
    public void newScalarSettingsAreStillAdded() throws Exception {
        YamlConfiguration defaults = bundled("chat");
        defaults.set("brand-new-setting", 42);
        defaults.set("mentions.brand-new-flag", true);
        YamlConfiguration configured = copy(bundled("chat"));
        configured.set("anti-spam.cooldown", null);

        assertTrue(RivetConfig.mergeDefaults(defaults, configured, RivetConfig.USER_COLLECTIONS.get("chat")));
        assertEquals(42, configured.getInt("brand-new-setting"));
        assertTrue(configured.getBoolean("mentions.brand-new-flag"));
        assertEquals(defaults.getString("anti-spam.cooldown"), configured.getString("anti-spam.cooldown"));
    }

    @Test
    public void deletedChatStylesTagsAndGuiItemsStayDeleted() throws Exception {
        YamlConfiguration defaults = bundled("chat");
        YamlConfiguration configured = copy(defaults);
        configured.set("chat-styles.gradients.sunset", null);
        configured.set("chat-styles.colors.pink", null);
        configured.set("tags.list.og", null);
        configured.set("gui.chat-tags.items.filler", null);

        assertFalse(RivetConfig.mergeDefaults(defaults, configured, RivetConfig.USER_COLLECTIONS.get("chat")));
        assertFalse(configured.contains("chat-styles.gradients.sunset"));
        assertFalse(configured.contains("chat-styles.colors.pink"));
        assertFalse(configured.contains("tags.list.og"));
        assertFalse(configured.contains("gui.chat-tags.items.filler"));
    }

    @Test
    public void missingCollectionIsSeededWhole() throws Exception {
        YamlConfiguration defaults = bundled("kits");
        YamlConfiguration configured = copy(defaults);
        configured.set("kits", null);

        assertTrue(RivetConfig.mergeDefaults(defaults, configured, RivetConfig.USER_COLLECTIONS.get("kits")));
        assertEquals(defaults.getKeys(true), configured.getKeys(true));
        assertEquals(defaults.getInt("kits.starter.cooldown-seconds"),
            configured.getInt("kits.starter.cooldown-seconds"));
    }

    @Test
    public void deletedPermissionGroupStaysDeleted() throws Exception {
        YamlConfiguration defaults = bundled("permissions");
        YamlConfiguration configured = copy(defaults);
        configured.set("groups/staff", null);

        RivetConfig.mergeDefaults(defaults, configured, RivetConfig.USER_COLLECTIONS.get("permissions"));
        assertFalse(configured.contains("groups/staff"));
        assertTrue(configured.contains("groups/admin"));
    }

    @Test
    public void everyCollectionExistsAndEveryBundledGuiItemMapIsListed() throws Exception {
        for (Map.Entry<String, List<String>> entry : RivetConfig.USER_COLLECTIONS.entrySet()) {
            assertTrue("settings file " + entry.getKey(), RivetConfig.SETTINGS.contains(entry.getKey()));
            YamlConfiguration defaults = bundled(entry.getKey());
            entry.getValue().forEach(path -> assertTrue(entry.getKey() + ": " + path,
                defaults.isConfigurationSection(path)));
        }
        for (String module : RivetConfig.SETTINGS) {
            YamlConfiguration defaults = bundled(module);
            char separator = defaults.options().pathSeparator();
            for (Map.Entry<String, Object> value : defaults.getValues(true).entrySet()) {
                String path = value.getKey();
                if (value.getValue() instanceof ConfigurationSection
                    && path.startsWith("gui" + separator) && path.endsWith(separator + "items")) {
                    assertTrue(module + ": " + path + " should be a user collection",
                        RivetConfig.USER_COLLECTIONS.getOrDefault(module, List.of()).contains(path));
                }
            }
        }
    }
}
