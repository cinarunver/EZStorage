package com.zerofall.ezstorage.compat.jei;

import java.util.List;
import java.util.Optional;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.client.RecipeViewerBridge;
import com.zerofall.ezstorage.client.StorageCoreScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class EZJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "jei");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CraftingTransferHandler(registration.getTransferHelper()),
            RecipeTypes.CRAFTING);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(StorageCoreScreen.class, new StorageScreenHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeViewerBridge.install(
            () -> runtime.getIngredientFilter()
                .getFilterText(),
            text -> runtime.getIngredientFilter()
                .setFilterText(text));
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeViewerBridge.uninstall();
    }

    /** Lets JEI see the virtual storage grid (R/U lookups) and keeps it clear of the side buttons. */
    private static final class StorageScreenHandler implements IGuiContainerHandler<StorageCoreScreen<?>> {

        @Override
        public List<Rect2i> getGuiExtraAreas(StorageCoreScreen<?> screen) {
            int[] area = screen.getSideButtonArea();
            return List.of(new Rect2i(area[0], area[1], area[2], area[3]));
        }

        @Override
        public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
            IClickableIngredientFactory builder, StorageCoreScreen<?> screen, double mouseX, double mouseY) {
            ItemStack stack = screen.getStoredItemAt(mouseX, mouseY);
            int[] area = screen.getStoredCellArea(mouseX, mouseY);
            if (stack.isEmpty() || area == null) {
                return Optional.empty();
            }
            return builder.createBuilder(stack)
                .buildWithArea(area[0], area[1], area[2], area[3]);
        }
    }
}
