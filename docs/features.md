# Feature overview

## Player and survival features

| Feature | Module | Default | Description |
|---|---|---:|---|
| Homes | `homes` | Enabled | Named player homes with configurable limits, effects, and shared teleport policy. |
| Warps | `warps` | Enabled | Named public server destinations using the shared teleport policy. |
| Graves | `graves` | Enabled | Persistent death graves, recovery rules, tracking compasses, and smart persistent `/back` history. |
| Death messages | `death-messages` | Enabled | Cause-specific MiniMessage pools, rare variants, safe placeholders, and hoverable killing weapons. |
| Fishing announcements | `fishing` | Enabled | Broadcasts every successful item catch and gives fish a playful random length. |
| Spawn | `spawn` | Enabled | Server spawn and immediate join spawning; `/spawn` uses the shared teleport policy. |
| Chat | `chat` | Enabled | Vanilla-style formatted chat, friendly styles, tags, green name mentions with sound/title pings, lightweight anti-spam, private messages, ignore lists, social spy, and item links. |
| Kits | `kits` | Disabled | Configured item kits with permissions and persistent cooldowns. |
| AFK | `afk` | Enabled | Manual and automatic AFK state, reasons, sleep exclusion, and status checks. |
| Backpacks | `backpacks` | Disabled | Persistent personal storage with permission-controlled rows. |
| Daily rewards | `daily` | Disabled | Timestamp-based rewards, streaks, milestone rewards, experience, and commands. |
| Random teleport | `rtp` | Disabled | Asynchronous searches for safe random destinations. |
| Nearby players | `near` | Disabled | Visibility-aware nearby-player listing. |
| Pickup filters | `filter` | Enabled | Persistent per-player item pickup allowlists or denylists. |
| Item magnets | `magnet` | Enabled | Persistent per-player nearby item collection with atomic inventory-capacity checks that respects pickup delay and ignores a player's own fresh drops. |
| Polls | `polls` | Enabled | Persistent yes/no polls with direct left/right-click voting, admin creation/deletion, and clickable reminders. |
| Statistics | `statistics` | Enabled | Native statistics, playtime, and compact Seen v2 activity summaries. |
| Nicknames | `nicknames` | Enabled | Persistent nicknames with controlled MiniMessage formatting. |
| Trash | `trash` | Enabled | A configurable disposable inventory. |
| Utilities | `utilities` | Enabled | Portable workstations, player lists, ping, Night Vision, jump and riding tools, low-durability alerts, and chance-based no-grief creeper confetti. |
| Poses | `poses` | Disabled | Native Paper sitting, laying, and crawling poses. |

## Death messages

Rivet replaces the broadcast component only when a configured pool is available. Player and mob kills take priority over environmental causes; other pools cover falls, fire and lava, drowning, explosions, projectiles, the void, suffocation, magic, and generic deaths. If the selected pool and `generic` are both empty, Paper's vanilla death message remains unchanged.

Templates support `%player%`, `%killer%`, `%mob%`, `%weapon%`, and `%world%`. Names are inserted as components instead of parsed as MiniMessage, preventing player-controlled text from injecting tags or actions. Killing weapons keep custom names and expose the real item details on hover. `killer-health` can append a player killer's remaining health, while `rare-chance` controls the optional global funny-message pool.

## Fishing announcements

Successful fishing-rod item catches are announced to the server. Cod, salmon, pufferfish,
and tropical fish also receive a randomly generated length from their configurable range;
junk and treasure catches use a separate message without a length. Catch names retain their
item hover details.

## World and gameplay features

