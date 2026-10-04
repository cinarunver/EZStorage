package com.zerofall.ezstorage.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.blockentity.StorageCoreBlockEntity;
import com.zerofall.ezstorage.client.RecipeViewerBridge;
import com.zerofall.ezstorage.client.StorageCraftingScreen;
import com.zerofall.ezstorage.menu.StorageClickType;
import com.zerofall.ezstorage.menu.StorageCraftingMenu;
import com.zerofall.ezstorage.menu.StorageMenus;
import com.zerofall.ezstorage.network.FillGridPayload;
import com.zerofall.ezstorage.network.StorageClickPayload;
import com.zerofall.ezstorage.registry.ModBlocks;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Client half of the CI self test ({@code -Dezstorage.selftest.client=true}): joins a world, opens the storage GUI
 * through the server, checks content sync, clicks and the crafting grid, takes screenshots and quits.
 */
@EventBusSubscriber(modid = EZStorage.MOD_ID, value = Dist.CLIENT)
public final class ClientSelfTest {

    private static final String TAG = "[EZSTORAGE-CLIENTTEST] ";
    private static final int TIMEOUT_TICKS = 20 * 60 * 4;

    private enum Phase {
        WAIT_WORLD, SETUP, WAIT_SCREEN, PICKUP, CHECK_PICKUP, CHECK_DEPOSIT, FILL_GRID, CHECK_GRID, FINISH, DONE
    }

    private static Phase phase = Phase.WAIT_WORLD;
    private static int totalTicks;
    private static int phaseTicks;
    private static final List<String> failures = new ArrayList<>();
    private static ItemResource picked = ItemResource.EMPTY;

    private ClientSelfTest() {}

    private static void next(Phase nextPhase) {
        phase = nextPhase;
        phaseTicks = 0;
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            EZStorage.LOG.info(TAG + "PASS " + name);
        } else {
            failures.add(name);
            EZStorage.LOG.error(TAG + "FAIL " + name + ": " + detail);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ezstorage.selftest.client") || phase == Phase.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        totalTicks++;
        phaseTicks++;
        if (totalTicks > TIMEOUT_TICKS && phase != Phase.FINISH) {
            check("timeout", false, "stuck in phase " + phase);
            next(Phase.FINISH);
        }
        try {
            tick(mc);
        } catch (Throwable t) {
            EZStorage.LOG.error(TAG + "exception", t);
            check("unexpected exception", false, t.toString());
            next(Phase.FINISH);
        }
    }

