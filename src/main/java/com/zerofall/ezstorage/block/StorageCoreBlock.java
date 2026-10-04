package com.zerofall.ezstorage.block;

import com.mojang.serialization.MapCodec;
import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;
import com.zerofall.ezstorage.menu.StorageMenus;
import com.zerofall.ezstorage.registry.ModBlockEntities;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The heart of a storage system. Can only be broken while empty. */
public class StorageCoreBlock extends StorageMultiblockBlock implements EntityBlock {

    public static final MapCodec<StorageCoreBlock> CODEC = simpleCodec(StorageCoreBlock::new);

    public StorageCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCoreBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
        BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.STORAGE_CORE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<StorageCoreBlockEntity>) StorageCoreBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
        BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core) {
            StorageInventory inventory = core.getOrCreateInventory();
            if (inventory != null) {
                StorageMenus.open(serverPlayer, inventory, core.hasCraftingBox(), StorageMenus.blockValidator(core));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Clients know the stored amount from the block entity's update tag, so mining feedback matches the server. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core && core.getStoredCount() > 0) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
        return CODEC;
    }
}
