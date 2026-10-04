package com.zerofall.ezstorage.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.recipe.PanelUpgradeRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {

    private ModRecipes() {}

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister
        .create(Registries.RECIPE_SERIALIZER, EZStorage.MOD_ID);

    public static final Supplier<RecipeSerializer<PanelUpgradeRecipe>> PANEL_UPGRADE = SERIALIZERS.register(
        "panel_upgrade",
        () -> new RecipeSerializer<>(
            MapCodec.unit(PanelUpgradeRecipe.INSTANCE),
            StreamCodec.unit(PanelUpgradeRecipe.INSTANCE)));
}
