package com.zerofall.ezstorage.menu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a click on the virtual storage grid should do. */
public enum StorageClickType {

    /** Left click: pick up a full stack (or deposit the carried stack). */
    PICKUP_STACK,
    /** Right click: pick up half a stack (or deposit the carried stack). */
    PICKUP_HALF,
    /** Shift + left click: move one stack into the player inventory. */
    QUICK_MOVE,
    /** Bulk key (default space) + click: move as much as fits into the player inventory. */
    MOVE_ALL;

    public static final StreamCodec<ByteBuf, StorageClickType> STREAM_CODEC = ByteBufCodecs.VAR_INT
        .map(i -> values()[Math.floorMod(i, values().length)], StorageClickType::ordinal);
}
