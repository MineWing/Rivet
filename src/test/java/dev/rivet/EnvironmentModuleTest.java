package dev.rivet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class EnvironmentModuleTest {
    @Test
    public void fastForwardsWorldTimeAcrossMidnightAndStopsExactlyAtTheTarget() {
        assertEquals(12_000, EnvironmentModule.forwardTimeDistance(1_000, 13_000));
        assertEquals(2_000, EnvironmentModule.forwardTimeDistance(23_000, 1_000));
        assertEquals(0, EnvironmentModule.forwardTimeDistance(13_000, 13_000));
        assertEquals(23_500, EnvironmentModule.transitionedTime(23_000, 2_000, 1, 4));
        assertEquals(0, EnvironmentModule.transitionedTime(23_000, 2_000, 2, 4));
        assertEquals(1_000, EnvironmentModule.transitionedTime(23_000, 2_000, 4, 4));
    }
}
