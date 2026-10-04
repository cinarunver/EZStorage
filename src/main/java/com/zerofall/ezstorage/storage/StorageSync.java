package com.zerofall.ezstorage.storage;

import java.util.Set;

import com.zerofall.ezstorage.menu.StorageCoreMenu;
import com.zerofall.ezstorage.network.StorageContentsPayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends changed storage contents to every player looking at them. Changes are batched per tick, so a hopper
 * chain pushing items every tick causes at most one packet per viewer per tick.
 */
public final class StorageSync {

    private StorageSync() {}

    public static void flush(MinecraftServer server) {
        Set<StorageInventory> changed = StorageManager.get(server)
            .drainPendingSync();
        if (changed.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList()
            .getPlayers()) {
            if (player.containerMenu instanceof StorageCoreMenu menu && changed.contains(menu.getInventory())) {
                PacketDistributor
                    .sendToPlayer(player, StorageContentsPayload.of(menu.containerId, menu.getInventory()));
            }
        }
    }
}
