package dev.rivet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.*;

import java.util.*;

/** Presentation and navigation state only. Machine mutations stay in AutoCrafter. */
final class AutoCrafterMenu implements InventoryHolder {
    static final int PREVIOUS = 45, PAUSE = 46, STORAGE = 47, SELECTED = 48,
        CONFIRM = 49, STATUS = 50, FILTER = 51, CLOSE = 52, NEXT = 53;
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    enum Category {
        ALL("All recipes", Material.CRAFTING_TABLE),
        BUILDING("Building", Material.BRICKS),
        MACHINERY("Redstone & utility", Material.HOPPER),
        EQUIPMENT("Tools & equipment", Material.IRON_PICKAXE),
        FOOD("Food", Material.BREAD),
        INGREDIENTS("Ingredients", Material.IRON_INGOT),
        CUSTOM("Custom recipes", Material.NETHER_STAR);

        final String label;
        final Material icon;
        Category(String label, Material icon) { this.label = label; this.icon = icon; }
    }

    enum Status {
        UNSELECTED("Choose a recipe", "Browse a category and select an output.", Material.CRAFTING_TABLE),
        PAUSED("Paused", "Resume when you are ready to craft.", Material.REDSTONE_TORCH),
        WAITING("Waiting for ingredients", "Add the recipe ingredients to storage.", Material.HOPPER),
        CRAFTING("Crafting", "One recipe is crafted each second.", Material.CRAFTER),
        OUTPUT_FULL("Output full", "Empty the container on the output side.", Material.CHEST),
        OUTPUT_BLOCKED("Output blocked", "Leave the output side clear or attach a container.", Material.BARRIER),
        OUTPUT_UNLOADED("Output chunk unloaded", "The output chunk must be loaded.", Material.MAP),
        TRANSFER_DENIED("Transfer blocked", "Another plugin refused the output transfer.", Material.BARRIER),
        INVALID_RECIPE("Recipe unavailable", "Choose another recipe from the menu.", Material.BARRIER),
        INVENTORY_CHANGED("Waiting for inventory", "Inventory changed during crafting; retrying.", Material.HOPPER);

        final String label;
        final String hint;
        final Material icon;
        Status(String label, String hint, Material icon) {
            this.label = label; this.hint = hint; this.icon = icon;
        }
    }

    record Machine(Recipe recipe, Status status, String facing, int occupied) { }

    final Location location;
    final Category category;
    final Material filter;
    final int page;
    final List<Recipe> recipes;
    final Recipe preview;
    private final Inventory inventory;
    boolean transitioning;

    AutoCrafterMenu(Location location, Category category, Material filter, int requestedPage,
                    Recipe preview, Machine machine) {
        this.location = location.clone();
        this.category = category;
        this.filter = filter;
        this.preview = preview;
        List<Recipe> catalogue = catalogue();
        recipes = catalogue.stream().filter(recipe -> (category == Category.ALL || category(recipe) == category)
            && (filter == null || recipe.getResult().getType() == filter)).toList();
        page = clampPage(requestedPage, recipes.size());
        inventory = Bukkit.createInventory(this, 54, RivetGui.title(preview == null ? "Autocrafter" : "Recipe preview"));
        RivetGui.frame(inventory);
        if (preview == null) renderBrowse(catalogue, machine);
        else renderPreview();
        inventory.setItem(STORAGE, RivetGui.button(Material.BARREL, "Ingredient storage",
            "Open the barrel to add or remove items.", "Hoppers can also feed ingredients."));
        inventory.setItem(CLOSE, RivetGui.button(Material.BARRIER, "Close"));
        refresh(machine);
    }

