package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalScrollState;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalSearchMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.compat.jei.RtsJeiSearchBridge;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsClearCraftingGridPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsBulkStorageOpPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.CLEAR_BUTTON_X;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.CLEAR_PLAYER_Y;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.CLEAR_STORAGE_Y;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.DEPOSIT_ALL_Y;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.DEPOSIT_BUTTON_X;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.DEPOSIT_HOTBAR_Y;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.SIDE_BUTTON_H;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.SIDE_BUTTON_W;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.TOOLBAR_H;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.TOOLBAR_W;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer.TOOLBAR_X;

/**
 * RTS 一体化合成终端界面。
 *
 * <p>界面借鉴 AE2/Refined Storage 已验证的“储存浏览器 + 3×3 合成区 + 玩家背包”
 * 纵向结构，但使用 RTS 自己的主题、网络协议和 linked storage。该类仍是屏幕状态机
 * 所有者；纯布局和跨页滚动分别交给小型辅助类，服务端物品变更全部通过权威菜单动作
 * 完成，客户端不预测生成、删除或搬动物品。</p>
 */
public final class RtsCraftTerminalScreen extends AbstractContainerScreen<RtsCraftTerminalMenu> {
    private final CraftTerminalLayout layout =
            new CraftTerminalLayout(RtsClientUiStateStore.getCraftTerminalRows());
    private final CraftTerminalScrollState scrollState = new CraftTerminalScrollState();
    private EditBox searchBox;
    private CraftTerminalSearchMode searchMode = CraftTerminalSearchMode.STANDARD;
    private int previousPageSize = 90;
    private boolean draggingScrollbar;
    private String lastSearchSentToJei = "";