| Feature | Module | Default | Description |
|---|---|---:|---|
| Auto breeders | `breeders` | Enabled | Persistent, species-specific automated breeders with a live next-breed hologram countdown, stored breeding XP, and chicken-egg collection. |
| Egg capture | `egg-capture` | Enabled | Capture supported mobs with thrown eggs and animated effects. See [Egg capture](#egg-capture). |
| Creeper restoration | `creeper-restoration` | Enabled | Visual creeper debris and throwable Restoration Cores that reconstruct saved craters. |
| Ground-item cleanup | `lagg` | Enabled | Periodically removes eligible dropped item entities from loaded worlds, with configurable warnings, protected named, plugin-tagged, or crop items, and hoverable removal details. |
| Faster hoppers | Core gameplay | Enabled | Shortens block-hopper transfer cooldowns from vanilla's 8 ticks to a configurable 2 ticks by default. Switch and cooldown apply with `/rivet reload`. |
| Tree felling | `tree-feller` | Enabled | Whole-tree cutting, including large 2×2 jungle trees, warped and crimson huge fungi, and their attached growth, plus connected ore or Glowstone vein mining with clumped drops and XP. |
| Mob heads | `mob-heads` | Enabled | Configurable, Looting-aware mob-head drops and cached player heads, with vanilla Wither Skeleton skull drops preserved by default. |
| Villager reroll | `villager-reroll` | Enabled | Adds a non-consuming reroll offer to the vanilla trade screen using Paper's native trade generation. Untraded villagers with workstations are eligible by default. |
| Environment | `environment` | Enabled | Animated time changes, time locking, and weather controls. |
| Worlds | `worlds` | Disabled | Tracked flat and void test worlds, biome search, world tools, and spawn controls. |
| Inventory tools | `inventory` | Disabled | Inventory administration, item creation, repair, editing, donation, and scanning. |

Small mechanics such as faster hoppers, crop protection, water replanting, and Iron Golem poppy drops live together in `settings/gameplay.yml` rather than creating one-switch modules.

When creeper confetti is selected, the creeper still deals its ordinary entity damage but
cannot destroy blocks or produce block drops. A colourful dust burst and firework sound
replace the griefing result. The utility is enabled with a 10% chance by default; its
chance, particle burst, and sound are configurable in `settings/utilities.yml`. Confetti
explosions do not create creeper-restoration craters because no blocks are destroyed.

## Server administration features

| Feature | Module | Default | Description |
|---|---|---:|---|
| Staff tools | `staff` | Disabled | Gamemode, teleportation, vanish, flight, bans, mutes, kicks, warnings, moderation notes and combined history, boss bars, toasts, and status tools. |
| Permissions | `permissions` | Disabled | Lightweight UUID users, weighted multi-group inheritance, explicit grants and denies, wildcards, metadata, and Bukkit attachments. |
| Holograms | `holograms` | Enabled | Persistent text, item, and block displays with animation and visibility controls. |
| Announcements | `announcements` | Disabled | Rotating MiniMessage announcements with sound and empty-server controls. |
| Join and leave | `join-leave` | Enabled | Join/leave actions, native inline heads, and configurable large skin portraits for first-time and returning players. |
| Help | `help` | Enabled | Permission-aware interactive command pages. |
| Inventory backups | `snapshots` | Enabled | AxInventoryRestore-style category browsing, event/automatic/manual backups, search, shulker export, and safety-first restores on SQLite. |
| Scheduled restarts | `restart` | Disabled | Day-of-week or daily restart schedules, configurable warnings, relative or per-day console commands, delay-while-players-online, and a staff delay/cancel override. |
| Server-list MOTD | `server-list` | Enabled | Configurable two-line MiniMessage descriptions with timed rotation or random selection and live player-count placeholders. |

## Inventory backups

When enabled, Rivet captures player state on death, join, quit, world change, gamemode
change, supported-container close, a configurable automatic interval, and staff-requested
manual saves. Ender chest closes create a separate restorable ender-chest category. Empty
inventories and players with `rivet.snapshots.dontsave` are skipped. The default policy
retains backups for 14 days with unlimited per-category counts.

Inventory payloads are binary GZIP data in `plugins/Rivet/snapshots.db`. Identical payloads
share one content-addressed blob by default, while snapshot rows retain their independent
reason, time, and location. Expired rows, over-limit rows, and unreferenced blobs are
cleaned transactionally. Category and total save limits can prune older rows independently.

[`/snapshot`](commands.md#snapshot) provides category menus, item-content search, read-only
exact-layout previews, event-location teleporting, and shulker-box exports. Restores require
an online target, a separate permission, and confirmation by default. Before replacing an
inventory or ender chest, Rivet persists a matching safety backup; a failed safety save
aborts the restore. Restoring legacy death data can never set a player to zero health.

## Egg capture

A player with `rivet.eggcapture` can throw an ordinary egg at a mob to turn it into a spawn egg that recreates the original creature. Captures are refused, with the `messages.cannot-capture` message, when:

- the mob's type is listed in `blocked-types` in `settings/egg-capture.yml` (bosses, the Warden, Elder Guardians, villagers, Iron Golems and similar by default), or `allowed-types` is non-empty and does not list it;
- the mob is a pet tamed by another player, or has a custom name, unless the thrower has `rivet.eggcapture.bypass`;
- a protection plugin cancels the zero-damage hit Rivet fires on the thrower's behalf before capturing.

Captured eggs are tagged and cannot be used on a monster spawner or trial spawner, so they cannot change a spawner's mob type. Eggs captured before this tag existed are also recognised for survival players because they carry a full creature snapshot.

## Creeper restoration

When a creeper destroys blocks, Rivet records their block data in memory as a crater. The explosion produces harmless visual debris and suppresses ordinary drops to prevent duplication. A crater that is not repaired is released when it expires (30 minutes by default), when it is evicted because too many craters are stored (200 by default, oldest first), or when the server stops: escrowed container contents drop where each container stood, and every block whose space is still empty drops as an item at the explosion's normal vanilla drop chance.

An authorized administrator can create a Restoration Core with [`/restorationcore`](commands.md#restorationcore). A player right-clicks the core to throw it like a projectile. If it lands near a saved crater, one core is consumed and empty spaces are rebuilt with reverse-flight block animation. A missed core drops as an item and can be recovered.

Container restoration, container contents, other block-entity data, activation distance, timing, sounds, and particles are configurable in `settings/creeper-restoration.yml`. When contents are enabled, Rivet captures every affected container first and clears its live block inventory before Paper destroys it. The snapshot is therefore the only copy: contents cannot both fall as drops and later reappear during repair. Items held by lecterns, jukeboxes, chiseled bookshelves, decorated pots, shelves, and campfires are escrowed the same way when other block-entity data is restored. Double-chest halves are handled independently so an undamaged half is not emptied. If a player builds into a crater before it is repaired, that block is left alone and its escrowed container contents drop at its location instead of being discarded. Crater snapshots are intentionally not persisted across restarts; they are released on shutdown instead.

## Scheduled restarts

`settings/restart.yml` defines one or more schedule entries such as `Daily;04;00` or
`Friday;02;00`, each evaluated in the server's local time. Rivet computes the single
nearest upcoming occurrence across every configured entry and counts down to it.

Warnings broadcast at each configured `warning-seconds` value before the restart, using
the same configurable message, actionbar, title, and sound actions as every other Rivet
feature; a `[centered]` action auto-centers a chat line for banner-style announcements.
`commands` and `day-commands` run arbitrary console commands relative to the restart, in
seconds before or after it fires. If `delay.enabled` is set and at least
`delay.player-threshold` players are online when the restart is due, Rivet postpones it by
`delay.check-interval-seconds` at a time, up to `delay.max-delay-seconds` total, before
restarting anyway. Staff can override the countdown with
[`/restart delay`](commands.md#restart) or [`/restart cancel`](commands.md#restart), each
broadcasting the acting staff member and an optional reason; any player can check the time
remaining with [`/restart`](commands.md#restart). The module is disabled by default because
it dispatches console commands, including the restart command itself.

## Data and privacy

Rivet stores ordinary gameplay state in local YAML files under `plugins/Rivet/data/` and compressed inventory snapshots in `plugins/Rivet/snapshots.db`. Chat and private messages are never stored. The [`/sameip`](commands.md#sameip) command compares current session addresses without saving or displaying the raw address. Rivet uses bStats for anonymous usage metrics; server owners can use the global bStats opt-out.

## Autocrafter and beacon tools

Craft a hopper-fed autocrafter with a recipe picker, or build and dismantle beacon pyramids using inventory materials. See [recipes and instructions](automation-blocks.md).
