package dev.rivet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class NicknameModule implements Listener, RivetModule {
    private static final MiniMessage FORMATTED = RivetMiniMessage.builder()
        .tags(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
            StandardTags.color(), StandardTags.decorations())).build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    static final String DEFAULT_ALLOWED_PATTERN = "[A-Za-z0-9_ ]+";
    static final List<String> DEFAULT_BLOCKED_WORDS = List.of("admin", "owner", "moderator", "mod", "staff", "console");
    private final RivetPlugin plugin;
    private final YamlConfiguration settings;
    private final YamlConfiguration data;

    NicknameModule(RivetPlugin plugin) {
        this.plugin = plugin;
        settings = plugin.settings("nicknames");
        data = plugin.data("nicknames");
    }

    boolean command(org.bukkit.command.CommandSender sender, String[] args) {
        Player target;
        String nickname;
        if (args.length == 1 && sender instanceof Player player) {
            target = player;
            nickname = args[0];
        } else if (args.length == 2 && sender.hasPermission("rivet.nick.others")) {
            target = plugin.getServer().getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(FORMATTED.deserialize("<white>That player is not online."));
                return true;
            }
            nickname = args[1];
        } else {
            sender.sendMessage(FORMATTED.deserialize("<white>Usage: /nick <nickname|off> or /nick &lt;player&gt; <nickname|off>"));
            return true;
        }

        String path = "nicknames." + target.getUniqueId();
        Object previous = data.get(path);
        if (nickname.equalsIgnoreCase("off")) {
            data.set(path, null);
        } else {
            Component rendered;
            try {
                rendered = render(target, nickname);
            } catch (RuntimeException exception) {
                sender.sendMessage(FORMATTED.deserialize("<white>That nickname contains invalid formatting.</white>"));
                return true;
            }
            String plain = PLAIN.serialize(rendered);
            int maximum = Math.max(1, settings.getInt("maximum-length", 24));
            // Staff renaming someone else skip the character and word rules, never the impersonation check.
            boolean ownNickname = sender instanceof Player self && self.getUniqueId().equals(target.getUniqueId());
            Pattern allowed = ownNickname && !sender.hasPermission("rivet.nick.unicode") ? allowedPattern() : null;
            List<String> blocked = !ownNickname ? List.of() : settings.contains("blocked-words")
                ? settings.getStringList("blocked-words") : DEFAULT_BLOCKED_WORDS;
            Rejection rejection = validate(plain, maximum, allowed, blocked,
                takenNames(target.getUniqueId(), plain));
            if (rejection != null) {
                plugin.messageActions().run(sender, settings, "messages." + rejection.messageKey,
                    rejection.fallback, net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                        "max", Integer.toString(maximum)));
                return true;
            }
            data.set(path, nickname);
        }
        try {
            plugin.saveData("nicknames");
        } catch (IOException exception) {
            data.set(path, previous);
            plugin.getLogger().severe("Could not save data/nicknames.yml: " + exception.getMessage());
            sender.sendMessage(FORMATTED.deserialize("<white>Could not save that nickname."));
            return true;
        }
        plugin.refreshDisplayName(target);
        plugin.messageActions().run(sender, settings, nickname.equalsIgnoreCase("off")
            ? "messages.removed" : "messages.set", nickname.equalsIgnoreCase("off")
            ? "<white>Nickname removed.</white>" : "<white>Nickname updated.</white>");
        return true;
    }

    List<String> completions(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("off"));
            if (sender.hasPermission("rivet.nick.others")) {
                plugin.getServer().getOnlinePlayers().stream().map(Player::getName).sorted().forEach(values::add);
            }
            return values;
        }
        return args.length == 2 && sender.hasPermission("rivet.nick.others") ? List.of("off") : List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.refreshDisplayName(event.getPlayer());
    }

    Component displayName(Player player) {
        String nickname = data.getString("nicknames." + player.getUniqueId());
        if (nickname == null) {
            return Component.text(player.getName());
        }
        try {
            return render(player, nickname);
        } catch (RuntimeException exception) {
            return Component.text(player.getName());
        }
    }

    String plainNickname(org.bukkit.OfflinePlayer player) {
        String nickname = data.getString("nicknames." + player.getUniqueId());
        return nickname == null ? player.getName() : PLAIN.serialize(FORMATTED.deserialize(nickname));
    }

    @Override
    public void shutdown() {
        plugin.getServer().getOnlinePlayers().forEach(player -> player.displayName(Component.text(player.getName())));
    }

    private Component render(Player player, String nickname) {
        return player.hasPermission("rivet.nick.format") ? FORMATTED.deserialize(nickname)
            : Component.text(nickname);
    }

    private Pattern allowedPattern() {
        String configured = settings.getString("allowed-pattern", DEFAULT_ALLOWED_PATTERN);
        if (configured == null || configured.isEmpty()) {
            return null;
        }
        try {
            return Pattern.compile(configured);
        } catch (PatternSyntaxException exception) {
            plugin.getLogger().warning("Invalid nicknames.yml allowed-pattern; using the default: "
                + exception.getDescription());
            return Pattern.compile(DEFAULT_ALLOWED_PATTERN);
        }
    }

    /**
     * Normalized usernames and nicknames that belong to players other than {@code target}: online
     * players, every stored nickname, and the cached profile (if any) whose username equals the
     * requested plain nickname.
     */
    private Set<String> takenNames(UUID target, String plain) {
        Set<String> taken = new HashSet<>();
        plugin.getServer().getOnlinePlayers().stream()
            .filter(player -> !player.getUniqueId().equals(target))
            .forEach(player -> taken.add(normalizeName(player.getName())));
        var stored = data.getConfigurationSection("nicknames");
        if (stored != null) {
            for (String key : stored.getKeys(false)) {
                String nickname = stored.getString(key);
                if (nickname == null || key.equals(target.toString())) {
                    continue;
                }
                // The stored value renders as plain text or MiniMessage depending on the owner's
                // rivet.nick.format permission, so treat both readings as taken.
                taken.add(normalizeName(nickname));
                try {
                    taken.add(normalizeName(PLAIN.serialize(FORMATTED.deserialize(nickname))));
                } catch (RuntimeException ignored) {
                    // Malformed formatting renders as the raw text, which is already included.
                }
            }
        }
        String candidate = plain.trim();
        if (!candidate.isEmpty() && candidate.length() <= 16) {
            org.bukkit.OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(candidate);
            if (cached != null && cached.getName() != null && !cached.getUniqueId().equals(target)) {
                taken.add(normalizeName(cached.getName()));
            }
        }
        return taken;
    }

    /**
     * Validates the plain-text rendering of a nickname. {@code allowedPattern} null skips the
     * character rule, and an empty {@code blockedWords} list skips the word rule.
     *
     * @return the first rule broken, or null when the nickname is acceptable
     */
    static Rejection validate(String plain, int maximumLength, Pattern allowedPattern,
                              List<String> blockedWords, Set<String> takenNames) {
        if (!validNickname(plain, maximumLength)) {
            return Rejection.INVALID;
        }
        if (takenNames.contains(normalizeName(plain))) {
            return Rejection.TAKEN;
        }
        if (allowedPattern != null && !allowedPattern.matcher(plain).matches()) {
            return Rejection.PATTERN;
        }
        return containsBlockedWord(plain, blockedWords) ? Rejection.BLOCKED_WORD : null;
    }

    static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Case-insensitive substring match. Also checks the nickname with separators removed so that
     * {@code A_d m-i n} still matches {@code admin}.
     */
    static boolean containsBlockedWord(String plain, List<String> blockedWords) {
        String lower = plain.toLowerCase(Locale.ROOT);
        String squashed = lower.replaceAll("[^\\p{L}\\p{N}]", "");
        for (String word : blockedWords) {
            String blocked = word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
            if (!blocked.isEmpty() && (lower.contains(blocked)
                || squashed.contains(blocked.replaceAll("[^\\p{L}\\p{N}]", "")))) {
                return true;
            }
        }
        return false;
    }

    enum Rejection {
        INVALID("invalid", "<white>Nicknames must be 1-%max% visible characters with no control characters.</white>"),
        TAKEN("taken", "<white>That nickname matches another player's name or nickname.</white>"),
        PATTERN("disallowed-characters", "<white>Nicknames may only use letters, numbers, spaces and underscores.</white>"),
        BLOCKED_WORD("blocked-word", "<white>That nickname contains a word that is not allowed.</white>");

        private final String messageKey;
        private final String fallback;

        Rejection(String messageKey, String fallback) {
            this.messageKey = messageKey;
            this.fallback = fallback;
        }
    }

    static boolean validNickname(String nickname, int maximumLength) {
        return !nickname.isBlank() && nickname.length() <= maximumLength
            && nickname.chars().noneMatch(Character::isISOControl);
    }
}
