package dev.rivet;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

final class AutoCrafter implements Listener {
    private final RivetPlugin plugin;
    private final NamespacedKey marker;
    private final NamespacedKey selection;
    private final Set<Location> loaded = new HashSet<>();
    private final BukkitTask task;
    private boolean transferringOutput;

    AutoCrafter(RivetPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "autocrafter");
        selection = new NamespacedKey(plugin, "autocrafter_recipe");
        ShapedRecipe recipe = new ShapedRecipe(marker, item());
        recipe.shape("IRI", "RCR", "IBI");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('C', Material.CRAFTING_TABLE);
        recipe.setIngredient('B', Material.BARREL);
        Bukkit.addRecipe(recipe);
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipe(marker));
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) scan(chunk);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private ItemStack item() {
        ItemStack item = RivetGui.button(Material.BARREL, "Autocrafter",
            "Right-click to select a recipe.", "Feed ingredients with hoppers.",
            "Output leaves the barrel's facing side.");
        item.editMeta(meta -> meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    private boolean isCrafter(Block block) {
        return block.getState(false) instanceof Barrel barrel
            && barrel.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (!event.getItemInHand().getPersistentDataContainer().has(marker, PersistentDataType.BYTE)
            || !(event.getBlockPlaced().getState() instanceof Barrel barrel)) return;
        barrel.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        barrel.update();
        loaded.add(barrel.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || !isCrafter(event.getClickedBlock())) return;
        // Sneaking with a block must still allow attaching hoppers.
        if (event.getPlayer().isSneaking() && event.hasItem() && event.getItem().getType().isBlock()) return;
        event.setCancelled(true);
        open(event.getPlayer(), event.getClickedBlock(), 0, null);
    }

    private List<Recipe> recipes(Material filter) {
        List<Recipe> result = new ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if ((recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)
                && (filter == null || recipe.getResult().getType() == filter)) result.add(recipe);
        });
        result.sort(Comparator.comparing(recipe -> ((Keyed) recipe).getKey().toString()));
        return result;
    }

    private void open(Player player, Block block, int requestedPage, Material filter) {
        List<Recipe> recipes = recipes(filter);
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (recipes.size() - 1) / 45)));
        Menu menu = new Menu(block.getLocation(), recipes, page, filter);
        menu.inventory = Bukkit.createInventory(menu, 54, RivetGui.title("Autocrafter recipes " + (page + 1)));
        for (int slot = 0; slot < 45 && page * 45 + slot < recipes.size(); slot++) {
            Recipe recipe = recipes.get(page * 45 + slot);
            ItemStack icon = recipe.getResult().clone();
            icon.editMeta(meta -> meta.lore(List.of(net.kyori.adventure.text.Component.text(
                "Select " + ((Keyed) recipe).getKey()))));
            menu.inventory.setItem(slot, icon);
        }
        menu.inventory.setItem(45, RivetGui.button(Material.ARROW, "Previous page"));
        menu.inventory.setItem(47, RivetGui.button(Material.BARRIER, "Pause crafting"));
        menu.inventory.setItem(49, RivetGui.button(Material.CHEST, "Ingredient storage",
            "Click an item in your inventory to filter recipes.",
            "Selected: " + ((Barrel) block.getState()).getPersistentDataContainer()
                .getOrDefault(selection, PersistentDataType.STRING, "none")));
        menu.inventory.setItem(51, RivetGui.button(Material.PAPER, "Show all recipes"));
        menu.inventory.setItem(53, RivetGui.button(Material.ARROW, "Next page"));
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !accessible(player, menu.location)) return;
        Block block = menu.location.getBlock();
        int slot = event.getRawSlot();
        if (slot >= 54 && event.getCurrentItem() != null) {
            open(player, block, 0, event.getCurrentItem().getType());
        } else if (slot >= 0 && slot < 45 && menu.page * 45 + slot < menu.recipes.size()) {
            Barrel barrel = (Barrel) block.getState();
            String key = ((Keyed) menu.recipes.get(menu.page * 45 + slot)).getKey().toString();
            barrel.getPersistentDataContainer().set(selection, PersistentDataType.STRING, key);
            barrel.update();
            open(player, block, menu.page, menu.filter);
        } else if (slot == 45 || slot == 53) {
            open(player, block, menu.page + (slot == 45 ? -1 : 1), menu.filter);
        } else if (slot == 49) {
            player.openInventory(((Barrel) block.getState()).getInventory());
        } else if (slot == 51) {
            open(player, block, 0, null);
        } else if (slot == 47) {
            Barrel barrel = (Barrel) block.getState();
            barrel.getPersistentDataContainer().remove(selection);
            barrel.update();
            open(player, block, menu.page, menu.filter);
        }
    }

    private boolean accessible(Player player, Location location) {
        return player.getWorld().equals(location.getWorld()) && location.isChunkLoaded()
            && player.getLocation().distanceSquared(location) <= 64 && isCrafter(location.getBlock());
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void extract(InventoryMoveItemEvent event) {
        Location source = event.getSource().getLocation();
        if (!transferringOutput && source != null && isCrafter(source.getBlock())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void drops(BlockDropItemEvent event) {
        if (!(event.getBlockState() instanceof Barrel barrel)
            || !barrel.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) return;
        ItemStack ordinaryDrop = new ItemStack(Material.BARREL);
        if (barrel.customName() != null) ordinaryDrop.editMeta(meta -> meta.displayName(barrel.customName()));
        event.getItems().stream().filter(entity -> entity.getItemStack().isSimilar(ordinaryDrop))
            .findFirst().ifPresent(entity -> entity.setItemStack(item()));
        loaded.remove(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true) public void extend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isCrafter)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void retract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isCrafter)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void explode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isCrafter);
    }
    @EventHandler(ignoreCancelled = true) public void explode(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(this::isCrafter);
    }
    @EventHandler public void load(ChunkLoadEvent event) { scan(event.getChunk()); }
    @EventHandler public void unload(ChunkUnloadEvent event) {
        loaded.removeIf(location -> location.getWorld().equals(event.getWorld())
            && location.getBlockX() >> 4 == event.getChunk().getX()
            && location.getBlockZ() >> 4 == event.getChunk().getZ());
    }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipe(marker); }

    private void scan(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Barrel barrel && barrel.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) {
                loaded.add(state.getLocation());
            }
        }
    }

    private void tick() {
        for (Location location : List.copyOf(loaded)) {
            if (!location.isChunkLoaded()) continue;
            if (!isCrafter(location.getBlock())) { loaded.remove(location); continue; }
            craft((Barrel) location.getBlock().getState());
        }
    }

    private void craft(Barrel barrel) {
        String selected = barrel.getPersistentDataContainer().get(selection, PersistentDataType.STRING);
        NamespacedKey key = selected == null ? null : NamespacedKey.fromString(selected);
        Recipe recipe = key == null ? null : Bukkit.getRecipe(key);
        List<RecipeChoice> choices = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, RecipeChoice> mapping = shaped.getChoiceMap();
            for (String row : shaped.getShape()) {
                for (char symbol : row.toCharArray()) {
                    RecipeChoice choice = mapping.get(symbol);
                    if (choice != null) choices.add(choice);
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) choices.addAll(shapeless.getChoiceList());
        if (choices.isEmpty()) return;
        Inventory input = barrel.getInventory();
        ItemStack[] contents = Arrays.stream(input.getContents())
            .map(stack -> stack == null ? null : stack.clone()).toArray(ItemStack[]::new);
        boolean[][] matches = new boolean[choices.size()][contents.length];
        int[] amounts = new int[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) continue;
            amounts[slot] = stack.getAmount();
            for (int choice = 0; choice < choices.size(); choice++) {
                // Preserve named, enchanted and plugin items unless the recipe explicitly requires them.
                matches[choice][slot] = (!stack.hasItemMeta() || choices.get(choice) instanceof RecipeChoice.ExactChoice)
                    && choices.get(choice).test(stack);
            }
        }
        int[] used = CraftingPlan.allocate(matches, amounts);
        if (used == null) return;
        List<ItemStack> products = new ArrayList<>();
        products.add(recipe.getResult().clone());
        for (int slot = 0; slot < contents.length; slot++) {
            if (used[slot] == 0) continue;
            Material remainder = contents[slot].getType().getCraftingRemainingItem();
            if (remainder != null) products.add(new ItemStack(remainder, used[slot]));
        }
        Block output = barrel.getBlock().getRelative(((Directional) barrel.getBlockData()).getFacing());
        if (!output.getLocation().isChunkLoaded()) return;
        BlockState outputState = output.getState();
        boolean storage = outputState instanceof org.bukkit.block.Chest || outputState instanceof Barrel
            || outputState instanceof org.bukkit.block.Hopper || outputState instanceof org.bukkit.block.Dropper
            || outputState instanceof org.bukkit.block.Dispenser || outputState instanceof org.bukkit.block.ShulkerBox;
        Inventory destination = storage ? ((Container) outputState).getInventory() : null;
        if (destination != null && isCrafter(output)) return;
        ItemStack[] result = null;
        if (destination != null) {
            for (ItemStack product : products) {
                InventoryMoveItemEvent transfer = new InventoryMoveItemEvent(input, product.clone(), destination, true);
                transferringOutput = true;
                try {
                    Bukkit.getPluginManager().callEvent(transfer);
                } finally {
                    transferringOutput = false;
                }
                if (transfer.isCancelled() || !product.equals(transfer.getItem())) return;
            }
            Inventory simulated = Bukkit.createInventory(null, ((destination.getSize() + 8) / 9) * 9);
            // Restrict insertion to actual destination slots, including five-slot hoppers.
            for (int slot = destination.getSize(); slot < simulated.getSize(); slot++) {
                simulated.setItem(slot, new ItemStack(Material.BARRIER, 64));
            }
            for (int slot = 0; slot < destination.getSize(); slot++) {
                ItemStack stack = destination.getItem(slot);
                simulated.setItem(slot, stack == null ? null : stack.clone());
            }
            simulated.setMaxStackSize(destination.getMaxStackSize());
            if (!simulated.addItem(products.toArray(ItemStack[]::new)).isEmpty()) return;
            result = Arrays.copyOf(simulated.getContents(), destination.getSize());
        } else if (!output.isPassable()) return;
        if (!isCrafter(barrel.getBlock()) || !Arrays.equals(contents, input.getContents())) return;
        for (int slot = 0; slot < contents.length; slot++) {
            if (used[slot] > 0) {
                contents[slot] = contents[slot].clone();
                contents[slot].setAmount(contents[slot].getAmount() - used[slot]);
            }
        }
        input.setContents(contents);
        if (destination != null) destination.setContents(result);
        else for (ItemStack product : products) output.getWorld().dropItem(output.getLocation().add(.5, .5, .5), product);
    }

    void shutdown() {
        task.cancel();
        Bukkit.removeRecipe(marker);
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) player.closeInventory();
        });
    }

    private static final class Menu implements InventoryHolder {
        private final Location location;
        private final List<Recipe> recipes;
        private final int page;
        private final Material filter;
        private Inventory inventory;
        private Menu(Location location, List<Recipe> recipes, int page, Material filter) {
            this.location = location; this.recipes = recipes; this.page = page; this.filter = filter;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
