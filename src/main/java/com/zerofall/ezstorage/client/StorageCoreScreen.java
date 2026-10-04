package com.zerofall.ezstorage.client;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.config.EZConfig;
import com.zerofall.ezstorage.menu.StorageClickType;
import com.zerofall.ezstorage.menu.StorageCoreMenu;
import com.zerofall.ezstorage.network.BulkImportPayload;
import com.zerofall.ezstorage.network.StorageClickPayload;
import com.zerofall.ezstorage.network.StorageDropPayload;
import com.zerofall.ezstorage.storage.StorageInventory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class StorageCoreScreen<M extends StorageCoreMenu> extends AbstractContainerScreen<M> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "textures/gui/storage.png");
    private static final Identifier SIDE_BUTTON = Identifier
        .fromNamespaceAndPath(EZStorage.MOD_ID, "textures/gui/side_button.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier
        .withDefaultNamespace("container/creative_inventory/scroller_disabled");

    protected static final int GRID_X = 8;
    protected static final int GRID_Y = 18;
    protected static final int COLUMNS = 9;
    protected static final int CELL = 18;
    protected static final int SCROLLBAR_X = 175;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final float COUNT_SCALE = 0.5F;

    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_STRIDE = 20;

    private enum SideButton {
        SORT_MODE, SORT_ORDER, SEARCH_MODE, SAVE_SEARCH
    }

    /** Session memory of the search text when "save search" is on. */
    private static String rememberedSearch;

    protected EditBox searchBox;
    private final List<ItemStack> visibleItems = new ArrayList<>();
    private final List<Long> visibleCounts = new ArrayList<>();
    private int scrollRow;
    private boolean draggingScrollbar;
    private boolean bulkKeyHeld;
    private int lastContentVersion = -1;
    private String lastRecipeViewerText;
    private int lastMouseX;
    private int lastMouseY;

    private SortMode sortMode;
    private SortOrder sortOrder;
    private SearchMode searchMode;
    private boolean saveSearch;

    public StorageCoreScreen(M menu, Inventory playerInventory, Component title) {
        this(menu, playerInventory, title, 222);
    }

    protected StorageCoreScreen(M menu, Inventory playerInventory, Component title, int imageHeight) {
        super(menu, playerInventory, title, 195, imageHeight);
        sortMode = EZConfig.CLIENT.sortMode.get();
        sortOrder = EZConfig.CLIENT.sortOrder.get();
        searchMode = EZConfig.CLIENT.searchMode.get();
        saveSearch = EZConfig.CLIENT.saveSearch.getAsBoolean();
        if (rememberedSearch == null) {
            rememberedSearch = saveSearch ? EZConfig.CLIENT.searchText.get() : "";
        }
    }

    protected Identifier getBackgroundTexture() {
        return TEXTURE;
    }

    protected int rows() {
        return menu.storageRows();
    }

    // ---------------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 10, topPos + 6, 89, font.lineHeight,
            Component.translatable("gui.ezstorage.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setCanLoseFocus(true);
        searchBox.setValue(saveSearch ? rememberedSearch : "");
        searchBox.setResponder(text -> {
            scrollRow = 0;
            refreshItems();
            if (isRecipeViewerSync()) {
                RecipeViewerBridge.setSearchText(text);
                lastRecipeViewerText = text;
            }
        });
        addWidget(searchBox);
        if (EZConfig.CLIENT.focusSearch.getAsBoolean()) {
            setInitialFocus(searchBox);
        }
        refreshItems();
    }

    @Override
    public void removed() {
        super.removed();
        rememberedSearch = saveSearch ? searchBox.getValue() : "";
        saveSettings();
    }

    private boolean isRecipeViewerSync() {
        return searchMode != SearchMode.STANDARD && RecipeViewerBridge.isAvailable();
    }

    private void saveSettings() {
        EZConfig.CLIENT.sortMode.set(sortMode);
        EZConfig.CLIENT.sortOrder.set(sortOrder);
        EZConfig.CLIENT.searchMode.set(searchMode);
        EZConfig.CLIENT.saveSearch.set(saveSearch);
        EZConfig.CLIENT.searchText.set(saveSearch && searchBox != null ? searchBox.getValue() : "");
        EZConfig.CLIENT_SPEC.save();
    }

    // ---------------------------------------------------------------------------------------------
    // Item list
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.getContentVersion() != lastContentVersion) {
            refreshItems();
        }
        if (isRecipeViewerSync()) {
            String text = RecipeViewerBridge.getSearchText();
            if (text != null && !text.equals(lastRecipeViewerText)) {
                lastRecipeViewerText = text;
                if (!text.equals(searchBox.getValue())) {
                    searchBox.setValue(text);
                }
            }
        }
    }

    protected void refreshItems() {
        lastContentVersion = menu.getContentVersion();
        StorageInventory inventory = menu.getInventory();
        String query = searchBox == null ? ""
            : searchBox.getValue()
                .trim()
                .toLowerCase(Locale.ROOT);

        List<StorageInventory.Entry> entries = new ArrayList<>();
        for (StorageInventory.Entry entry : inventory.entries()) {
            if (matches(entry, query)) {
                entries.add(entry);
            }
        }
        entries.sort(comparator());

        visibleItems.clear();
        visibleCounts.clear();
        for (StorageInventory.Entry entry : entries) {
            visibleItems.add(entry.toDisplayStack());
            visibleCounts.add(entry.count());
        }
        scrollRow = Math.min(scrollRow, maxScrollRow());
    }

    private boolean matches(StorageInventory.Entry entry, String query) {
        if (query.isEmpty()) {
            return true;
        }
        ItemStack stack = entry.toDisplayStack();
        if (query.startsWith("@")) {
            String mod = query.substring(1);
            return BuiltInRegistries.ITEM.getKey(stack.getItem())
                .getNamespace()
                .contains(mod);
        }
        if (stack.getHoverName()
            .getString()
            .toLowerCase(Locale.ROOT)
            .contains(query)) {
            return true;
        }
        if (minecraft == null || minecraft.level == null) {
            return false;
        }
        TooltipFlag flag = minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        for (Component line : stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, flag)) {
            if (line.getString()
                .toLowerCase(Locale.ROOT)
                .contains(query)) {
                return true;
            }
        }
        return false;
    }

    private Comparator<StorageInventory.Entry> comparator() {
        Comparator<StorageInventory.Entry> comparator = switch (sortMode) {
            case AMOUNT -> Comparator.comparingLong(StorageInventory.Entry::count)
                .reversed();
            case NAME -> Comparator.comparing(
                e -> e.toDisplayStack()
                    .getHoverName()
                    .getString(),
                String.CASE_INSENSITIVE_ORDER);
            case MOD -> Comparator.comparing(
                (StorageInventory.Entry e) -> BuiltInRegistries.ITEM.getKey(
                    e.resource()
                        .getItem())
                    .getNamespace())
                .thenComparing(
                    e -> e.toDisplayStack()
                        .getHoverName()
                        .getString(),
                    String.CASE_INSENSITIVE_ORDER);
        };
        return sortOrder == SortOrder.ASCENDING ? comparator.reversed() : comparator;
    }

    private int totalRows() {
        return (visibleItems.size() + COLUMNS - 1) / COLUMNS;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - rows());
    }

    /** Index into the visible list of the cell under the mouse, or -1. */
    protected int cellAt(double mouseX, double mouseY) {
        double x = mouseX - (leftPos + GRID_X);
        double y = mouseY - (topPos + GRID_Y);
        if (x < 0 || y < 0) {
            return -1;
        }
        int col = (int) (x / CELL);
        int row = (int) (y / CELL);
        if (col >= COLUMNS || row >= rows()) {
            return -1;
        }
        return (row + scrollRow) * COLUMNS + col;
    }

    private boolean isOverGrid(double mouseX, double mouseY) {
        return mouseX >= leftPos + GRID_X && mouseX < leftPos + GRID_X + COLUMNS * CELL
            && mouseY >= topPos + GRID_Y
            && mouseY < topPos + GRID_Y + rows() * CELL;
    }

    /** The stored item under the mouse (count 1), or empty. Used by the JEI plugin as well. */
    public ItemStack getStoredItemAt(double mouseX, double mouseY) {
        int index = cellAt(mouseX, mouseY);
        return index >= 0 && index < visibleItems.size() ? visibleItems.get(index) : ItemStack.EMPTY;
    }

    /** Screen rectangle {x, y, w, h} of the stored item cell under the mouse, or null. */
    public int[] getStoredCellArea(double mouseX, double mouseY) {
        int index = cellAt(mouseX, mouseY);
        if (index < 0 || index >= visibleItems.size()) {
            return null;
        }
        int visibleIndex = index - scrollRow * COLUMNS;
        int x = leftPos + GRID_X + (visibleIndex % COLUMNS) * CELL;
        int y = topPos + GRID_Y + (visibleIndex / COLUMNS) * CELL;
        return new int[] { x, y, 16, 16 };
    }

    private long getStoredCountAt(double mouseX, double mouseY) {
        int index = cellAt(mouseX, mouseY);
        return index >= 0 && index < visibleCounts.size() ? visibleCounts.get(index) : 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, getBackgroundTexture(), leftPos, topPos, 0, 0, imageWidth,
            imageHeight, 256, 256);

        // Search field background
        graphics.fill(leftPos + 8, topPos + 4, leftPos + 100, topPos + 16, 0xFF000000);
        graphics.outline(leftPos + 8, topPos + 4, 92, 12, 0xFFA0A0A0);

        // Scrollbar
        int trackTop = topPos + GRID_Y;
        int trackHeight = rows() * CELL - 15;
        int thumbY = maxScrollRow() == 0 ? trackTop : trackTop + trackHeight * scrollRow / maxScrollRow();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, maxScrollRow() == 0 ? SCROLLER_DISABLED : SCROLLER,
            leftPos + SCROLLBAR_X, thumbY, 12, 15);

        extractSideButtons(graphics, mouseX, mouseY);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        StorageInventory inventory = menu.getInventory();
        NumberFormat format = NumberFormat.getIntegerInstance();
        String amount = format.format(inventory.getTotalCount()) + " / " + format.format(inventory.getCapacity());
        int width = font.width(amount);
        if (width > 84) {
            amount = ReadableNumberConverter.wide(inventory.getTotalCount()) + " / "
                + ReadableNumberConverter.wide(inventory.getCapacity());
            width = font.width(amount);
        }
        graphics.text(font, amount, 187 - width, 6, TEXT_COLOR, false);

        int hovered = cellAt(mouseX, mouseY);
        for (int row = 0; row < rows(); row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= visibleItems.size()) {
                    return;
                }
                int x = GRID_X + col * CELL;
                int y = GRID_Y + row * CELL;
                ItemStack stack = visibleItems.get(index);
                graphics.item(stack, x, y);
                graphics.itemDecorations(font, stack, x, y, "");
                extractCount(graphics, x, y, visibleCounts.get(index));
                if (index == hovered) {
                    graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                }
            }
        }
    }

    /** Half-size count in the bottom right corner of a cell, like the 1.7.10 version. */
    private void extractCount(GuiGraphicsExtractor graphics, int x, int y, long count) {
        String text = ReadableNumberConverter.wide(count);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + 16.0F - font.width(text) * COUNT_SCALE, y + 16.0F - 7.0F * COUNT_SCALE);
        pose.scale(COUNT_SCALE, COUNT_SCALE);
        graphics.text(font, text, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private int sideButtonX() {
        return leftPos - BUTTON_SIZE - 2;
    }

    private int sideButtonY(SideButton button) {
        return topPos + 8 + button.ordinal() * BUTTON_STRIDE;
    }

    private SideButton sideButtonAt(double mouseX, double mouseY) {
        for (SideButton button : SideButton.values()) {
            int x = sideButtonX();
            int y = sideButtonY(button);
            if (mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE) {
                return button;
            }
        }
        return null;
    }

    /** Screen area of the side buttons, so recipe viewers can keep clear of it. */
    public int[] getSideButtonArea() {
        return new int[] { sideButtonX(), sideButtonY(SideButton.SORT_MODE), BUTTON_SIZE,
            BUTTON_STRIDE * (SideButton.values().length - 1) + BUTTON_SIZE };
    }

    private void extractSideButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SideButton hovered = sideButtonAt(mouseX, mouseY);
        for (SideButton button : SideButton.values()) {
            int x = sideButtonX();
            int y = sideButtonY(button);
            graphics.blit(RenderPipelines.GUI_TEXTURED, SIDE_BUTTON, x, y, 0, 0, BUTTON_SIZE, BUTTON_SIZE, 32, 32, 32,
                32);
            graphics.outline(x, y, BUTTON_SIZE, BUTTON_SIZE, button == hovered ? 0xFFFFFFFF : 0xFF222222);
            String label = labelFor(button);
            boolean active = button != SideButton.SAVE_SEARCH || saveSearch;
            graphics.text(font, label, x + (BUTTON_SIZE - font.width(label)) / 2 + 1,
                y + (BUTTON_SIZE - font.lineHeight) / 2 + 1, active ? 0xFFFFFFFF : 0xFFAAAAAA, true);
        }
    }

    private String labelFor(SideButton button) {
        return switch (button) {
            case SORT_MODE -> switch (sortMode) {
                case AMOUNT -> "#";
                case NAME -> "N";
                case MOD -> "M";
            };
            case SORT_ORDER -> sortOrder == SortOrder.ASCENDING ? "↑" : "↓";
            case SEARCH_MODE -> switch (searchMode) {
                case AUTO -> "A";
                case JEI_SYNC -> "J";
                case STANDARD -> "S";
            };
            case SAVE_SEARCH -> "✓";
        };
    }

    private Component tooltipFor(SideButton button) {
        return switch (button) {
            case SORT_MODE -> Component.translatable(sortMode.langKey);
            case SORT_ORDER -> Component.translatable(sortOrder.langKey);
            case SEARCH_MODE -> Component.translatable(searchMode.langKey);
            case SAVE_SEARCH -> Component
                .translatable(saveSearch ? "gui.ezstorage.save_search.on" : "gui.ezstorage.save_search.off");
        };
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        SideButton button = sideButtonAt(mouseX, mouseY);
        if (button != null) {
            graphics.setTooltipForNextFrame(font, tooltipFor(button), mouseX, mouseY);
            return;
        }
        if (!menu.getCarried()
            .isEmpty()) {
            return;
        }
        ItemStack stack = getStoredItemAt(mouseX, mouseY);
        if (stack.isEmpty() || minecraft == null) {
            return;
        }
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
        lines.add(
            Component
                .translatable(
                    "gui.ezstorage.stored_count",
                    NumberFormat.getIntegerInstance()
                        .format(getStoredCountAt(mouseX, mouseY)))
                .withStyle(ChatFormatting.GRAY));
        graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        SideButton side = sideButtonAt(mouseX, mouseY);
        if (side != null && (button == 0 || button == 1)) {
            onSideButton(side);
            return true;
        }

        if (searchBox.isMouseOver(mouseX, mouseY)
            || (mouseX >= leftPos + 8 && mouseX < leftPos + 100 && mouseY >= topPos + 4 && mouseY < topPos + 16)) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                searchBox.setValue(ChatFormatting.stripFormatting(carried.getHoverName()
                    .getString()));
            } else if (button == 1) {
                searchBox.setValue("");
            }
            setFocused(searchBox);
            searchBox.setFocused(true);
            return button == 1 || !carried.isEmpty() || searchBox.mouseClicked(event, doubleClick);
        }
        searchBox.setFocused(false);

        if (mouseX >= leftPos + SCROLLBAR_X && mouseX < leftPos + SCROLLBAR_X + 14 && mouseY >= topPos + GRID_Y
            && mouseY < topPos + GRID_Y + rows() * CELL) {
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return true;
        }

        if (isOverGrid(mouseX, mouseY) && (button == 0 || button == 1)) {
            ItemStack stored = getStoredItemAt(mouseX, mouseY);
            StorageClickType type;
            if (bulkKeyHeld) {
                type = StorageClickType.MOVE_ALL;
            } else if (event.hasShiftDown()) {
                type = StorageClickType.QUICK_MOVE;
            } else {
                type = button == 1 ? StorageClickType.PICKUP_HALF : StorageClickType.PICKUP_STACK;
            }
            ItemResource resource = stored.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stored);
            ClientPacketDistributor.sendToServer(new StorageClickPayload(resource, type));
            return true;
        }

        if (bulkKeyHeld && button == 0) {
            Slot slot = getHoveredSlot();
            if (slot != null && slot.hasItem() && menu.isPlayerInventorySlot(slot)) {
                boolean hotbar = slot.getContainerSlot() < 9;
                ClientPacketDistributor.sendToServer(new BulkImportPayload(hotbar));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private void scrollToMouse(double mouseY) {
        double top = topPos + GRID_Y + 7.5;
        double height = rows() * CELL - 15.0;
        double progress = Math.max(0.0, Math.min(1.0, (mouseY - top) / height));
        scrollRow = (int) Math.round(progress * maxScrollRow());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && maxScrollRow() > 0
            && (isOverGrid(mouseX, mouseY) || mouseX < leftPos + imageWidth && mouseY < topPos + GRID_Y + rows() * CELL)) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void onSideButton(SideButton button) {
        switch (button) {
            case SORT_MODE -> sortMode = sortMode.next();
            case SORT_ORDER -> sortOrder = sortOrder.next();
            case SEARCH_MODE -> searchMode = searchMode.next();
            case SAVE_SEARCH -> saveSearch = !saveSearch;
        }
        saveSettings();
        refreshItems();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ClientKeys.BULK_ACTION.matches(event)) {
            bulkKeyHeld = true;
        }
        if (searchBox.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return super.keyPressed(event);
            }
            if (searchBox.keyPressed(event)) {
                return true;
            }
            // Swallow everything else (e.g. the inventory key) while typing.
            return true;
        }
        if (minecraft != null && minecraft.options.keyDrop.matches(event)) {
            ItemStack hovered = getStoredItemAt(lastMouseX(), lastMouseY());
            if (!hovered.isEmpty()) {
                int amount;
                if (event.hasControlDown() && event.hasShiftDown()) {
                    amount = 0;
                } else if (event.hasControlDown()) {
                    amount = hovered.getMaxStackSize();
                } else {
                    amount = 1;
                }
                ClientPacketDistributor.sendToServer(new StorageDropPayload(ItemResource.of(hovered), amount));
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (ClientKeys.BULK_ACTION.matches(event)) {
            bulkKeyHeld = false;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox.isFocused() && searchBox.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    private double lastMouseX() {
        return lastMouseX;
    }

    private double lastMouseY() {
        return lastMouseY;
    }
}
