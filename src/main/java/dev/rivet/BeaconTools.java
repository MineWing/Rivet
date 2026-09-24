package dev.rivet;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

final class BeaconTools implements Listener {
    private static final List<Material> BASES = List.of(Material.IRON_BLOCK, Material.GOLD_BLOCK,
        Material.EMERALD_BLOCK, Material.DIAMOND_BLOCK, Material.NETHERITE_BLOCK);
    private final RivetPlugin plugin;
    private final NamespacedKey makerKey;

    BeaconTools(RivetPlugin plugin) {
        this.plugin = plugin;
        makerKey = new NamespacedKey(plugin, "beacon_maker");
        ShapedRecipe recipe = new ShapedRecipe(makerKey, maker());
        recipe.shape("PPP", "PEP", "PPP");
        recipe.setIngredient('P', Material.ENDER_PEARL);
        recipe.setIngredient('E', Material.EGG);
        Bukkit.addRecipe(recipe);
        Bukkit.getOnlinePlayers().forEach(player -> player.discoverRecipe(makerKey));
    }

    private ItemStack maker() {
        ItemStack item = RivetGui.button(Material.EGG, "Beacon maker",
            "Right-click a block to build a beacon pyramid.",
            "Requires a beacon and mineral blocks in your inventory.",
            "Reusable. Sneak-right-click a beacon to dismantle it.");
        item.editMeta(meta -> meta.getPersistentDataContainer().set(makerKey, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    private boolean isMaker(ItemStack item) {
        return item != null && item.getPersistentDataContainer().has(makerKey, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            if (isMaker(event.getItem())) event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null
            && clicked.getType() == Material.BEACON && player.isSneaking()) {
            event.setCancelled(true);
            Menu menu = new Menu(clicked.getLocation(), true, clicked.getLocation());
            menu.expected = pyramid(clicked);
            open(player, menu);
        } else if (isMaker(event.getItem())
            && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            event.setCancelled(true);
            if (clicked != null) open(player, new Menu(clicked.getRelative(event.getBlockFace()).getLocation(), false,
                clicked.getLocation()));
        }
    }

    private void open(Player player, Menu menu) {
        menu.inventory = Bukkit.createInventory(menu, 27,
            RivetGui.title(menu.destroy ? "Dismantle beacon?" : "Beacon maker"));
        if (menu.destroy) {
            menu.inventory.setItem(13, RivetGui.button(Material.TNT, "Confirm dismantling",
                "Returns the beacon and " + (menu.expected.size() - 1) + " pyramid blocks.",
                "Only complete pyramid layers are included."));
        } else {
            for (int level = 1; level <= 4; level++) {
                menu.inventory.setItem(9 + level, RivetGui.button(Material.BEACON, "Build level " + level,
                    "Requires 1 beacon and " + baseCount(level) + " " + menu.material.name().toLowerCase(Locale.ROOT) + ".",
                    "The bottom layer is centered above the clicked block."));
            }
            menu.inventory.setItem(16, RivetGui.button(menu.material, "Change pyramid material",
                "Current: " + menu.material.name().toLowerCase(Locale.ROOT)));
        }
        menu.inventory.setItem(22, RivetGui.button(Material.BARRIER, "Cancel"));
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 22) { player.closeInventory(); return; }
        if (!near(player, menu.origin) || player.getGameMode() == GameMode.SPECTATOR
            || player.getGameMode() == GameMode.ADVENTURE) { player.closeInventory(); return; }
        if (menu.destroy && event.getRawSlot() == 13) {
            player.closeInventory();
            destroy(player, menu);
        } else if (!menu.destroy && event.getRawSlot() == 16) {
            menu.material = BASES.get((BASES.indexOf(menu.material) + 1) % BASES.size());
            open(player, menu);
        } else if (!menu.destroy && event.getRawSlot() >= 10 && event.getRawSlot() <= 13) {
            player.closeInventory();
            build(player, menu, event.getRawSlot() - 9);
        }
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipe(makerKey); }

    private boolean near(Player player, Location origin) {
        return origin.isChunkLoaded() && player.getWorld().equals(origin.getWorld())
            && player.getLocation().distanceSquared(origin) <= 100;
    }

    static int baseCount(int levels) {
        if (levels < 1 || levels > 4) throw new IllegalArgumentException("Beacon level must be 1 to 4");
        int count = 0;
        for (int radius = 1; radius <= levels; radius++) count += (2 * radius + 1) * (2 * radius + 1);
        return count;
    }

    private void build(Player player, Menu menu, int levels) {
        if (!isMaker(player.getInventory().getItemInMainHand())) {
            tell(player, "Hold your beacon maker to build."); return;
        }
        int required = baseCount(levels);
        if (plainCount(player, Material.BEACON) < 1 || plainCount(player, menu.material) < required) {
            tell(player, "You need 1 beacon and " + required + " " + menu.material.name().toLowerCase(Locale.ROOT)
                + " in your inventory."); return;
        }
        Location top = menu.origin.clone().add(0, levels, 0);
        List<Location> locations = new ArrayList<>();
        locations.add(top);
        for (int radius = 1; radius <= levels; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) locations.add(top.clone().add(x, -radius, z));
            }
        }
        for (Location location : locations) {
            if (!location.isChunkLoaded() || location.getY() < location.getWorld().getMinHeight()
                || location.getY() >= location.getWorld().getMaxHeight()
                || !location.getWorld().getWorldBorder().isInside(location)
                || !location.getBlock().isEmpty()
                || !location.getWorld().getNearbyEntities(location.clone().add(.5, .5, .5), .5, .5, .5,
                    entity -> entity instanceof org.bukkit.entity.LivingEntity).isEmpty()) {
                tell(player, "The whole pyramid needs empty space inside the world border, with no players or mobs in it.");
                return;
            }
        }
        List<BlockState> previous = locations.stream().map(location -> location.getBlock().getState()).toList();
        boolean accepted = false;
        try {
            for (int i = 0; i < locations.size(); i++) {
                locations.get(i).getBlock().setType(i == 0 ? Material.BEACON : menu.material, false);
            }
            BlockMultiPlaceEvent placement = new BlockMultiPlaceEvent(previous, menu.clicked.getBlock(),
                player.getInventory().getItemInMainHand(), player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placement);
            if (placement.isCancelled() || !placement.canBuild()) {
                tell(player, "You cannot build a beacon here."); return;
            }
            // Recheck after protection plugins have handled the placement event.
            if (plainCount(player, Material.BEACON) < 1 || plainCount(player, menu.material) < required) return;
            consume(player, Material.BEACON, 1);
            consume(player, menu.material, required);
            accepted = true;
        } finally {
            if (!accepted) previous.forEach(state -> state.update(true, false));
        }
        animate(locations, false);
        tell(player, "Built a level " + levels + " beacon.");
    }

