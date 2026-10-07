package dev.rivet;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ItemToolsTest {
    @Test
    public void splitsAmountsIntoLegalStacks() {
        assertEquals(List.of(64, 64, 2), ItemTools.stackAmounts(130, 64));
        assertEquals(List.of(16, 4), ItemTools.stackAmounts(20, 16));
        assertEquals(List.of(1, 1, 1), ItemTools.stackAmounts(3, 1));
        assertEquals(List.of(64), ItemTools.stackAmounts(64, 64));
        assertEquals(List.of(), ItemTools.stackAmounts(0, 64));
        assertEquals(List.of(1, 1), ItemTools.stackAmounts(2, 0));
    }

    @Test
    public void giveLimitDefaultsAndStaysPositive() {
        YamlConfiguration settings = new YamlConfiguration();
        assertEquals(2_304, ItemTools.maximumGiveAmount(settings));
        settings.set("maximum-give-amount", 128);
        assertEquals(128, ItemTools.maximumGiveAmount(settings));
        settings.set("maximum-give-amount", 0);
        assertEquals(1, ItemTools.maximumGiveAmount(settings));
    }

    @Test
    public void bindingCurseBlocksHatOutsideCreative() {
        assertTrue(ItemTools.hatBlocked(true, GameMode.SURVIVAL));
        assertTrue(ItemTools.hatBlocked(true, GameMode.ADVENTURE));
        assertFalse(ItemTools.hatBlocked(true, GameMode.CREATIVE));
        assertFalse(ItemTools.hatBlocked(false, GameMode.SURVIVAL));
    }
}
