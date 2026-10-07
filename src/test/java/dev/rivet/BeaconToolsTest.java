package dev.rivet;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class BeaconToolsTest {
    @Test public void accountsForEveryPyramidLayer() {
        assertEquals(9, BeaconTools.baseCount(1));
        assertEquals(34, BeaconTools.baseCount(2));
        assertEquals(83, BeaconTools.baseCount(3));
        assertEquals(164, BeaconTools.baseCount(4));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsOversizedPyramids() {
        BeaconTools.baseCount(5);
    }
}
