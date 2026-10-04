package com.zerofall.ezstorage.recipe;

import com.zerofall.ezstorage.item.PanelTier;
import com.zerofall.ezstorage.item.PortableStoragePanelItem;
import com.zerofall.ezstorage.registry.ModComponents;
import com.zerofall.ezstorage.registry.ModItems;
import com.zerofall.ezstorage.registry.ModRecipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shapeless upgrade of the portable storage panel: panel + redstone block + either the next tier's upgrade item
 * (ender pearl, ender eye, nether star) or a crafting box.
 */
public class PanelUpgradeRecipe extends CustomRecipe {

    public static final PanelUpgradeRecipe INSTANCE = new PanelUpgradeRecipe();

    private PanelUpgradeRecipe() {}

    private record Match(ItemStack panel, boolean tierUpgrade, boolean craftingUpgrade) {}

    private static Match find(CraftingInput input) {
        ItemStack panel = ItemStack.EMPTY;
        boolean redstone = false;
        int others = 0;
        ItemStack other = ItemStack.EMPTY;

        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof PortableStoragePanelItem && panel.isEmpty()) {
                panel = stack;
            } else if (stack.is(Items.REDSTONE_BLOCK) && !redstone) {
                redstone = true;
            } else {
                others++;
                other = stack;
            }
        }
        if (panel.isEmpty() || !redstone || others != 1) {
            return null;
        }

        PanelTier next = PortableStoragePanelItem.getTier(panel)
            .next();
        if (next != null && other.is(next.upgradeItem())) {
            return new Match(panel, true, false);
        }
        if (other.is(ModItems.CRAFTING_BOX.get()) && !PortableStoragePanelItem.hasCrafting(panel)) {
            return new Match(panel, false, true);
        }
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Match match = find(input);
        if (match == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = match.panel()
            .copyWithCount(1);
        if (match.tierUpgrade()) {
            result.set(
                ModComponents.PANEL_TIER.get(),
                PortableStoragePanelItem.getTier(match.panel())
                    .next());
        }
        if (match.craftingUpgrade()) {
            result.set(ModComponents.PANEL_CRAFTING.get(), true);
        }
        return result;
    }

    @Override
    public RecipeSerializer<PanelUpgradeRecipe> getSerializer() {
        return ModRecipes.PANEL_UPGRADE.get();
    }
}
