package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageCoreMenu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: move the whole main inventory (or hotbar) into storage. */
public record BulkImportPayload(boolean hotbar) implements CustomPacketPayload {

    public static final Type<BulkImportPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "bulk_import"));

    public static final StreamCodec<ByteBuf, BulkImportPayload> STREAM_CODEC = ByteBufCodecs.BOOL
        .map(BulkImportPayload::new, BulkImportPayload::hotbar);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BulkImportPayload payload, IPayloadContext context) {
        if (!context.player()
            .isSpectator() && context.player().containerMenu instanceof StorageCoreMenu menu) {
            menu.importPlayerInventory(payload.hotbar());
        }
    }
}
