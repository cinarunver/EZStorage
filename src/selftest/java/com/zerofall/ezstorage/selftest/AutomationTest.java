package com.zerofall.ezstorage.selftest;

import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;
import com.zerofall.ezstorage.registry.ModBlocks;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Exercises the inventory proxy the way other blocks do: real hoppers (ticking), NeoForge's helper methods, and
 * code migrated from the legacy IItemHandler API.
 */
final class AutomationTest {

    static final int TICKS = 200;

    private static final BlockPos CORE = new BlockPos(20, 200, 20);
    private static final BlockPos BOX = CORE.west();
    private static final BlockPos PROXY = CORE.east();
    private static final BlockPos PUSH_HOPPER = PROXY.above();
    private static final BlockPos PULL_HOPPER = PROXY.below();

    private static StorageInventory inventory;

    private AutomationTest() {}

    private static ResourceHandler<ItemResource> proxyHandler(ServerLevel level, Direction side) {
        return level.getCapability(Capabilities.Item.BLOCK, PROXY, side);
    }

    static void setUp(ServerLevel level) {
        level.getChunk(CORE);
        level.setBlock(CORE, ModBlocks.STORAGE_CORE.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(BOX, ModBlocks.HYPER_STORAGE_BOX.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(PROXY, ModBlocks.INVENTORY_PROXY.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        StorageCoreBlockEntity core = (StorageCoreBlockEntity) level.getBlockEntity(CORE);
        inventory = core.getOrCreateInventory();
        core.rescan();

        for (Direction side : Direction.values()) {
            SelfTest.check("proxy capability from " + side, proxyHandler(level, side) != null, "null");
        }
        SelfTest.check("proxy capability without side", proxyHandler(level, null) != null, "null");
        ResourceHandler<ItemResource> handler = proxyHandler(level, Direction.UP);

        // NeoForge helpers used by many mods
        int stacked = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(Items.GLASS), 64, null);
        SelfTest.check("insertStacking new type", stacked == 64, "inserted " + stacked);
        stacked = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(Items.GLASS), 100, null);
        SelfTest.check("insertStacking existing type", stacked == 100, "inserted " + stacked);

        BlockPos chestPos = CORE.north(3);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        Container chest = (Container) level.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.SAND, 64));
        chest.setItem(1, new ItemStack(Items.SAND, 20));
        chest.setItem(2, new ItemStack(Items.STICK, 7));
        ResourceHandler<ItemResource> chestHandler = level.getCapability(Capabilities.Item.BLOCK, chestPos, null);
        int moved = ResourceHandlerUtil.moveStacking(chestHandler, handler, r -> true, Integer.MAX_VALUE, null);
        SelfTest.check("moveStacking from a chest", moved == 91 && inventory.getAmount(ItemResource.of(Items.SAND)) == 84,
            "moved " + moved);
        int extracted = ResourceHandlerUtil.moveStacking(handler, chestHandler, r -> r.is(Items.STICK), 5, null);
        SelfTest.check("moveStacking into a chest", extracted == 5, "moved " + extracted);

        // Code migrated from the legacy API through NeoForge's adapter
        IItemHandler legacy = IItemHandler.of(handler);
        ItemStack rest = insertItemStacked(legacy, new ItemStack(Items.GLASS, 64));
        SelfTest.check("legacy insertItemStacked existing type", rest.isEmpty(), "rest " + rest);
        rest = insertItemStacked(legacy, new ItemStack(Items.BRICK, 30));
        SelfTest.check("legacy insertItemStacked new type", rest.isEmpty(), "rest " + rest);
        rest = insertPerSlot(legacy, new ItemStack(Items.FLINT, 12));
        SelfTest.check("legacy per-slot insert", rest.isEmpty(), "rest " + rest);
        ItemStack fromLegacy = legacy.extractItem(indexOf(legacy, Items.BRICK), 10, false);
        SelfTest.check("legacy extract", fromLegacy.is(Items.BRICK) && fromLegacy.getCount() == 10,
            "extracted " + fromLegacy);

        // Real hoppers: one pushing into the proxy, one pulling from it
        level.setBlock(PUSH_HOPPER, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
        ((Container) level.getBlockEntity(PUSH_HOPPER)).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        inventory.insert(new ItemStack(Items.DIRT, 3));
        level.setBlock(PULL_HOPPER, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
    }

    static void verify(ServerLevel level) {
        Container push = (Container) level.getBlockEntity(PUSH_HOPPER);
        Container pull = (Container) level.getBlockEntity(PULL_HOPPER);
        SelfTest.check("hopper pushes into proxy", push.countItem(Items.COBBLESTONE) == 0,
            "left in hopper " + push.countItem(Items.COBBLESTONE));
        int pulled = 0;
        for (int i = 0; i < pull.getContainerSize(); i++) {
            pulled += pull.getItem(i)
                .getCount();
        }
        SelfTest.check("hopper pulls from proxy", pulled > 0, "pulled nothing");
        long cobble = inventory.getAmount(ItemResource.of(Items.COBBLESTONE)) + pull.countItem(Items.COBBLESTONE);
        SelfTest.check("hopper transfer conserves items", cobble == 10, "cobblestone total " + cobble);
    }

    /** The classic ItemHandlerHelper.insertItemStacked algorithm that many mods still carry around. */
    private static ItemStack insertItemStacked(IItemHandler inventory, ItemStack stack) {
        int slots = inventory.getSlots();
        for (int i = 0; i < slots && !stack.isEmpty(); i++) {
            if (ItemStack.isSameItemSameComponents(inventory.getStackInSlot(i), stack)) {
                stack = inventory.insertItem(i, stack, false);
            }
        }
        for (int i = 0; i < slots && !stack.isEmpty(); i++) {
            if (inventory.getStackInSlot(i)
                .isEmpty()) {
                stack = inventory.insertItem(i, stack, false);
            }
        }
        return stack;
    }

    /** Simulate-then-insert per slot, respecting slot limits and isItemValid, as many pipes do. */
    private static ItemStack insertPerSlot(IItemHandler inventory, ItemStack stack) {
        for (int i = 0; i < inventory.getSlots() && !stack.isEmpty(); i++) {
            if (!inventory.isItemValid(i, stack) || inventory.getSlotLimit(i) <= 0) {
                continue;
            }
            ItemStack simulated = inventory.insertItem(i, stack, true);
            if (simulated.getCount() < stack.getCount()) {
                stack = inventory.insertItem(i, stack, false);
            }
        }
        return stack;
    }

    private static int indexOf(IItemHandler inventory, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (inventory.getStackInSlot(i)
                .is(item)) {
                return i;
            }
        }
        return -1;
    }
}