    private static void tick(Minecraft mc) {
        switch (phase) {
            case WAIT_WORLD -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && phaseTicks > 100) {
                    check("joined world", true, "");
                    next(Phase.SETUP);
                }
            }
            case SETUP -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID playerId = mc.player.getUUID();
                server.execute(() -> setupOnServer(server, playerId));
                next(Phase.WAIT_SCREEN);
            }
            case WAIT_SCREEN -> {
                if (mc.gui.screen() instanceof StorageCraftingScreen screen
                    && screen.getMenu()
                        .getInventory()
                        .size() > 0
                    && phaseTicks > 20) {
                    StorageInventory clientCopy = screen.getMenu()
                        .getInventory();
                    check("crafting screen opened", true, "");
                    check("contents synced to client", clientCopy.size() >= 40 && clientCopy.getCapacity() > 0,
                        "types " + clientCopy.size() + " capacity " + clientCopy.getCapacity());
                    check("JEI runtime bridge", RecipeViewerBridge.isAvailable(), "JEI search bridge not installed");
                    ClientScreenshots.take(mc, "ezstorage_storage");
                    next(Phase.PICKUP);
                } else if (phaseTicks > 400) {
                    check("crafting screen opened", false, "screen is " + mc.gui.screen());
                    next(Phase.FINISH);
                }
            }
            case PICKUP -> {
                StorageCraftingScreen screen = (StorageCraftingScreen) mc.gui.screen();
                // Middle of the first grid cell, through the screen's own hit testing.
                ItemStack first = screen.getStoredItemAt(screen.getGuiLeft() + 8 + 8, screen.getGuiTop() + 18 + 8);
                check("hit test finds first cell", !first.isEmpty(), "empty");
                picked = ItemResource.of(first);
                ClientPacketDistributor.sendToServer(new StorageClickPayload(picked, StorageClickType.PICKUP_STACK));
                next(Phase.CHECK_PICKUP);
            }
            case CHECK_PICKUP -> {
                if (phaseTicks < 20) {
                    return;
                }
                ItemStack carried = mc.player.containerMenu.getCarried();
                check("pickup reaches client cursor", !carried.isEmpty() && picked.matches(carried),
                    "carried " + carried);
                ClientPacketDistributor.sendToServer(new StorageClickPayload(ItemResource.EMPTY, StorageClickType.PICKUP_STACK));
                next(Phase.CHECK_DEPOSIT);
            }
            case CHECK_DEPOSIT -> {
                if (phaseTicks < 20) {
                    return;
                }
                check("deposit clears cursor", mc.player.containerMenu.getCarried()
                    .isEmpty(), "carried " + mc.player.containerMenu.getCarried());
                next(Phase.FILL_GRID);
            }
            case FILL_GRID -> {
                List<List<ItemStack>> table = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    boolean used = i == 0 || i == 1 || i == 3 || i == 4;
                    table.add(used ? List.of(new ItemStack(Items.OAK_PLANKS)) : List.of());
                }
                ClientPacketDistributor.sendToServer(new FillGridPayload(table, true));
                next(Phase.CHECK_GRID);
            }
            case CHECK_GRID -> {
                if (phaseTicks < 30) {
                    return;
                }
                if (mc.player.containerMenu instanceof StorageCraftingMenu menu) {
                    ItemStack result = menu.getSlot(StorageCraftingMenu.RESULT_SLOT)
                        .getItem();
                    check("crafting result synced", result.is(Items.CRAFTING_TABLE), "result " + result);
                    check("max transfer fills grid", menu.getGrid()
                        .getItem(0)
                        .getCount() > 1, "grid slot " + menu.getGrid()
                            .getItem(0));
                } else {
                    check("crafting menu still open", false, "menu " + mc.player.containerMenu);
                }
                ClientScreenshots.take(mc, "ezstorage_crafting");
                next(Phase.FINISH);
            }
            case FINISH -> {
                if (phaseTicks < 40) {
                    return; // let the screenshot writer finish
                }
                EZStorage.LOG.info(TAG + "RESULT: " + (failures.isEmpty() ? "PASSED" : "FAILED " + failures));
                next(Phase.DONE);
                mc.stop();
            }
            default -> {}
        }
    }

    private static void setupOnServer(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList()
            .getPlayer(playerId);
        if (player == null) {
            EZStorage.LOG.error(TAG + "FAIL server player missing");
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos core = player.blockPosition()
            .offset(3, 1, 0);
        level.setBlock(core, ModBlocks.STORAGE_CORE.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(core.east(), ModBlocks.HYPER_STORAGE_BOX.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(core.above(), ModBlocks.CRAFTING_BOX.get()
            .defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(core) instanceof StorageCoreBlockEntity coreEntity)) {
            EZStorage.LOG.error(TAG + "FAIL core block entity missing");
            return;
        }
        StorageInventory inventory = coreEntity.getOrCreateInventory();
        coreEntity.rescan();

        int added = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty() || item == Items.OAK_PLANKS) {
                continue;
            }
            inventory.insert(ItemResource.of(stack), 1 + (added * 37L) % 5000, false);
            if (++added >= 80) {
                break;
            }
        }
        inventory.insert(ItemResource.of(Items.OAK_PLANKS), 1234, false);
        StorageMenus.open(player, inventory, true, p -> true);
    }
}