    private static List<Recipe> catalogue() {
        List<Recipe> recipes = new ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) recipes.add(recipe);
        });
        recipes.sort(Comparator.comparing((Recipe recipe) -> recipe.getResult().getType().name())
            .thenComparing(recipe -> ((Keyed) recipe).getKey().toString()));
        return recipes;
    }

    static Category category(Recipe recipe) {
        Material material = recipe.getResult().getType();
        return category(((Keyed) recipe).getKey().getNamespace(), material.name(), material.isBlock(), material.isEdible());
    }

    static Category category(String namespace, String name, boolean block, boolean edible) {
        if (!namespace.equals("minecraft")) return Category.CUSTOM;
        if (edible || name.equals("CAKE")) return Category.FOOD;
        if (name.contains("REDSTONE") || name.contains("PISTON") || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE") || name.endsWith("_RAIL")
            || Set.of("RAIL", "LEVER", "REPEATER", "COMPARATOR", "OBSERVER", "TARGET", "TRIPWIRE_HOOK",
                "HOPPER", "DROPPER", "DISPENSER", "CRAFTER", "CRAFTING_TABLE", "FURNACE", "SMOKER",
                "BLAST_FURNACE", "CHEST", "TRAPPED_CHEST", "BARREL", "NOTE_BLOCK", "DAYLIGHT_DETECTOR",
                "BREWING_STAND", "ENCHANTING_TABLE", "ANVIL", "GRINDSTONE", "STONECUTTER", "SMITHING_TABLE",
                "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE", "BEACON", "JUKEBOX").contains(name)) return Category.MACHINERY;
        if (name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
            || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
            || name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.contains("MINECART")
            || name.endsWith("_HARNESS") || Set.of("BOW", "CROSSBOW", "SHIELD", "ARROW", "SPECTRAL_ARROW",
                "FISHING_ROD", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "FLINT_AND_STEEL", "SHEARS",
                "BUCKET", "BRUSH", "COMPASS", "RECOVERY_COMPASS", "CLOCK", "SPYGLASS", "LEAD", "SADDLE",
                "MACE", "BUNDLE", "MAP").contains(name)) return Category.EQUIPMENT;
        return block ? Category.BUILDING : Category.INGREDIENTS;
    }

    static int clampPage(int requested, int count) {
        return Math.max(0, Math.min(requested, Math.max(0, (count - 1) / RivetGui.CONTENT_SLOTS.length)));
    }

    private void renderBrowse(List<Recipe> catalogue, Machine machine) {
        for (Category tab : Category.values()) {
            long count = catalogue.stream().filter(recipe -> tab == Category.ALL || category(recipe) == tab).count();
            ItemStack icon = accent(tab.icon, tab.label, count + " recipes", "Click to browse this category.");
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(tab == category));
            inventory.setItem(tab.ordinal() + 1, icon);
        }
        for (int index = 0; index < RivetGui.CONTENT_SLOTS.length; index++) {
            int offset = page * RivetGui.CONTENT_SLOTS.length + index;
            if (offset >= recipes.size()) break;
            Recipe recipe = recipes.get(offset);
            ItemStack icon = recipe.getResult().clone();
            boolean selected = sameRecipe(recipe, machine.recipe);
            List<Component> lore = new ArrayList<>();
            lore.add(gray(recipe instanceof ShapedRecipe ? "Shaped crafting recipe" : "Shapeless crafting recipe"));
            lore.add(gray("Produces " + icon.getAmount() + " per craft"));
            lore.add(Component.empty());
            lore.add(Component.text(selected ? "Selected output" : "Click to preview ingredients", RivetPalette.SECONDARY));
            icon.editMeta(meta -> {
                meta.lore(lore.stream().map(AutoCrafterMenu::plain).toList());
                meta.setEnchantmentGlintOverride(selected);
            });
            inventory.setItem(RivetGui.CONTENT_SLOTS[index], icon);
        }
        if (recipes.isEmpty()) inventory.setItem(22, RivetGui.button(Material.PAPER, "No matching recipes",
            "Choose another category or clear the item filter."));
        if (page > 0) inventory.setItem(PREVIOUS, RivetGui.button(Material.ARROW, "Previous page"));
        if ((page + 1) * RivetGui.CONTENT_SLOTS.length < recipes.size()) {
            inventory.setItem(NEXT, RivetGui.button(Material.ARROW, "Next page"));
        }
        inventory.setItem(CONFIRM, accent(Material.BOOK, category.label,
            "Page " + (page + 1) + " of " + (clampPage(Integer.MAX_VALUE, recipes.size()) + 1),
            recipes.size() + " matching recipes", "Click an inventory item to find its recipes."));
        inventory.setItem(FILTER, RivetGui.button(Material.COMPASS, filter == null ? "Find an output" : "Clear item filter",
            filter == null ? "Click an item in your inventory to search." : "Showing: " + friendly(filter),
            "Category tabs reset the item filter."));
    }

    private void renderPreview() {
        inventory.setItem(4, accent(Material.CRAFTING_TABLE, "Recipe ingredients",
            "Ghost items show the ingredients; they cannot be taken.",
            "Feed the actual items into ingredient storage."));
        Map<Integer, RecipeChoice> grid = previewGrid(preview);
        for (int slot : GRID) inventory.setItem(slot, RivetGui.pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        grid.forEach((index, choice) -> {
            ItemStack ingredient = choice.getItemStack().clone();
            ingredient.setAmount(1);
            List<Component> lore = new ArrayList<>();
            lore.add(gray("1 ingredient required here"));
            if (choice instanceof RecipeChoice.MaterialChoice materials && materials.getChoices().size() > 1) {
                lore.add(gray("Accepts any of these materials:"));
                materials.getChoices().stream().limit(5).forEach(material -> lore.add(gray(friendly(material))));
                if (materials.getChoices().size() > 5) lore.add(gray("+ " + (materials.getChoices().size() - 5) + " more"));
            } else if (choice instanceof RecipeChoice.ExactChoice) lore.add(gray("Requires an exact matching item."));
            ingredient.editMeta(meta -> meta.lore(lore.stream().map(AutoCrafterMenu::plain).toList()));
            inventory.setItem(GRID[index], ingredient);
        });
        inventory.setItem(23, RivetGui.button(Material.ARROW, "Produces", "One batch per second when supplied."));
        inventory.setItem(24, preview.getResult().clone());
        inventory.setItem(33, RivetGui.button(Material.PAPER, "Recipe details",
            preview instanceof ShapedRecipe ? "Shaped recipe" : "Shapeless recipe",
            "Variant: " + ((Keyed) preview).getKey(), "Storage ingredients can be in any slot."));
        inventory.setItem(PREVIOUS, RivetGui.button(Material.ARROW, "Back to recipes", "Returns to your category and page."));
        inventory.setItem(CONFIRM, accent(Material.CRAFTER, "Craft this recipe",
            "Select this output and start the autocrafter.", "Consumes items from ingredient storage."));
    }

    static Map<Integer, RecipeChoice> previewGrid(Recipe recipe) {
        Map<Integer, RecipeChoice> result = new LinkedHashMap<>();
        if (recipe instanceof ShapedRecipe shaped) {
            result.putAll(shapedGrid(shaped.getShape(), shaped.getChoiceMap()));
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = shapeless.getChoiceList();
            for (int i = 0; i < choices.size(); i++) result.put(i, choices.get(i));
        }
        return result;
    }

    static <T> Map<Integer, T> shapedGrid(String[] shape, Map<Character, T> choices) {
        Map<Integer, T> result = new LinkedHashMap<>();
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                T choice = choices.get(shape[row].charAt(col));
                if (choice != null) result.put(row * 3 + col, choice);
            }
        }
        return result;
    }

    void refresh(Machine machine) {
        boolean paused = machine.status == Status.PAUSED;
        inventory.setItem(PAUSE, RivetGui.button(paused ? Material.LEVER : Material.REDSTONE_TORCH,
            paused ? "Resume crafting" : "Pause crafting", "Keeps your selected recipe."));
        ItemStack selected = machine.recipe == null ? RivetGui.button(Material.CRAFTING_TABLE, "No output selected",
            "Choose a recipe to start crafting.") : machine.recipe.getResult().clone();
        if (machine.recipe != null) selected.editMeta(meta -> meta.lore(List.of(plain(gray("Current output")),
            plain(gray("Click to view this recipe.")))));
        inventory.setItem(SELECTED, selected);
        inventory.setItem(STATUS, accent(machine.status.icon, machine.status.label, machine.status.hint,
            "Output side: " + machine.facing, "Storage: " + machine.occupied + "/27 slots used"));
    }

    static Component outputName(Recipe recipe) {
        if (recipe == null) return Component.text("No recipe selected", NamedTextColor.GRAY);
        ItemStack result = recipe.getResult();
        Component custom = result.getItemMeta().displayName();
        Component name = custom == null ? Component.translatable(result.translationKey()) : custom;
        return Component.text(result.getAmount() + "x ").append(name);
    }

    static Component hologram(Machine machine) {
        return Component.text("Autocrafter", RivetPalette.SECONDARY).decorate(TextDecoration.BOLD)
            .append(Component.newline()).append(outputName(machine.recipe).color(RivetPalette.PRIMARY)
                .decoration(TextDecoration.BOLD, false))
            .append(Component.newline()).append(Component.text(machine.status.label, NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false));
    }

    private static ItemStack accent(Material icon, String name, String... lore) {
        return RivetGui.item(icon, Component.text(name, RivetPalette.SECONDARY),
            Arrays.stream(lore).map(AutoCrafterMenu::gray).toList());
    }
    private static Component gray(String text) { return Component.text(text, NamedTextColor.GRAY); }
    private static Component plain(Component text) { return text.decoration(TextDecoration.ITALIC, false); }
    private static String friendly(Material material) {
        String value = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private static boolean sameRecipe(Recipe first, Recipe second) {
        return first instanceof Keyed a && second instanceof Keyed b && a.getKey().equals(b.getKey());
    }
    @Override public Inventory getInventory() { return inventory; }
}
