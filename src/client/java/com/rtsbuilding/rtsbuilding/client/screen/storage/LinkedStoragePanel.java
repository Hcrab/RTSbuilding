package com.rtsbuilding.rtsbuilding.client.screen.storage;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiAction;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiTransition;
import com.rtsbuilding.rtsbuilding.uikit.canvas.StorageWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

/**
 * 查看、编辑与解绑 RTS 储存绑定的浮动窗口。
 *
 * <p>窗口拥有文本焦点、滚动和平台命令；Core 提供有界可见状态，Kit 提供行/控件/滚动条
 * 几何与 chrome。真实 ItemStack、BlockPos、EditBox 和网络副作用不会进入 Core/Kit。</p>
 */
public final class LinkedStoragePanel extends RtsWindowPanel {
    private static final int PANEL_W = StorageWindowLayout.WINDOW_W;
    private static final int PANEL_H = StorageWindowLayout.WINDOW_H;
    private static final int PRIORITY_MIN = -9999;
    private static final int PRIORITY_MAX = 9999;

    private int scroll;
    private WindowTextBox priorityInput;
    private BlockPos editingPriorityPos;
    private int editingPriorityFallback;

    @Override
    public void init(
            BuilderScreen screen,
            ClientRtsController controller) {
        super.init(screen, controller);
        this.priorityInput = null;
        this.editingPriorityPos = null;
    }

    public void openNear(int anchorX, int anchorY) {
        if (!hasUserBoundsPreference()) {
            int x = Mth.clamp(
                    anchorX,
                    4,
                    Math.max(4, this.screen.width - PANEL_W - 4));
            int y = Mth.clamp(
                    anchorY,
                    TOP_H + 2,
                    Math.max(
                            TOP_H + 2,
                            this.screen.getBottomY() - PANEL_H - 4));
            setTransientBounds(x, y, PANEL_W, PANEL_H);
        }
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        List<LinkedStorageEntry> entries =
                this.controller.getLinkedStorageEntries();
        StorageUiState state = snapshot();
        this.scroll = state.scroll;
        StorageWindowLayout.Geometry geometry =
                storageGeometry(state);

        LinkedStoragePanelRenderer.renderHeader(
                graphics,
                this.screen.font(),
                geometry);
        if (state.status != StorageUiStatus.READY) {
            LinkedStoragePanelRenderer.renderStatus(
                    graphics,
                    this.screen.font(),
                    geometry,
                    state);
            return;
        }

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(
                graphics,
                this.screen.font(),
                this.screen);
        for (int rowIndex = 0;
                rowIndex < geometry.rows.size();
                rowIndex++) {
            int platformIndex = state.scroll + rowIndex;
            if (platformIndex < 0 || platformIndex >= entries.size()) {
                break;
            }
            LinkedStorageEntry platformEntry =
                    entries.get(platformIndex);
            StorageUiEntry coreEntry =
                    state.visibleEntry(rowIndex);
            if (platformEntry == null || coreEntry == null) {
                continue;
            }
            boolean priorityEditing =
                    isEditingPriority(platformEntry.pos());
            StorageWindowLayout.RowGeometry rowGeometry =
                    geometry.rows.get(rowIndex);
            LinkedStoragePanelRenderer.renderRow(
                    graphics,
                    this.screen.font(),
                    canvas,
                    rowGeometry,
                    platformEntry,
                    coreEntry,
                    priorityEditing,
                    mouseX,
                    mouseY);
            if (priorityEditing) {
                placePriorityInput(rowGeometry);
                this.priorityInput.renderWidget(
                        graphics,
                        mouseX,
                        mouseY,
                        partialTick);
            }
        }
        StorageWindowChromeRenderer.renderScrollbar(
                canvas,
                geometry);
    }

    @Override
    protected void handleContentClick(
            double mouseX,
            double mouseY,
            int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        List<LinkedStorageEntry> entries =
                this.controller.getLinkedStorageEntries();
        StorageUiState state = snapshot();
        StorageWindowLayout.Geometry geometry =
                storageGeometry(state);
        StorageWindowLayout.Hit hit =
                geometry.hitAt(mouseX, mouseY);
        if (hit == null) {
            if (geometry.rowIndexAt(mouseY) >= 0) {
                commitPriorityEdit();
            }
            return;
        }

        int platformIndex = state.scroll + hit.rowIndex;
        if (platformIndex < 0 || platformIndex >= entries.size()) {
            commitPriorityEdit();
            return;
        }
        LinkedStorageEntry entry = entries.get(platformIndex);
        StorageUiEntry core = state.visibleEntry(hit.rowIndex);
        if (entry == null || core == null) {
            commitPriorityEdit();
            return;
        }
        StorageWindowLayout.RowGeometry rowGeometry =
                geometry.rows.get(hit.rowIndex);
        if (hit.control == StorageWindowLayout.Control.PRIORITY) {
            beginPriorityEdit(
                    entry,
                    rowGeometry.priority);
            return;
        }

        // 离开优先级框时先提交一次草稿，再执行目标动作。
        commitPriorityEdit();
        if (hit.control == StorageWindowLayout.Control.EXTRACT) {
            StorageUiAdapter.dispatch(
                    this.controller,
                    state,
                    StorageUiAction.key(
                            StorageUiAction.Type.TOGGLE_EXTRACT,
                            core.stableKey));
        } else if (hit.control == StorageWindowLayout.Control.UNLINK) {
            StorageUiAdapter.dispatch(
                    this.controller,
                    state,
                    StorageUiAction.key(
                            StorageUiAction.Type.UNLINK,
                            core.stableKey));
        }
    }

