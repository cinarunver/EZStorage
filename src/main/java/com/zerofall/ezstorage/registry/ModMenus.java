package com.zerofall.ezstorage.registry;

import java.util.function.Supplier;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageCoreMenu;
import com.zerofall.ezstorage.menu.StorageCraftingMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister
        .create(Registries.MENU, EZStorage.MOD_ID);

    public static final Supplier<MenuType<StorageCoreMenu>> STORAGE = MENU_TYPES
        .register("storage", () -> IMenuTypeExtension.create(StorageCoreMenu::fromNetwork));

    public static final Supplier<MenuType<StorageCraftingMenu>> STORAGE_CRAFTING = MENU_TYPES
        .register("storage_crafting", () -> IMenuTypeExtension.create(StorageCraftingMenu::fromNetwork));
}
