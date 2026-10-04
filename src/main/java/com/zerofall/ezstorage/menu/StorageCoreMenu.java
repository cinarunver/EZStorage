package com.zerofall.ezstorage.menu;

import java.util.UUID;
import java.util.function.Predicate;

import com.zerofall.ezstorage.registry.ModMenus;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The storage core GUI. Only the player inventory consists of real slots; the stored items are shown as a
 * virtual grid on the client and interacted with through custom payloads.
 */
public class StorageCoreMenu extends AbstractContainerMenu {

    public static final int PLAYER_INVENTORY_SLOT_COUNT = 36;

    protected final Player player;
    protected final StorageInventory inventory;
    private final Predicate<Player> validator;
    private int contentVersion;

    /** Server side. */
    public StorageCoreMenu(int containerId, Inventory playerInventory, StorageInventory inventory,
        Predicate<Player> validator) {
        this(ModMenus.STORAGE.get(), containerId, playerInventory, inventory, validator);
    }

    protected StorageCoreMenu(MenuType<?> type, int containerId, Inventory playerInventory,
        StorageInventory inventory, Predicate<Player> validator) {
        super(type, containerId);
        this.player = playerInventory.player;
        this.inventory = inventory;
        this.validator = validator;
        addStandardInventorySlots(playerInventory, 8, playerInventoryY());
    }

    /** Client side: contents arrive separately through {@code StorageContentsPayload}. */
    public static StorageCoreMenu fromNetwork(int containerId, Inventory playerInventory,
        RegistryFriendlyByteBuf buf) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
        return new StorageCoreMenu(containerId, playerInventory, new StorageInventory(id), p -> true);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, StorageInventory inventory) {
        UUIDUtil.STREAM_CODEC.encode(buf, inventory.getId());
    }

    protected int playerInventoryY() {
        return 140;
    }

    public int storageRows() {
        return 6;
    }

    public StorageInventory getInventory() {
        return inventory;
    }

    protected int playerSlotStart() {
        return 0;
    }

    protected int playerSlotEnd() {
        return PLAYER_INVENTORY_SLOT_COUNT;
    }

    public boolean isPlayerInventorySlot(Slot slot) {
        return slot.index >= playerSlotStart() && slot.index < playerSlotEnd();
    }

    // ---------------------------------------------------------------------------------------------
    // Client content updates
    // ---------------------------------------------------------------------------------------------

    public void receiveContents(StorageInventory received) {
        inventory.replaceContents(received);
        contentVersion++;
    }

    /** Increments whenever new contents arrived; screens use it to refresh their filtered list. */
    public int getContentVersion() {
        return contentVersion;
    }

    // ---------------------------------------------------------------------------------------------
    // Server actions (triggered by payloads)
    // ---------------------------------------------------------------------------------------------

    public void handleStorageClick(ItemResource clicked, StorageClickType type) {
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            if (type == StorageClickType.PICKUP_STACK || type == StorageClickType.PICKUP_HALF) {
                setCarried(inventory.insert(carried));
            }
            return;
        }
        if (clicked.isEmpty()) {
            return;
        }
        long available = inventory.getAmount(clicked);
        if (available <= 0) {
            return;
        }

        switch (type) {
            case PICKUP_STACK -> setCarried(inventory.extractStack(clicked, clicked.getMaxStackSize()));
            case PICKUP_HALF -> {
                int full = (int) Math.min(available, clicked.getMaxStackSize());
                int half = full > 1 ? full / 2 : 1;
                setCarried(inventory.extractStack(clicked, half));
            }
            case QUICK_MOVE -> moveToPlayer(clicked, 1);
            case MOVE_ALL -> moveToPlayer(clicked, Integer.MAX_VALUE);
        }
    }

    /** Moves up to {@code maxStacks} stacks of the resource from storage into the player inventory. */
    protected void moveToPlayer(ItemResource resource, int maxStacks) {
        for (int i = 0; i < maxStacks; i++) {
            ItemStack stack = inventory.extractStack(resource, resource.getMaxStackSize());
            if (stack.isEmpty()) {
                return;
            }
            int before = stack.getCount();
            moveItemStackTo(stack, playerSlotStart(), playerSlotEnd(), true);
            if (!stack.isEmpty()) {
                inventory.insert(stack);
            }
            if (stack.getCount() == before || !stack.isEmpty()) {
                return; // player inventory is full
            }
        }
    }

    /** Moves the player's main inventory (or hotbar) into storage. */
    public void importPlayerInventory(boolean hotbarOnly) {
        Inventory playerInventory = player.getInventory();
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty()) {
                playerInventory.setItem(i, inventory.insert(stack));
            }
        }
    }

    /** Drops items from storage in front of the player. {@code amount <= 0} drops everything of that type. */
    public void dropFromStorage(ItemResource resource, int amount) {
        long remaining = amount <= 0 ? inventory.getAmount(resource) : amount;
        while (remaining > 0) {
            ItemStack stack = inventory.extractStack(resource, (int) Math.min(remaining, resource.getMaxStackSize()));
            if (stack.isEmpty()) {
                return;
            }
            remaining -= stack.getCount();
            player.drop(stack, false);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Vanilla menu behaviour
    // ---------------------------------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot.hasItem() && isPlayerInventorySlot(slot)) {
            slot.set(inventory.insert(slot.getItem()));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return validator.test(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level()
            .isClientSide()) {
            inventory.sortByCount();
        }
    }
}
