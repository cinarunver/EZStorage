package com.zerofall.ezstorage.client;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageCraftingMenu;
import com.zerofall.ezstorage.network.ClearGridPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class StorageCraftingScreen extends StorageCoreScreen<StorageCraftingMenu> {

    private static final Identifier TEXTURE = Identifier
        .fromNamespaceAndPath(EZStorage.MOD_ID, "textures/gui/storage_crafting.png");
    private static final int CLEAR_X = 99;
    private static final int CLEAR_Y = 114;
    private static final int CLEAR_SIZE = 8;

    public StorageCraftingScreen(StorageCraftingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 256);
    }

    @Override
    protected Identifier getBackgroundTexture() {
        return TEXTURE;
    }

    private boolean isOverClearButton(double mouseX, double mouseY) {
        return mouseX >= leftPos + CLEAR_X && mouseX < leftPos + CLEAR_X + CLEAR_SIZE && mouseY >= topPos + CLEAR_Y
            && mouseY < topPos + CLEAR_Y + CLEAR_SIZE;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos + CLEAR_X;
        int y = topPos + CLEAR_Y;
        boolean hovered = isOverClearButton(mouseX, mouseY);
        graphics.fill(x, y, x + CLEAR_SIZE, y + CLEAR_SIZE, hovered ? 0xFFB0B0B0 : 0xFF8B8B8B);
        graphics.outline(x, y, CLEAR_SIZE, CLEAR_SIZE, 0xFF373737);
        graphics.fill(x + 2, y + 3, x + CLEAR_SIZE - 2, y + 5, 0xFFFFFFFF);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isOverClearButton(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.ezstorage.clear_grid"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isOverClearButton(event.x(), event.y())) {
            ClientPacketDistributor.sendToServer(ClearGridPayload.INSTANCE);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
