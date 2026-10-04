package com.zerofall.ezstorage.client;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.menu.StorageCoreMenu;
import com.zerofall.ezstorage.network.OpenPanelPayload;
import com.zerofall.ezstorage.network.PickBlockPayload;
import com.zerofall.ezstorage.network.SelectHotbarSlotPayload;
import com.zerofall.ezstorage.network.StorageContentsPayload;
import com.zerofall.ezstorage.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@Mod(value = EZStorage.MOD_ID, dist = Dist.CLIENT)
public class EZStorageClient {

    public EZStorageClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(EZStorageClient::registerScreens);
        modBus.addListener(EZStorageClient::registerKeys);
        modBus.addListener(EZStorageClient::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(EZStorageClient::onClientTick);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STORAGE.get(), StorageCoreScreen<StorageCoreMenu>::new);
        event.register(ModMenus.STORAGE_CRAFTING.get(), StorageCraftingScreen::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(ClientKeys.CATEGORY);
        event.register(ClientKeys.OPEN_PANEL);
        event.register(ClientKeys.BULK_ACTION);
        event.register(ClientKeys.PICK_BLOCK);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(StorageContentsPayload.TYPE, EZStorageClient::handleContents);
        event.register(SelectHotbarSlotPayload.TYPE, EZStorageClient::handleSelectSlot);
    }

    private static void handleContents(StorageContentsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StorageCoreMenu menu
            && menu.containerId == payload.containerId()) {
            menu.receiveContents(payload.inventory());
        }
    }

    private static void handleSelectSlot(SelectHotbarSlotPayload payload, IPayloadContext context) {
        if (payload.slot() >= 0 && payload.slot() < 9) {
            context.player()
                .getInventory()
                .setSelectedSlot(payload.slot());
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        while (ClientKeys.OPEN_PANEL.consumeClick()) {
            ClientPacketDistributor.sendToServer(OpenPanelPayload.INSTANCE);
        }
        while (ClientKeys.PICK_BLOCK.consumeClick()) {
            if (minecraft.screen != null || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            BlockPos pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            ItemStack target = new ItemStack(state.getBlock()
                .asItem());
            if (!target.isEmpty()) {
                ClientPacketDistributor.sendToServer(new PickBlockPayload(target));
            }
        }
    }
}
