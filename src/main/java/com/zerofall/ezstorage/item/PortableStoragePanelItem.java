package com.zerofall.ezstorage.item;

import java.util.UUID;
import java.util.function.Consumer;

import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;
import com.zerofall.ezstorage.menu.StorageMenus;
import com.zerofall.ezstorage.network.SelectHotbarSlotPayload;
import com.zerofall.ezstorage.registry.ModComponents;
import com.zerofall.ezstorage.storage.StorageInventory;
import com.zerofall.ezstorage.storage.StorageManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Wireless access to a storage core. Right-click a core to bind, right-click anywhere to open. The target core
 * does not have to be chunk-loaded. Upgradable in range and with a crafting grid (see {@code PanelUpgradeRecipe}).
 */
public class PortableStoragePanelItem extends Item {

    public PortableStoragePanelItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------------------------------

    public static PanelTier getTier(ItemStack stack) {
        return stack.getOrDefault(ModComponents.PANEL_TIER.get(), PanelTier.TIER_1);
    }

    public static boolean hasCrafting(ItemStack stack) {
        return stack.getOrDefault(ModComponents.PANEL_CRAFTING.get(), false);
    }

    public static PanelLink getLink(ItemStack stack) {
        return stack.get(ModComponents.PANEL_LINK.get());
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction
    // ---------------------------------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            StorageInventory inventory = core.getOrCreateInventory();
            if (inventory != null) {
                context.getItemInHand()
                    .set(ModComponents.PANEL_LINK.get(), new PanelLink(inventory.getId(), level.dimension(), pos));
                Player player = context.getPlayer();
                if (player != null) {
                    player.sendOverlayMessage(
                        Component.translatable("chat.ezstorage.storagecore_connected", pos.getX(), pos.getY(), pos.getZ()));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, player.getItemInHand(hand), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Resolves the storage behind a panel, clearing links to cores that no longer exist. */
    private static StorageInventory resolve(ServerPlayer player, ItemStack panel) {
        PanelLink link = getLink(panel);
        if (link == null) {
            return null;
        }
        ServerLevel coreLevel = player.level()
            .getServer()
            .getLevel(link.dimension());
        if (coreLevel != null && coreLevel.isLoaded(link.pos())) {
            if (!(coreLevel.getBlockEntity(link.pos()) instanceof StorageCoreBlockEntity core)
                || !link.inventoryId()
                    .equals(core.getInventoryId())) {
                panel.remove(ModComponents.PANEL_LINK.get());
                return null;
            }
        }
        return StorageManager.get(player.level()
            .getServer())
            .get(link.inventoryId());
    }

    public static boolean isInRange(ItemStack panel, PanelLink link, Player player) {
        PanelTier tier = getTier(panel);
        if (tier.isInfinite()) {
            return true;
        }
        if (!player.level()
            .dimension()
            .equals(link.dimension())) {
            return false;
        }
        BlockPos pos = link.pos();
        double range = tier.range();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= range * range;
    }

    /** Opens the panel's storage; with {@code feedback} the player is told why it did not open. */
    public static boolean open(ServerPlayer player, ItemStack panel, boolean feedback) {
        StorageInventory inventory = resolve(player, panel);
        if (inventory == null) {
            if (feedback) {
                player.sendOverlayMessage(Component.translatable("chat.ezstorage.storagecore_not_found"));
            }
            return false;
        }
        if (!isInRange(panel, getLink(panel), player)) {
            if (feedback) {
                player.sendOverlayMessage(Component.translatable("chat.ezstorage.storagecore_out_of_range"));
            }
            return false;
        }
        StorageMenus.open(player, inventory, hasCrafting(panel), StorageMenus.panelValidator(inventory.getId()));
        return true;
    }

    /** Opens the first portable panel found in the player's inventory (keybind). */
    public static void openFromInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof PortableStoragePanelItem) {
                open(player, stack, true);
                return;
            }
        }
    }

