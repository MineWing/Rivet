package dev.rivet;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import dev.rivet.AutoCrafterMenu.Category;
import dev.rivet.AutoCrafterMenu.Status;
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
    private final NamespacedKey paused;
    private final Map<Location, TextDisplay> holograms = new HashMap<>();
    private final Map<Location, Status> statuses = new HashMap<>();
    private final Set<Location> loaded = new HashSet<>();
    private final BukkitTask task;
    private boolean transferringOutput;

    AutoCrafter(RivetPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "autocrafter");
        selection = new NamespacedKey(plugin, "autocrafter_recipe");
        paused = new NamespacedKey(plugin, "autocrafter_paused");
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
        open(event.getPlayer(), event.getClickedBlock(), Category.ALL, null, 0, null);
    }

    private void open(Player player, Block block, Category category, Material filter, int page, Recipe preview) {
        Barrel barrel = (Barrel) block.getState();
        player.openInventory(new AutoCrafterMenu(block.getLocation(), category, filter, page, preview,
            machine(barrel)).getInventory());
    }

    private Recipe selectedRecipe(Barrel barrel) {
        String selected = barrel.getPersistentDataContainer().get(selection, PersistentDataType.STRING);
        NamespacedKey key = selected == null ? null : NamespacedKey.fromString(selected);
        return key == null ? null : Bukkit.getRecipe(key);
    }

    private AutoCrafterMenu.Machine machine(Barrel barrel) {
        Recipe recipe = selectedRecipe(barrel);
        Status status = statuses.getOrDefault(barrel.getLocation(), Status.WAITING);
        if (barrel.getPersistentDataContainer().has(paused, PersistentDataType.BYTE)) status = Status.PAUSED;
        else if (recipe == null) status = barrel.getPersistentDataContainer().has(selection, PersistentDataType.STRING)
            ? Status.INVALID_RECIPE : Status.UNSELECTED;
        int occupied = (int) Arrays.stream(barrel.getInventory().getContents())
            .filter(item -> item != null && !item.getType().isAir()).count();
        String facing = ((Directional) barrel.getBlockData()).getFacing().name().toLowerCase(Locale.ROOT);
        return new AutoCrafterMenu.Machine(recipe, status, facing, occupied);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof AutoCrafterMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || menu.transitioning) return;
        int slot = event.getRawSlot();
        Material filter = slot >= 54 && event.getCurrentItem() != null ? event.getCurrentItem().getType() : null;
        // Bukkit requires opening another inventory after InventoryClickEvent has finished.
        menu.transitioning = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            menu.transitioning = false;
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) != menu) return;
            if (!accessible(player, menu.location) || player.getGameMode() == GameMode.SPECTATOR) {
                player.closeInventory();
                return;
            }
            Block block = menu.location.getBlock();
            Barrel barrel = (Barrel) block.getState();
            if (filter != null && !filter.isAir()) {
                open(player, block, Category.ALL, filter, 0, null);
            } else if (slot == AutoCrafterMenu.CLOSE) {
                player.closeInventory();
            } else if (slot == AutoCrafterMenu.STORAGE) {
                player.openInventory(barrel.getInventory());
            } else if (slot == AutoCrafterMenu.PAUSE) {
                if (selectedRecipe(barrel) != null) {
                    if (barrel.getPersistentDataContainer().has(paused, PersistentDataType.BYTE)) {
                        barrel.getPersistentDataContainer().remove(paused);
                        statuses.put(menu.location, Status.WAITING);
                    } else barrel.getPersistentDataContainer().set(paused, PersistentDataType.BYTE, (byte) 1);
                    barrel.update(false, false);
                    refresh(block);
                }
            } else if (slot == AutoCrafterMenu.SELECTED && selectedRecipe(barrel) != null) {
                open(player, block, menu.category, menu.filter, menu.page, selectedRecipe(barrel));
            } else if (menu.preview != null) {
                if (slot == AutoCrafterMenu.PREVIOUS) open(player, block, menu.category, menu.filter, menu.page, null);
                else if (slot == AutoCrafterMenu.CONFIRM) {
                    NamespacedKey key = ((Keyed) menu.preview).getKey();
                    Recipe recipe = Bukkit.getRecipe(key);
                    if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) return;
                    barrel.getPersistentDataContainer().set(selection, PersistentDataType.STRING, key.toString());
                    barrel.getPersistentDataContainer().remove(paused);
                    barrel.update(false, false);
                    statuses.put(menu.location, Status.WAITING);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .5f, 1.2f);
                    refresh(block);
                    open(player, block, menu.category, menu.filter, menu.page, null);
                }
            } else if (slot >= 1 && slot <= Category.values().length) {
                open(player, block, Category.values()[slot - 1], null, 0, null);
            } else if (slot == AutoCrafterMenu.PREVIOUS || slot == AutoCrafterMenu.NEXT) {
                open(player, block, menu.category, menu.filter,
                    menu.page + (slot == AutoCrafterMenu.PREVIOUS ? -1 : 1), null);
            } else if (slot == AutoCrafterMenu.FILTER) {
                open(player, block, menu.category, null, 0, null);
            } else {
                int index = RivetGui.contentIndex(slot);
                int offset = menu.page * RivetGui.CONTENT_SLOTS.length + index;
                if (index >= 0 && offset < menu.recipes.size()) {
                    open(player, block, menu.category, menu.filter, menu.page, menu.recipes.get(offset));
                }
            }
        });
    }

    private boolean accessible(Player player, Location location) {
        return player.getWorld().equals(location.getWorld()) && location.isChunkLoaded()
            && player.getLocation().distanceSquared(location) <= 64 && isCrafter(location.getBlock());
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AutoCrafterMenu) event.setCancelled(true);
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
        forget(event.getBlock().getLocation());
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
        List.copyOf(loaded).stream().filter(location -> location.getWorld().equals(event.getWorld())
            && location.getBlockX() >> 4 == event.getChunk().getX()
            && location.getBlockZ() >> 4 == event.getChunk().getZ()).forEach(this::forget);
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
            if (!isCrafter(location.getBlock())) { forget(location); continue; }
            statuses.put(location, craft((Barrel) location.getBlock().getState()));
            refresh(location.getBlock());
        }
    }

    private Status craft(Barrel barrel) {
        if (barrel.getPersistentDataContainer().has(paused, PersistentDataType.BYTE)) return Status.PAUSED;
        Recipe recipe = selectedRecipe(barrel);
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
        if (choices.isEmpty()) return barrel.getPersistentDataContainer().has(selection, PersistentDataType.STRING)
            ? Status.INVALID_RECIPE : Status.UNSELECTED;
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
        if (used == null) return Status.WAITING;
        List<ItemStack> products = new ArrayList<>();
        products.add(recipe.getResult().clone());
        for (int slot = 0; slot < contents.length; slot++) {
            if (used[slot] == 0) continue;
            Material remainder = contents[slot].getType().getCraftingRemainingItem();
            if (remainder != null) products.add(new ItemStack(remainder, used[slot]));
        }
        Block output = barrel.getBlock().getRelative(((Directional) barrel.getBlockData()).getFacing());
        if (!output.getLocation().isChunkLoaded()) return Status.OUTPUT_UNLOADED;
        BlockState outputState = output.getState();
        boolean storage = outputState instanceof org.bukkit.block.Chest || outputState instanceof Barrel
            || outputState instanceof org.bukkit.block.Hopper || outputState instanceof org.bukkit.block.Dropper
            || outputState instanceof org.bukkit.block.Dispenser || outputState instanceof org.bukkit.block.ShulkerBox;
        Inventory destination = storage ? ((Container) outputState).getInventory() : null;
        if (destination != null && isCrafter(output)) return Status.OUTPUT_BLOCKED;
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
                if (transfer.isCancelled() || !product.equals(transfer.getItem())) return Status.TRANSFER_DENIED;
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
            if (!simulated.addItem(products.toArray(ItemStack[]::new)).isEmpty()) return Status.OUTPUT_FULL;
            result = Arrays.copyOf(simulated.getContents(), destination.getSize());
        } else if (!output.isPassable()) return Status.OUTPUT_BLOCKED;
        if (!isCrafter(barrel.getBlock()) || !Arrays.equals(contents, input.getContents())) return Status.INVENTORY_CHANGED;
        for (int slot = 0; slot < contents.length; slot++) {
            if (used[slot] > 0) {
                contents[slot] = contents[slot].clone();
                contents[slot].setAmount(contents[slot].getAmount() - used[slot]);
            }
        }
        input.setContents(contents);
        if (destination != null) destination.setContents(result);
        else for (ItemStack product : products) output.getWorld().dropItem(output.getLocation().add(.5, .5, .5), product);
        return Status.CRAFTING;
    }

    private void refresh(Block block) {
        if (!isCrafter(block)) return;
        AutoCrafterMenu.Machine machine = machine((Barrel) block.getState());
        Location location = block.getLocation();
        TextDisplay display = holograms.get(location);
        if (display == null || !display.isValid()) {
            display = location.getWorld().spawn(location.clone().add(.5, 1.65, .5), TextDisplay.class, text -> {
                text.setBillboard(Display.Billboard.CENTER);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                text.setBackgroundColor(Color.fromARGB(150, 18, 18, 18));
                text.setShadowed(false);
                text.setPersistent(false);
                text.setInvulnerable(true);
                text.setGravity(false);
                text.setLineWidth(240);
                text.setViewRange(.4f);
            });
            holograms.put(location, display);
        }
        net.kyori.adventure.text.Component text = AutoCrafterMenu.hologram(machine);
        if (!text.equals(display.text())) display.text(text);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof AutoCrafterMenu menu
                && menu.location.equals(location)) menu.refresh(machine);
        }
    }

    private void forget(Location location) {
        loaded.remove(location);
        statuses.remove(location);
        TextDisplay display = holograms.remove(location);
        if (display != null) display.remove();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof AutoCrafterMenu menu
                && menu.location.equals(location)) player.closeInventory();
        }
    }

    void shutdown() {
        task.cancel();
        List.copyOf(loaded).forEach(this::forget);
        Bukkit.removeRecipe(marker);
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof AutoCrafterMenu) player.closeInventory();
        });
    }

}
