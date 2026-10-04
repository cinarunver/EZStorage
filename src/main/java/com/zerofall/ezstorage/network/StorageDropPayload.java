package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageCoreMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Client to server: drop items straight out of storage. {@code amount <= 0} drops all of that type. */
public record StorageDropPayload(ItemResource resource, int amount) implements CustomPacketPayload {

    public static final Type<StorageDropPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "storage_drop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageDropPayload> STREAM_CODEC = StreamCodec.composite(
        ItemResource.STREAM_CODEC,
        StorageDropPayload::resource,
        ByteBufCodecs.VAR_INT,
        StorageDropPayload::amount,
        StorageDropPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageDropPayload payload, IPayloadContext context) {
        if (!payload.resource()
            .isEmpty() && !context.player()
                .isSpectator()
            && context.player().containerMenu instanceof StorageCoreMenu menu) {
            menu.dropFromStorage(payload.resource(), payload.amount());
        }
    }
}
