package com.zerofall.ezstorage.menu;

import java.util.UUID;
import java.util.function.Predicate;

import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;
import com.zerofall.ezstorage.item.PortableStoragePanelItem;
import com.zerofall.ezstorage.network.StorageContentsPayload;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens storage GUIs and decides when they have to close again. */
public final class StorageMenus {

    private static final double MAX_BLOCK_DISTANCE_SQ = 8.0 * 8.0;

    private StorageMenus() {}

    public static void open(ServerPlayer player, StorageInventory inventory, boolean withCrafting,
        Predicate<Player> validator) {
        boolean crafting = withCrafting && !player.isSpectator();
        Component title = Component.translatable("block.ezstorage.storage_core");
        player.openMenu(
            new SimpleMenuProvider(
                (id, playerInventory, p) -> crafting
                    ? new StorageCraftingMenu(id, playerInventory, inventory, validator)
                    : new StorageCoreMenu(id, playerInventory, inventory, validator),
                title),
            buf -> StorageCoreMenu.writeOpenData(buf, inventory));
        if (player.containerMenu instanceof StorageCoreMenu menu && menu.getInventory() == inventory) {
            PacketDistributor.sendToPlayer(player, StorageContentsPayload.of(menu.containerId, inventory));
        }
    }

    /** Valid while the player stays near the clicked core and the core keeps the same storage. */
    public static Predicate<Player> blockValidator(StorageCoreBlockEntity core) {
        Level level = core.getLevel();
        BlockPos pos = core.getBlockPos();
        UUID inventoryId = core.getInventoryId();
        return player -> player.level() == level && !core.isRemoved()
            && inventoryId != null
            && inventoryId.equals(core.getInventoryId())
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_BLOCK_DISTANCE_SQ;
    }

    /** Valid while the player carries a panel linked to this storage and stays within its range. */
    public static Predicate<Player> panelValidator(UUID inventoryId) {
        return player -> player instanceof ServerPlayer serverPlayer
            && PortableStoragePanelItem.findUsablePanel(serverPlayer, inventoryId) != null;
    }
}
