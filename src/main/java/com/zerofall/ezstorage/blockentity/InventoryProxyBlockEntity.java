package com.zerofall.ezstorage.blockentity;

import com.zerofall.ezstorage.registry.ModBlockEntities;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class InventoryProxyBlockEntity extends BlockEntity {

    private BlockPos corePos;
    private final StorageResourceHandler handler = new StorageResourceHandler(this::getInventory);

    public InventoryProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INVENTORY_PROXY.get(), pos, state);
    }

    void setCorePos(BlockPos corePos) {
        if (!corePos.equals(this.corePos)) {
            this.corePos = corePos;
            setChanged();
        }
    }

    /** The core this proxy is connected to, if it still considers this proxy part of its system. */
    public StorageCoreBlockEntity getCore() {
        if (corePos == null || level == null || !level.isLoaded(corePos)) {
            return null;
        }
        if (level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core && core.isMember(worldPosition)) {
            return core;
        }
        return null;
    }

    public StorageInventory getInventory() {
        StorageCoreBlockEntity core = getCore();
        return core == null ? null : core.getInventory();
    }

    public ResourceHandler<ItemResource> getHandler() {
        return handler;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable("core", BlockPos.CODEC, corePos);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        corePos = in.read("core", BlockPos.CODEC)
            .orElse(null);
    }
}