    @Override
    protected boolean handleWindowKeyPressed(
            int keyCode,
            int scanCode,
            int modifiers) {
        if (this.priorityInput == null
                || !this.priorityInput.isFocused()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitPriorityEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelPriorityEdit();
            return true;
        }
        return this.priorityInput.keyPressed(
                keyCode,
                scanCode,
                modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(
            char codePoint,
            int modifiers) {
        return this.priorityInput != null
                && this.priorityInput.isFocused()
                && this.priorityInput.charTyped(codePoint, modifiers);
    }

    @Override
    protected void onClose() {
        commitPriorityEdit();
    }

    @Override
    protected boolean handleContentScroll(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        int delta = scrollY > 0.0D ? -1 : 1;
        StorageUiTransition transition =
                StorageUiAdapter.dispatch(
                        this.controller,
                        snapshot(),
                        StorageUiAction.scroll(delta));
        this.scroll = transition.state.scroll;
        return true;
    }

    @Override
    protected Component getTitle() {
        return Component.translatable(
                "screen.rtsbuilding.storage_links.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return PANEL_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return PANEL_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return PANEL_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = 8;
        this.windowY = TOP_H + 6;
    }

    private StorageUiState snapshot() {
        return StorageUiAdapter.snapshot(
                this.controller,
                isOpen(),
                this.scroll,
                visibleRows());
    }

    private StorageWindowLayout.Geometry storageGeometry(
            StorageUiState state) {
        return StorageWindowLayout.geometry(
                contentX(),
                contentY(),
                contentWidth(),
                contentHeight(),
                state.visibleEntries.size(),
                state.totalRows,
                state.scroll);
    }

    private int visibleRows() {
        return StorageWindowLayout.visibleRows(contentHeight());
    }

    private WindowTextBox createPriorityInput() {
        WindowTextBox input = new WindowTextBox(
                this.screen.font(),
                0,
                0,
                StorageWindowLayout.PRIORITY_W,
                StorageWindowLayout.CONTROL_H);
        input.setMaxLength(6);
        input.setInputFilter(
                value -> value != null && value.matches("-?\\d*"));
        return input;
    }

    private void beginPriorityEdit(
            LinkedStorageEntry entry,
            com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect bounds) {
        if (entry == null || entry.pos() == null) {
            return;
        }
        if (!entry.pos().equals(this.editingPriorityPos)) {
            commitPriorityEdit();
        }
        if (this.priorityInput == null) {
            this.priorityInput = createPriorityInput();
        }
        this.editingPriorityPos = entry.pos();
        this.editingPriorityFallback = entry.priority();
        placePriorityInput(bounds);
        this.priorityInput.setValue(
                Integer.toString(entry.priority()));
        this.priorityInput.setFocused(true);
    }

    private void placePriorityInput(
            StorageWindowLayout.RowGeometry geometry) {
        placePriorityInput(geometry.priority);
    }

    private void placePriorityInput(
            com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect bounds) {
        this.priorityInput.setX((int) bounds.getX());
        this.priorityInput.setY((int) bounds.getY());
    }

    private void commitPriorityEdit() {
        if (this.editingPriorityPos == null
                || this.priorityInput == null) {
            return;
        }
        BlockPos position = this.editingPriorityPos;
        int priority = parsePriorityDraft(
                this.priorityInput.getValue(),
                this.editingPriorityFallback);
        LinkedStorageEntry entry = findEntry(position);
        boolean extractOnly = entry != null
                && entry.mode()
                == C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;
        if (entry == null) {
            this.controller.updateLinkedStorageSettings(
                    position,
                    extractOnly,
                    priority);
        } else if (entry.priority() != priority) {
            StorageUiState state = snapshot();
            StorageUiAdapter.dispatch(
                    this.controller,
                    state,
                    StorageUiAction.priority(
                            StorageUiAdapter.key(entry),
                            priority));
        }
        cancelPriorityEdit();
    }

    private void cancelPriorityEdit() {
        this.editingPriorityPos = null;
        this.editingPriorityFallback = 0;
        if (this.priorityInput != null) {
            this.priorityInput.setFocused(false);
        }
    }

    private LinkedStorageEntry findEntry(BlockPos position) {
        if (position == null) {
            return null;
        }
        for (LinkedStorageEntry entry
                : this.controller.getLinkedStorageEntries()) {
            if (entry != null && position.equals(entry.pos())) {
                return entry;
            }
        }
        return null;
    }

    private boolean isEditingPriority(BlockPos position) {
        return position != null
                && position.equals(this.editingPriorityPos)
                && this.priorityInput != null
                && this.priorityInput.isFocused();
    }

    private static int parsePriorityDraft(
            String value,
            int fallback) {
        if (value == null
                || value.isBlank()
                || "-".equals(value)) {
            return Mth.clamp(
                    fallback,
                    PRIORITY_MIN,
                    PRIORITY_MAX);
        }
        try {
            return Mth.clamp(
                    Integer.parseInt(value),
                    PRIORITY_MIN,
                    PRIORITY_MAX);
        } catch (NumberFormatException ignored) {
            return Mth.clamp(
                    fallback,
                    PRIORITY_MIN,
                    PRIORITY_MAX);
        }
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("linked_storage", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
