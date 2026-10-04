package com.zerofall.ezstorage.item;

import java.util.function.Consumer;

import com.zerofall.ezstorage.block.StorageBoxBlock;
import com.zerofall.ezstorage.config.EZConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Block item of the storage boxes; shows the capacity a box adds. */
public class StorageBoxItem extends BlockItem {

    public StorageBoxItem(StorageBoxBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
        Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        if (EZConfig.SERVER_SPEC.isLoaded() && getBlock() instanceof StorageBoxBlock box) {
            tooltip.accept(
                Component.translatable("tooltip.ezstorage.capacity", String.format("%,d", box.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
