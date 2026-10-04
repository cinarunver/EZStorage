package com.zerofall.ezstorage.blockentity;

import com.zerofall.ezstorage.registry.ModBlockEntities;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Gives automation access to a storage system in two ways: the NeoForge item capability (preferred, transactional)
 * and a vanilla {@link WorldlyContainer}, for blocks that only look for vanilla inventories (as the 1.7.10 proxy port
 * was an {@code ISidedInventory}).
 */
public class InventoryProxyBlockEntity extends BlockEntity implements WorldlyContainer {

    private BlockPos corePos;
    private final StorageResourceHandler handler = new StorageResourceHandler(this::getInventory);
    private final StorageContainerView container = new StorageContainerView(this::getInventory, this::dropOverflow);

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

    private void dropOverflow(ItemStack stack) {
        if (level != null && !stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5, stack);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Vanilla container view
    // ---------------------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return container.size();
    }

    @Override
    public boolean isEmpty() {
        return container.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return container.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return container.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return container.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        container.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (container != null) {
            container.setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /**
     * Vanilla drops the contents of container block entities when they are removed. The proxy only mirrors the
     * storage, so dropping here would duplicate items: do nothing.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {}

    @Override
    public void clearContent() {
        // Never wipe a storage system because something asked its proxy to clear.
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return container.canPlaceItem(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int size = getContainerSize();
        int[] slots = new int[size];
        for (int i = 0; i < size; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return container.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return container.canTakeItem(slot);
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

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
