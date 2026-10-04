package com.zerofall.ezstorage;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.zerofall.ezstorage.config.EZConfig;
import com.zerofall.ezstorage.event.CommonEvents;
import com.zerofall.ezstorage.network.ModPayloads;
import com.zerofall.ezstorage.registry.ModBlockEntities;
import com.zerofall.ezstorage.registry.ModBlocks;
import com.zerofall.ezstorage.registry.ModComponents;
import com.zerofall.ezstorage.registry.ModItems;
import com.zerofall.ezstorage.registry.ModMenus;
import com.zerofall.ezstorage.registry.ModRecipes;
import com.zerofall.ezstorage.registry.ModTabs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(EZStorage.MOD_ID)
public class EZStorage {

    public static final String MOD_ID = "ezstorage";
    public static final Logger LOG = LogUtils.getLogger();

    public EZStorage(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENU_TYPES.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModTabs.TABS.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, EZConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, EZConfig.CLIENT_SPEC);

        modBus.addListener(ModPayloads::register);
        modBus.addListener(CommonEvents::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onBreakBlock);
    }
}
