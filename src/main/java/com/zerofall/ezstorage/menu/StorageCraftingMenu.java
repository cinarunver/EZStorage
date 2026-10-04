package com.zerofall.ezstorage.menu;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.zerofall.ezstorage.registry.ModMenus;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Storage core GUI with the crafting box's 3x3 grid, which refills itself from storage. */
public class StorageCraftingMenu extends StorageCoreMenu {

    public static final int RESULT_SLOT = StorageCoreMenu.PLAYER_INVENTORY_SLOT_COUNT;
    public static final int GRID_START = RESULT_SLOT + 1;
    public static final int GRID_END = GRID_START + 9;

    private final SharedCraftingGrid grid;
    private final ResultContainer result = new ResultContainer();
    private final ResultSlot resultSlot;
    private final Runnable gridListener = () -> slotsChanged(null);

    /** Server side. */
    public StorageCraftingMenu(int containerId, Inventory playerInventory, StorageInventory inventory,
        Predicate<Player> validator) {
        super(ModMenus.STORAGE_CRAFTING.get(), containerId, playerInventory, inventory, validator);
        this.grid = new SharedCraftingGrid(inventory.getCraftGrid(), inventory::onGridChanged);

        resultSlot = addSlot(new ResultSlot(player, grid, result, 0, 116, 132));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(grid, col + row * 3, 44 + col * 18, 114 + row * 18));
            }
        }

        if (!player.level()
            .isClientSide()) {
            inventory.addGridListener(gridListener);
        }
        slotsChanged(grid);
    }

    public static StorageCraftingMenu fromNetwork(int containerId, Inventory playerInventory,
        RegistryFriendlyByteBuf buf) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
        return new StorageCraftingMenu(containerId, playerInventory, new StorageInventory(id), p -> true);
    }

    @Override
    protected int playerInventoryY() {
        return 174;
    }

    @Override
    public int storageRows() {
        return 5;
    }

    public SharedCraftingGrid getGrid() {
        return grid;
    }

    public List<Slot> getGridSlots() {
        return slots.subList(GRID_START, GRID_END);
    }

    // ---------------------------------------------------------------------------------------------
    // Crafting result
    // ---------------------------------------------------------------------------------------------

    @Override
    public void slotsChanged(Container container) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        CraftingInput input = grid.asCraftInput();
        ItemStack output = ItemStack.EMPTY;
        if (!input.isEmpty()) {
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
            if (recipe.isPresent()) {
                result.setRecipeUsed(recipe.get());
                output = recipe.get()
                    .value()
                    .assemble(input);
            } else {
                result.setRecipeUsed(null);
            }
        }
        result.setItem(0, output);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId == RESULT_SLOT && !player.level()
            .isClientSide()) {
            ItemStack[] pattern = capturePattern();
            super.clicked(slotId, button, input, player);
            refillGrid(pattern);
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (index == RESULT_SLOT) {
            craftIntoPlayerInventory(player);
            return ItemStack.EMPTY;
        }
        if (index >= GRID_START && index < GRID_END) {
            slot.set(inventory.insert(slot.getItem()));
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    /** Shift-click on the result: craft up to one stack of output, refilling the grid from storage. */
    private void craftIntoPlayerInventory(Player player) {
        ItemStack first = resultSlot.getItem();
        if (first.isEmpty()) {
            return;
        }
        ItemStack target = first.copy();
        int crafted = 0;
        while (crafted < target.getMaxStackSize()) {
            ItemStack output = resultSlot.getItem();
            if (output.isEmpty() || !ItemStack.isSameItemSameComponents(output, target)) {
                return;
            }
            ItemStack[] pattern = capturePattern();
            ItemStack moving = output.copy();
            if (!moveItemStackTo(moving, playerSlotStart(), playerSlotEnd(), true)) {
                return; // player inventory is full
            }
            crafted += output.getCount();
            resultSlot.onQuickCraft(moving, output);
            resultSlot.onTake(player, output);
            if (!moving.isEmpty()) {
                // Like vanilla: whatever did not fit is dropped, and crafting stops.
                player.drop(moving, false);
                refillGrid(pattern);
                return;
            }
            refillGrid(pattern);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        inventory.removeGridListener(gridListener);
    }

    // ---------------------------------------------------------------------------------------------
    // Grid filling
    // ---------------------------------------------------------------------------------------------

    private ItemStack[] capturePattern() {
        ItemStack[] pattern = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack stack = grid.getItem(i);
            pattern[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return pattern;
    }

    /** Puts one matching item from storage into every grid slot that ran empty. */
    private void refillGrid(ItemStack[] pattern) {
        for (int i = 0; i < 9; i++) {
            if (pattern[i].isEmpty() || !grid.getItem(i)
                .isEmpty()) {
                continue;
            }
            ItemResource match = ItemResource.of(pattern[i]);
            if (inventory.extract(match, 1, false) == 1) {
                grid.setItem(i, match.toStack(1));
            }
        }
    }

    /** Moves the grid content into storage (or the player inventory / the ground if storage is full). */
    public void clearGrid() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = inventory.insert(stack);
            grid.setItem(i, ItemStack.EMPTY);
            if (!rest.isEmpty() && !player.getInventory()
                .add(rest)) {
                player.drop(rest, false);
            }
        }
    }

    /**
     * Fills the grid with a recipe requested by a recipe viewer. Every slot lists acceptable alternatives; items are
     * taken from storage first, then from the player inventory.
     *
     * @param maxTransfer fill up to a full stack per slot instead of a single item
     */
    public void fillGrid(List<List<ItemStack>> recipe, boolean maxTransfer) {
        clearGrid();
        int passes = maxTransfer ? 64 : 1;
        for (int pass = 0; pass < passes; pass++) {
            if (!fillOnePass(recipe, pass == 0)) {
                break;
            }
        }
    }

    /** Adds one item to every recipe slot; on the first pass partial results are kept, later passes are atomic. */
    private boolean fillOnePass(List<List<ItemStack>> recipe, boolean allowPartial) {
        ItemStack[] taken = new ItemStack[9];
        boolean[] fromPlayer = new boolean[9];
        boolean complete = true;
        for (int i = 0; i < 9 && i < recipe.size(); i++) {
            List<ItemStack> alternatives = recipe.get(i);
            if (alternatives.isEmpty()) {
                continue;
            }
            ItemStack current = grid.getItem(i);
            if (!current.isEmpty() && current.getCount() >= current.getMaxStackSize()) {
                complete = false;
                continue;
            }
            ItemStack got = takeIngredient(alternatives, current);
            if (got.isEmpty()) {
                got = takeIngredientFromPlayer(alternatives, current);
                fromPlayer[i] = !got.isEmpty();
            }
            if (got.isEmpty()) {
                complete = false;
            } else {
                taken[i] = got;
            }
        }

        if (!complete && !allowPartial) {
            for (int i = 0; i < 9; i++) {
                if (taken[i] == null) {
                    continue;
                }
                if (fromPlayer[i] && player.getInventory()
                    .add(taken[i])) {
                    continue;
                }
                ItemStack rest = inventory.insert(taken[i]);
                if (!rest.isEmpty()) {
                    player.drop(rest, false);
                }
            }
            return false;
        }

        for (int i = 0; i < 9; i++) {
            if (taken[i] == null) {
                continue;
            }
            ItemStack current = grid.getItem(i);
            if (current.isEmpty()) {
                grid.setItem(i, taken[i]);
            } else {
                grid.setItem(i, current.copyWithCount(current.getCount() + 1));
            }
        }
        return complete;
    }

    private ItemStack takeIngredient(List<ItemStack> alternatives, ItemStack current) {
        for (ItemStack alternative : alternatives) {
            if (alternative.isEmpty() || (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, alternative))) {
                continue;
            }
            ItemResource exact = ItemResource.of(alternative);
            if (inventory.extract(exact, 1, false) == 1) {
                return exact.toStack(1);
            }
            if (alternative.isDamageableItem() && current.isEmpty()) {
                ItemResource similar = inventory.findMatching(stored -> ItemStack.isSameItem(stored, alternative));
                if (!similar.isEmpty() && inventory.extract(similar, 1, false) == 1) {
                    return similar.toStack(1);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack takeIngredientFromPlayer(List<ItemStack> alternatives, ItemStack current) {
        Inventory playerInventory = player.getInventory();
        for (ItemStack alternative : alternatives) {
            if (alternative.isEmpty() || (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, alternative))) {
                continue;
            }
            for (int slot = 0; slot < 36; slot++) {
                ItemStack candidate = playerInventory.getItem(slot);
                if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, alternative)) {
                    return playerInventory.removeItem(slot, 1);
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
