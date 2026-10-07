package dev.rivet;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One entry in {@link ModuleCatalog}: the switch that turns a feature on, the class that
 * implements it, and the commands it owns.
 */
final class ModuleDefinition<T extends RivetModule> {
    private static final MiniMessage MM = RivetMiniMessage.miniMessage();

    @FunctionalInterface
    interface Factory<T> {
        T create(RivetPlugin plugin) throws Exception;
    }

    @FunctionalInterface
    interface SenderCommand<T> {
        boolean run(T module, CommandSender sender, String[] args);
    }

    @FunctionalInterface
    interface PlayerCommand<T> {
        boolean run(T module, Player player, String[] args);
    }

    @FunctionalInterface
    interface NamedPlayerCommand<T> {
        boolean run(T module, Player player, String command, String[] args);
    }

    @FunctionalInterface
    interface SenderCompleter<T> {
        List<String> complete(T module, CommandSender sender, String[] args);
    }

    @FunctionalInterface
    interface PlayerCompleter<T> {
        List<String> complete(T module, Player player, String[] args);
    }

    @FunctionalInterface
    interface NamedPlayerCompleter<T> {
        List<String> complete(T module, Player player, String command, String[] args);
    }

    private final String id;
    private final boolean enabledByDefault;
    private final Predicate<RivetPlugin> customSwitch;
    private final Factory<T> factory;
    private final Map<String, SenderCommand<T>> commands = new LinkedHashMap<>();
    private final Map<String, SenderCompleter<T>> completers = new HashMap<>();
    private final Set<String> pluginCommands = new LinkedHashSet<>();

    private ModuleDefinition(String id, boolean enabledByDefault, Predicate<RivetPlugin> customSwitch,
                             Factory<T> factory) {
        this.id = id;
        this.enabledByDefault = enabledByDefault;
        this.customSwitch = customSwitch;
        this.factory = factory;
    }

    /** A module switched by its {@code id} entry in modules.yml. */
    static <T extends RivetModule> ModuleDefinition<T> module(String id, boolean enabledByDefault,
                                                              Factory<T> factory) {
        return new ModuleDefinition<>(id, enabledByDefault, null, factory);
    }

    /** A modules.yml switch whose commands are still implemented by {@link RivetPlugin}. */
    static ModuleDefinition<RivetModule> hosted(String id, boolean enabledByDefault) {
        return new ModuleDefinition<>(id, enabledByDefault, null, null);
    }

    /** A feature switched somewhere other than modules.yml, such as settings/gameplay.yml. */
    static <T extends RivetModule> ModuleDefinition<T> feature(String id, Predicate<RivetPlugin> enabled,
                                                               Factory<T> factory) {
        return new ModuleDefinition<>(id, false, enabled, factory);
    }

    ModuleDefinition<T> command(String name, SenderCommand<T> handler) {
        claim(name);
        commands.put(name, handler);
        return this;
    }

    ModuleDefinition<T> playerCommand(String name, PlayerCommand<T> handler) {
        return command(name, (module, sender, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MM.deserialize("<white>This command is only available to players."));
                return true;
            }
            return handler.run(module, player, args);
        });
    }

    ModuleDefinition<T> playerCommands(List<String> names, NamedPlayerCommand<T> handler) {
        names.forEach(name -> playerCommand(name, (module, player, args) -> handler.run(module, player, name, args)));
        return this;
    }

    ModuleDefinition<T> completer(String name, SenderCompleter<T> completer) {
        if (!commands.containsKey(name)) {
            throw new IllegalArgumentException("Bind /" + name + " before its completer");
        }
        completers.put(name, completer);
        return this;
    }

    ModuleDefinition<T> playerCompleter(String name, PlayerCompleter<T> completer) {
        return completer(name, (module, sender, args) -> sender instanceof Player player
            ? completer.complete(module, player, args) : List.of());
    }

    ModuleDefinition<T> playerCompleters(List<String> names, NamedPlayerCompleter<T> completer) {
        names.forEach(name -> playerCompleter(name,
            (module, player, args) -> completer.complete(module, player, name, args)));
        return this;
    }

    /** Commands owned by this module but still dispatched by {@link RivetPlugin#onCommand}. */
    ModuleDefinition<T> pluginCommands(String... names) {
        for (String name : names) {
            claim(name);
            pluginCommands.add(name);
        }
        return this;
    }

    private void claim(String name) {
        if (commands.containsKey(name) || pluginCommands.contains(name)) {
            throw new IllegalArgumentException("/" + name + " is bound twice in " + id);
        }
    }

    String id() {
        return id;
    }

    boolean enabledByDefault() {
        return enabledByDefault;
    }

    /** Whether this definition has an entry in modules.yml. */
    boolean switchedInModulesFile() {
        return customSwitch == null;
    }

    boolean enabled(RivetPlugin plugin) {
        return customSwitch == null ? plugin.moduleEnabled(id) : customSwitch.test(plugin);
    }

    boolean implemented() {
        return factory != null;
    }

    T create(RivetPlugin plugin) throws Exception {
        return factory.create(plugin);
    }

    Map<String, SenderCommand<T>> commands() {
        return Collections.unmodifiableMap(commands);
    }

    SenderCompleter<T> completer(String name) {
        return completers.getOrDefault(name, (module, sender, args) -> List.of());
    }

    /** Every command this module owns, whichever class dispatches it. */
    Set<String> commandNames() {
        Set<String> names = new LinkedHashSet<>(commands.keySet());
        names.addAll(pluginCommands);
        return names;
    }
}
