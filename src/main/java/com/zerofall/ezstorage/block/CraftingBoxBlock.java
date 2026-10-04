package com.zerofall.ezstorage.block;

import com.mojang.serialization.MapCodec;

/** Adds a crafting grid to the storage core GUI. */
public class CraftingBoxBlock extends StorageMultiblockBlock {

    public static final MapCodec<CraftingBoxBlock> CODEC = simpleCodec(CraftingBoxBlock::new);

    public CraftingBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
        return CODEC;
    }
}
