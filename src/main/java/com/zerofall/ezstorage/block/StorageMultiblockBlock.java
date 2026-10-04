package com.zerofall.ezstorage.block;

import java.util.Set;

import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Any block that can be part of a storage system. Notifies the system's core when it is placed or removed. */
public class StorageMultiblockBlock extends Block {

    public StorageMultiblockBlock(Properties properties) {
        super(properties);
    }

    /** Refuse placement if the block would join two storage systems (each system may only have one core). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Set<BlockPos> cores = MultiblockScanner.findCores(level, MultiblockScanner.neighbors(pos));
        int allowed = this instanceof StorageCoreBlock ? 0 : 1;
        if (cores.size() > allowed) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            rescanCores(level, Set.of(pos));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
        boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        rescanCores(level, MultiblockScanner.neighbors(pos));
    }

    protected static void rescanCores(Level level, Iterable<BlockPos> starts) {
        for (BlockPos corePos : MultiblockScanner.findCores(level, starts)) {
            if (level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core) {
                core.rescan();
            }
        }
    }
}
