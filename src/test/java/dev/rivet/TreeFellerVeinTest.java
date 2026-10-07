package dev.rivet;

import org.bukkit.block.Block;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class TreeFellerVeinTest {
    @Test
    public void minesNoMoreOresThanRemainingDurability() {
        assertEquals(1, TreeFeller.affordableOres(20, 1561, 1560, false));
        assertEquals(5, TreeFeller.affordableOres(20, 250, 245, false));
        assertEquals(20, TreeFeller.affordableOres(20, 1561, 0, false));
        assertEquals(0, TreeFeller.affordableOres(20, 250, 250, false));
        assertEquals(0, TreeFeller.affordableOres(20, 250, 400, false));
    }

    @Test
    public void unlimitedOrDurabilityLessToolsMineTheWholeVein() {
        assertEquals(20, TreeFeller.affordableOres(20, 250, 249, true));
        assertEquals(20, TreeFeller.affordableOres(20, 0, 0, false));
    }

    @Test
    public void picksTheOresNearestTheBrokenBlockFirst() {
        Block base = block(0, 10, 0);
        Block adjacent = block(1, 10, 0);
        Block diagonal = block(1, 11, 1);
        Block far = block(3, 10, 0);
        Set<Block> vein = Set.of(far, diagonal, base, adjacent);

        assertEquals(List.of(base, adjacent), TreeFeller.closestOres(vein, base, 2));
        assertEquals(List.of(base, adjacent, diagonal, far),
            TreeFeller.closestOres(vein, base, 10));
        assertEquals(List.of(), TreeFeller.closestOres(vein, base, 0));
    }

    private static Block block(int x, int y, int z) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),
            new Class<?>[]{Block.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> x + "," + y + "," + z;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
