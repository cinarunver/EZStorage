# Porting notes: 1.7.10 (GTNH) → Minecraft 26.2 (NeoForge)

This branch is a rewrite of Simple Storage for Minecraft 26.2 on NeoForge 26.2.0.88 (Java 25, ModDevGradle).
Gameplay and GUI follow the 1.7.10 version; the code underneath is new because almost every API it used is gone.
This file maps the old classes to their replacements and lists what changed on purpose.

## Where things went

| 1.7.10 | 26.2 | Notes |
|---|---|---|
| `EZInventory` (list of `ItemStack`, int sizes) | `storage/StorageInventory` | Entries are `(ItemResource, long count)` records; counts are no longer capped at `int`. |
| `EZInventoryManager` + one `.dat` file per storage | `storage/StorageManager` (`SavedData`) | Saved in the world's `data` folder by the game's normal save cycle instead of ad-hoc threads. |
| `EZInventoryManager.sendToClients` | `storage/StorageSync` | Changes are batched and sent once per tick to players with the GUI open. |
| `TileEntityStorageCore` | `blockentity/StorageCoreBlockEntity` | Stores only the storage UUID; also syncs the stored amount so clients can't start mining a filled core. |
| `TileEntityInventoryProxy` (`ISidedInventory`) | `blockentity/InventoryProxyBlockEntity` + `StorageResourceHandler` | Exposed through NeoForge's transactional `ResourceHandler<ItemResource>` capability (`Capabilities.Item.BLOCK`). Same slot layout as before: one slot per item type plus a free slot. |
| `StorageMultiblock` recursive scan | `block/MultiblockScanner` | Bounded breadth-first search; never loads chunks. |
| `BlockStorage*`, `BlockCraftingBox`, `BlockInventoryProxy`, `BlockStorageCore` | `block/*` | Placement that would join two systems is refused (as before). |
| `ContainerStorageCore` / `ContainerStorageCoreCrafting` | `menu/StorageCoreMenu` / `menu/StorageCraftingMenu` | The crafting grid is one shared list per storage (`SharedCraftingGrid`); every open menu is notified on change. |
| `GuiStorageCore` / `GuiCraftingCore` | `client/StorageCoreScreen` / `client/StorageCraftingScreen` | Ported to the 26.2 `GuiGraphicsExtractor` rendering API. Same layout, textures, side buttons and keys. |
| `SimpleNetworkWrapper` messages | `network/*Payload` | `CustomPacketPayload` records; clicks identify items by `ItemResource`, not by list index. |
| `ItemPortableStoragePanel` (metadata + NBT) | `item/PortableStoragePanelItem` | Tier, crafting upgrade and link are data components (`panel_tier`, `panel_crafting`, `panel_link`). |
| `PortableStoragePanelUpgradeRecipe` (`IRecipe`) | `recipe/PanelUpgradeRecipe` (`CustomRecipe`) | `data/ezstorage/recipe/panel_upgrade.json`. |
| GTNHLib `@Config` | `config/EZConfig` (`ModConfigSpec`) | Capacities and type limit are **server** config; GUI settings are client config. |
| Shaped ore recipes in code | `data/ezstorage/recipe/*.json` | Ore dictionary names became `c:` / vanilla tags. |
| `.lang` files | `assets/ezstorage/lang/*.json` | Keys renamed to 26.x conventions; en_us, de_de, zh_cn carried over. |

## Integrations

| 1.7.10 | 26.2 |
|---|---|
| NEI overlay / one-click crafting | **JEI**: "+" transfer into the crafting grid (storage first, then player inventory; missing items are highlighted), R/U lookups on stored items |
| NEI search sync | JEI search sync (search mode button: Auto / JEI sync / Standard) |
| Waila, Jabba, Crafting Tweaks, Et Futurum, AE2 storage bus, Baubles | Not ported (those mods don't exist on 26.2 in that form). Any mod that reads the standard item capability, including modern AE2's storage bus, can use the inventory proxy. |

The Baubles-based features (open-terminal and pick-block keybinds) now look for a portable panel anywhere in the
player's inventory.

## Intentional behaviour changes

- Breaking a filled core is still prevented. If a filled core disappears anyway (commands, other mods), its contents are
  kept in the save data instead of being deleted.
- Two players using the same crafting box can no longer duplicate items through a stale crafting result.
- Shift-crafting from the result slot crafts up to one stack per click and refills the grid from storage, like before.
- The experimental Storage Panel and Storage Cable blocks (disabled by default in 1.7.10) are not ported.
- Old 1.7.10 worlds are not migrated.

## Testing

`./gradlew build` compiles the mod. CI additionally runs two in-game self tests from the `selftest` source set
(not shipped in the jar):

- `./gradlew runSelftest` — dedicated server: recipes, multiblock capacity, proxy capability and transactions,
  storage clicks, crafting grid fill and shift-craft refill, panel upgrades, save data round trip.
- `./gradlew runSelftestClient` — client on a virtual display (`xvfb-run`): opens the GUI through the server and checks
  content sync, cursor pickup/deposit, JEI bridge and recipe transfer; saves screenshots.