    public RtsCraftTerminalScreen(RtsCraftTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CraftTerminalLayout.WIDTH;
        this.imageHeight = CraftTerminalLayout.IMAGE_HEIGHT;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 210;
        this.titleLabelX = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.layout.setRows(Math.min(this.layout.rows(), maximumRowsForScreen()));
        recenterVisibleArea();
        ClientRtsController controller = ClientRtsController.get();
        this.previousPageSize = controller.getStoragePageSize();
        controller.updateStoragePageSize(CraftTerminalScrollState.PAGE_SIZE);
        this.scrollState.reset(controller);

        this.searchMode = CraftTerminalSearchMode.parse(
                RtsClientUiStateStore.getCraftTerminalSearchMode());
        boolean pinned = RtsClientUiStateStore.isCraftTerminalSearchPinned();
        String initialSearch = pinned ? RtsClientUiStateStore.getCraftTerminalSearch() : "";

        this.searchBox = new EditBox(
                this.font,
                this.leftPos + CraftTerminalLayout.SEARCH_X + 3,
                this.topPos + this.layout.searchY() + 2,
                CraftTerminalLayout.SEARCH_WIDTH - 14,
                8,
                Component.translatable("screen.rtsbuilding.craft_terminal.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setMaxLength(128);
        this.searchBox.setTextColor(CraftTerminalStyle.TEXT.toArgb());
        this.searchBox.setTextColorUneditable(CraftTerminalStyle.UNEDITABLE_TEXT.toArgb());
        this.searchBox.setValue(initialSearch);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);
        if (!initialSearch.equals(controller.getStorageSearch())) {
            controller.setStorageSearch(initialSearch);
        }
        syncSearchToJei(initialSearch);
    }

    @Override
    public void removed() {
        ClientRtsController.get().updateStoragePageSize(this.previousPageSize);
        if (!RtsClientUiStateStore.isCraftTerminalSearchPinned()) {
            RtsClientUiStateStore.setCraftTerminalSearch("");
        }
        RtsClientUiStateStore.cache().flushIfDirty();
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.scrollState.update(ClientRtsController.get(), this.layout.rows());
        syncSearchFromController();
        syncSearchFromJei();
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLocalCraftResultFallback(graphics);
        this.renderTooltip(graphics, mouseX, mouseY);
        renderStorageTooltip(graphics, mouseX, mouseY);
        renderButtonTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ClientRtsController controller = ClientRtsController.get();
        CraftTerminalRenderer.render(
                graphics, this.font, this.leftPos, this.topPos,
                this.layout, this.scrollState,
                controller.getStorageTotalEntries(),
                this.searchBox == null ? "" : this.searchBox.getValue(),
                this.searchBox != null && this.searchBox.isFocused(),
                this.searchMode, RtsClientUiStateStore.isCraftTerminalSearchPinned(),
                maximumRowsForScreen(),
                controller.getStorageSort(), controller.isStorageSortAscending(),
                mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font,
                Component.translatable("screen.rtsbuilding.craft_terminal.short_title"),
                this.titleLabelX, this.layout.visualTop() + 5,
                CraftTerminalStyle.TEXT.toArgb(), false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY,
                CraftTerminalStyle.MUTED_TEXT.toArgb(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleHeaderClick(mouseX, mouseY) || handleActionButtonClick(mouseX, mouseY)) {
                return true;
            }
            if (isInsideScrollbar(mouseX, mouseY)) {
                this.draggingScrollbar = true;
                updateScrollbarFromMouse(mouseY);
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (handleSearchClick(mouseX, mouseY, button)) {
                return true;
            }
            if (handleStorageClick(mouseX, mouseY, button)) {
                return true;
            }
            if (Screen.hasShiftDown()) {
                Slot hovered = this.getSlotUnderMouse();
                if (hovered != null && hovered.hasItem()) {
                    int menuSlot = this.menu.slots.indexOf(hovered);
                    if (menuSlot >= 0 && (menuSlot != 0 || button == GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                        PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateScrollbarFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideVisibleTerminal(mouseX, mouseY)) {
            int delta = scrollY > 0.0D ? -1 : (scrollY < 0.0D ? 1 : 0);
            if (delta != 0) {
                this.scrollState.scrollRows(ClientRtsController.get(), delta, this.layout.rows());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchBox.setValue("");
                this.searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean handleHeaderClick(double mouseX, double mouseY) {
        if (containsRelative(CraftTerminalLayout.MODE_X, this.layout.visualTop() + 3,
                CraftTerminalLayout.HEADER_BUTTON_SIZE, CraftTerminalLayout.HEADER_BUTTON_SIZE,
                mouseX, mouseY)) {
            this.searchMode = this.searchMode.next();
            RtsClientUiStateStore.setCraftTerminalSearchMode(this.searchMode.name());
            syncSearchToJei(this.searchBox == null ? "" : this.searchBox.getValue());
            return true;
        }
        if (containsRelative(CraftTerminalLayout.PIN_X, this.layout.visualTop() + 3,
                CraftTerminalLayout.HEADER_BUTTON_SIZE, CraftTerminalLayout.HEADER_BUTTON_SIZE,
                mouseX, mouseY)) {
            boolean pinned = !RtsClientUiStateStore.isCraftTerminalSearchPinned();
            RtsClientUiStateStore.setCraftTerminalSearchPinned(pinned);
            RtsClientUiStateStore.setCraftTerminalSearch(
                    pinned && this.searchBox != null ? this.searchBox.getValue() : "");
            return true;
        }
        if (containsRelative(CraftTerminalLayout.ROW_BUTTON_X, this.layout.visualTop() + 1,
                10, 7, mouseX, mouseY)) {
            changeRows(this.layout.rows() + 1);
            return true;
        }
        if (containsRelative(CraftTerminalLayout.ROW_BUTTON_X, this.layout.visualTop() + 9,
                10, 7, mouseX, mouseY)) {
            changeRows(this.layout.rows() - 1);
            return true;
        }
        return false;
    }

    private boolean handleActionButtonClick(double mouseX, double mouseY) {
        if (containsRelative(TOOLBAR_X, this.layout.visualTop() + 1,
                TOOLBAR_W, TOOLBAR_H, mouseX, mouseY)) {
            ClientRtsController controller = ClientRtsController.get();
            controller.cycleSort();
            this.scrollState.expectFreshPage(controller, 0);
            return true;
        }
        if (containsRelative(TOOLBAR_X, this.layout.visualTop() + 16,
                TOOLBAR_W, TOOLBAR_H, mouseX, mouseY)) {
            ClientRtsController controller = ClientRtsController.get();
            controller.toggleSortDirection();
            this.scrollState.expectFreshPage(controller, 0);
            return true;
        }
        if (containsRelative(CLEAR_BUTTON_X, CLEAR_STORAGE_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2SRtsClearCraftingGridPayload(false));
            return true;
        }
        if (containsRelative(CLEAR_BUTTON_X, CLEAR_PLAYER_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2SRtsClearCraftingGridPayload(true));
            return true;
        }
        if (containsRelative(DEPOSIT_BUTTON_X, DEPOSIT_ALL_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            sendBulkDeposit(C2SRtsBulkStorageOpPayload.DEPOSIT_ALL);
            return true;
        }
        if (containsRelative(DEPOSIT_BUTTON_X, DEPOSIT_HOTBAR_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            sendBulkDeposit(C2SRtsBulkStorageOpPayload.DEPOSIT_HOTBAR);
            return true;
        }
        return false;
    }

    private boolean handleSearchClick(double mouseX, double mouseY, int button) {
        int x = this.leftPos + CraftTerminalLayout.SEARCH_X;
        int y = this.topPos + this.layout.searchY();
        if (!UiRect.contains(x, y, CraftTerminalLayout.SEARCH_WIDTH,
                CraftTerminalLayout.SEARCH_HEIGHT, mouseX, mouseY)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || UiRect.contains(x + CraftTerminalLayout.SEARCH_WIDTH - 12, y,
                12, CraftTerminalLayout.SEARCH_HEIGHT, mouseX, mouseY)) {
            this.searchBox.setValue("");
        } else if (!this.menu.getCarried().isEmpty()) {
            this.searchBox.setValue(this.menu.getCarried().getHoverName().getString());
        }
        this.searchBox.setFocused(true);
        this.setFocused(this.searchBox);
        return true;
    }

    private boolean handleStorageClick(double mouseX, double mouseY, int button) {
        int cell = resolveVisibleStorageCell(mouseX, mouseY);
        if (cell < 0) {
            return false;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            this.setFocused(null);
        }
        StorageEntry entry = getStorageEntryForCell(cell);
        if (entry == null) {
            if (!this.menu.getCarried().isEmpty()) {
                return returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
            }
            return true;
        }

        boolean spaceDown = InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE);
        if (this.menu.getCarried().isEmpty()
                && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && (Screen.hasShiftDown() || spaceDown)) {
            int requested = spaceDown
                    ? (int) Math.min(Integer.MAX_VALUE, entry.count())
                    : Math.min(entry.stack().getMaxStackSize(), (int) Math.min(Integer.MAX_VALUE, entry.count()));
            PacketDistributor.sendToServer(new C2SRtsBulkStorageOpPayload(
                    C2SRtsBulkStorageOpPayload.WITHDRAW,
                    entry.stack().copyWithCount(1), requested));
            return true;
        }
        if (!this.menu.getCarried().isEmpty()) {
            return returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
        }
        int requested = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE;
        ItemStack prototype = entry.stack().copyWithCount(1);
        PacketDistributor.sendToServer(new C2SRtsLinkedPickupPayload(prototype, requested));
        return true;
    }

    private boolean returnCarriedToLinked(int requestedAmount) {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (itemId == null) {
            return false;
        }
        int amount = Math.max(1, Math.min(requestedAmount, carried.getCount()));
        PacketDistributor.sendToServer(new C2SRtsReturnCarriedPayload(itemId.toString(), amount));
        return true;
    }

    private void sendBulkDeposit(byte action) {
        PacketDistributor.sendToServer(new C2SRtsBulkStorageOpPayload(
                action, ItemStack.EMPTY, 0));
    }

    private void changeRows(int rows) {
        int previous = this.layout.rows();
        this.layout.setRows(Math.min(rows, maximumRowsForScreen()));
        if (previous == this.layout.rows()) {
            return;
        }
        RtsClientUiStateStore.setCraftTerminalRows(this.layout.rows());
        recenterVisibleArea();
        if (this.searchBox != null) {
            this.searchBox.setY(this.topPos + this.layout.searchY() + 2);
        }
        this.scrollState.update(ClientRtsController.get(), this.layout.rows());
    }

    private int maximumRowsForScreen() {
        int fixedHeight = CraftTerminalLayout.IMAGE_HEIGHT
                - CraftTerminalLayout.MAX_ROWS * CraftTerminalLayout.SLOT_SIZE;
        int fittingRows = (this.height - fixedHeight) / CraftTerminalLayout.SLOT_SIZE;
        return Mth.clamp(fittingRows,
                CraftTerminalLayout.MIN_ROWS, CraftTerminalLayout.MAX_ROWS);
    }

    /** 只按当前真实可见区域居中；Slot 仍使用菜单定义的固定相对坐标。 */
    private void recenterVisibleArea() {
        this.topPos = (this.height - this.layout.visibleHeight()) / 2 - this.layout.visualTop();
    }

    private void onSearchChanged(String value) {
        String safe = value == null ? "" : value;
        ClientRtsController controller = ClientRtsController.get();
        if (RtsClientUiStateStore.isCraftTerminalSearchPinned()) {
            RtsClientUiStateStore.setCraftTerminalSearch(safe);
        }
        if (!safe.equals(controller.getStorageSearch())) {
            controller.setStorageSearch(safe);
            this.scrollState.expectFreshPage(controller, 0);
        }
        syncSearchToJei(safe);
    }

    private void syncSearchFromController() {
        if (this.searchBox == null || this.searchBox.isFocused()) {
            return;
        }
        String controllerSearch = ClientRtsController.get().getStorageSearch();
        String safe = controllerSearch == null ? "" : controllerSearch;
        if (!safe.equals(this.searchBox.getValue())) {
            this.searchBox.setValue(safe);
        }
    }

    private void syncSearchToJei(String value) {
        if (this.searchMode == CraftTerminalSearchMode.STANDARD
                || !ModList.get().isLoaded("jei")
                || !RtsJeiSearchBridge.isAvailable()) {
            return;
        }
        String safe = value == null ? "" : value;
        if (!safe.equals(RtsJeiSearchBridge.getSearchText())) {
            RtsJeiSearchBridge.setSearchText(safe);
        }
        this.lastSearchSentToJei = safe;
    }

    private void syncSearchFromJei() {
        if (this.searchMode != CraftTerminalSearchMode.BIDIRECTIONAL
                || this.searchBox == null
                || this.searchBox.isFocused()
                || !ModList.get().isLoaded("jei")
                || !RtsJeiSearchBridge.isAvailable()) {
            return;
        }
        String jeiSearch = RtsJeiSearchBridge.getSearchText();
        if (!jeiSearch.equals(this.lastSearchSentToJei)
                && !jeiSearch.equals(this.searchBox.getValue())) {
            this.searchBox.setValue(jeiSearch);
        }
        this.lastSearchSentToJei = jeiSearch;
    }

    private int resolveVisibleStorageCell(double mouseX, double mouseY) {
        int gridX = this.leftPos + CraftTerminalLayout.GRID_X;
        int gridY = this.topPos + this.layout.storageGridY();
        int width = CraftTerminalLayout.COLUMNS * CraftTerminalLayout.SLOT_SIZE;
        int height = this.layout.rows() * CraftTerminalLayout.SLOT_SIZE;
        if (!UiRect.contains(gridX, gridY, width, height, mouseX, mouseY)) {
            return -1;
        }
        int column = Mth.floor((mouseX - gridX) / CraftTerminalLayout.SLOT_SIZE);
        int row = Mth.floor((mouseY - gridY) / CraftTerminalLayout.SLOT_SIZE);
        return row * CraftTerminalLayout.COLUMNS + column;
    }

    private StorageEntry getStorageEntryForCell(int cell) {
        return this.scrollState.entryAtVisibleCell(cell);
    }

    private void renderStorageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int cell = resolveVisibleStorageCell(mouseX, mouseY);
        StorageEntry entry = cell < 0 ? null : getStorageEntryForCell(cell);
        if (entry != null) {
            graphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
        }
    }

    private void renderButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        String key = null;
        if (containsRelative(CraftTerminalLayout.MODE_X, this.layout.visualTop() + 3,
                10, 10, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.search_mode." + this.searchMode.name().toLowerCase();
        } else if (containsRelative(CraftTerminalLayout.PIN_X, this.layout.visualTop() + 3,
                10, 10, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.search_pin";
        } else if (containsRelative(CLEAR_BUTTON_X, CLEAR_STORAGE_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.clear_to_storage";
        } else if (containsRelative(CLEAR_BUTTON_X, CLEAR_PLAYER_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.clear_to_inventory";
        } else if (containsRelative(DEPOSIT_BUTTON_X, DEPOSIT_ALL_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.deposit_all";
        } else if (containsRelative(DEPOSIT_BUTTON_X, DEPOSIT_HOTBAR_Y,
                SIDE_BUTTON_W, SIDE_BUTTON_H, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.deposit_hotbar";
        } else if (containsRelative(TOOLBAR_X, this.layout.visualTop() + 1,
                TOOLBAR_W, TOOLBAR_H, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.sort";
        } else if (containsRelative(TOOLBAR_X, this.layout.visualTop() + 16,
                TOOLBAR_W, TOOLBAR_H, mouseX, mouseY)) {
            key = "screen.rtsbuilding.craft_terminal.sort_direction";
        }
        if (key != null) {
            graphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
        }
    }

    private void renderLocalCraftResultFallback(GuiGraphics graphics) {
        Slot resultSlot = this.menu.getSlot(0);
        if (!resultSlot.getItem().isEmpty()) {
            return;
        }
        ItemStack preview = resolveLocalCraftPreview();
        if (!preview.isEmpty()) {
            int x = this.leftPos + resultSlot.x;
            int y = this.topPos + resultSlot.y;
            graphics.renderItem(preview, x, y);
            graphics.renderItemDecorations(this.font, preview, x, y);
        }
    }

    private ItemStack resolveLocalCraftPreview() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.level == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> inputs = new ArrayList<>(9);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.menu.getSlot(1 + i).getItem();
            inputs.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            any |= !stack.isEmpty();
        }
        if (!any) {
            return ItemStack.EMPTY;
        }
        CraftingInput input = CraftingInput.of(3, 3, inputs);
        return minecraft.level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, minecraft.level)
                .map(holder -> {
                    CraftingRecipe recipe = holder.value();
                    ItemStack result = recipe.assemble(input, minecraft.level.registryAccess());
                    return result.isEmpty()
                            ? recipe.getResultItem(minecraft.level.registryAccess()).copy()
                            : result.copy();
                })
                .orElse(ItemStack.EMPTY);
    }

    public Rect2i getToolbarArea() {
        return new Rect2i(this.leftPos + TOOLBAR_X,
                this.topPos + this.layout.visualTop(),
                -TOOLBAR_X,
                29);
    }

    public StorageEntry getStorageEntryAt(double mouseX, double mouseY) {
        int cell = resolveVisibleStorageCell(mouseX, mouseY);
        return cell < 0 ? null : getStorageEntryForCell(cell);
    }

    public Rect2i getStorageSlotAreaAt(double mouseX, double mouseY) {
        int cell = resolveVisibleStorageCell(mouseX, mouseY);
        if (cell < 0) {
            return null;
        }
        int column = cell % CraftTerminalLayout.COLUMNS;
        int row = cell / CraftTerminalLayout.COLUMNS;
        return new Rect2i(
                this.leftPos + CraftTerminalLayout.GRID_X + column * CraftTerminalLayout.SLOT_SIZE,
                this.topPos + this.layout.storageGridY() + row * CraftTerminalLayout.SLOT_SIZE,
                CraftTerminalLayout.SLOT_SIZE,
                CraftTerminalLayout.SLOT_SIZE);
    }

    private boolean isInsideVisibleTerminal(double mouseX, double mouseY) {
        return UiRect.contains(this.leftPos + TOOLBAR_X,
                this.topPos + this.layout.visualTop(),
                CraftTerminalLayout.WIDTH - TOOLBAR_X,
                CraftTerminalLayout.TERMINAL_BOTTOM - this.layout.visualTop(),
                mouseX, mouseY);
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        return UiRect.contains(
                this.leftPos + CraftTerminalLayout.SCROLLBAR_X,
                this.topPos + this.layout.scrollbarY(),
                CraftTerminalLayout.SCROLLBAR_WIDTH,
                this.layout.scrollbarHeight(),
                mouseX, mouseY);
    }

    private void updateScrollbarFromMouse(double mouseY) {
        double fraction = (mouseY - (this.topPos + this.layout.scrollbarY()))
                / Math.max(1.0D, this.layout.scrollbarHeight());
        this.scrollState.setFromFraction(
                ClientRtsController.get(), fraction, this.layout.rows());
    }

    private boolean containsRelative(
            int x, int y, int width, int height, double mouseX, double mouseY) {
        return UiRect.contains(this.leftPos + x, this.topPos + y,
                width, height, mouseX, mouseY);
    }
}
