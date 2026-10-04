package com.zerofall.ezstorage.registry;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.item.PanelLink;
import com.zerofall.ezstorage.item.PanelTier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {

    private ModComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister
        .createDataComponents(Registries.DATA_COMPONENT_TYPE, EZStorage.MOD_ID);

    public static final Supplier<DataComponentType<PanelTier>> PANEL_TIER = COMPONENTS.registerComponentType(
        "panel_tier",
        b -> b.persistent(PanelTier.CODEC)
            .networkSynchronized(PanelTier.STREAM_CODEC));

    public static final Supplier<DataComponentType<Boolean>> PANEL_CRAFTING = COMPONENTS.registerComponentType(
        "panel_crafting",
        b -> b.persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL));

    public static final Supplier<DataComponentType<PanelLink>> PANEL_LINK = COMPONENTS.registerComponentType(
        "panel_link",
        b -> b.persistent(PanelLink.CODEC)
            .networkSynchronized(PanelLink.STREAM_CODEC));
}
