package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageCraftingMenu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: empty the crafting grid into storage. */
public record ClearGridPayload() implements CustomPacketPayload {

    public static final ClearGridPayload INSTANCE = new ClearGridPayload();
    public static final Type<ClearGridPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "clear_grid"));
    public static final StreamCodec<ByteBuf, ClearGridPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearGridPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StorageCraftingMenu menu) {
            menu.clearGrid();
        }
    }
}
