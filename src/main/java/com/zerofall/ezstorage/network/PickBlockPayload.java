package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.item.PortableStoragePanelItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: pull the looked-at block out of the storage linked to a panel the player carries. */
public record PickBlockPayload(ItemStack target) implements CustomPacketPayload {

    public static final Type<PickBlockPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "pick_block"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickBlockPayload> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC
        .map(PickBlockPayload::new, PickBlockPayload::target);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PickBlockPayload payload, IPayloadContext context) {
        if (!payload.target()
            .isEmpty() && context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            PortableStoragePanelItem.pickBlock(player, payload.target());
        }
    }
}
