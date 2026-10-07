package dev.rivet;

import org.bukkit.block.Banner;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrushableBlock;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.Shelf;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.Vault;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CreeperRestorationTest {
    private static final long MINUTE = 60_000;

    @Test
    public void expiresCratersOnceTheirLifetimeHasPassed() {
        long created = 1_000_000;
        assertFalse(CreeperRestoration.expired(created, created, 30));
        assertFalse(CreeperRestoration.expired(created, created + 30 * MINUTE - 1, 30));
        assertTrue(CreeperRestoration.expired(created, created + 30 * MINUTE, 30));
        assertTrue(CreeperRestoration.expired(created, created + 90 * MINUTE, 30));
    }

    @Test
    public void neverExpiresCratersWhenTheLifetimeIsDisabled() {
        assertFalse(CreeperRestoration.expired(0, Long.MAX_VALUE, 0));
        assertFalse(CreeperRestoration.expired(0, Long.MAX_VALUE, -5));
        assertFalse(CreeperRestoration.expired(0, 365L * 24 * 60 * MINUTE, Long.MAX_VALUE));
    }

    @Test
    public void evictsOnlyTheCratersAboveTheMaximum() {
        assertEquals(0, CreeperRestoration.excessCraters(0, 200));
        assertEquals(0, CreeperRestoration.excessCraters(200, 200));
        assertEquals(1, CreeperRestoration.excessCraters(201, 200));
        assertEquals(5, CreeperRestoration.excessCraters(15, 10));
    }

    @Test
    public void alwaysKeepsAtLeastOneCrater() {
        assertEquals(0, CreeperRestoration.excessCraters(1, 0));
        assertEquals(2, CreeperRestoration.excessCraters(3, 0));
        assertEquals(2, CreeperRestoration.excessCraters(3, -10));
    }

    @Test
    public void dropsReleasedBlocksOnlyIntoEmptySpacesAtTheExplosionYield() {
        assertTrue(CreeperRestoration.dropsBlock(true, 1, .999));
        assertTrue(CreeperRestoration.dropsBlock(true, 1 / 3d, .2));
        assertFalse(CreeperRestoration.dropsBlock(true, 1 / 3d, .5));
        assertFalse(CreeperRestoration.dropsBlock(true, 0, 0));
        assertFalse(CreeperRestoration.dropsBlock(false, 1, 0));
    }

    @Test
    public void escrowsContainersByContainerSettingsAndOtherItemHoldersByBlockEntitySetting() {
        assertTrue(CreeperRestoration.shouldEscrowContents(true, true, false, true, false));
        assertFalse(CreeperRestoration.shouldEscrowContents(true, false, true, true, false));
        assertFalse(CreeperRestoration.shouldEscrowContents(false, true, true, true, false));
        assertTrue(CreeperRestoration.shouldEscrowContents(false, false, true, false, true));
        assertFalse(CreeperRestoration.shouldEscrowContents(true, true, false, false, true));
        assertFalse(CreeperRestoration.shouldEscrowContents(true, true, true, false, false));
    }

    @Test
    public void escrowsEveryBlockEntityWhoseItemsVanillaDropsOnRemoval() {
        // Verified against Paper 1.21.11: these block entities drop their items from
        // BlockEntity.preRemoveSideEffects even when the explosion yield is zero.
        for (Class<? extends BlockState> type : List.of(Chest.class, Barrel.class, ShulkerBox.class,
            Lectern.class, Jukebox.class, ChiseledBookshelf.class, DecoratedPot.class, Shelf.class,
            Campfire.class)) {
            assertTrue(type.getSimpleName(), CreeperRestoration.holdsEscrowableItems(stub(type)));
        }
        // Vanilla never drops a brushable block's item or a sign's data, so the snapshot is
        // the only copy and must be kept.
        for (Class<? extends BlockState> type : List.of(BrushableBlock.class, Sign.class,
            Banner.class, Vault.class)) {
            assertFalse(type.getSimpleName(), CreeperRestoration.holdsEscrowableItems(stub(type)));
        }
    }

    private static BlockState stub(Class<? extends BlockState> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            }));
    }

    @Test
    public void bundledSettingsBoundCraterLifetimeAndCount() throws Exception {
        try (Reader reader = new InputStreamReader(
            CreeperRestorationTest.class.getResourceAsStream("/settings/creeper-restoration.yml"),
            StandardCharsets.UTF_8)) {
            YamlConfiguration settings = YamlConfiguration.loadConfiguration(reader);
            assertEquals(30, settings.getInt("restoration.crater-lifetime-minutes", -1));
            assertEquals(200, settings.getInt("restoration.maximum-craters", -1));
        }
    }
}
