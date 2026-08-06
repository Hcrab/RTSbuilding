package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalScrollState;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalSearchMode;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalSortAdapter;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalSortControlsRenderer;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.compat.jei.RtsJeiSearchBridge;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedQuickMovePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalSortField;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.ModList;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * RTS 一体化合成终端界面。
 *
 * <p>界面借鉴 AE2/Refined Storage 已验证的“储存浏览器 + 3×3 合成区 + 玩家背包”
 * 纵向结构，但使用 RTS 自己的主题、网络协议和 linked storage。该类仍是屏幕状态机
 * 所有者；纯布局和跨页滚动分别交给小型辅助类，服务端物品变更全部通过权威菜单动作
 * 完成，客户端不预测生成、删除或搬动物品。</p>
 */
public final class RtsCraftTerminalScreen extends AbstractContainerScreen<CraftingMenu> {
    private static final int CRAFT_SLOT_START = 1;
    private static final int CRAFT_SLOT_END = 10;
    private static final int PLAYER_SLOT_START = 10;
    private static final int HOTBAR_SLOT_START = 37;
    private static final int PLAYER_SLOT_END = 46;
    private CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(
            RtsClientUiStateStore.getCraftTerminalRows());
    private final CraftTerminalScrollState scrollState = new CraftTerminalScrollState();
    private EditBox searchBox;
    private CraftTerminalSearchMode searchMode = CraftTerminalSearchMode.STANDARD;
    private int previousPageSize = 90;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset = CraftTerminalLayout.SCROLLBAR_HANDLE_HEIGHT / 2.0D;
    private String lastSearchSentToJei = "";
    private final UiControlAnimationRegistry<CraftTerminalUiAction> actionAnimations =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE,
                    CraftTerminalUiAction.values().length);
    private final CraftTerminalSortControlsRenderer sortControls =
            new CraftTerminalSortControlsRenderer();

    public RtsCraftTerminalScreen(CraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CraftTerminalLayout.WIDTH;
        this.imageHeight = CraftTerminalLayout.IMAGE_HEIGHT;
        this.inventoryLabelX = 8;
        this.titleLabelX = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.layout = CraftTerminalLayout.geometry(
                Math.min(this.layout.rows, maximumRowsForScreen()));
        recenterVisibleArea();
        ClientRtsController controller = ClientRtsController.get();
        this.previousPageSize = controller.getStoragePageSize();
        controller.updateStoragePageSize(CraftTerminalScrollState.PAGE_SIZE);
        controller.setStorageSort(CraftTerminalSortAdapter.normalize(
                controller.getStorageSort()));
        this.scrollState.reset(controller);

        this.searchMode = CraftTerminalSearchMode.parse(
                RtsClientUiStateStore.getCraftTerminalSearchMode());
        boolean pinned = RtsClientUiStateStore.isCraftTerminalSearchPinned();
        String initialSearch = pinned ? RtsClientUiStateStore.getCraftTerminalSearch() : "";

        this.searchBox = new EditBox(
                this.font,
                this.leftPos + (int) this.layout.search.getX() + 3,
                this.topPos + (int) this.layout.search.getY() + 2,
                (int) this.layout.search.getWidth() - 14,
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
        this.sortControls.clear();
        this.actionAnimations.clear();
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.scrollState.update(ClientRtsController.get(), this.layout.rows);
        syncSearchFromController();
        syncSearchFromJei();
        this.renderBackground(graphics);
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
                controller.getStorageTotalEntries());
        this.sortControls.render(
                graphics,
                this.leftPos,
                this.topPos,
                this.layout.sortControls,
                CraftTerminalSortAdapter.fromStorage(controller.getStorageSort()),
                controller.isStorageSortAscending(),
                mouseX,
                mouseY);
        renderActionAnimations(graphics, mouseX, mouseY);
    }

    private void renderActionAnimations(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        CraftTerminalUiAction hovered = actionAt(mouseX, mouseY);
        for (CraftTerminalUiAction action : CraftTerminalUiAction.values()) {
            if (action == CraftTerminalUiAction.SEARCH
                    || action == CraftTerminalUiAction.SCROLLBAR
                    || action == CraftTerminalUiAction.SORT
                    || action == CraftTerminalUiAction.SORT_DIRECTION) {
                continue;
            }
            double strength = this.actionAnimations.update(
                    action,
                    UiControlState.enabled().withInteraction(
                            action == hovered, false, false),
                    Config.isUiAnimationsEnabled()).hover();
            CraftTerminalRenderer.renderActionHover(
                    graphics,
                    this.leftPos,
                    this.topPos,
                    this.layout.actionBounds(action),
                    strength);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font,
                Component.translatable("screen.rtsbuilding.craft_terminal.short_title"),
                this.titleLabelX, this.layout.visualTop + 5,
                CraftTerminalStyle.TEXT.toArgb(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (handleChromeActionClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isInsideScrollbar(mouseX, mouseY)) {
                this.draggingScrollbar = true;
                double relativeX = mouseX - this.leftPos;
                double relativeY = mouseY - this.topPos;
                ClientRtsController controller = ClientRtsController.get();
                UiRect handle = this.layout.scrollbarHandle(this.scrollState.fraction(
                        controller.getStorageTotalEntries(), this.layout.rows));
                this.scrollbarDragOffset = handle.contains(relativeX, relativeY)
                        ? relativeY - handle.getY()
                        : CraftTerminalLayout.SCROLLBAR_HANDLE_HEIGHT / 2.0D;
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
        this.scrollbarDragOffset = CraftTerminalLayout.SCROLLBAR_HANDLE_HEIGHT / 2.0D;
        this.sortControls.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (isInsideStorageViewport(mouseX, mouseY)) {
            int delta = scrollY > 0.0D ? -1 : (scrollY < 0.0D ? 1 : 0);
            if (delta != 0) {
                this.scrollState.scrollRows(ClientRtsController.get(), delta, this.layout.rows);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
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

    private boolean handleChromeActionClick(double mouseX, double mouseY, int button) {
        CraftTerminalUiAction action = actionAt(mouseX, mouseY);
        if (action == null || action == CraftTerminalUiAction.SEARCH
                || action == CraftTerminalUiAction.SEARCH_CLEAR
                || action == CraftTerminalUiAction.SCROLLBAR) {
            return false;
        }
        if (action == CraftTerminalUiAction.SORT
                || action == CraftTerminalUiAction.SORT_DIRECTION) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return true;
            }
            this.sortControls.press(action);
        }
        switch (action) {
            case SEARCH_MODE:
                this.searchMode = this.searchMode.next();
                RtsClientUiStateStore.setCraftTerminalSearchMode(this.searchMode.name());
                syncSearchToJei(this.searchBox == null ? "" : this.searchBox.getValue());
                return true;
            case SEARCH_PIN:
                boolean pinned = !RtsClientUiStateStore.isCraftTerminalSearchPinned();
                RtsClientUiStateStore.setCraftTerminalSearchPinned(pinned);
                RtsClientUiStateStore.setCraftTerminalSearch(
                        pinned && this.searchBox != null ? this.searchBox.getValue() : "");
                return true;
            case CYCLE_ROWS:
                changeRows(this.layout.rows >= maximumRowsForScreen()
                        ? CraftTerminalLayout.MIN_ROWS : this.layout.rows + 1);
                return true;
            case SORT:
                ClientRtsController sortController = ClientRtsController.get();
                CraftTerminalSortField currentField =
                        CraftTerminalSortAdapter.fromStorage(
                                sortController.getStorageSort());
                sortController.setStorageSort(CraftTerminalSortAdapter.toStorage(
                        currentField.next()));
                this.scrollState.expectFreshPage(sortController, 0);
                return true;
            case SORT_DIRECTION:
                ClientRtsController directionController = ClientRtsController.get();
                directionController.toggleSortDirection();
                this.scrollState.expectFreshPage(directionController, 0);
                return true;
            case CLEAR_TO_STORAGE:
                if (Screen.hasShiftDown()) {
                    sendMenuSlotsToStorage(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                            ? HOTBAR_SLOT_START : PLAYER_SLOT_START, PLAYER_SLOT_END);
                } else {
                    sendMenuSlotsToStorage(CRAFT_SLOT_START, CRAFT_SLOT_END);
                }
                return true;
            case CLEAR_TO_INVENTORY:
                quickMoveCraftingGridToInventory();
                return true;
            case DEPOSIT_ALL:
                sendMenuSlotsToStorage(PLAYER_SLOT_START, PLAYER_SLOT_END);
                return true;
            case DEPOSIT_HOTBAR:
                sendMenuSlotsToStorage(HOTBAR_SLOT_START, PLAYER_SLOT_END);
                return true;
            default:
                return false;
        }
    }

    private boolean handleSearchClick(double mouseX, double mouseY, int button) {
        if (!this.layout.search.contains(mouseX - this.leftPos, mouseY - this.topPos)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || this.layout.searchClear.contains(
                mouseX - this.leftPos, mouseY - this.topPos)) {
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
            int transfers = spaceDown
                    ? Math.min(36, (int) Math.max(1L,
                    (entry.count() + entry.stack().getMaxStackSize() - 1L)
                            / entry.stack().getMaxStackSize()))
                    : 1;
            ItemStack prototype = entry.stack().copyWithCount(1);
            for (int i = 0; i < transfers; i++) {
                PacketDistributor.sendToServer(new C2SRtsLinkedQuickMovePayload(prototype));
            }
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

    /**
     * 复用 1.20.1 已有的单格权威协议完成批量存入，不引入新的服务端储存变更路径。
     */
    private void sendMenuSlotsToStorage(int startInclusive, int endExclusive) {
        int upper = Math.min(endExclusive, this.menu.slots.size());
        for (int slot = Math.max(0, startInclusive); slot < upper; slot++) {
            if (this.menu.getSlot(slot).hasItem()) {
                PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(slot));
            }
        }
    }

    /** 清空到玩家背包继续走原版菜单 QUICK_MOVE，服务端仍是菜单权威。 */
    private void quickMoveCraftingGridToInventory() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        for (int slot = CRAFT_SLOT_START; slot < CRAFT_SLOT_END; slot++) {
            if (this.menu.getSlot(slot).hasItem()) {
                minecraft.gameMode.handleInventoryMouseClick(
                        this.menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
            }
        }
    }

    private void changeRows(int rows) {
        int previous = this.layout.rows;
        this.layout = CraftTerminalLayout.geometry(Math.min(rows, maximumRowsForScreen()));
        if (previous == this.layout.rows) {
            return;
        }
        RtsClientUiStateStore.setCraftTerminalRows(this.layout.rows);
        recenterVisibleArea();
        if (this.searchBox != null) {
            this.searchBox.setX(this.leftPos + (int) this.layout.search.getX() + 3);
            this.searchBox.setY(this.topPos + (int) this.layout.search.getY() + 2);
        }
        this.scrollState.update(ClientRtsController.get(), this.layout.rows);
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
        this.leftPos = (this.width - CraftTerminalLayout.VISIBLE_WIDTH) / 2;
        this.topPos = (this.height - this.layout.visibleHeight()) / 2 - this.layout.visualTop;
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
        return this.layout.storageCellAt(mouseX - this.leftPos, mouseY - this.topPos);
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
        CraftTerminalUiAction action = actionAt(mouseX, mouseY);
        if (action != null) {
            switch (action) {
                case SEARCH_MODE:
                    key = "screen.rtsbuilding.craft_terminal.search_mode."
                            + this.searchMode.name().toLowerCase();
                    break;
                case SEARCH_PIN:
                    key = "screen.rtsbuilding.craft_terminal.search_pin";
                    break;
                case CYCLE_ROWS:
                    key = "screen.rtsbuilding.craft_terminal.rows";
                    break;
                case CLEAR_TO_STORAGE:
                    key = "screen.rtsbuilding.craft_terminal.utility_button";
                    break;
                case CLEAR_TO_INVENTORY:
                    key = "screen.rtsbuilding.craft_terminal.clear_to_inventory";
                    break;
                case DEPOSIT_ALL:
                    key = "screen.rtsbuilding.craft_terminal.deposit_all";
                    break;
                case DEPOSIT_HOTBAR:
                    key = "screen.rtsbuilding.craft_terminal.deposit_hotbar";
                    break;
                case SORT:
                    RtsStorageSort sort = CraftTerminalSortAdapter.normalize(
                            ClientRtsController.get().getStorageSort());
                    key = "screen.rtsbuilding.craft_terminal.sort_field."
                            + (sort == RtsStorageSort.NAME ? "name" : "quantity");
                    break;
                case SORT_DIRECTION:
                    key = "screen.rtsbuilding.craft_terminal.sort_direction."
                            + (ClientRtsController.get().isStorageSortAscending()
                            ? "ascending" : "descending");
                    break;
                default:
                    break;
            }
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
        TransientCraftingContainer input = new TransientCraftingContainer(this.menu, 3, 3);
        for (int i = 0; i < inputs.size(); i++) {
            input.setItem(i, inputs.get(i));
        }
        java.util.Optional<CraftingRecipe> recipe = minecraft.level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, minecraft.level);
        if (recipe.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipe.get().assemble(input, minecraft.level.registryAccess());
        if (result.isEmpty()) {
            result = recipe.get().getResultItem(minecraft.level.registryAccess());
        }
        return result.isEmpty() ? ItemStack.EMPTY : result.copy();
    }

    public Rect2i getToolbarArea() {
        return new Rect2i(this.leftPos + CraftTerminalLayout.WIDTH,
                this.topPos + this.layout.visualTop,
                CraftTerminalLayout.VISIBLE_WIDTH - CraftTerminalLayout.WIDTH,
                this.layout.visibleHeight());
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
        UiRect bounds = this.layout.storageCell(cell);
        return new Rect2i(
                this.leftPos + (int) bounds.getX(),
                this.topPos + (int) bounds.getY(),
                (int) bounds.getWidth(),
                (int) bounds.getHeight());
    }

    private boolean isInsideStorageViewport(double mouseX, double mouseY) {
        double relativeX = mouseX - this.leftPos;
        double relativeY = mouseY - this.topPos;
        return this.layout.storageGrid.contains(relativeX, relativeY)
                || this.layout.scrollbar.contains(relativeX, relativeY);
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        return this.layout.scrollbar.contains(
                mouseX - this.leftPos, mouseY - this.topPos);
    }

    private void updateScrollbarFromMouse(double mouseY) {
        double fraction = this.layout.scrollbarFractionForPointer(
                mouseY - this.topPos, this.scrollbarDragOffset);
        this.scrollState.setFromFraction(
                ClientRtsController.get(), fraction, this.layout.rows);
    }

    private CraftTerminalUiAction actionAt(double mouseX, double mouseY) {
        return this.layout.actionAt(mouseX - this.leftPos, mouseY - this.topPos);
    }
}
