package com.zerofall.ezstorage.selftest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.DataResult;
import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;
import com.zerofall.ezstorage.config.EZConfig;
import com.zerofall.ezstorage.item.PanelTier;
import com.zerofall.ezstorage.item.PortableStoragePanelItem;
import com.zerofall.ezstorage.menu.StorageClickType;
import com.zerofall.ezstorage.menu.StorageCraftingMenu;
import com.zerofall.ezstorage.registry.ModBlocks;
import com.zerofall.ezstorage.registry.ModComponents;
import com.zerofall.ezstorage.registry.ModItems;
import com.zerofall.ezstorage.storage.StorageInventory;
import com.zerofall.ezstorage.storage.StorageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * In-game smoke test, run by CI on a dedicated server ({@code -Dezstorage.selftest=true}). Builds a storage system
 * in the overworld, exercises it through the same code paths players and automation use, logs one line per check
 * and stops the server. Not part of the mod jar.
 */
@EventBusSubscriber(modid = EZStorage.MOD_ID)
public final class SelfTest {

    private static final String TAG = "[EZSTORAGE-SELFTEST] ";
    private static final List<String> failures = new ArrayList<>();

    private SelfTest() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("ezstorage.selftest")) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            run(server);
            AutomationTest.setUp(server.overworld());
            ticksLeft = AutomationTest.TICKS;
        } catch (Throwable t) {
            fail("unexpected exception", t.toString());
            EZStorage.LOG.error(TAG + "exception", t);
            finish(server);
        }
    }

    private static int ticksLeft = -1;

    /** Lets hoppers run for a while before checking what they moved. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ticksLeft < 0) {
            return;
        }
        if (--ticksLeft == 0) {
            ticksLeft = -1;
            try {
                AutomationTest.verify(event.getServer()
                    .overworld());
            } catch (Throwable t) {
                fail("unexpected exception", t.toString());
                EZStorage.LOG.error(TAG + "exception", t);
            }
            finish(event.getServer());
        }
    }

    private static void finish(MinecraftServer server) {
        EZStorage.LOG.info(TAG + "RESULT: " + (failures.isEmpty() ? "PASSED" : "FAILED " + failures));
        server.halt(false);
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            EZStorage.LOG.info(TAG + "PASS " + name);
        } else {
            fail(name, detail);
        }
    }

    static void fail(String name, String detail) {
        failures.add(name);
        EZStorage.LOG.error(TAG + "FAIL " + name + ": " + detail);
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getChunk(0, 0);
        BlockPos core = new BlockPos(2, 200, 2);
        BlockPos box = core.east();
        BlockPos condensed = box.east();
        BlockPos crafting = core.west();
        BlockPos proxy = core.above();

        // --- Recipes -----------------------------------------------------------------------------
        for (String id : List.of(
            "storage_core",
            "storage_box",
            "condensed_storage_box",
            "hyper_storage_box",
            "hyper_storage_box_from_netherite",
            "inventory_proxy",
            "crafting_box",
            "portable_storage_panel",
            "panel_upgrade")) {
            ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = ResourceKey
                .create(Registries.RECIPE, Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, id));
            check("recipe loaded " + id, level.recipeAccess()
                .byKey(key)
                .isPresent(), "missing");
        }

        // --- Multiblock --------------------------------------------------------------------------
        level.setBlock(core, ModBlocks.STORAGE_CORE.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(box, ModBlocks.STORAGE_BOX.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(condensed, ModBlocks.CONDENSED_STORAGE_BOX.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(crafting, ModBlocks.CRAFTING_BOX.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(proxy, ModBlocks.INVENTORY_PROXY.get()
            .defaultBlockState(), Block.UPDATE_ALL);

        if (!(level.getBlockEntity(core) instanceof StorageCoreBlockEntity coreEntity)) {
            fail("core block entity", "not found");
            return;
        }
        StorageInventory inventory = coreEntity.getOrCreateInventory();
        check("inventory created", inventory != null, "null");
        if (inventory == null) {
            return;
        }
        coreEntity.rescan();
        long expectedCapacity = EZConfig.SERVER.basicCapacity.getAsInt() + EZConfig.SERVER.condensedCapacity.getAsInt();
        check("capacity from boxes", inventory.getCapacity() == expectedCapacity,
            "expected " + expectedCapacity + " got " + inventory.getCapacity());
        check("crafting box detected", coreEntity.hasCraftingBox(), "false");

        // --- Proxy capability & transactions -----------------------------------------------------
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, proxy, null);
        check("proxy capability", handler != null, "null");
        if (handler == null) {
            return;
        }
        ItemResource cobble = ItemResource.of(Items.COBBLESTONE);
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(cobble, 64, tx);
            check("insert in transaction", inserted == 64, "inserted " + inserted);
            // not committed -> rolled back on close
        }
        check("aborted transaction rolled back", inventory.getAmount(cobble) == 0, "amount " + inventory.getAmount(cobble));
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(cobble, 64, tx);
            tx.commit();
        }
        check("committed insert", inventory.getAmount(cobble) == 64, "amount " + inventory.getAmount(cobble));
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(cobble, 10, tx);
            tx.commit();
            check("extract", extracted == 10 && inventory.getAmount(cobble) == 54,
                "extracted " + extracted + " amount " + inventory.getAmount(cobble));
        }
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(cobble, Integer.MAX_VALUE / 2, tx);
            tx.commit();
            check("insert limited by capacity", inserted == expectedCapacity - 54, "inserted " + inserted);
        }
        try (Transaction tx = Transaction.openRoot()) {
            handler.extract(cobble, (int) expectedCapacity, tx);
            tx.commit();
        }
        check("empty after extracting all", inventory.isEmpty(), "size " + inventory.size());

        // --- Menu: storage clicks and crafting -------------------------------------------------------
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory()
            .clearContent();
        StorageCraftingMenu menu = new StorageCraftingMenu(1, player.getInventory(), inventory, p -> true);

        inventory.insert(new ItemStack(Items.COBBLESTONE, 100));
        menu.handleStorageClick(cobble, StorageClickType.PICKUP_STACK);
        check("pickup stack", menu.getCarried()
            .getCount() == 64 && inventory.getAmount(cobble) == 36,
            "carried " + menu.getCarried()
                .getCount() + " stored " + inventory.getAmount(cobble));
        menu.handleStorageClick(ItemResource.EMPTY, StorageClickType.PICKUP_STACK);
        check("deposit carried", menu.getCarried()
            .isEmpty() && inventory.getAmount(cobble) == 100, "stored " + inventory.getAmount(cobble));
        menu.handleStorageClick(cobble, StorageClickType.MOVE_ALL);
        check("move all to player", inventory.getAmount(cobble) == 0 && player.getInventory()
            .countItem(Items.COBBLESTONE) == 100, "stored " + inventory.getAmount(cobble));
        menu.importPlayerInventory(true);
        menu.importPlayerInventory(false);
        check("bulk import", inventory.getAmount(cobble) == 100, "stored " + inventory.getAmount(cobble));

        ItemResource planks = ItemResource.of(Items.OAK_PLANKS);
        inventory.insert(new ItemStack(Items.OAK_PLANKS, 40));
        List<List<ItemStack>> table = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            boolean used = i == 0 || i == 1 || i == 3 || i == 4;
            table.add(used ? List.of(new ItemStack(Items.OAK_PLANKS)) : List.of());
        }
        menu.fillGrid(table, false);
        check("fill grid from storage", inventory.getAmount(planks) == 36, "stored " + inventory.getAmount(planks));
        ItemStack result = menu.getSlot(StorageCraftingMenu.RESULT_SLOT)
            .getItem();
        check("crafting result", result.is(Items.CRAFTING_TABLE), "result " + result);
        menu.quickMoveStack(player, StorageCraftingMenu.RESULT_SLOT);
        int tables = player.getInventory()
            .countItem(Items.CRAFTING_TABLE);
        check("shift-craft refills from storage", tables == 10 && inventory.getAmount(planks) == 0,
            "tables " + tables + " planks " + inventory.getAmount(planks));
        inventory.insert(new ItemStack(Items.OAK_PLANKS, 3));
        menu.fillGrid(table, false);
        menu.clearGrid();
        check("clear grid back to storage", menu.getGrid()
            .isEmpty() && inventory.getAmount(planks) == 3, "planks " + inventory.getAmount(planks));
        menu.removed(player);

        // --- Panel upgrade recipe ---------------------------------------------------------------
        ItemStack panel = new ItemStack(ModItems.PORTABLE_STORAGE_PANEL.get());
        CraftingInput tierInput = CraftingInput.of(3, 1,
            List.of(panel, new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(Items.ENDER_PEARL)));
        ItemStack upgraded = level.recipeAccess()
            .getRecipeFor(RecipeType.CRAFTING, tierInput, level)
            .map(RecipeHolder::value)
            .map(r -> ((CraftingRecipe) r).assemble(tierInput))
            .orElse(ItemStack.EMPTY);
        check("panel tier upgrade", PortableStoragePanelItem.getTier(upgraded) == PanelTier.TIER_2,
            "result " + upgraded);
        CraftingInput craftingInput = CraftingInput.of(3, 1,
            List.of(panel, new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(ModItems.CRAFTING_BOX.get())));
        ItemStack withGrid = level.recipeAccess()
            .getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
            .map(r -> r.value()
                .assemble(craftingInput))
            .orElse(ItemStack.EMPTY);
        check("panel crafting upgrade", withGrid.getOrDefault(ModComponents.PANEL_CRAFTING.get(), false),
            "result " + withGrid);

        // --- Save data round trip ----------------------------------------------------------------
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        StorageManager manager = StorageManager.get(server);
        DataResult<Tag> encoded = StorageManager.CODEC.encodeStart(ops, manager);
        check("save data encodes", encoded.isSuccess(), encoded.error()
            .map(Object::toString)
            .orElse(""));
        StorageManager decoded = encoded.flatMap(tag -> StorageManager.CODEC.parse(ops, tag))
            .result()
            .orElse(null);
        StorageInventory reloaded = decoded == null ? null : decoded.get(inventory.getId());
        check("save data round trip", reloaded != null && reloaded.getAmount(cobble) == 100
            && reloaded.getAmount(planks) == 3
            && reloaded.getCapacity() == inventory.getCapacity(), "reloaded " + reloaded);

        // --- Multiblock shrink and removal -----------------------------------------------------------
        level.removeBlock(condensed, false);
        check("capacity shrinks when a box is removed",
            inventory.getCapacity() == EZConfig.SERVER.basicCapacity.getAsInt(), "capacity " + inventory.getCapacity());
        level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check("filled storage kept when core disappears", manager.get(inventory.getId()) != null, "deleted");
    }
}
