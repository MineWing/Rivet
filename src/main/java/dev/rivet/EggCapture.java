package dev.rivet;

import com.google.common.base.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

final class EggCapture implements Listener {
    private static final MiniMessage MM = RivetMiniMessage.miniMessage();
    private static final int ANIMATION_TICKS = 28;

    /** Used when the setting is missing entirely; an explicit empty list blocks nothing. */
    static final Set<String> DEFAULT_BLOCKED_TYPES = Set.of(
        "WITHER", "ENDER_DRAGON", "WARDEN", "ELDER_GUARDIAN", "VILLAGER", "ZOMBIE_VILLAGER",
        "WANDERING_TRADER", "IRON_GOLEM", "PIGLIN_BRUTE", "SHULKER", "EVOKER", "GIANT",
        "ILLUSIONER");

    private final RivetPlugin plugin;
    private final YamlConfiguration settings;
    private final Map<UUID, Capture> captures = new HashMap<>();
    private final NamespacedKey capturedKey;
    private final Set<String> blockedTypes;
    private final Set<String> allowedTypes;

    EggCapture(RivetPlugin plugin) {
        this.plugin = plugin;
        settings = plugin.settings("egg-capture");
        capturedKey = new NamespacedKey(plugin, "captured_egg");
        blockedTypes = settings.isList("blocked-types")
            ? typeNames(settings.getStringList("blocked-types")) : DEFAULT_BLOCKED_TYPES;
        allowedTypes = typeNames(settings.getStringList("allowed-types"));
        warnUnknownTypes("blocked-types", blockedTypes);
        warnUnknownTypes("allowed-types", allowedTypes);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)
            || !(egg.getShooter() instanceof Player player)
            || !(event.getHitEntity() instanceof Mob mob)
            || !player.hasPermission("rivet.eggcapture")
            || captures.containsKey(mob.getUniqueId())) {
            return;
        }

        boolean ownedByOther = mob instanceof Tameable tameable
            && ownedByOther(tameable.isTamed(), tameable.getOwnerUniqueId(), player.getUniqueId());
        if (captureRefusal(mob.getType().name(), blockedTypes, allowedTypes, ownedByOther,
            mob.customName() != null, player.hasPermission("rivet.eggcapture.bypass")) != Refusal.NONE) {
            refuse(player);
            return;
        }

        EntitySnapshot snapshot = mob.createSnapshot();
        Material material = Bukkit.getItemFactory().getSpawnEgg(mob.getType());
        AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
        if (snapshot == null || material == null || scale == null) {
            refuse(player);
            return;
        }

        ItemStack capturedEgg = new ItemStack(material);
        if (!(capturedEgg.getItemMeta() instanceof SpawnEggMeta meta)) {
            refuse(player);
            return;
        }
        // Let protection plugins veto the capture exactly as they would a melee hit.
        if (!protectionAllows(player, mob)) {
            refuse(player);
            return;
        }
        meta.setSpawnedEntity(snapshot);
        meta.getPersistentDataContainer().set(capturedKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(MM.deserialize(settings.getString("captured-egg.name",
                "<white>Captured <#f72a4c>%mob%</#f72a4c></white>"),
            Placeholder.component("mob", mob.name())));
        java.util.List<String> lore = settings.getStringList("captured-egg.lore");
        if (lore.isEmpty()) {
            lore = java.util.List.of("<white>Contains the original creature.</white>");
        }
        meta.lore(lore.stream().map(MM::deserialize).toList());
        capturedEgg.setItemMeta(meta);

        event.setCancelled(true);
        egg.remove();
        Capture capture = new Capture(mob, player, capturedEgg, scale, scale.getBaseValue(),
            mob.hasAI(), mob.hasGravity(), mob.isInvulnerable(), mob.isGlowing());
        captures.put(mob.getUniqueId(), capture);
        mob.setAI(false);
        mob.setGravity(false);
        mob.setInvulnerable(true);
        mob.setGlowing(true);
        mob.setVelocity(new Vector());
        animate(capture);
    }

    /**
     * Paper lets survival players use a spawn egg on a spawner to change its mob type, so a
     * captured egg would otherwise turn any spawner into an endless source of that creature.
     * HIGHEST and no ignoreCancelled: a plugin may deny only the block interaction while still
     * allowing the item use, which would let the egg through.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null
            || !blocksSpawnerUse(event.getAction(), block.getType(),
                isCapturedEgg(event.getItem(), event.getPlayer()))) {
            return;
        }
        event.setCancelled(true);
        plugin.messageActions().run(event.getPlayer(), settings, "messages.cannot-use-on-spawner",
            "actionbar", "<white>Captured creatures cannot be used on spawners.</white>");
    }

    void shutdown() {
        captures.values().forEach(capture -> {
            restore(capture);
            give(capture.player, new ItemStack(Material.EGG), capture.mob.getLocation());
        });
        captures.clear();
    }

    private void animate(Capture capture) {
        Mob mob = capture.mob;
        playSound(mob.getLocation(), "effects.start-sound",
            Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, .8f, 1.35f);
        Particle.DustTransition vortexDust = new Particle.DustTransition(
            ConfiguredEffect.color(plugin, settings,
                "effects.particles.vortex-first-color", 0x55FFFF),
            ConfiguredEffect.color(plugin, settings,
                "effects.particles.vortex-second-color", 0xBE55FF),
            (float) Math.max(.01, settings.getDouble("effects.particles.vortex-size", 1.25)));
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!captures.containsKey(mob.getUniqueId())) {
                    cancel();
                    return;
                }
                if (tick == ANIMATION_TICKS) {
                    captures.remove(mob.getUniqueId());
                    cancel();
                    pop(capture);
                    return;
                }

                try {
                    double progress = (tick + 1d) / ANIMATION_TICKS;
                    capture.scale.setBaseValue(captureScale(capture.originalScale, progress));
                    Location center = mob.getLocation().add(0, Math.min(mob.getHeight() / 2, 4), 0);
                    double radius = .65 + progress * 1.5;
                    if (settings.getBoolean("effects.particles.enabled", true)
                        && settings.getBoolean("effects.particles.vortex-enabled", true)) {
                        for (int point = 0; point < 4; point++) {
                            double angle = tick * .55 + point * Math.PI / 2;
                            Location ring = center.clone().add(Math.cos(angle) * radius,
                                Math.sin(tick * .35 + point) * .45, Math.sin(angle) * radius);
                            mob.getWorld().spawnParticle(
                                Particle.DUST_COLOR_TRANSITION, ring, 1, vortexDust);
                        }
                    }
                    if (settings.getBoolean("effects.particles.enabled", true)
                        && settings.getBoolean("effects.particles.portal-enabled", true)) {
                        mob.getWorld().spawnParticle(ConfiguredEffect.particle(plugin, settings,
                                "effects.particles.portal-name", Particle.REVERSE_PORTAL), center,
                            Math.max(0, settings.getInt("effects.particles.portal-count", 8)),
                            radius, Math.max(1, mob.getHeight() / 3), radius, .15);
                    }
                    tick++;
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, "Capture animation failed for "
                        + mob.getType() + "; completing capture without it.", exception);
                    captures.remove(mob.getUniqueId());
                    cancel();
                    pop(capture);
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void pop(Capture capture) {
        Location drop = capture.mob.getLocation();
        Location center = drop.clone().add(0, Math.min(capture.mob.getHeight() / 2, 4), 0);
        World world = drop.getWorld();
        capture.mob.remove();
        give(capture.player, capture.egg, drop);
        if (settings.getBoolean("effects.particles.enabled", true)) {
            if (settings.getBoolean("effects.particles.completion-flash-enabled", true)) {
                world.spawnParticle(Particle.FLASH, center,
                    Math.max(0, settings.getInt("effects.particles.completion-flash-count", 1)));
            }
            if (settings.getBoolean("effects.particles.completion-explosion-enabled", true)) {
                world.spawnParticle(Particle.EXPLOSION, center,
                    Math.max(0, settings.getInt("effects.particles.completion-explosion-count", 4)),
                    .5, .5, .5, 0);
            }
            if (settings.getBoolean("effects.particles.completion-sparks-enabled", true)) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, center,
                    Math.max(0, settings.getInt("effects.particles.completion-sparks-count", 45)),
                    1.5, 1.5, 1.5, .3);
            }
            if (settings.getBoolean("effects.particles.completion-portals-enabled", true)) {
                world.spawnParticle(Particle.REVERSE_PORTAL, center,
                    Math.max(0, settings.getInt("effects.particles.completion-portals-count", 70)),
                    1.5, 1.5, 1.5, .4);
            }
        }
        playSound(center, "effects.completion-sound",
            Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1, .8f);
    }

    private static void restore(Capture capture) {
        if (!capture.mob.isValid()) {
            return;
        }
        capture.scale.setBaseValue(capture.originalScale);
        capture.mob.setAI(capture.ai);
        capture.mob.setGravity(capture.gravity);
        capture.mob.setInvulnerable(capture.invulnerable);
        capture.mob.setGlowing(capture.glowing);
    }

    private void give(Player player, ItemStack item, Location fallback) {
        if (!player.isOnline()) {
            fallback.getWorld().dropItemNaturally(fallback, item);
            return;
        }
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        playSound(player.getLocation(), "effects.received-sound",
            Sound.ENTITY_ALLAY_ITEM_GIVEN, .8f, 1.3f);
    }

    private void playSound(Location location, String path, Sound fallback,
                           float defaultVolume, float defaultPitch) {
        if (!settings.getBoolean(path + ".enabled", true)) {
            return;
        }
        location.getWorld().playSound(location,
            ConfiguredEffect.sound(plugin, settings, path + ".name", fallback),
            (float) settings.getDouble(path + ".volume", defaultVolume),
            (float) settings.getDouble(path + ".pitch", defaultPitch));
    }

    private void refuse(Player player) {
        plugin.messageActions().run(player, settings, "messages.cannot-capture", "actionbar",
            "<white>That creature cannot be captured.</white>");
    }

    private boolean protectionAllows(Player player, Mob mob) {
        Map<EntityDamageEvent.DamageModifier, Double> modifiers =
            new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, 0d);
        Map<EntityDamageEvent.DamageModifier, Function<? super Double, Double>> functions =
            new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        functions.put(EntityDamageEvent.DamageModifier.BASE, damage -> -0d);
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
            .withCausingEntity(player).withDirectEntity(player).build();
        EntityDamageByEntityEvent check = new EntityDamageByEntityEvent(player, mob,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, modifiers, functions, false);
        Bukkit.getPluginManager().callEvent(check);
        return !check.isCancelled();
    }

    private boolean isCapturedEgg(ItemStack item, Player player) {
        if (item == null || !(item.getItemMeta() instanceof SpawnEggMeta meta)) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(capturedKey, PersistentDataType.BYTE)) {
            return true;
        }
        // Eggs captured before the marker existed still carry a full entity snapshot, which
        // survival players cannot otherwise obtain. Creative players may pick such eggs freely.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        try {
            return meta.getSpawnedEntity() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void warnUnknownTypes(String path, Set<String> types) {
        for (String type : types) {
            try {
                EntityType.valueOf(type);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Unknown entity type '" + type + "' in egg-capture.yml "
                    + path + "; it will never match.");
            }
        }
    }

    enum Refusal {
        NONE,
        BLOCKED_TYPE,
        NOT_ALLOWED_TYPE,
        OWNED_BY_OTHER,
        NAMED
    }

    /**
     * Decides whether a mob may be captured. Type rules are absolute; the bypass permission
     * only lifts the protection for other players' pets and named mobs.
     */
    static Refusal captureRefusal(String type, Set<String> blocked, Set<String> allowed,
                                  boolean ownedByOther, boolean named, boolean bypass) {
        String normalized = normalizeType(type);
        if (blocked.contains(normalized)) {
            return Refusal.BLOCKED_TYPE;
        }
        if (!allowed.isEmpty() && !allowed.contains(normalized)) {
            return Refusal.NOT_ALLOWED_TYPE;
        }
        if (bypass) {
            return Refusal.NONE;
        }
        if (ownedByOther) {
            return Refusal.OWNED_BY_OTHER;
        }
        return named ? Refusal.NAMED : Refusal.NONE;
    }

    static boolean ownedByOther(boolean tamed, UUID owner, UUID thrower) {
        return tamed && owner != null && !owner.equals(thrower);
    }

    static boolean blocksSpawnerUse(Action action, Material block, boolean capturedEgg) {
        return capturedEgg && action == Action.RIGHT_CLICK_BLOCK
            && (block == Material.SPAWNER || block == Material.TRIAL_SPAWNER);
    }

    /** Accepts {@code iron_golem}, {@code minecraft:iron_golem} or {@code IRON_GOLEM}. */
    static String normalizeType(String configured) {
        String type = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("minecraft:")) {
            type = type.substring("minecraft:".length());
        }
        return type.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    static Set<String> typeNames(Collection<String> configured) {
        Set<String> types = new LinkedHashSet<>();
        for (String entry : configured) {
            String type = normalizeType(entry);
            if (!type.isEmpty()) {
                types.add(type);
            }
        }
        return Set.copyOf(types);
    }

    static double captureScale(double original, double progress) {
        return original + (Math.min(original * 6, 16) - original) * progress * progress;
    }

    private record Capture(Mob mob, Player player, ItemStack egg, AttributeInstance scale,
                           double originalScale, boolean ai, boolean gravity,
                           boolean invulnerable, boolean glowing) {
    }
}
