package dev.rivet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.rivet.ModuleDefinition.feature;
import static dev.rivet.ModuleDefinition.hosted;
import static dev.rivet.ModuleDefinition.module;

/**
 * Every Rivet module, in start order. Adding a module means adding its definition here, its
 * switch to modules.yml, and its commands to plugin.yml.
 */
final class ModuleCatalog {
    private static final boolean ON = true;
    private static final boolean OFF = false;

    static final List<ModuleDefinition<?>> DEFINITIONS = List.of(
        feature("autocrafter", plugin -> plugin.settings("gameplay").getBoolean("autocrafter.enabled", true),
            AutoCrafter::new),
        feature("beacon-tools", plugin -> plugin.settings("gameplay").getBoolean("beacon-tools.enabled", true),
            BeaconTools::new),
        module("breeders", ON, AutoBreeder::new)
            .command("givebreeder", AutoBreeder::command)
            .completer("givebreeder", AutoBreeder::completions)
            .command("clearhologram", (breeders, sender, args) -> breeders.clearHolograms(sender)),
        module("egg-capture", ON, EggCapture::new),
        module("villager-reroll", ON, VillagerRerollModule::new),
        module("creeper-restoration", ON, CreeperRestoration::new)
            .command("restorationcore", CreeperRestoration::command)
            .completer("restorationcore", CreeperRestoration::completions),
        module("permissions", OFF, PermissionModule::new)
            .command("perm", PermissionModule::command)
            .completer("perm", (permissions, sender, args) -> permissions.completions(args)),
        module("chat", ON, ChatModule::new)
            .playerCommand("msg", ChatModule::message)
            .completer("msg", (chat, sender, args) -> args.length == 1
                ? RivetPlugin.visiblePlayerNames(sender) : List.of())
            .playerCommand("r", ChatModule::reply)
            .playerCommand("socialspy", ChatModule::socialSpy)
            .playerCommand("ignore", ChatModule::ignore)
            .playerCompleter("ignore", ChatModule::ignoreCompletions)
            .playerCommand("chatcolor", ChatModule::chatColor)
            .playerCompleter("chatcolor", ChatModule::chatColorCompletions)
            .playerCommand("tag", ChatModule::tag)
            .playerCompleter("tag", ChatModule::tagCompletions)
            .playerCommand("me", ChatModule::me),
        module("graves", ON, plugin -> new GraveModule(plugin, plugin.delayedTeleports()))
            .playerCommand("back", GraveModule::back),
        module("holograms", ON, HologramModule::new)
            .command("hologram", HologramModule::command)
            .completer("hologram", (holograms, sender, args) -> holograms.completions(args)),
        module("tree-feller", ON, TreeFeller::new),
        module("spawn", ON, plugin -> new SpawnModule(plugin, plugin.delayedTeleports()))
            .playerCommands(List.of("spawn", "setspawn"), SpawnModule::command),
        module("tpa", OFF, plugin -> new TpaModule(plugin, plugin.delayedTeleports()))
            .playerCommands(List.of("tpa", "tpahere", "tpaccept", "tpdeny"), TpaModule::command)
            .playerCompleters(List.of("tpa", "tpahere", "tpaccept", "tpdeny"), TpaModule::completions),
        module("kits", OFF, KitsModule::new)
            .playerCommand("kit", KitsModule::command)
            .playerCompleter("kit", KitsModule::completions),
        module("nicknames", ON, NicknameModule::new)
            .command("nick", NicknameModule::command)
            .completer("nick", NicknameModule::completions),
        module("afk", ON, AfkModule::new)
            .playerCommand("afk", AfkModule::command)
            .playerCommand("afkcheck", AfkModule::check)
            .playerCompleters(List.of("afk", "afkcheck"), AfkModule::completions),
        module("join-leave", ON, JoinLeaveModule::new),
        module("announcements", OFF, AnnouncementsModule::new),
        module("lagg", ON, LaggModule::new)
            .command("lagg", LaggModule::command)
            .completer("lagg", (lagg, sender, args) -> args.length == 1 ? List.of("clear", "timer") : List.of()),
        module("snapshots", ON, SnapshotModule::new)
            .command("snapshot", SnapshotModule::command)
            .completer("snapshot", SnapshotModule::completions),
        module("death-messages", ON, DeathMessagesModule::new),
        module("fishing", ON, FishingModule::new),
        module("statistics", ON, StatisticsModule::new)
            .command("stats", StatisticsModule::command)
            .completer("stats", StatisticsModule::completions)
            .command("playtime", StatisticsModule::playtime)
            .completer("playtime", StatisticsModule::playtimeCompletions)
            .command("seen", StatisticsModule::seen)
            .completer("seen", StatisticsModule::seenCompletions),
        module("trash", ON, TrashModule::new)
            .playerCommand("trash", TrashModule::command),
        module("utilities", ON, UtilitiesModule::new)
            .playerCommands(List.of("craft", "anvil", "smithing", "stonecutter", "grindstone", "jump", "list",
                "nv", "ping", "ride"), UtilitiesModule::command)
            .playerCompleter("ping", (utilities, player, args) -> utilities.completions(player, "ping", args)),
        module("poses", OFF, PosesModule::new)
            .playerCommands(List.of("sit", "lay", "crawl"), PosesModule::command),
        module("backpacks", OFF, BackpacksModule::new)
            .playerCommand("backpack", BackpacksModule::command),
        module("daily", OFF, DailyModule::new)
            .playerCommand("daily", DailyModule::command),
        module("rtp", OFF, plugin -> new RtpModule(plugin, plugin.delayedTeleports()))
            .playerCommand("rtp", RtpModule::command)
            .playerCompleter("rtp", RtpModule::completions),
        module("near", OFF, NearModule::new)
            .playerCommand("near", NearModule::command),
        module("inventory", OFF, ItemTools::new)
            .playerCommand("clear", ItemTools::clear)
            .playerCommand("condense", ItemTools::condense)
            .playerCommand("donate", ItemTools::donate)
            .playerCommand("giveall", ItemTools::giveAll)
            .playerCompleters(List.of("clear", "condense", "donate", "giveall"), ItemTools::completions)
            .playerCommand("repair", ItemTools::repair)
            .completer("repair", (items, sender, args) -> args.length == 1
                && sender.hasPermission("rivet.repair.all") ? List.of("all") : List.of())
            .playerCommand("hat", ItemTools::hat)
            .playerCommand("rename", ItemTools::rename)
            .completer("rename", (items, sender, args) -> args.length == 1 ? List.of("clear") : List.of())
            .playerCommand("lore", ItemTools::lore)
            .completer("lore", (items, sender, args) -> args.length == 1
                ? List.of("add", "set", "remove", "clear") : List.of())
            .pluginCommands("i", "invsee", "enderchest"),
        module("inventory", OFF, ScanModule::new)
            .command("scan", ScanModule::command)
            .completer("scan", ScanModule::completions),
        module("filter", ON, FilterModule::new)
            .playerCommand("filter", FilterModule::command)
            .completer("filter", (filter, sender, args) -> filter.completions(args)),
        module("help", ON, HelpModule::new)
            .command("help", HelpModule::command)
            .completer("help", HelpModule::completions),
        // Always running; hoppers.enabled is checked per event so /rivet reload can toggle it.
        feature("hoppers", plugin -> true, HopperModule::new),
        module("staff", OFF, StaffTools::new)
            .playerCommand("heal", StaffTools::heal)
            .playerCompleter("heal", (staff, player, args) -> staff.completions(player, args, "rivet.heal.others"))
            .playerCommand("feed", StaffTools::feed)
            .playerCompleter("feed", (staff, player, args) -> staff.completions(player, args, "rivet.feed.others"))
            .playerCommand("god", StaffTools::god)
            .playerCompleter("god", StaffTools::godCompletions)
            .playerCommand("flyspeed", StaffTools::flySpeed)
            .playerCompleter("flyspeed", StaffTools::flySpeedCompletions)
            .playerCommand("commandspy", StaffTools::commandSpy)
            .playerCommand("bossbarmsg", StaffTools::bossBar)
            .playerCompleter("bossbarmsg", StaffTools::bossBarCompletions)
            .playerCommand("note", StaffTools::note)
            .playerCompleter("note", StaffTools::noteCompletions)
            .playerCommand("sameip", StaffTools::sameIp)
            .playerCompleter("sameip", StaffTools::sameIpCompletions)
            .playerCommand("toast", StaffTools::toast)
            .playerCompleter("toast", StaffTools::toastCompletions)
            .playerCommand("ban", StaffTools::ban)
            .playerCommand("tempban", StaffTools::tempBan)
            .playerCommand("unban", StaffTools::unban)
            .playerCommand("mute", StaffTools::mute)
            .playerCommand("tempmute", StaffTools::tempMute)
            .playerCommand("unmute", StaffTools::unmute)
            .playerCommand("kick", StaffTools::kick)
            .playerCommand("warn", StaffTools::warn)
            .playerCommand("history", StaffTools::history)
            .playerCompleters(List.of("ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "warn",
                "history"), (staff, player, command, args) -> staff.moderationCompletions(command, player, args))
            .pluginCommands("gmc", "gms", "tp", "tphere", "tppos", "vanish", "fly"),
        module("environment", ON, EnvironmentModule::new)
            .playerCommands(List.of("day", "night", "noon", "midnight", "sun", "rain", "thunder", "locktime"),
                EnvironmentModule::command)
            .completer("locktime", (environment, sender, args) -> environment.completions(args)),
        module("restart", OFF, RestartModule::new)
            .command("restart", RestartModule::command)
            .completer("restart", RestartModule::completions),
        module("magnet", ON, MagnetModule::new)
            .playerCommand("magnet", MagnetModule::command),
        module("polls", ON, PollModule::new)
            .command("poll", PollModule::command)
            .completer("poll", PollModule::completions),
        module("server-list", ON, ServerListModule::new),
        // Implemented in RivetPlugin until they move into their own classes.
        hosted("homes", ON).pluginCommands("sethome", "home", "delhome"),
        hosted("warps", ON).pluginCommands("setwarp", "warp", "delwarp"),
        hosted("worlds", OFF).pluginCommands("flat", "flatworld", "voidworld", "worldspawn", "setworldspawn",
            "killall", "findbiome", "top", "tree"),
        hosted("mob-heads", ON).pluginCommands("head"));

    private static final Map<String, String> COMMAND_MODULES = commandModules();

    private ModuleCatalog() {
    }

    /** The switches in modules.yml, in catalog order. */
    static List<String> switches() {
        return DEFINITIONS.stream().filter(ModuleDefinition::switchedInModulesFile)
            .map(ModuleDefinition::id).distinct().toList();
    }

    static Set<String> enabledByDefault() {
        return DEFINITIONS.stream()
            .filter(definition -> definition.switchedInModulesFile() && definition.enabledByDefault())
            .map(ModuleDefinition::id).collect(Collectors.toUnmodifiableSet());
    }

    /** The module that owns {@code command}, or {@code null} when no module switch gates it. */
    static String moduleForCommand(String command) {
        return COMMAND_MODULES.get(command);
    }

    private static Map<String, String> commandModules() {
        Map<String, String> owners = new LinkedHashMap<>();
        DEFINITIONS.forEach(definition -> definition.commandNames().forEach(command -> {
            String previous = owners.put(command, definition.id());
            if (previous != null) {
                throw new IllegalStateException("/" + command + " is claimed by both "
                    + previous + " and " + definition.id());
            }
        }));
        return Map.copyOf(owners);
    }
}
