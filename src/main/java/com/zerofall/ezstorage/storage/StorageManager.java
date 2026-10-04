package com.zerofall.ezstorage.storage;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.zerofall.ezstorage.EZStorage;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Owns every storage system of a world. Saved with the overworld's data storage, replacing the
 * per-inventory files of the 1.7.10 version.
 */
public final class StorageManager extends SavedData {

    public static final Codec<StorageManager> CODEC = StorageInventory.CODEC.listOf()
        .xmap(StorageManager::new, StorageManager::all);

    public static final SavedDataType<StorageManager> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "storages"),
        StorageManager::new,
        CODEC);

    private final Map<UUID, StorageInventory> inventories = new HashMap<>();
    /** Inventories changed since the last tick; flushed to viewers by {@link StorageSync}. */
    private final Set<StorageInventory> pendingSync = new LinkedHashSet<>();

    public StorageManager() {}

    private StorageManager(List<StorageInventory> loaded) {
        for (StorageInventory inventory : loaded) {
            track(inventory);
        }
    }

    public static StorageManager get(MinecraftServer server) {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(TYPE);
    }

    private List<StorageInventory> all() {
        return List.copyOf(inventories.values());
    }

    private void track(StorageInventory inventory) {
        inventories.put(inventory.getId(), inventory);
        inventory.setChangeListener(() -> {
            setDirty();
            pendingSync.add(inventory);
        });
    }

    public StorageInventory create() {
        StorageInventory inventory = new StorageInventory(UUID.randomUUID());
        track(inventory);
        setDirty();
        return inventory;
    }

    public StorageInventory get(UUID id) {
        return id == null ? null : inventories.get(id);
    }

    public void delete(UUID id) {
        StorageInventory removed = inventories.remove(id);
        if (removed != null) {
            removed.setChangeListener(() -> {});
            pendingSync.remove(removed);
            setDirty();
        }
    }

    /** Returns and clears the inventories that changed since the last call. */
    Set<StorageInventory> drainPendingSync() {
        if (pendingSync.isEmpty()) {
            return Set.of();
        }
        Set<StorageInventory> drained = new LinkedHashSet<>(pendingSync);
        pendingSync.clear();
        return drained;
    }
}
