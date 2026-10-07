package dev.rivet;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;
import java.util.Map;
import static dev.rivet.AutoCrafterMenu.Category.*;
import static org.junit.Assert.*;

public final class AutoCrafterMenuTest {
    @Test public void putsCustomOutputsInTheirOwnCategoryEvenWhenTheyAreBlocks() {
        assertEquals(CUSTOM, AutoCrafterMenu.category("rivet", "BARREL", true, false));
        assertEquals(MACHINERY, AutoCrafterMenu.category("minecraft", "BARREL", true, false));
    }
    @Test public void separatesFoodAndRedstoneFromOrdinaryBuildingBlocks() {
        assertEquals(FOOD, AutoCrafterMenu.category("minecraft", "CAKE", true, false));
        assertEquals(FOOD, AutoCrafterMenu.category("minecraft", "BREAD", false, true));
        assertEquals(MACHINERY, AutoCrafterMenu.category("minecraft", "OAK_PRESSURE_PLATE", true, false));
        assertEquals(BUILDING, AutoCrafterMenu.category("minecraft", "OAK_STAIRS", true, false));
        assertEquals(EQUIPMENT, AutoCrafterMenu.category("minecraft", "IRON_PICKAXE", false, false));
        assertEquals(INGREDIENTS, AutoCrafterMenu.category("minecraft", "IRON_INGOT", false, false));
    }
    @Test public void preservesHolesAndRepeatedIngredientsInTheCraftingPreview() {
        assertEquals(Map.of(0, "iron", 2, "iron", 4, "stick", 7, "stick"),
            AutoCrafterMenu.shapedGrid(new String[]{"I I", " S ", " S "}, Map.of('I', "iron", 'S', "stick")));
        assertEquals(Map.of(0, "plank", 1, "plank", 3, "plank", 4, "plank"),
            AutoCrafterMenu.shapedGrid(new String[]{"PP", "PP"}, Map.of('P', "plank")));
    }
    @Test public void clampsPagesAfterSwitchingCategoriesOrClearingResults() {
        assertEquals(0, AutoCrafterMenu.clampPage(8, 0));
        assertEquals(0, AutoCrafterMenu.clampPage(8, 28));
        assertEquals(1, AutoCrafterMenu.clampPage(8, 29));
        assertEquals(1, AutoCrafterMenu.clampPage(8, 56));
        assertEquals(0, AutoCrafterMenu.clampPage(-1, 100));
    }
    @Test public void unconfiguredHologramExplainsWhatToDo() {
        String text = PlainTextComponentSerializer.plainText().serialize(AutoCrafterMenu.hologram(
            new AutoCrafterMenu.Machine(null, AutoCrafterMenu.Status.UNSELECTED, "north", 0)));
        assertEquals("Autocrafter\nNo recipe selected\nChoose a recipe", text);
    }
}
