package dev.rivet;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HopperModuleTest {
    @Test
    public void transferCooldownIsAlwaysAtLeastOneTick() {
        assertEquals(2, HopperModule.transferCooldown(2));
        assertEquals(1, HopperModule.transferCooldown(1));
        assertEquals(1, HopperModule.transferCooldown(0));
        assertEquals(1, HopperModule.transferCooldown(-10));
    }

    @Test
    public void batchRequestsOneFlushPerTickAndDeduplicatesHoppers() {
        HopperModule.TickBatch<String> batch = new HopperModule.TickBatch<>();
        assertTrue(batch.add("a"));
        assertFalse(batch.add("b"));
        assertFalse(batch.add("a"));
        assertEquals(List.of("a", "b"), batch.drain());
        assertEquals(List.of(), batch.drain());
        assertTrue(batch.add("c"));
        assertEquals(List.of("c"), batch.drain());
    }
}
