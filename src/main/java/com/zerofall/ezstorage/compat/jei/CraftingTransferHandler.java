package com.zerofall.ezstorage.compat.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.zerofall.ezstorage.menu.StorageCraftingMenu;
import com.zerofall.ezstorage.network.FillGridPayload;
import com.zerofall.ezstorage.registry.ModMenus;
import com.zerofall.ezstorage.storage.StorageInventory;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Moves a JEI crafting recipe into the crafting box grid. The client only checks availability (storage contents +
 * player inventory) and sends the alternatives per slot; the server takes the items.
 */
public class CraftingTransferHandler implements IRecipeTransferHandler<StorageCraftingMenu, RecipeHolder<CraftingRecipe>> {

    private static final int MAX_ALTERNATIVES = 64;

    private final IRecipeTransferHandlerHelper helper;

    public CraftingTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends StorageCraftingMenu> getContainerClass() {
        return StorageCraftingMenu.class;
    }

    @Override
    public Optional<MenuType<StorageCraftingMenu>> getMenuType() {
        return Optional.of(ModMenus.STORAGE_CRAFTING.get());
    }

    @Override
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, StorageCraftingMenu> context,
        boolean doTransfer) {
        IRecipeTransferError error = transfer(context.getContainer(), context.getRecipeSlots(), context.getPlayer(),
            context.isMaxTransfer(), doTransfer);
        if (doTransfer) {
            context.completeRecipeTransfer(error == null ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
        }
        return error;
    }

    @Override
    @SuppressWarnings("removal")
    public IRecipeTransferError transferRecipe(StorageCraftingMenu container, RecipeHolder<CraftingRecipe> recipe,
        IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        return transfer(container, recipeSlots, player, maxTransfer, doTransfer);
    }

    private IRecipeTransferError transfer(StorageCraftingMenu menu, IRecipeSlotsView recipeSlots, Player player,
        boolean maxTransfer, boolean doTransfer) {
        List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        if (inputs.size() > 9) {
            return helper.createInternalError();
        }

        List<List<ItemStack>> recipe = new ArrayList<>(9);
        for (IRecipeSlotView slot : inputs) {
            recipe.add(
                slot.getItemStacks()
                    .filter(stack -> !stack.isEmpty())
                    .limit(MAX_ALTERNATIVES)
                    .map(stack -> stack.copyWithCount(1))
                    .toList());
        }
        while (recipe.size() < 9) {
            recipe.add(List.of());
        }

        List<IRecipeSlotView> missing = findMissing(menu, player, inputs, recipe);
        if (!missing.isEmpty()) {
            return helper.createUserErrorForMissingSlots(Component.translatable("jei.tooltip.error.recipe.transfer.missing"),
                missing);
        }

        if (doTransfer) {
            ClientPacketDistributor.sendToServer(new FillGridPayload(recipe, maxTransfer));
        }
        return null;
    }

    /** Greedy availability check against the client copy of the storage, the grid and the player inventory. */
    private static List<IRecipeSlotView> findMissing(StorageCraftingMenu menu, Player player, List<IRecipeSlotView> inputs,
        List<List<ItemStack>> recipe) {
        Map<ItemResource, Long> available = new HashMap<>();
        StorageInventory inventory = menu.getInventory();
        for (StorageInventory.Entry entry : inventory.entries()) {
            available.merge(entry.resource(), entry.count(), Long::sum);
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = menu.getGrid()
                .getItem(i);
            if (!stack.isEmpty()) {
                available.merge(ItemResource.of(stack), (long) stack.getCount(), Long::sum);
            }
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory()
                .getItem(i);
            if (!stack.isEmpty()) {
                available.merge(ItemResource.of(stack), (long) stack.getCount(), Long::sum);
            }
        }

        List<IRecipeSlotView> missing = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            List<ItemStack> alternatives = recipe.get(i);
            if (alternatives.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (ItemStack alternative : alternatives) {
                ItemResource resource = ItemResource.of(alternative);
                Long count = available.get(resource);
                if (count != null && count > 0) {
                    available.put(resource, count - 1);
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(inputs.get(i));
            }
        }
        return missing;
    }
}
