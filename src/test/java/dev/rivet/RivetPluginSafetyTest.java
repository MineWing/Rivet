package dev.rivet;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RivetPluginSafetyTest {
    @Test
    public void killAllProtectsPetsNamedPersistentAndUtilityMobs() {
        assertFalse(RivetPlugin.killAllProtected(EntityType.ZOMBIE, false, false, true));
        assertFalse(RivetPlugin.killAllProtected(EntityType.COW, false, false, true));
        assertTrue(RivetPlugin.killAllProtected(EntityType.WOLF, true, false, true));
        assertTrue(RivetPlugin.killAllProtected(EntityType.ZOMBIE, false, true, true));
        assertTrue(RivetPlugin.killAllProtected(EntityType.SKELETON, false, false, false));
        assertTrue(RivetPlugin.killAllProtected(EntityType.VILLAGER, false, false, true));
        assertTrue(RivetPlugin.killAllProtected(EntityType.WANDERING_TRADER, false, false, true));
        assertTrue(RivetPlugin.killAllProtected(EntityType.IRON_GOLEM, false, false, true));
    }

    @Test
    public void waterReplantOnlyQueuesWhenSeedWasConsumed() {
        assertTrue(RivetPlugin.queuesWaterReplant(
            new RivetPlugin.PlantingReservation(Material.WHEAT_SEEDS, true)));
        assertFalse(RivetPlugin.queuesWaterReplant(
            new RivetPlugin.PlantingReservation(Material.WHEAT_SEEDS, false)));
        assertFalse(RivetPlugin.queuesWaterReplant(null));
    }

    @Test
    public void waterCropEntriesInUnloadedChunksWaitThenExpireWithoutBlockAccess() {
        assertEquals(RivetPlugin.WaterCropStep.WAIT,
            RivetPlugin.waterCropStep(false, false, false, true, 1_195));
        assertEquals(RivetPlugin.WaterCropStep.DISCARD,
            RivetPlugin.waterCropStep(false, false, false, true, 1_200));
    }

    @Test
    public void waterCropEntriesInLoadedChunksReplantRefundOrExpire() {
        assertEquals(RivetPlugin.WaterCropStep.REPLANT,
            RivetPlugin.waterCropStep(true, false, true, true, 5));
        assertEquals(RivetPlugin.WaterCropStep.REFUND,
            RivetPlugin.waterCropStep(true, true, false, true, 5));
        assertEquals(RivetPlugin.WaterCropStep.WAIT,
            RivetPlugin.waterCropStep(true, false, false, true, 5));
        assertEquals(RivetPlugin.WaterCropStep.WAIT,
            RivetPlugin.waterCropStep(true, false, true, false, 5));
        assertEquals(RivetPlugin.WaterCropStep.REFUND,
            RivetPlugin.waterCropStep(true, false, false, true, 1_200));
    }

    @Test
    public void reportsGameplaySwitchesThatDifferFromWhatIsRunning() {
        Map<String, Boolean> running = new LinkedHashMap<>();
        running.put("autocrafter.enabled", true);
        running.put("beacon-tools.enabled", false);
        assertEquals(List.of(), RivetPlugin.pendingRestartSwitches(running, Map.copyOf(running)));
        assertEquals(List.of("autocrafter.enabled", "beacon-tools.enabled"),
            RivetPlugin.pendingRestartSwitches(running,
                Map.of("autocrafter.enabled", false, "beacon-tools.enabled", true)));
        assertEquals(List.of("beacon-tools.enabled"),
            RivetPlugin.pendingRestartSwitches(running,
                Map.of("autocrafter.enabled", true, "beacon-tools.enabled", true)));
    }
}