    /** A panel in the player's inventory that is linked to {@code inventoryId} (any, if null) and in range. */
    public static ItemStack findUsablePanel(ServerPlayer player, UUID inventoryId) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof PortableStoragePanelItem)) {
                continue;
            }
            PanelLink link = getLink(stack);
            if (link != null && (inventoryId == null || inventoryId.equals(link.inventoryId()))
                && isInRange(stack, link, player)) {
                return stack;
            }
        }
        return null;
    }

    /** Pick block from storage: moves a stack of the target item into the hotbar and selects it. */
    public static void pickBlock(ServerPlayer player, ItemStack target) {
        ItemStack panel = findUsablePanel(player, null);
        if (panel == null) {
            player.sendOverlayMessage(Component.translatable("chat.ezstorage.storagecore_not_found"));
            return;
        }
        StorageInventory storage = resolve(player, panel);
        if (storage == null) {
            return;
        }
        ItemResource resource = ItemResource.of(target);
        ItemStack extracted = storage.extractStack(resource, resource.getMaxStackSize());
        if (extracted.isEmpty()) {
            return;
        }

        Inventory inventory = player.getInventory();
        int targetSlot = -1;

        // 1) merge into matching hotbar stacks, 2) use an empty hotbar slot
        for (int i = 0; i < 9 && !extracted.isEmpty(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, extracted)
                && slot.getCount() < slot.getMaxStackSize()) {
                int moved = Math.min(slot.getMaxStackSize() - slot.getCount(), extracted.getCount());
                slot.grow(moved);
                extracted.shrink(moved);
                targetSlot = i;
            }
        }
        for (int i = 0; i < 9 && !extracted.isEmpty(); i++) {
            if (inventory.getItem(i)
                .isEmpty()) {
                inventory.setItem(i, extracted);
                extracted = ItemStack.EMPTY;
                targetSlot = i;
            }
        }
        // 3) hotbar full: move the selected item into the main inventory and take its place
        if (!extracted.isEmpty()) {
            int selected = inventory.getSelectedSlot();
            ItemStack held = inventory.getItem(selected);
            int free = -1;
            for (int i = 9; i < 36; i++) {
                if (inventory.getItem(i)
                    .isEmpty()) {
                    free = i;
                    break;
                }
            }
            if (free >= 0) {
                inventory.setItem(free, held);
                inventory.setItem(selected, extracted);
                extracted = ItemStack.EMPTY;
                targetSlot = selected;
            }
        }
        if (!extracted.isEmpty()) {
            storage.insert(extracted);
            player.sendOverlayMessage(Component.translatable("chat.ezstorage.inventory_full"));
        }
        if (targetSlot >= 0) {
            inventory.setSelectedSlot(targetSlot);
            PacketDistributor.sendToPlayer(player, new SelectHotbarSlotPayload(targetSlot));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tooltip
    // ---------------------------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
        Consumer<Component> tooltip, TooltipFlag flag) {
        PanelLink link = getLink(stack);
        PanelTier tier = getTier(stack);
        boolean crafting = hasCrafting(stack);

        Component status = link != null
            ? Component.translatable("tooltip.ezstorage.panel.connected")
                .withStyle(ChatFormatting.DARK_GREEN)
            : Component.translatable("tooltip.ezstorage.panel.not_connected")
                .withStyle(ChatFormatting.DARK_RED);
        tooltip.accept(Component.translatable("tooltip.ezstorage.panel.status", status)
            .withStyle(ChatFormatting.GRAY));
        if (link != null) {
            tooltip.accept(
                Component
                    .literal(
                        "  " + link.pos()
                            .getX() + ", "
                            + link.pos()
                                .getY()
                            + ", "
                            + link.pos()
                                .getZ()
                            + " (" + link.dimension()
                                .identifier()
                            + ")")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        Component range = tier.isInfinite() ? Component.literal("∞")
            .withStyle(ChatFormatting.DARK_GREEN) : Component.literal(String.valueOf(tier.range()));
        tooltip.accept(Component.translatable("tooltip.ezstorage.panel.range", range)
            .withStyle(ChatFormatting.GRAY));

        Component craftingText = crafting ? Component.translatable("tooltip.ezstorage.panel.crafting.enabled")
            .withStyle(ChatFormatting.DARK_GREEN)
            : Component.translatable("tooltip.ezstorage.panel.crafting.disabled")
                .withStyle(ChatFormatting.DARK_RED);
        tooltip.accept(Component.translatable("tooltip.ezstorage.panel.crafting", craftingText)
            .withStyle(ChatFormatting.GRAY));

        PanelTier next = tier.next();
        if (next != null || !crafting) {
            tooltip.accept(Component.translatable("tooltip.ezstorage.panel.upgrades")
                .withStyle(ChatFormatting.GRAY));
            Component redstone = Blocks.REDSTONE_BLOCK.getName();
            if (next != null) {
                tooltip.accept(
                    Component
                        .translatable(
                            "tooltip.ezstorage.panel.upgrade.next_tier",
                            redstone,
                            next.upgradeItem()
                                .getName(next.upgradeItem()
                                    .getDefaultInstance()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            if (!crafting) {
                tooltip.accept(
                    Component.translatable(
                        "tooltip.ezstorage.panel.upgrade.crafting",
                        redstone,
                        com.zerofall.ezstorage.registry.ModBlocks.CRAFTING_BOX.get()
                            .getName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
