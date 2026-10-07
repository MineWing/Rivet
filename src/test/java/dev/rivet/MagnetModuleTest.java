package dev.rivet;

import org.bukkit.GameMode;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MagnetModuleTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    public void onlyLivingNonSpectatorsThatCanPickUpUseTheMagnet() {
        assertTrue(MagnetModule.canCollect(GameMode.SURVIVAL, false, 20, true));
        assertTrue(MagnetModule.canCollect(GameMode.CREATIVE, false, 1, true));
        assertFalse(MagnetModule.canCollect(GameMode.SPECTATOR, false, 20, true));
        assertFalse(MagnetModule.canCollect(GameMode.SURVIVAL, true, 20, true));
        assertFalse(MagnetModule.canCollect(GameMode.SURVIVAL, false, 0, true));
        assertFalse(MagnetModule.canCollect(GameMode.SURVIVAL, false, 20, false));
    }

    @Test
    public void attractsUnownedItemsWithNoPickupDelay() {
        assertTrue(MagnetModule.canAttract(PLAYER, null, null, 0, 0, 60));
        assertTrue(MagnetModule.canAttract(PLAYER, PLAYER, OTHER, 0, 0, 60));
    }

    @Test
    public void leavesItemsOnPickupDelayOrOwnedBySomeoneElse() {
        assertFalse(MagnetModule.canAttract(PLAYER, null, null, 40, 500, 60));
        assertFalse(MagnetModule.canAttract(PLAYER, null, OTHER, 1, 500, 60));
        assertFalse(MagnetModule.canAttract(PLAYER, OTHER, null, 0, 500, 60));
    }

    @Test
    public void ignoresOwnDropsUntilTheyAreOldEnough() {
        assertFalse(MagnetModule.canAttract(PLAYER, null, PLAYER, 0, 59, 60));
        assertTrue(MagnetModule.canAttract(PLAYER, null, PLAYER, 0, 60, 60));
        assertTrue(MagnetModule.canAttract(PLAYER, null, OTHER, 0, 0, 60));
        assertTrue(MagnetModule.canAttract(PLAYER, null, PLAYER, 0, 0, 0));
    }
}
