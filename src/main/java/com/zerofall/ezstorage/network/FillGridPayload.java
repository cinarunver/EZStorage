package com.zerofall.ezstorage.network;

import java.util.List;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageCraftingMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: fill the crafting grid with a recipe (alternatives per grid slot) from a recipe viewer. */
public record FillGridPayload(List<List<ItemStack>> recipe, boolean maxTransfer) implements CustomPacketPayload {

    private static final int MAX_ALTERNATIVES = 64;

    public static final Type<FillGridPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "fill_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FillGridPayload> STREAM_CODEC = StreamCodec.composite(
        ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ALTERNATIVES))
            .apply(ByteBufCodecs.list(9)),
        FillGridPayload::recipe,
        ByteBufCodecs.BOOL,
        FillGridPayload::maxTransfer,
        FillGridPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FillGridPayload payload, IPayloadContext context) {
        if (!context.player()
            .isSpectator() && context.player().containerMenu instanceof StorageCraftingMenu menu) {
            menu.fillGrid(payload.recipe(), payload.maxTransfer());
        }
    }
}
