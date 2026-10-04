package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the full content of the storage shown in the open menu. */
public record StorageContentsPayload(int containerId, StorageInventory inventory) implements CustomPacketPayload {

    public static final Type<StorageContentsPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "storage_contents"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageContentsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        StorageContentsPayload::containerId,
        StorageInventory.CLIENT_STREAM_CODEC,
        StorageContentsPayload::inventory,
        StorageContentsPayload::new);

    /** Snapshots the inventory, so the payload never shares mutable state with the server thread. */
    public static StorageContentsPayload of(int containerId, StorageInventory inventory) {
        return new StorageContentsPayload(containerId, inventory.copyForClient());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
