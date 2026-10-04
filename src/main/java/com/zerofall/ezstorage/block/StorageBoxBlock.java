package com.zerofall.ezstorage.block;

import java.util.function.IntSupplier;

/** Adds capacity to the storage system it is attached to. */
public class StorageBoxBlock extends StorageMultiblockBlock {

    private final IntSupplier capacity;

    public StorageBoxBlock(IntSupplier capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity.getAsInt();
    }
}
