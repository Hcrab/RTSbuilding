package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal.CraftTerminalScrollState;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsClearCraftingGridPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsBulkStorageOpPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * RTS 合成终端的 26.1 客户端界面。
 *
 * <p>终端只展示 {@link ClientRtsController} 的共享储存页面状态：排序、搜索、分页和刷新
 * 都继续由既有 C2S/服务端页面链裁定。因此从终端切回容器 overlay 或 RTS 底栏时，三者不会
 * 各自维护一份排序视图。物品提取、归还和 Shift 存入也都只发往已有服务端权威操作，不在
 * 客户端伪造库存变更。</p>
 */
public final class RtsCraftTerminalScreen extends AbstractContainerScreen<CraftingMenu> {
    private static final int STORAGE_ROWS = CraftTerminalLayout.MAX_ROWS;

    private final CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(STORAGE_ROWS);
    private final CraftTerminalScrollState scrollState = new CraftTerminalScrollState();
    private EditBox searchBox;
    private int previousPageSize = CraftTerminalScrollState.PAGE_SIZE;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset = CraftTerminalLayout.SCROLLBAR_HANDLE_HEIGHT / 2.0D;

    public RtsCraftTerminalScreen(CraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CraftTerminalLayout.VISIBLE_WIDTH, CraftTerminalLayout.IMAGE_HEIGHT);
        this.inventoryLabelX = CraftTerminalLayout.INVENTORY_X;
        this.inventoryLabelY = CraftTerminalLayout.INVENTORY_Y - 11;
        this.titleLabelX = 6;
        this.titleLabelY = 5;
    }

    @Override
    protected void init() {
        super.init();
        recreateTerminalSlots();
        ClientRtsController controller = ClientRtsController.get();
        this.previousPageSize = controller.getStoragePageSize();
        controller.updateStoragePageSize(CraftTerminalScrollState.PAGE_SIZE);
        this.scrollState.reset(controller);

        int searchX = this.leftPos + (int) this.layout.search.getX() + 3;
        int searchY = this.topPos + (int) this.layout.search.getY() + 2;
        this.searchBox = new EditBox(
                this.font,
                searchX,
                searchY,
                (int) this.layout.search.getWidth() - 14,
                8,
                Component.translatable("screen.rtsbuilding.craft_terminal.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setMaxLength(128);
        this.searchBox.setTextColor(CraftTerminalStyle.TEXT.toArgb());
        this.searchBox.setTextColorUneditable(CraftTerminalStyle.UNEDITABLE_TEXT.toArgb());
        this.searchBox.setValue(controller.getStorageSearch());
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);
    }

    /**
     * 服务器和客户端都保留原版 CraftingMenu 的槽位编号，但客户端构造的是原版菜单实例，
     * 因此在这里同步一次终端皮肤坐标。编号不变，Shift 导入仍按既有 C2S menu slot 处理。
     */
    private void recreateTerminalSlots() {
        Slot result = this.menu.getSlot(0);
        Slot firstCraftingSlot = this.menu.getSlot(1);
        if (this.minecraft.player == null || !(firstCraftingSlot.container instanceof CraftingContainer craftingSlots)) {
            return;
        }
        replaceTerminalSlot(0, new ResultSlot(this.minecraft.player, craftingSlots, result.container,
                result.getContainerSlot(), CraftTerminalLayout.menuSlotX(0), CraftTerminalLayout.menuSlotY(0)));
        for (int index = 1; index <= 45; index++) {
            Slot original = this.menu.getSlot(index);
            replaceTerminalSlot(index, new Slot(original.container, original.getContainerSlot(),
                    CraftTerminalLayout.menuSlotX(index), CraftTerminalLayout.menuSlotY(index)));
        }
    }

    /** 客户端保持服务端菜单槽位编号，仅替换显示与命中使用的坐标化槽位。 */
    private void replaceTerminalSlot(int menuSlot, Slot replacement) {
        replacement.index = this.menu.getSlot(menuSlot).index;
        this.menu.slots.set(menuSlot, replacement);
    }

    @Override
    public void removed() {
        ClientRtsController.get().updateStoragePageSize(this.previousPageSize);
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.scrollState.update(ClientRtsController.get(), this.layout.rows);
        syncSearchFromController();
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        ClientRtsController controller = ClientRtsController.get();
        CraftTerminalRenderer.render(
                guiGraphics,
                this.font,
                this.leftPos,
                this.topPos,
                this.layout,
                this.scrollState,
                controller.getStorageTotalEntries());
        renderSortControls(guiGraphics, mouseX, mouseY, controller);
        renderStorageTooltip(guiGraphics, mouseX, mouseY);
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.text(
                this.font,
                Component.translatable("screen.rtsbuilding.craft_terminal.short_title"),
                this.titleLabelX,
                this.titleLabelY,
                CraftTerminalStyle.TEXT.toArgb(),
                false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (handleControlClick(mouseX, mouseY, button)) {
                return true;
            }
            if (handleSearchClick(mouseX, mouseY, button)) {
                return true;
            }
            if (handleStorageClick(mouseX, mouseY, button)) {
                return true;
            }
            if (com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys.isShiftDown()) {
                Slot hovered = this.getSlotUnderMouse();
                if (hovered != null && hovered.hasItem()) {
                    int menuSlot = this.menu.slots.indexOf(hovered);
                    if (menuSlot >= 0) {
                        if (menuSlot == 0 && button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                            return true;
                        }
                        // 复用已有 C2S 导入与服务端容器槽位校验；终端不再额外要求近距离或会话确认。
                        RtsClientNetworkBridge.send(new C2SRtsImportMenuSlotPayload(menuSlot));
                        return true;
                    }
                }
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            UiRect handle = this.layout.scrollbarHandle(this.scrollState.fraction(
                    ClientRtsController.get().getStorageTotalEntries(), this.layout.rows));
            double relativeY = mouseY - this.topPos;
            this.scrollbarDragOffset = handle.contains(mouseX - this.leftPos, relativeY)
                    ? relativeY - handle.getY()
                    : CraftTerminalLayout.SCROLLBAR_HANDLE_HEIGHT / 2.0D;
            updateScrollbarFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateScrollbarFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingScrollbar = false;
        this.scrollbarDragOffset = CraftTerminalLayout.SCROLLBAR_HANDLE_HEIGHT / 2.0D;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideStorageViewport(mouseX, mouseY)) {
            int delta = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
            if (delta != 0) {
                this.scrollState.scrollRows(ClientRtsController.get(), delta, this.layout.rows);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
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
            this.searchBox.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }

    /** 控件只发送语义请求；清格、批量存取和排序都由服务端/共享 controller 裁定。 */
    private boolean handleControlClick(double mouseX, double mouseY, int button) {
        CraftTerminalUiAction action = this.layout.actionAt(
                mouseX - this.leftPos, mouseY - this.topPos);
        if (action == null) {
            return false;
        }
        ClientRtsController controller = ClientRtsController.get();
        switch (action) {
            case SORT -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    controller.cycleSort();
                    this.scrollState.expectFreshPage(controller, 0);
                }
                return true;
            }
            case SORT_DIRECTION -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    controller.toggleSortDirection();
                    this.scrollState.expectFreshPage(controller, 0);
                }
                return true;
            }
            case CLEAR_TO_STORAGE -> {
                if (com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys.isShiftDown()) {
                    sendBulkDeposit(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                            ? C2SRtsBulkStorageOpPayload.DEPOSIT_HOTBAR
                            : C2SRtsBulkStorageOpPayload.DEPOSIT_ALL);
                } else {
                    RtsClientNetworkBridge.send(new C2SRtsClearCraftingGridPayload(
                            button == GLFW.GLFW_MOUSE_BUTTON_RIGHT));
                }
                return true;
            }
            case CLEAR_TO_INVENTORY -> {
                RtsClientNetworkBridge.send(new C2SRtsClearCraftingGridPayload(true));
                return true;
            }
            case DEPOSIT_ALL -> {
                sendBulkDeposit(C2SRtsBulkStorageOpPayload.DEPOSIT_ALL);
                return true;
            }
            case DEPOSIT_HOTBAR -> {
                sendBulkDeposit(C2SRtsBulkStorageOpPayload.DEPOSIT_HOTBAR);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleSearchClick(double mouseX, double mouseY, int button) {
        double relativeX = mouseX - this.leftPos;
        double relativeY = mouseY - this.topPos;
        if (!this.layout.search.contains(relativeX, relativeY)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || this.layout.searchClear.contains(relativeX, relativeY)) {
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
            return !this.menu.getCarried().isEmpty()
                    && returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
        }
        if (!this.menu.getCarried().isEmpty()) {
            return returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys.isShiftDown()) {
            RtsClientNetworkBridge.send(new C2SRtsBulkStorageOpPayload(
                    C2SRtsBulkStorageOpPayload.WITHDRAW,
                    entry.stack().copyWithCount(1),
                    Math.min(entry.stack().getMaxStackSize(),
                            (int) Math.min(Integer.MAX_VALUE, entry.count()))));
            return true;
        }
        int requested = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                ? 1
                : (int) Math.min((long) entry.stack().getMaxStackSize(), entry.count());
        ItemStack prototype = entry.stack().copyWithCount(1);
        RtsClientNetworkBridge.send(new C2SRtsLinkedPickupPayload(prototype, requested));
        return true;
    }

    /** 将鼠标携带栈交回链接存储；库存变化只等待服务端菜单同步，不作本地预扣。 */
    private boolean returnCarriedToLinked(int requestedAmount) {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (itemId == null) {
            return false;
        }
        int amount = Math.max(1, Math.min(requestedAmount, carried.getCount()));
        RtsClientNetworkBridge.send(new C2SRtsReturnCarriedPayload(itemId.toString(), amount));
        return true;
    }

    private static void sendBulkDeposit(byte action) {
        RtsClientNetworkBridge.send(new C2SRtsBulkStorageOpPayload(
                action, ItemStack.EMPTY, 0));
    }

    private void renderSortControls(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            ClientRtsController controller) {
        UiRect field = this.layout.sortControls.field;
        UiRect direction = this.layout.sortControls.direction;
        drawSortButton(
                guiGraphics,
                field,
                sortShort(controller.getStorageSort()),
                field.contains(mouseX - this.leftPos, mouseY - this.topPos));
        drawSortButton(
                guiGraphics,
                direction,
                controller.isStorageSortAscending() ? "A" : "D",
                direction.contains(mouseX - this.leftPos, mouseY - this.topPos));
    }

    private void drawSortButton(GuiGraphicsExtractor guiGraphics, UiRect bounds, String label, boolean hovered) {
        int x = this.leftPos + (int) bounds.getX();
        int y = this.topPos + (int) bounds.getY();
        CraftTerminalRenderer.renderActionHover(guiGraphics, this.leftPos, this.topPos, bounds, hovered);
        UiCompactFrameRenderer.frame(
                new MinecraftUiCanvas(guiGraphics, this.font),
                new UiRect(x, y, bounds.getWidth(), bounds.getHeight()),
                hovered ? CraftTerminalStyle.BUTTON_HOVER : CraftTerminalStyle.BUTTON,
                CraftTerminalStyle.BUTTON_BORDER_LIGHT,
                CraftTerminalStyle.BUTTON_BORDER_DARK);
        guiGraphics.centeredText(
                this.font,
                label,
                x + (int) bounds.getWidth() / 2,
                y + 7,
                CraftTerminalStyle.BUTTON_TEXT.toArgb());
    }

    private void renderStorageTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        StorageEntry entry = getStorageEntryAt(mouseX, mouseY);
        if (entry != null && !entry.stack().isEmpty()) {
            guiGraphics.setTooltipForNextFrame(this.font, entry.stack(), mouseX, mouseY);
        }
    }

    private void onSearchChanged(String value) {
        String safe = value == null ? "" : value;
        ClientRtsController controller = ClientRtsController.get();
        if (!safe.equals(controller.getStorageSearch())) {
            controller.setStorageSearch(safe);
            this.scrollState.expectFreshPage(controller, 0);
        }
    }

    private void syncSearchFromController() {
        if (this.searchBox == null || this.searchBox.isFocused()) {
            return;
        }
        String expected = ClientRtsController.get().getStorageSearch();
        String safe = expected == null ? "" : expected;
        if (!safe.equals(this.searchBox.getValue())) {
            this.searchBox.setValue(safe);
        }
    }

    private int resolveVisibleStorageCell(double mouseX, double mouseY) {
        return this.layout.storageCellAt(mouseX - this.leftPos, mouseY - this.topPos);
    }

    private StorageEntry getStorageEntryForCell(int cell) {
        return cell < 0 ? null : this.scrollState.entryAtVisibleCell(cell);
    }

    public Rect2i getToolbarArea() {
        return new Rect2i(
                this.leftPos + CraftTerminalLayout.WIDTH,
                this.topPos,
                CraftTerminalLayout.VISIBLE_WIDTH - CraftTerminalLayout.WIDTH,
                CraftTerminalLayout.IMAGE_HEIGHT);
    }

    public StorageEntry getStorageEntryAt(double mouseX, double mouseY) {
        return getStorageEntryForCell(resolveVisibleStorageCell(mouseX, mouseY));
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
        return this.layout.scrollbar.contains(mouseX - this.leftPos, mouseY - this.topPos);
    }

    private void updateScrollbarFromMouse(double mouseY) {
        double fraction = this.layout.scrollbarFractionForPointer(
                mouseY - this.topPos,
                this.scrollbarDragOffset);
        this.scrollState.setFromFraction(ClientRtsController.get(), fraction, this.layout.rows);
    }

    private static String sortShort(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Q";
            case MOD -> "M";
            case NAME -> "N";
        };
    }
}
