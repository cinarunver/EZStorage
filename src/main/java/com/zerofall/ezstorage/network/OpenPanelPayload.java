package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.item.PortableStoragePanelItem;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: open the storage of the first portable panel in the player's inventory (keybind). */
public record OpenPanelPayload() implements CustomPacketPayload {

    public static final OpenPanelPayload INSTANCE = new OpenPanelPayload();
    public static final Type<OpenPanelPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "open_panel"));
    public static final StreamCodec<ByteBuf, OpenPanelPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenPanelPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PortableStoragePanelItem.openFromInventory(player);
        }
    }
}
