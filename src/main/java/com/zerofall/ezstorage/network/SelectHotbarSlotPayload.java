package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: select a hotbar slot after picking a block from the storage. */
public record SelectHotbarSlotPayload(int slot) implements CustomPacketPayload {

    public static final Type<SelectHotbarSlotPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "select_hotbar_slot"));

    public static final StreamCodec<ByteBuf, SelectHotbarSlotPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT
        .map(SelectHotbarSlotPayload::new, SelectHotbarSlotPayload::slot);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
