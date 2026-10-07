package dev.rivet;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Exposes Rivet state to PlaceholderAPI when it's installed; only loaded from behind that soft-dependency check. */
final class RivetPlaceholders extends PlaceholderExpansion {
    private final RivetPlugin plugin;

    RivetPlaceholders(RivetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rivet";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rivet";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String poll = plugin.pollPlaceholder(params);
        if (poll != null) {
            return poll;
        }
        Player online = player.getPlayer();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "nickname" -> plugin.plainNickname(player);
            case "afk" -> Boolean.toString(online != null && plugin.isAfk(online));
            case "vanished" -> Boolean.toString(online != null && plugin.isVanished(online));
            case "muted" -> Boolean.toString(plugin.isMuted(player));
            case "playtime" -> StatisticsModule.duration(player.getStatistic(Statistic.PLAY_ONE_MINUTE) * 50L);
            case "homes" -> Integer.toString(plugin.homeNames(player).size());
            default -> null;
        };
    }
}
