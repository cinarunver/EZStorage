package com.zerofall.ezstorage.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The storage core a portable storage panel is bound to. */
public record PanelLink(UUID inventoryId, ResourceKey<Level> dimension, BlockPos pos) {

    public static final Codec<PanelLink> CODEC = RecordCodecBuilder.create(
        i -> i.group(
            UUIDUtil.CODEC.fieldOf("inventory")
                .forGetter(PanelLink::inventoryId),
            ResourceKey.codec(Registries.DIMENSION)
                .fieldOf("dimension")
                .forGetter(PanelLink::dimension),
            BlockPos.CODEC.fieldOf("pos")
                .forGetter(PanelLink::pos))
            .apply(i, PanelLink::new));

    public static final StreamCodec<ByteBuf, PanelLink> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        PanelLink::inventoryId,
        ResourceKey.streamCodec(Registries.DIMENSION),
        PanelLink::dimension,
        BlockPos.STREAM_CODEC,
        PanelLink::pos,
        PanelLink::new);
}
