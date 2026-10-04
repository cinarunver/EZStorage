package com.zerofall.ezstorage.registry;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.item.PortableStoragePanelItem;
import com.zerofall.ezstorage.item.StorageBoxItem;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EZStorage.MOD_ID);

    public static final DeferredItem<BlockItem> STORAGE_CORE = ITEMS.registerSimpleBlockItem(ModBlocks.STORAGE_CORE);
    public static final DeferredItem<StorageBoxItem> STORAGE_BOX = ITEMS.registerItem(
        "storage_box",
        p -> new StorageBoxItem(ModBlocks.STORAGE_BOX.get(), p.useBlockDescriptionPrefix()));
    public static final DeferredItem<StorageBoxItem> CONDENSED_STORAGE_BOX = ITEMS.registerItem(
        "condensed_storage_box",
        p -> new StorageBoxItem(ModBlocks.CONDENSED_STORAGE_BOX.get(), p.useBlockDescriptionPrefix()));
    public static final DeferredItem<StorageBoxItem> HYPER_STORAGE_BOX = ITEMS.registerItem(
        "hyper_storage_box",
        p -> new StorageBoxItem(ModBlocks.HYPER_STORAGE_BOX.get(), p.useBlockDescriptionPrefix()));
    public static final DeferredItem<BlockItem> INVENTORY_PROXY = ITEMS.registerSimpleBlockItem(ModBlocks.INVENTORY_PROXY);
    public static final DeferredItem<BlockItem> CRAFTING_BOX = ITEMS.registerSimpleBlockItem(ModBlocks.CRAFTING_BOX);

    public static final DeferredItem<PortableStoragePanelItem> PORTABLE_STORAGE_PANEL = ITEMS.registerItem(
        "portable_storage_panel",
        PortableStoragePanelItem::new,
        p -> p.stacksTo(1));
}
