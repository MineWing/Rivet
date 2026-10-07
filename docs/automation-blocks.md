# Autocrafter and beacon tools

Both features are enabled by default. No commands or extra permissions are required. Use `settings/gameplay.yml` to disable `autocrafter.enabled` or `beacon-tools.enabled`, then restart the server. Existing settings files do not need replacing; absent switches default to enabled.

## Autocrafter

Craft an autocrafter at a crafting table:

```text
Iron ingot | Redstone      | Iron ingot
Redstone   | Crafting table| Redstone
Iron ingot | Barrel        | Iron ingot
```

Place the barrel, then right-click it to open the autocrafter menu. The top tabs group recipes into **All recipes**, **Building**, **Redstone & utility**, **Tools & equipment**, **Food**, **Ingredients** and **Custom recipes**. The selected tab glows. Use the footer arrows to browse each category, or click an item in your own inventory to find recipes for that output. Category tabs reset the item filter; **Clear item filter** removes it within the current category.

Click a recipe to preview its ingredients in a crafting grid and see the output quantity. These are display items and cannot be taken. Click **Craft this recipe** to select it and start crafting. **Back to recipes** returns to the same category and page. The selected output glows in the recipe list. Click the current output in the footer to reopen its preview.

The footer shows the current machine status, output direction and occupied storage slots. A hologram above the barrel shows its selected output and live status. It distinguishes missing ingredients, a full or blocked output, a refused transfer, a missing recipe and paused crafting. Existing autocrafters gain holograms when their chunks load. Holograms disappear when the block breaks or its chunk unloads and return when it loads again.

The **Ingredient storage** button opens the barrel's inventory. Add ingredients yourself or feed them through hoppers. Sneak while holding a hopper to attach it. The barrel stores ingredients only; hoppers cannot pull them back out. You can remove ingredients through its storage menu.

The autocrafter attempts one craft every second while its chunk is loaded. Output goes through the barrel's facing side into a chest, barrel, hopper, dropper, dispenser or shulker box. Face it toward open space to drop the output instead. It pauses when the destination is full or obstructed. Direct output into another autocrafter is disabled.

Recipes consume their normal ingredients and produce their normal output quantities. Container leftovers, such as empty buckets, leave with the output. Named, enchanted and plugin items are preserved unless the recipe explicitly requires those exact items. Smelting, smithing and special dynamic recipes are not included.

Click **Pause crafting** to stop the machine while keeping its selected recipe. Click **Resume crafting** to restart it. The paused state also persists across restarts. Ingredients and the chosen recipe persist in the barrel across restarts and chunk unloads. Breaking the block returns its ingredients and the autocrafter item in survival. The picked-up autocrafter starts without a selected recipe when placed again. Autocrafters resist explosions and piston movement.

## Beacon maker

Craft a reusable beacon maker from an egg surrounded by eight ender pearls:

```text
Ender pearl | Ender pearl | Ender pearl
Ender pearl | Egg         | Ender pearl
Ender pearl | Ender pearl | Ender pearl
```

Hold the maker and right-click a block. The menu lets you cycle through iron, gold, emerald, diamond and netherite blocks, then choose the pyramid level. The bottom layer is centered in the block space next to the clicked face. Click the top face of the ground to build upward from it.

| Level | Mineral blocks | Beacons |
| --- | ---: | ---: |
| 1 | 9 | 1 |
| 2 | 34 | 1 |
| 3 | 83 | 1 |
| 4 | 164 | 1 |

All materials must be in your inventory, including in creative mode. Named or modified materials are not consumed. The maker is reusable. Each pyramid uses one mineral type. It requires empty, loaded space inside the world border and cannot overlap players or mobs. Normal beacon sky access rules still apply.

Construction checks the standard multi-block placement event so protection plugins can refuse it. Cancelled placement restores the original blocks and consumes no materials. An ascending particle animation and beacon sound mark successful construction.

## Beacon destroyer

Sneak-right-click any beacon to open the dismantle confirmation. No separate tool is required. Confirm to remove the beacon and its complete pyramid layers, up to level four. Mixed mineral layers are supported. Incomplete layers and blocks below them remain in place.

The confirmation shows how many pyramid blocks will be returned. Every block must pass a block-break protection event. If any block is denied, or the blocks change while the menu is open, dismantling stops. Returned materials go into your inventory; overflow drops at your feet. Descending portal particles and a beacon deactivation sound play when it succeeds.

Adventure and spectator players cannot build or dismantle beacons through these menus. Both actions require staying near the selected block.
