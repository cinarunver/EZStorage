package com.zerofall.ezstorage.block;

import com.mojang.serialization.MapCodec;

import com.zerofall.ezstorage.blockentity.InventoryProxyBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exposes the storage to hoppers, pipes and other automation through the item handler capability. */
public class InventoryProxyBlock extends StorageMultiblockBlock implements EntityBlock {

    public static final MapCodec<InventoryProxyBlock> CODEC = simpleCodec(InventoryProxyBlock::new);

    public InventoryProxyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InventoryProxyBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
        return CODEC;
    }
}
