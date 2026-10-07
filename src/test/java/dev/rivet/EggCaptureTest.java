package dev.rivet;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EggCaptureTest {
    private static final Set<String> BLOCKED = Set.of("WITHER", "IRON_GOLEM");

    @Test
    public void blockedTypesAreRefusedEvenWithBypass() {
        assertEquals(EggCapture.Refusal.BLOCKED_TYPE,
            EggCapture.captureRefusal("IRON_GOLEM", BLOCKED, Set.of(), false, false, false));
        assertEquals(EggCapture.Refusal.BLOCKED_TYPE,
            EggCapture.captureRefusal("WITHER", BLOCKED, Set.of(), false, false, true));
    }

    @Test
    public void emptyAllowListAllowsEverythingNotBlocked() {
        assertEquals(EggCapture.Refusal.NONE,
            EggCapture.captureRefusal("COW", BLOCKED, Set.of(), false, false, false));
    }

    @Test
    public void nonEmptyAllowListRestrictsTypes() {
        Set<String> allowed = Set.of("COW", "SHEEP");
        assertEquals(EggCapture.Refusal.NONE,
            EggCapture.captureRefusal("SHEEP", BLOCKED, allowed, false, false, false));
        assertEquals(EggCapture.Refusal.NOT_ALLOWED_TYPE,
            EggCapture.captureRefusal("ZOMBIE", BLOCKED, allowed, false, false, true));
    }

    @Test
    public void blockListWinsOverAllowList() {
        assertEquals(EggCapture.Refusal.BLOCKED_TYPE,
            EggCapture.captureRefusal("IRON_GOLEM", BLOCKED, Set.of("IRON_GOLEM"), false, false, false));
    }

    @Test
    public void otherPlayersPetsAndNamedMobsNeedBypass() {
        assertEquals(EggCapture.Refusal.OWNED_BY_OTHER,
            EggCapture.captureRefusal("WOLF", BLOCKED, Set.of(), true, false, false));
        assertEquals(EggCapture.Refusal.NAMED,
            EggCapture.captureRefusal("COW", BLOCKED, Set.of(), false, true, false));
        assertEquals(EggCapture.Refusal.NONE,
            EggCapture.captureRefusal("WOLF", BLOCKED, Set.of(), true, true, true));
    }

    @Test
    public void ownershipComparesTamerWithThrower() {
        UUID thrower = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(EggCapture.ownedByOther(true, other, thrower));
        assertFalse(EggCapture.ownedByOther(true, thrower, thrower));
        assertFalse(EggCapture.ownedByOther(false, other, thrower));
        assertFalse(EggCapture.ownedByOther(true, null, thrower));
    }

    @Test
    public void typeNamesAcceptNamespacedAndLowercaseEntries() {
        assertEquals(Set.of("IRON_GOLEM", "ELDER_GUARDIAN", "COW"), EggCapture.typeNames(
            List.of("minecraft:iron_golem", " elder-guardian ", "COW", "")));
        assertEquals(EggCapture.Refusal.BLOCKED_TYPE, EggCapture.captureRefusal("iron_golem",
            EggCapture.typeNames(List.of("minecraft:iron_golem")), Set.of(), false, false, false));
    }

    @Test
    public void capturedEggsCannotRetypeSpawners() {
        assertTrue(EggCapture.blocksSpawnerUse(Action.RIGHT_CLICK_BLOCK, Material.SPAWNER, true));
        assertTrue(EggCapture.blocksSpawnerUse(Action.RIGHT_CLICK_BLOCK, Material.TRIAL_SPAWNER, true));
        assertFalse(EggCapture.blocksSpawnerUse(Action.RIGHT_CLICK_BLOCK, Material.SPAWNER, false));
        assertFalse(EggCapture.blocksSpawnerUse(Action.RIGHT_CLICK_BLOCK, Material.GRASS_BLOCK, true));
        assertFalse(EggCapture.blocksSpawnerUse(Action.LEFT_CLICK_BLOCK, Material.SPAWNER, true));
    }

    @Test
    public void bundledSettingsBlockBossesAndUtilityMobs() {
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/settings/egg-capture.yml"), StandardCharsets.UTF_8));
        Set<String> blocked = EggCapture.typeNames(settings.getStringList("blocked-types"));

        assertTrue(blocked.containsAll(Set.of("WITHER", "ENDER_DRAGON", "WARDEN",
            "ELDER_GUARDIAN", "VILLAGER", "IRON_GOLEM")));
        assertEquals(EggCapture.DEFAULT_BLOCKED_TYPES, blocked);
        assertTrue(settings.isList("allowed-types"));
        assertTrue(settings.getStringList("allowed-types").isEmpty());
    }
}
