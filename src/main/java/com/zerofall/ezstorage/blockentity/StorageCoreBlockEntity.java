package com.zerofall.ezstorage.blockentity;

import java.util.Set;
import java.util.UUID;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.block.MultiblockScanner;
import com.zerofall.ezstorage.registry.ModBlockEntities;
import com.zerofall.ezstorage.storage.StorageInventory;
import com.zerofall.ezstorage.storage.StorageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class StorageCoreBlockEntity extends BlockEntity {

    /** 20 ticks * 20 seconds, as in the original mod. */
    private static final int RESCAN_INTERVAL = 400;
    private static final int CLIENT_SYNC_INTERVAL = 20;

    private UUID inventoryId;
    private boolean hasCraftingBox;
    private Set<BlockPos> members = Set.of();
    private int ticks;

    // Mirrored to clients through the update tag.
    private long storedCount;
    private long capacity;

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_CORE.get(), pos, state);
    }

    // ---------------------------------------------------------------------------------------------
    // Inventory access (server)
    // ---------------------------------------------------------------------------------------------

    public StorageInventory getInventory() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return StorageManager.get(serverLevel.getServer())
            .get(inventoryId);
    }

    public StorageInventory getOrCreateInventory() {
        StorageInventory inventory = getInventory();
        if (inventory == null && level instanceof ServerLevel serverLevel) {
            inventory = StorageManager.get(serverLevel.getServer())
                .create();
            inventoryId = inventory.getId();
            setChanged();
            rescan();
        }
        return inventory;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public boolean hasCraftingBox() {
        return hasCraftingBox;
    }

    public boolean isMember(BlockPos pos) {
        return members.contains(pos);
    }

    /** Client-safe: the stored amount as last synced. */
    public long getStoredCount() {
        return storedCount;
    }

    public long getCapacity() {
        return capacity;
    }

    // ---------------------------------------------------------------------------------------------
    // Multiblock
    // ---------------------------------------------------------------------------------------------

    public void rescan() {
        if (level == null || level.isClientSide()) {
            return;
        }
        MultiblockScanner.Result result = MultiblockScanner.scan(level, worldPosition);
        members = result.members();
        hasCraftingBox = result.hasCraftingBox();
        for (BlockPos proxyPos : result.proxies()) {
            if (level.getBlockEntity(proxyPos) instanceof InventoryProxyBlockEntity proxy) {
                proxy.setCorePos(worldPosition);
            }
        }
        StorageInventory inventory = getInventory();
        if (inventory != null) {
            inventory.setCapacity(result.capacity());
        }
        updateClientData(true);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StorageCoreBlockEntity core) {
        if (core.ticks % RESCAN_INTERVAL == 0) {
            core.rescan();
        } else if (core.ticks % CLIENT_SYNC_INTERVAL == 0) {
            core.updateClientData(false);
        }
        core.ticks++;
    }

    private void updateClientData(boolean force) {
        StorageInventory inventory = getInventory();
        long newStored = inventory == null ? 0 : inventory.getTotalCount();
        long newCapacity = inventory == null ? 0 : inventory.getCapacity();
        if (force || newStored != storedCount || newCapacity != capacity) {
            storedCount = newStored;
            capacity = newCapacity;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Removal
    // ---------------------------------------------------------------------------------------------

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel) || inventoryId == null) {
            return;
        }
        StorageManager manager = StorageManager.get(serverLevel.getServer());
        StorageInventory inventory = manager.get(inventoryId);
        if (inventory == null) {
            return;
        }
        if (inventory.isEmpty()) {
            manager.delete(inventoryId);
        } else {
            // Breaking a filled core is prevented; if it still disappears (commands, other mods) keep the data.
            EZStorage.LOG.warn(
                "Storage core at {} was removed while holding {} items; keeping storage {} in the save data.",
                pos,
                inventory.getTotalCount(),
                inventoryId);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence & sync
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable("inventory", UUIDUtil.CODEC, inventoryId);
        out.putBoolean("crafting", hasCraftingBox);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        inventoryId = in.read("inventory", UUIDUtil.CODEC)
            .orElse(null);
        hasCraftingBox = in.getBooleanOr("crafting", false);
        storedCount = in.getLongOr("stored", storedCount);
        capacity = in.getLongOr("capacity", capacity);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("stored", storedCount);
        tag.putLong("capacity", capacity);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
