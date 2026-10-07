package dev.rivet;

/**
 * A feature started by {@link ModuleRegistry} when its switch is on. Modules that implement
 * {@link org.bukkit.event.Listener} are registered for events automatically.
 */
interface RivetModule {
    /** Applies settings that {@code /rivet reload} has just re-read. */
    default void reload() {
    }

    /** Stops tasks and restores world state; called once when Rivet disables. */
    default void shutdown() {
    }
}
