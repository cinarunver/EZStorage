package com.zerofall.ezstorage.registry;

import java.util.function.Supplier;

import com.zerofall.ezstorage.EZStorage;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {

    private ModTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister
        .create(Registries.CREATIVE_MODE_TAB, EZStorage.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ezstorage"))
            .icon(
                () -> ModItems.STORAGE_CORE.get()
                    .getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.STORAGE_CORE.get());
                output.accept(ModItems.STORAGE_BOX.get());
                output.accept(ModItems.CONDENSED_STORAGE_BOX.get());
                output.accept(ModItems.HYPER_STORAGE_BOX.get());
                output.accept(ModItems.INVENTORY_PROXY.get());
                output.accept(ModItems.CRAFTING_BOX.get());
                output.accept(ModItems.PORTABLE_STORAGE_PANEL.get());
            })
            .build());
}
