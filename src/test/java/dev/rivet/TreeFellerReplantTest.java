package dev.rivet;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class TreeFellerReplantTest {
    @Test
    public void matchesEveryOverworldTreeSpecies() {
        for (String species : new String[]{"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA",
            "DARK_OAK", "CHERRY", "PALE_OAK"}) {
            Material expected = Material.valueOf(species + "_SAPLING");
            for (String suffix : new String[]{"_LOG", "_WOOD"}) {
                assertEquals(expected, TreeFeller.saplingFor(Material.valueOf(species + suffix)));
                assertEquals(expected,
                    TreeFeller.saplingFor(Material.valueOf("STRIPPED_" + species + suffix)));
            }
        }
        assertEquals(Material.MANGROVE_PROPAGULE, TreeFeller.saplingFor(Material.MANGROVE_LOG));
        assertEquals(Material.WARPED_FUNGUS, TreeFeller.saplingFor(Material.WARPED_STEM));
        assertEquals(Material.CRIMSON_FUNGUS, TreeFeller.saplingFor(Material.CRIMSON_STEM));
        assertNull(TreeFeller.saplingFor(Material.STONE));
    }

    @Test
    public void replantsClearedTrunkOnSuitableGround() {
        assertEquals(Material.OAK_SAPLING, replant(Material.AIR, true));
    }

    @Test
    public void doesNotPlantOnUnsuitableGroundOrOverwriteAnotherBlock() {
        assertEquals(Material.AIR, replant(Material.AIR, false));
        assertEquals(Material.STONE, replant(Material.STONE, true));
        assertEquals(Material.OAK_LOG, replant(Material.OAK_LOG, true));
    }

    @Test
    public void leavesBlocksWithoutAReplacementAlone() {
        TreeFeller.replant(null, null);
    }

    private Material replant(Material initial, boolean supported) {
        AtomicReference<Material> material = new AtomicReference<>(initial);
        BlockData sapling = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
            new Class<?>[]{BlockData.class}, (proxy, method, args) -> {
                if (method.getName().equals("getMaterial")) {
                    return Material.OAK_SAPLING;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        Block block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),
            new Class<?>[]{Block.class}, (proxy, method, args) -> switch (method.getName()) {
                case "isEmpty" -> material.get() == Material.AIR;
                case "canPlace" -> supported;
                case "setBlockData" -> {
                    material.set(((BlockData) args[0]).getMaterial());
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        TreeFeller.replant(block, sapling);
        return material.get();
    }
}
