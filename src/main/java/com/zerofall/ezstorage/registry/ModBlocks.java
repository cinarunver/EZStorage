package com.zerofall.ezstorage.registry;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.block.CraftingBoxBlock;
import com.zerofall.ezstorage.block.InventoryProxyBlock;
import com.zerofall.ezstorage.block.StorageBoxBlock;
import com.zerofall.ezstorage.block.StorageCoreBlock;
import com.zerofall.ezstorage.config.EZConfig;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EZStorage.MOD_ID);

    public static final DeferredBlock<StorageCoreBlock> STORAGE_CORE = BLOCKS.registerBlock(
        "storage_core",
        StorageCoreBlock::new,
        p -> p.mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .strength(2.0F, 6000.0F));

    public static final DeferredBlock<StorageBoxBlock> STORAGE_BOX = BLOCKS.registerBlock(
        "storage_box",
        p -> new StorageBoxBlock(() -> EZConfig.SERVER.basicCapacity.getAsInt(), p),
        p -> p.mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .strength(2.0F));

    public static final DeferredBlock<StorageBoxBlock> CONDENSED_STORAGE_BOX = BLOCKS.registerBlock(
        "condensed_storage_box",
        p -> new StorageBoxBlock(() -> EZConfig.SERVER.condensedCapacity.getAsInt(), p),
        p -> p.mapColor(MapColor.METAL)
            .sound(SoundType.METAL)
            .strength(2.0F));

    public static final DeferredBlock<StorageBoxBlock> HYPER_STORAGE_BOX = BLOCKS.registerBlock(
        "hyper_storage_box",
        p -> new StorageBoxBlock(() -> EZConfig.SERVER.hyperCapacity.getAsInt(), p),
        p -> p.mapColor(MapColor.COLOR_BLACK)
            .sound(SoundType.METAL)
            .strength(2.0F, 1200.0F));

    public static final DeferredBlock<InventoryProxyBlock> INVENTORY_PROXY = BLOCKS.registerBlock(
        "inventory_proxy",
        InventoryProxyBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .sound(SoundType.METAL)
            .strength(2.0F));

    public static final DeferredBlock<CraftingBoxBlock> CRAFTING_BOX = BLOCKS.registerBlock(
        "crafting_box",
        CraftingBoxBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .sound(SoundType.METAL)
            .strength(2.0F));
}
