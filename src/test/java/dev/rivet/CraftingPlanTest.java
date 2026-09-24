package dev.rivet;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CraftingPlanTest {
    @Test public void reallocatesFlexibleIngredientsToLeaveExactIngredientsAvailable() {
        assertArrayEquals(new int[]{1, 1}, CraftingPlan.allocate(
            new boolean[][]{{true, true}, {true, false}}, new int[]{1, 1}));
    }
    @Test public void consumesRepeatedIngredientsAcrossStacks() {
        assertArrayEquals(new int[]{2, 1, 0}, CraftingPlan.allocate(
            new boolean[][]{{true, true, false}, {true, true, false}, {true, true, false}},
            new int[]{2, 64, 64}));
    }
    @Test public void refusesMissingIngredientsWithoutChangingAmounts() {
        int[] amounts = {1, 64};
        assertNull(CraftingPlan.allocate(new boolean[][]{{true, false}, {true, false}}, amounts));
        assertArrayEquals(new int[]{1, 64}, amounts);
    }
    @Test(timeout = 1000) public void boundsWorkWhenManyChoicesShareTooFewItems() {
        boolean[][] choices = new boolean[9][27];
        for (boolean[] choice : choices) java.util.Arrays.fill(choice, true);
        int[] amounts = new int[27];
        java.util.Arrays.fill(amounts, 0, 8, 1);
        assertNull(CraftingPlan.allocate(choices, amounts));
    }
    @Test public void ignoresEmptySlots() {
        assertArrayEquals(new int[]{0, 1}, CraftingPlan.allocate(new boolean[][]{{true, true}}, new int[]{0, 1}));
    }
}
