package com.zerofall.ezstorage.blockentity;

import java.util.List;
import java.util.function.Supplier;

import com.zerofall.ezstorage.storage.StorageInventory;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Exposes a storage system to automation. Index {@code i < size-1} is the i-th stored item type; the last index
 * is an empty slot that accepts new item types while there is room (same layout as the 1.7.10 proxy port).
 */
public final class StorageResourceHandler implements ResourceHandler<ItemResource> {

    private final Supplier<StorageInventory> inventorySupplier;
    private final Journal journal = new Journal();

    public StorageResourceHandler(Supplier<StorageInventory> inventorySupplier) {
        this.inventorySupplier = inventorySupplier;
    }

    private final class Journal extends SnapshotJournal<Journal.Snapshot> {

        record Snapshot(StorageInventory inventory, List<StorageInventory.Entry> entries) {}

        @Override
        protected Snapshot createSnapshot() {
            StorageInventory inventory = inventorySupplier.get();
            return new Snapshot(inventory, inventory == null ? List.of() : inventory.createSnapshot());
        }

        @Override
        protected void revertToSnapshot(Snapshot snapshot) {
            if (snapshot.inventory() != null) {
                snapshot.inventory()
                    .restoreSnapshot(snapshot.entries());
            }
        }

        @Override
        protected void onRootCommit(Snapshot originalState) {
            if (originalState.inventory() != null) {
                originalState.inventory()
                    .onExternalCommit();
            }
        }
    }

    @Override
    public int size() {
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null) {
            return 0;
        }
        return inventory.size() + (inventory.getFreeSpace() > 0 && inventory.canAddNewType() ? 1 : 0);
    }

    @Override
    public ItemResource getResource(int index) {
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null || index < 0 || index >= inventory.size()) {
            return ItemResource.EMPTY;
        }
        return inventory.get(index)
            .resource();
    }

    @Override
    public long getAmountAsLong(int index) {
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null || index < 0 || index >= inventory.size()) {
            return 0;
        }
        return inventory.get(index)
            .count();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null || index < 0) {
            return 0;
        }
        long stored = index < inventory.size() ? inventory.get(index)
            .count() : 0;
        return stored + inventory.getFreeSpace();
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null || index < 0 || resource.isEmpty()) {
            return false;
        }
        if (index < inventory.size()) {
            return inventory.get(index)
                .resource()
                .equals(resource);
        }
        return index == inventory.size() && (inventory.indexOf(resource) >= 0 || inventory.canAddNewType());
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) {
            return 0;
        }
        return insert(resource, amount, transaction);
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null || inventory.insert(resource, amount, true) <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        return (int) inventory.insert(resource, amount, false);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null || index < 0 || index >= inventory.size() || !inventory.get(index)
            .resource()
            .equals(resource)) {
            return 0;
        }
        return extract(resource, amount, transaction);
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        StorageInventory inventory = inventorySupplier.get();
        if (inventory == null || inventory.extract(resource, amount, true) <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        return (int) inventory.extract(resource, amount, false);
    }
}
