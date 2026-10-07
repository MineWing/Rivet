package dev.rivet;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Time and weather commands: /day, /night, /noon, /midnight, /sun, /rain, /thunder, /locktime. */
final class EnvironmentModule implements RivetModule {
    private static final long MINECRAFT_DAY_TICKS = 24_000;
    private static final MiniMessage MM = RivetMiniMessage.miniMessage();
    private final RivetPlugin plugin;
    private final YamlConfiguration settings;
    private final Map<UUID, BukkitRunnable> timeTransitions = new HashMap<>();

    EnvironmentModule(RivetPlugin plugin) {
        this.plugin = plugin;
        settings = plugin.settings("environment");
    }

    boolean command(Player player, String name, String[] args) {
        return switch (name) {
            case "day", "night", "noon", "midnight" -> setTime(player, name);
            case "sun", "rain", "thunder" -> setWeather(player, name);
            case "locktime" -> lockTime(player, args);
            default -> false;
        };
    }

    List<String> completions(String[] args) {
        return args.length == 1 ? List.of("day", "night", "noon", "midnight", "current", "off") : List.of();
    }

    @Override
    public void shutdown() {
        timeTransitions.values().forEach(BukkitRunnable::cancel);
        timeTransitions.clear();
    }

    private boolean setTime(Player player, String command) {
        long time = switch (command) {
            case "day" -> 1000;
            case "noon" -> 6000;
            case "night" -> 13000;
            default -> 18000;
        };
        World world = player.getWorld();
        cancelTimeTransition(world);
        if ((command.equals("day") || command.equals("night"))
            && settings.getBoolean("speedUpEffect.enabled", true)) {
            transitionTime(world, time);
        } else {
            world.setTime(time);
        }
        runEnvironmentActions(player, "times", command);
        return true;
    }

    private void transitionTime(World world, long target) {
        long start = Math.floorMod(world.getTime(), MINECRAFT_DAY_TICKS);
        long distance = forwardTimeDistance(start, target);
        if (distance == 0) {
            world.setTime(target);
            return;
        }
        int duration = 100;
        int period = 1;
        int steps = Math.max(1, (duration + period - 1) / period);
        UUID worldId = world.getUID();
        BukkitRunnable transition = new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (plugin.getServer().getWorld(worldId) != world) {
                    finish();
                    return;
                }
                world.setTime(transitionedTime(start, distance, ++step, steps));
                if (step == steps) {
                    finish();
                }
            }

            private void finish() {
                timeTransitions.remove(worldId, this);
                cancel();
            }
        };
        timeTransitions.put(worldId, transition);
        transition.runTaskTimer(plugin, period, period);
    }

    private void cancelTimeTransition(World world) {
        BukkitRunnable transition = timeTransitions.remove(world.getUID());
        if (transition != null) {
            transition.cancel();
        }
    }

    static long forwardTimeDistance(long current, long target) {
        long normalizedCurrent = Math.floorMod(current, MINECRAFT_DAY_TICKS);
        long normalizedTarget = Math.floorMod(target, MINECRAFT_DAY_TICKS);
        return Math.floorMod(normalizedTarget - normalizedCurrent, MINECRAFT_DAY_TICKS);
    }

    static long transitionedTime(long start, long distance, int step, int steps) {
        if (step >= steps) {
            return Math.floorMod(start + distance, MINECRAFT_DAY_TICKS);
        }
        long progressed = Math.round(distance * (step / (double) steps));
        return Math.floorMod(start + progressed, MINECRAFT_DAY_TICKS);
    }

    private boolean lockTime(Player player, String[] args) {
        World world = player.getWorld();
        String option = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "current";
        if (option.equals("off")) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            plugin.messageActions().run(player, settings, "locktime.off.commands_when_ran", List.of(
                "[actionbar] <white>Time unlocked</white>",
                "[sound] ui_button_click"));
            return true;
        }
        long time;
        switch (option) {
            case "day" -> time = 1000;
            case "noon" -> time = 6000;
            case "night" -> time = 13000;
            case "midnight" -> time = 18000;
            case "current" -> time = world.getTime();
            default -> {
                try {
                    time = Long.parseLong(option);
                } catch (NumberFormatException e) {
                    player.sendMessage(MM.deserialize(
                        "<white>Usage: <#f72a4c>/locktime [day|night|noon|midnight|current|ticks|off]</#f72a4c>"));
                    return true;
                }
                if (time < 0) {
                    player.sendMessage(MM.deserialize(
                        "<white>Usage: <#f72a4c>/locktime [day|night|noon|midnight|current|ticks|off]</#f72a4c>"));
                    return true;
                }
            }
        }
        cancelTimeTransition(world);
        world.setTime(time);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        String key = switch (option) {
            case "day", "night", "noon", "midnight" -> option;
            default -> "custom";
        };
        String label = switch (option) {
            case "day", "night", "noon", "midnight" -> RivetPlugin.titleCase(option);
            default -> String.valueOf(time);
        };
        plugin.messageActions().run(player, settings, "locktime." + key + ".commands_when_ran", List.of(
            "[actionbar] <white>Time locked at <green>" + label + "</green></white>",
            "[sound] ui_button_click"));
        return true;
    }

    private boolean setWeather(Player player, String command) {
        World world = player.getWorld();
        world.setStorm(!command.equals("sun"));
        world.setThundering(command.equals("thunder"));
        runEnvironmentActions(player, "weather", command);
        return true;
    }

    private void runEnvironmentActions(Player player, String section, String command) {
        String path = section + "." + command + ".commands_when_ran";
        plugin.messageActions().run(player, settings, path, List.of(
                "[actionbar] <white>" + (section.equals("times") ? "Time" : "Weather")
                    + " set to <green>" + RivetPlugin.titleCase(command) + "</green></white>",
                "[sound] ui_button_click"));
    }
}
