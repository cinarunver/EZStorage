package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageClickType;
import com.zerofall.ezstorage.menu.StorageCoreMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Client to server: a click on the virtual storage grid (resource may be empty for clicks on free cells). */
public record StorageClickPayload(ItemResource resource, StorageClickType clickType) implements CustomPacketPayload {

    public static final Type<StorageClickPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "storage_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageClickPayload> STREAM_CODEC = StreamCodec.composite(
        ItemResource.STREAM_CODEC,
        StorageClickPayload::resource,
        StorageClickType.STREAM_CODEC,
        StorageClickPayload::clickType,
        StorageClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageClickPayload payload, IPayloadContext context) {
        if (!context.player()
            .isSpectator() && context.player().containerMenu instanceof StorageCoreMenu menu) {
            menu.handleStorageClick(payload.resource(), payload.clickType());
        }
    }
}
