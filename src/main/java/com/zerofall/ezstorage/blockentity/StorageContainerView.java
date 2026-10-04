package com.zerofall.ezstorage.blockentity;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Presents a storage as a vanilla {@link net.minecraft.world.Container}, for blocks that look for vanilla inventories
 * instead of the item capability (many modded hoppers, pipes and machines).
 * <p>
 * Layout as in 1.7.10: one slot per stored item type, plus one free slot accepting anything while there is room.
 * Vanilla code mutates the stacks returned by {@code getItem} directly (e.g. a hopper calls {@code grow} and then
 * {@code setChanged}), so the view hands out live stacks, remembers what it showed, and turns every difference into an
 * exact insert/extract on the storage before the next access. Nothing is ever applied twice or lost silently.
 */
final class StorageContainerView {

    private static final ItemResource[] NO_RESOURCES = new ItemResource[0];
    private static final ItemStack[] NO_STACKS = new ItemStack[0];

    private final Supplier<StorageInventory> inventorySupplier;
    private final Consumer<ItemStack> overflow;

    private StorageInventory viewOf;
    private int viewVersion = -1;
    /** Resource per slot; the free slot (if any) is the last one and has no entry here. */
    private ItemResource[] resources = NO_RESOURCES;
    private ItemStack[] stacks = NO_STACKS;
    private int[] shownCounts = new int[0];
    private boolean hasFreeSlot;

    /** @param overflow receives items a caller forced in beyond the storage's capacity (dropped in the world) */
    StorageContainerView(Supplier<StorageInventory> inventorySupplier, Consumer<ItemStack> overflow) {
        this.inventorySupplier = inventorySupplier;
        this.overflow = overflow;
    }

    private void insertOrDrop(StorageInventory inventory, ItemStack stack) {
        ItemStack rest = inventory.insert(stack);
        if (!rest.isEmpty()) {
            overflow.accept(rest);
        }
    }

    private void insertOrDrop(StorageInventory inventory, ItemResource resource, int amount) {
        long inserted = inventory.insert(resource, amount, false);
        if (inserted < amount) {
            overflow.accept(resource.toStack((int) (amount - inserted)));
        }
    }

    /** Applies pending changes to handed-out stacks and makes sure the view matches the storage. */
    private StorageInventory sync() {
        applyPending();
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null) {
            clear();
            return null;
        }
        if (inventory != viewOf || inventory.getVersion() != viewVersion) {
            rebuild(inventory);
        }
        return inventory;
    }

    private void applyPending() {
        if (viewOf == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < resources.length; i++) {
            ItemStack stack = stacks[i];
            int now = stack.isEmpty() ? 0 : stack.getCount();
            int delta = now - shownCounts[i];
            if (delta > 0) {
                insertOrDrop(viewOf, resources[i], delta);
                changed = true;
            } else if (delta < 0) {
                viewOf.extract(resources[i], -delta, false);
                changed = true;
            }
        }
        if (changed) {
            viewVersion = -1; // force a rebuild
        }
    }

    private void rebuild(StorageInventory inventory) {
        int types = inventory.size();
        resources = new ItemResource[types];
        stacks = new ItemStack[types + 1];
        shownCounts = new int[types];
        for (int i = 0; i < types; i++) {
            StorageInventory.Entry entry = inventory.get(i);
            int shown = (int) Math.min(entry.count(), entry.resource()
                .getMaxStackSize());
            resources[i] = entry.resource();
            stacks[i] = entry.resource()
                .toStack(shown);
            shownCounts[i] = shown;
        }
        hasFreeSlot = inventory.getFreeSpace() > 0;
        if (hasFreeSlot) {
            stacks[types] = ItemStack.EMPTY;
        } else {
            ItemStack[] trimmed = new ItemStack[types];
            System.arraycopy(stacks, 0, trimmed, 0, types);
            stacks = trimmed;
        }
        viewOf = inventory;
        viewVersion = inventory.getVersion();
    }

    private void clear() {
        viewOf = null;
        viewVersion = -1;
        resources = NO_RESOURCES;
        stacks = NO_STACKS;
        shownCounts = new int[0];
        hasFreeSlot = false;
    }

    private boolean isFreeSlot(int slot) {
        return hasFreeSlot && slot == resources.length;
    }

    // ---------------------------------------------------------------------------------------------
    // Container operations
    // ---------------------------------------------------------------------------------------------

    int size() {
        sync();
        return stacks.length;
    }

    boolean isEmpty() {
        StorageInventory inventory = sync();
        return inventory == null || inventory.isEmpty();
    }

    ItemStack getItem(int slot) {
        sync();
        return slot >= 0 && slot < stacks.length ? stacks[slot] : ItemStack.EMPTY;
    }

    ItemStack removeItem(int slot, int amount) {
        StorageInventory inventory = sync();
        if (inventory == null || amount <= 0 || slot < 0 || slot >= resources.length) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = resources[slot];
        int taken = (int) inventory.extract(resource, Math.min(amount, shownCounts[slot]), false);
        viewVersion = -1;
        return taken <= 0 ? ItemStack.EMPTY : resource.toStack(taken);
    }

    ItemStack removeItemNoUpdate(int slot) {
        sync();
        if (slot < 0 || slot >= resources.length) {
            return ItemStack.EMPTY;
        }
        return removeItem(slot, shownCounts[slot]);
    }

    void setItem(int slot, ItemStack stack) {
        // Code that mutates the stack from getItem and then sets it back: the mutation is applied by sync() already.
        boolean handedOut = slot >= 0 && slot < stacks.length && stack == stacks[slot] && !stack.isEmpty();
        StorageInventory inventory = sync();
        if (inventory == null || slot < 0 || handedOut) {
            return;
        }
        if (slot >= stacks.length) {
            if (!stack.isEmpty()) {
                insertOrDrop(inventory, stack);
                viewVersion = -1;
            }
            return;
        }
        if (isFreeSlot(slot)) {
            if (!stack.isEmpty()) {
                insertOrDrop(inventory, stack);
            }
        } else {
            ItemResource resource = resources[slot];
            int shown = shownCounts[slot];
            if (!stack.isEmpty() && resource.matches(stack)) {
                int delta = stack.getCount() - shown;
                if (delta > 0) {
                    insertOrDrop(inventory, resource, delta);
                } else if (delta < 0) {
                    inventory.extract(resource, -delta, false);
                }
            } else {
                // The slot's content was taken out (and possibly replaced by something else).
                inventory.extract(resource, shown, false);
                if (!stack.isEmpty()) {
                    insertOrDrop(inventory, stack);
                }
            }
        }
        viewVersion = -1;
    }

    void setChanged() {
        sync();
    }

    boolean canPlaceItem(int slot, ItemStack stack) {
        StorageInventory inventory = sync();
        if (inventory == null || stack.isEmpty() || inventory.getFreeSpace() < stack.getCount()) {
            return false;
        }
        ItemResource resource = ItemResource.of(stack);
        if (isFreeSlot(slot)) {
            return inventory.indexOf(resource) >= 0 || inventory.canAddNewType();
        }
        return slot >= 0 && slot < resources.length && resources[slot].equals(resource);
    }

    boolean canTakeItem(int slot) {
        sync();
        return slot >= 0 && slot < resources.length;
    }
}