    private Map<Location, Material> pyramid(Block beacon) {
        Map<Location, Material> blocks = new LinkedHashMap<>();
        blocks.put(beacon.getLocation(), Material.BEACON);
        for (int radius = 1; radius <= 4; radius++) {
            Map<Location, Material> layer = new LinkedHashMap<>();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Location location = beacon.getLocation().add(x, -radius, z);
                    if (!location.isChunkLoaded() || location.getY() < beacon.getWorld().getMinHeight()) return blocks;
                    Material material = location.getBlock().getType();
                    if (!BASES.contains(material)) return blocks;
                    layer.put(location, material);
                }
            }
            blocks.putAll(layer);
        }
        return blocks;
    }

    private void destroy(Player player, Menu menu) {
        for (Map.Entry<Location, Material> entry : menu.expected.entrySet()) {
            if (!entry.getKey().isChunkLoaded() || entry.getKey().getBlock().getType() != entry.getValue()) {
                tell(player, "The beacon changed. Open its confirmation again."); return;
            }
        }
        for (Location location : menu.expected.keySet()) {
            BlockBreakEvent breaking = new BlockBreakEvent(location.getBlock(), player);
            Bukkit.getPluginManager().callEvent(breaking);
            if (breaking.isCancelled()) { tell(player, "You cannot dismantle this beacon here."); return; }
        }
        // Event listeners may have changed a block. Never refund a changed pyramid.
        if (menu.expected.entrySet().stream().anyMatch(entry -> entry.getKey().getBlock().getType() != entry.getValue())) return;
        Map<Material, Integer> refund = new EnumMap<>(Material.class);
        menu.expected.forEach((location, material) -> {
            location.getBlock().setType(Material.AIR, false);
            refund.merge(material, 1, Integer::sum);
        });
        refund.forEach((material, amount) -> {
            while (amount > 0) {
                int size = Math.min(amount, material.getMaxStackSize());
                player.getInventory().addItem(new ItemStack(material, size)).values().forEach(item ->
                    player.getWorld().dropItem(player.getLocation(), item));
                amount -= size;
            }
        });
        animate(new ArrayList<>(menu.expected.keySet()), true);
        tell(player, "Beacon dismantled. Returned its blocks; anything that did not fit was dropped at your feet.");
    }

    private int plainCount(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material && !stack.hasItemMeta()) total += stack.getAmount();
        }
        return total;
    }

    private void consume(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() != material || stack.hasItemMeta()) continue;
            int take = Math.min(stack.getAmount(), amount);
            stack.setAmount(stack.getAmount() - take);
            amount -= take;
            if (amount == 0) break;
        }
        player.getInventory().setStorageContents(contents);
    }

    private void animate(List<Location> blocks, boolean destroying) {
        List<Location> ordered = new ArrayList<>(blocks);
        ordered.sort(Comparator.comparingDouble(Location::getY));
        if (destroying) Collections.reverse(ordered);
        new BukkitRunnable() {
            private int index;
            @Override public void run() {
                for (int step = 0; step < 8 && index < ordered.size(); step++, index++) {
                    Location location = ordered.get(index).clone().add(.5, .5, .5);
                    if (!location.isChunkLoaded()) continue;
                    location.getWorld().spawnParticle(destroying ? Particle.REVERSE_PORTAL : Particle.END_ROD,
                        location, 12, .35, .35, .35, .04);
                }
                if (index >= ordered.size()) {
                    Location beacon = blocks.getFirst().clone().add(.5, .5, .5);
                    if (beacon.isChunkLoaded()) {
                        beacon.getWorld().playSound(beacon, destroying ? Sound.BLOCK_BEACON_DEACTIVATE
                            : Sound.BLOCK_BEACON_ACTIVATE, 1, destroying ? .7f : 1.3f);
                        beacon.getWorld().spawnParticle(Particle.FIREWORK, beacon, 60, .7, 1.5, .7, .08);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private void tell(Player player, String text) {
        player.sendMessage(net.kyori.adventure.text.Component.text(text, RivetPalette.PRIMARY));
    }

    void shutdown() {
        Bukkit.removeRecipe(makerKey);
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) player.closeInventory();
        });
    }

    private static final class Menu implements InventoryHolder {
        private final Location origin;
        private final boolean destroy;
        private final Location clicked;
        private Material material = Material.IRON_BLOCK;
        private Map<Location, Material> expected = Map.of();
        private Inventory inventory;
        private Menu(Location origin, boolean destroy, Location clicked) {
            this.origin = origin; this.destroy = destroy; this.clicked = clicked;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
