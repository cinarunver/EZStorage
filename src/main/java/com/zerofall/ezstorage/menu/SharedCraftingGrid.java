package com.zerofall.ezstorage.menu;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A 3x3 crafting container backed by the storage system's persistent grid list. Every crafting menu opened on the
 * same storage gets its own instance over the same list, and every change is broadcast to all of them, so two
 * players can never craft from a stale result.
 */
public final class SharedCraftingGrid implements CraftingContainer {

    private final NonNullList<ItemStack> items;
    private final Runnable onChange;

    public SharedCraftingGrid(NonNullList<ItemStack> items, Runnable onChange) {
        this.items = items;
        this.onChange = onChange;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(items);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            onChange.run();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        onChange.run();
    }

    @Override
    public void setChanged() {
        onChange.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        onChange.run();
    }

    @Override
    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack stack : items) {
            contents.accountSimpleStack(stack);
        }
    }
}
