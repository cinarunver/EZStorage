package com.zerofall.ezstorage;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(EZStorage.MOD_ID)
public class EZStorage {
    public static final String MOD_ID = "ezstorage";
    public static final Logger LOG = LogUtils.getLogger();

    public EZStorage(IEventBus modBus, ModContainer container) {
        LOG.info("Simple Storage bootstrap");
    }
}
