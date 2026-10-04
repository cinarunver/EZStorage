package com.zerofall.ezstorage.item;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Range tiers of the portable storage panel. */
public enum PanelTier implements StringRepresentable {

    TIER_1("tier_1", 16),
    TIER_2("tier_2", 32),
    TIER_3("tier_3", 128),
    INFINITY("infinity", 0);

    public static final Codec<PanelTier> CODEC = StringRepresentable.fromEnum(PanelTier::values);
    public static final StreamCodec<ByteBuf, PanelTier> STREAM_CODEC = ByteBufCodecs.VAR_INT
        .map(i -> values()[Math.floorMod(i, values().length)], PanelTier::ordinal);

    private final String name;
    private final int range;

    PanelTier(String name, int range) {
        this.name = name;
        this.range = range;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int range() {
        return range;
    }

    public boolean isInfinite() {
        return this == INFINITY;
    }

    public PanelTier next() {
        return this == INFINITY ? null : values()[ordinal() + 1];
    }

    /** The item that upgrades a panel <em>to</em> this tier. */
    public Item upgradeItem() {
        return switch (this) {
            case TIER_2 -> Items.ENDER_PEARL;
            case TIER_3 -> Items.ENDER_EYE;
            case INFINITY -> Items.NETHER_STAR;
            default -> Items.ENDER_EYE;
        };
    }
}
