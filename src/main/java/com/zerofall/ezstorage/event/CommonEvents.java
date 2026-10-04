package com.zerofall.ezstorage.event;

import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;
import com.zerofall.ezstorage.registry.ModBlockEntities;
import com.zerofall.ezstorage.storage.StorageInventory;
import com.zerofall.ezstorage.storage.StorageSync;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CommonEvents {

    private CommonEvents() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            ModBlockEntities.INVENTORY_PROXY.get(),
            (proxy, side) -> proxy.getHandler());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        StorageSync.flush(event.getServer());
    }

    /** A storage core can only be broken while it is empty (runs on both sides so clients don't desync). */
    public static void onBreakBlock(BreakBlockEvent event) {
        if (!(event.getLevel()
            .getBlockEntity(event.getPos()) instanceof StorageCoreBlockEntity core)) {
            return;
        }
        boolean hasItems;
        if (event.getLevel()
            .isClientSide()) {
            hasItems = core.getStoredCount() > 0;
        } else {
            StorageInventory inventory = core.getInventory();
            hasItems = inventory != null && !inventory.isEmpty();
        }
        if (hasItems) {
            event.setCanceled(true);
        }
    }
}
