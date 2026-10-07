package dev.rivet;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Starts the modules switched on in {@link ModuleCatalog} and routes their commands. */
final class ModuleRegistry {
    private static final MiniMessage MM = RivetMiniMessage.miniMessage();

    private final RivetPlugin plugin;
    private final Map<Class<?>, RivetModule> running = new LinkedHashMap<>();

    ModuleRegistry(RivetPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts every enabled module in catalog order. If one fails to start, Rivet is disabled and
     * this returns {@code false}.
     */
    boolean start() {
        for (ModuleDefinition<?> definition : ModuleCatalog.DEFINITIONS) {
            if (!definition.enabled(plugin)) {
                definition.commandNames().forEach(name -> bindDisabled(name, definition.id()));
            } else if (!start(definition)) {
                return false;
            }
        }
        return true;
    }

    private <T extends RivetModule> boolean start(ModuleDefinition<T> definition) {
        if (!definition.implemented()) {
            return true;
        }
        T module;
        try {
            module = definition.create(plugin);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not start Rivet's " + definition.id() + " module", exception);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return false;
        }
        running.put(module.getClass(), module);
        if (module instanceof Listener listener) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        definition.commands().forEach((name, handler) -> {
            ModuleDefinition.SenderCompleter<T> completer = definition.completer(name);
            PluginCommand command = command(name);
            if (command == null) {
                return;
            }
            command.setExecutor((sender, ignored, label, args) -> handler.run(module, sender, args));
            command.setTabCompleter((sender, ignored, alias, args) -> RivetPlugin.completions(
                completer.complete(module, sender, args), args.length == 0 ? "" : args[args.length - 1]));
        });
        return true;
    }

    private void bindDisabled(String name, String module) {
        PluginCommand command = command(name);
        if (command == null) {
            return;
        }
        command.setExecutor((sender, ignored, label, args) -> {
            sender.sendMessage(MM.deserialize("<white>The <#f72a4c>" + module + "</#f72a4c> module is disabled."));
            return true;
        });
        command.setTabCompleter((sender, ignored, alias, args) -> List.of());
    }

    private PluginCommand command(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("/" + name + " is bound to a module but missing from plugin.yml.");
        }
        return command;
    }

    /** The running instance of {@code type}, or {@code null} when that module is switched off. */
    <T extends RivetModule> T get(Class<T> type) {
        return type.cast(running.get(type));
    }

    void reload() {
        running.values().forEach(RivetModule::reload);
    }

    /** Shuts modules down in reverse start order so later modules can still use earlier ones. */
    void shutdown() {
        List<RivetModule> modules = new ArrayList<>(running.values());
        for (int index = modules.size() - 1; index >= 0; index--) {
            RivetModule module = modules.get(index);
            try {
                module.shutdown();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                    "Could not shut down " + module.getClass().getSimpleName(), exception);
            }
        }
        running.clear();
    }
}
