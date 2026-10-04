package com.zerofall.ezstorage.registry;

import java.util.function.Supplier;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.blockentity.InventoryProxyBlockEntity;
import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
        .create(Registries.BLOCK_ENTITY_TYPE, EZStorage.MOD_ID);

    public static final Supplier<BlockEntityType<StorageCoreBlockEntity>> STORAGE_CORE = BLOCK_ENTITY_TYPES.register(
        "storage_core",
        () -> new BlockEntityType<>(StorageCoreBlockEntity::new, ModBlocks.STORAGE_CORE.get()));

    public static final Supplier<BlockEntityType<InventoryProxyBlockEntity>> INVENTORY_PROXY = BLOCK_ENTITY_TYPES
        .register(
            "inventory_proxy",
            () -> new BlockEntityType<>(InventoryProxyBlockEntity::new, ModBlocks.INVENTORY_PROXY.get()));
}
