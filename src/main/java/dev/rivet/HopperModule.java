package dev.rivet;

import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class HopperModule implements Listener, RivetModule {
    private static final int DEFAULT_TRANSFER_COOLDOWN = 2;

    private final RivetPlugin plugin;
    private final YamlConfiguration settings;
    // Busy hoppers move several items per tick; one task per tick handles all of them.
    private final TickBatch<Block> pending = new TickBatch<>();
    private boolean enabled;
    private int transferCooldown;

    HopperModule(RivetPlugin plugin) {
        this.plugin = plugin;
        settings = plugin.settings("gameplay");
        reload();
    }

    // Always registered so /rivet reload can switch hoppers on or off without a restart.
    @Override
    public void reload() {
        enabled = settings.getBoolean("hoppers.enabled", true);
        transferCooldown = transferCooldown(settings.getInt(
            "hoppers.transfer-cooldown-ticks", DEFAULT_TRANSFER_COOLDOWN));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (enabled) {
            accelerate(event.getInitiator());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(InventoryPickupItemEvent event) {
        if (enabled) {
            accelerate(event.getInventory());
        }
    }

    private void accelerate(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (!(holder instanceof Hopper hopper)) {
            return;
        }
        if (pending.add(hopper.getBlock())) {
            plugin.getServer().getScheduler().runTask(plugin, this::flush);
        }
    }

    private void flush() {
        List<Block> blocks = pending.drain();
        if (enabled) {
            blocks.forEach(this::shortenCooldown);
        }
    }

    private void shortenCooldown(Block block) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
            || !(block.getState(false) instanceof Hopper hopper)) {
            return;
        }
        hopper.setTransferCooldown(Math.min(hopper.getTransferCooldown(), transferCooldown));
    }

    static int transferCooldown(int configured) {
        return Math.max(1, configured);
    }

    // Deduplicating queue that requests a flush only on its empty-to-pending transition.
    static final class TickBatch<T> {
        private final Set<T> entries = new LinkedHashSet<>();
        private boolean scheduled;

        boolean add(T entry) {
            entries.add(entry);
            if (scheduled) {
                return false;
            }
            scheduled = true;
            return true;
        }

        List<T> drain() {
            List<T> drained = new ArrayList<>(entries);
            entries.clear();
            scheduled = false;
            return drained;
        }
    }
}
