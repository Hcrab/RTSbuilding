package com.rtsbuilding.rtsbuilding.client.screen.storage;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiAction;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiTransition;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

/**
 * Movable window for inspecting and unlinking RTS storage bindings.
 *
 * <p>This panel is intentionally a thin client-side management view. It owns
 * only the window layout, row hover/click handling, and scroll state. The
 * authoritative list of linked storage blocks comes from the latest server
 * storage-page payload through {@link ClientRtsController}; the server remains
 * responsible for validating and applying an unlink request. Keeping that
 * boundary explicit prevents this UI from becoming a second storage resolver or
 * inventing client-only binding state.
 *
 * <p>The player-facing goal is issue #41: when storage binding looks confusing,
 * the player should be able to open a small Windows-style RTS panel, see each
 * bound block by icon/name/coordinate/mode, and remove a bad binding directly.
 * This deliberately replaces the earlier long-tooltip idea with a real,
 * reviewable window using the same {@link RtsWindowPanel} infrastructure as
 * Quick Build and Ultimine.
 */
public final class LinkedStoragePanel extends RtsWindowPanel {
    private static final int PANEL_W = StorageWindowLayout.WINDOW_W;
    private static final int PANEL_H = StorageWindowLayout.WINDOW_H;
    private static final int ROW_H = StorageWindowLayout.ROW_H;
    private static final int HEADER_H = StorageWindowLayout.HEADER_H;
    private static final int PRIORITY_W = StorageWindowLayout.PRIORITY_W;
    private static final int EXTRACT_W = StorageWindowLayout.EXTRACT_W;
    private static final int UNLINK_W = StorageWindowLayout.UNLINK_W;
    private static final int UNLINK_H = StorageWindowLayout.UNLINK_H;
    private static final int CONTROL_H = StorageWindowLayout.CONTROL_H;
    private static final int SCROLLBAR_W = StorageWindowLayout.SCROLLBAR_W;
    private static final int SCROLLBAR_GAP = StorageWindowLayout.SCROLLBAR_GAP;
    private static final int PRIORITY_MIN = -9999;
    private static final int PRIORITY_MAX = 9999;

    private int scroll;
    private WindowTextBox priorityInput;
    private BlockPos editingPriorityPos;
    private int editingPriorityFallback;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.priorityInput = null;
        this.editingPriorityPos = null;
    }

    public void openNear(int anchorX, int anchorY) {
        if (!hasUserBoundsPreference()) {
            int x = Mth.clamp(anchorX, 4, Math.max(4, this.screen.width - PANEL_W - 4));
            int y = Mth.clamp(anchorY, TOP_H + 2, Math.max(TOP_H + 2, this.screen.getBottomY() - PANEL_H - 4));
            setTransientBounds(x, y, PANEL_W, PANEL_H);
        }
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<LinkedStorageEntry> entries = this.controller.getLinkedStorageEntries();
        StorageUiState state=StorageUiAdapter.snapshot(this.controller,isOpen(),this.scroll,visibleRows());
        this.scroll=state.scroll;

        int x = contentX() + 8;
        int y = contentY() + 8;
        int w = contentWidth() - 16;
        int visibleRows = visibleRows();
        boolean hasScrollbar = entries.size() > visibleRows;
        int rowW = w - (hasScrollbar ? SCROLLBAR_W + SCROLLBAR_GAP : 0);
        g.drawString(this.screen.font(), Component.translatable("screen.rtsbuilding.storage_links.header"),
                x, y, 0xFFD8E3EE, false);

        if (state.status == StorageUiStatus.EMPTY || state.status == StorageUiStatus.LOADING
                || state.status == StorageUiStatus.FAILED) {
            int emptyY = y + HEADER_H + 12;
            String key=state.status==StorageUiStatus.LOADING?"screen.rtsbuilding.storage_links.loading"
                    :state.status==StorageUiStatus.FAILED?"screen.rtsbuilding.storage_links.failed"
                    :"screen.rtsbuilding.storage_links.empty";
            g.drawString(this.screen.font(), Component.translatable(key),
                    x, emptyY, 0xFFFFD480, false);
            g.drawString(this.screen.font(),
                    RtsClientUiUtil.trimToWidth(this.screen.font(),
                            Component.translatable("screen.rtsbuilding.storage_links.empty_detail").getString(), w),
                    x, emptyY + 12, 0xFFBFD0E0, false);
            return;
        }

        g.drawString(this.screen.font(), Component.translatable("screen.rtsbuilding.storage_links.priority"),
                priorityBoxX(x, rowW), y + 12, 0xFF9FB3C8, false);
        g.drawString(this.screen.font(), Component.translatable("screen.rtsbuilding.storage_links.mode_extract_header"),
                extractButtonX(x, rowW), y + 12, 0xFF9FB3C8, false);

        int firstY = y + HEADER_H;
        int end = Math.min(entries.size(), this.scroll + visibleRows);
        for (int i = this.scroll; i < end; i++) {
            int rowY = firstY + (i - this.scroll) * ROW_H;
            renderRow(g, mouseX, mouseY, entries.get(i),
                    state.visibleEntry(i-this.scroll), x, rowY, rowW);
        }
        renderScrollbar(g, entries.size(), x + rowW + SCROLLBAR_GAP, firstY, visibleRows * ROW_H);
    }

    private void renderRow(GuiGraphics g, int mouseX, int mouseY, LinkedStorageEntry entry,
            StorageUiEntry core,
            int x, int y, int w) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_H - 2;
        int fill = hovered ? 0xCC243244 : 0xAA1A222D;
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, ROW_H - 2, fill, 0xFF566D83, 0xFF0D1117);

        ItemStack preview = entry.preview();
        if (preview != null && !preview.isEmpty()) {
            g.renderItem(preview, x + 5, y + 5);
        } else {
            g.fill(x + 5, y + 5, x + 21, y + 21, 0xAA101820);
            g.hLine(x + 5, x + 21, y + 5, 0xFF566D83);
            g.vLine(x + 5, y + 5, y + 21, 0xFF566D83);
        }

        int priorityX = priorityBoxX(x, w);
        int priorityY = controlY(y);
        int extractX = extractButtonX(x, w);
        int unlinkX = unlinkButtonX(x, w);
        String name = RtsClientUiUtil.trimToWidth(this.screen.font(), core.label,
                Math.max(30, priorityX - (x + 26) - 6));
        g.drawString(this.screen.font(), name, x + 26, y + 4, 0xFFEAF2FF, false);
        g.drawString(this.screen.font(), core.position, x + 26, y + 15, 0xFF9FB3C8, false);

        renderPriorityControl(g, mouseX, mouseY, entry, priorityX, priorityY);
        renderExtractToggle(g, mouseX, mouseY, core.extractOnly, extractX, priorityY);

        int buttonY = controlY(y);
        boolean buttonHover = inside(mouseX, mouseY, unlinkX, buttonY, UNLINK_W, UNLINK_H);
        RtsClientUiUtil.drawPanelFrame(g, unlinkX, buttonY, UNLINK_W, UNLINK_H,
                buttonHover ? 0xCC5A2B34 : 0xAA2A2228,
                buttonHover ? 0xFFE28A96 : 0xFF7B5660,
                0xFF180B0E);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, this.screen.font(),
                Component.translatable("screen.rtsbuilding.storage_links.unlink"),
                unlinkX + UNLINK_W / 2, buttonY + 4, 0xFFFFF0F0);
    }

    private void renderPriorityControl(GuiGraphics g, int mouseX, int mouseY,
            LinkedStorageEntry entry, int x, int y) {
        if (isEditingPriority(entry.pos())) {
            this.priorityInput.setX(x);
            this.priorityInput.setY(y);
            this.priorityInput.renderWidget(g, mouseX, mouseY, 0.0F);
            return;
        }
        boolean hovered = inside(mouseX, mouseY, x, y, PRIORITY_W, CONTROL_H);
        RtsClientUiUtil.drawPanelFrame(g, x, y, PRIORITY_W, CONTROL_H,
                hovered ? 0xCC26394A : 0xAA101820,
                hovered ? 0xFF8EA9C4 : 0xFF566D83,
                0xFF0D1117);
        String text = RtsClientUiUtil.trimToWidth(this.screen.font(), Integer.toString(entry.priority()), PRIORITY_W - 6);
        g.drawString(this.screen.font(), text, x + 4, y + 4, 0xFFEAF2FF, false);
    }

    private void renderExtractToggle(GuiGraphics g, int mouseX, int mouseY,
            boolean extractOnly, int x, int y) {
        boolean hovered = inside(mouseX, mouseY, x, y, EXTRACT_W, CONTROL_H);
        int fill = extractOnly
                ? (hovered ? 0xFF5A2D50 : 0xFF4A253F)
                : (hovered ? 0xCC26394A : 0xAA1A222D);
        int light = extractOnly
                ? (hovered ? 0xFFFF9DDE : 0xFFFF74C9)
                : (hovered ? 0xFF8EA9C4 : 0xFF566D83);
        RtsClientUiUtil.drawPanelFrame(g, x, y, EXTRACT_W, CONTROL_H, fill, light, 0xFF0D1117);
        String labelKey = extractOnly
                ? "screen.rtsbuilding.storage_links.mode_yes"
                : "screen.rtsbuilding.storage_links.mode_no";
        RtsClientUiUtil.drawCenteredStringNoShadow(g, this.screen.font(),
                RtsClientUiUtil.trimToWidth(this.screen.font(),
                        Component.translatable(labelKey).getString(),
                        EXTRACT_W - 6),
                x + EXTRACT_W / 2, y + 4, extractOnly ? 0xFFFFECFA : 0xFFCDE7D2);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        List<LinkedStorageEntry> entries = this.controller.getLinkedStorageEntries();
        if (entries.isEmpty()) {
            commitPriorityEdit();
            return;
        }
        int x = contentX() + 8;
        int w = contentWidth() - 16;
        int rowW = w - (maxScroll(entries) > 0 ? SCROLLBAR_W + SCROLLBAR_GAP : 0);
        int firstY = contentY() + 8 + HEADER_H;
        int row = (int) ((mouseY - firstY) / ROW_H);
        if (row < 0 || row >= visibleRows()) {
            return;
        }
        int index = this.scroll + row;
        if (index < 0 || index >= entries.size()) {
            commitPriorityEdit();
            return;
        }
        int rowY = firstY + row * ROW_H;
        LinkedStorageEntry entry = entries.get(index);
        StorageUiState state=StorageUiAdapter.snapshot(this.controller,isOpen(),this.scroll,visibleRows());
        StorageUiEntry core=state.visibleEntry(row);
        if(core==null)return;
        int controlY = controlY(rowY);
        if (inside(mouseX, mouseY, priorityBoxX(x, rowW), controlY, PRIORITY_W, CONTROL_H)) {
            beginPriorityEdit(entry, priorityBoxX(x, rowW), controlY);
            return;
        }
        int priorityForUpdate = isEditingPriority(entry.pos())
                ? parsePriorityDraft(this.priorityInput.getValue(), this.editingPriorityFallback)
                : entry.priority();
        commitPriorityEdit();
        if (inside(mouseX, mouseY, extractButtonX(x, rowW), controlY, EXTRACT_W, CONTROL_H)) {
            if(priorityForUpdate!=entry.priority())StorageUiAdapter.dispatch(this.controller,state,
                    StorageUiAction.priority(core.stableKey,priorityForUpdate));
            StorageUiAdapter.dispatch(this.controller,state,StorageUiAction.key(
                    StorageUiAction.Type.TOGGLE_EXTRACT,core.stableKey));
            return;
        }
        if (inside(mouseX, mouseY, unlinkButtonX(x, rowW), controlY, UNLINK_W, UNLINK_H)) {
            StorageUiAdapter.dispatch(this.controller,state,
                    StorageUiAction.key(StorageUiAction.Type.UNLINK,core.stableKey));
        }
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.priorityInput == null || !this.priorityInput.isFocused()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitPriorityEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelPriorityEdit();
            return true;
        }
        return this.priorityInput.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return this.priorityInput != null
                && this.priorityInput.isFocused()
                && this.priorityInput.charTyped(codePoint, modifiers);
    }

    @Override
    protected void onClose() {
        commitPriorityEdit();
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0.0D ? -1 : 1;
        StorageUiState state=StorageUiAdapter.snapshot(this.controller,isOpen(),this.scroll,visibleRows());
        StorageUiTransition transition=StorageUiAdapter.dispatch(this.controller,state,StorageUiAction.scroll(delta));
        this.scroll=transition.state.scroll;
        return true;
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.storage_links.title");
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

    private int visibleRows() {
        return StorageWindowLayout.visibleRows(contentHeight());
    }

    private int maxScroll(List<LinkedStorageEntry> entries) {
        return Math.max(0, entries.size() - visibleRows());
    }

    private WindowTextBox createPriorityInput() {
        WindowTextBox input = new WindowTextBox(this.screen.font(), 0, 0, PRIORITY_W, CONTROL_H);
        input.setMaxLength(6);
        input.setInputFilter(value -> value != null && value.matches("-?\\d*"));
        return input;
    }

    private void beginPriorityEdit(LinkedStorageEntry entry, int x, int y) {
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
        this.priorityInput.setX(x);
        this.priorityInput.setY(y);
        this.priorityInput.setValue(Integer.toString(entry.priority()));
        this.priorityInput.setFocused(true);
    }

    private void commitPriorityEdit() {
        if (this.editingPriorityPos == null || this.priorityInput == null) {
            return;
        }
        BlockPos pos = this.editingPriorityPos;
        int priority = parsePriorityDraft(this.priorityInput.getValue(), this.editingPriorityFallback);
        LinkedStorageEntry entry = findEntry(pos);
        boolean extractOnly = entry != null
                && entry.mode() == C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;
        if (entry == null) {
            this.controller.updateLinkedStorageSettings(pos, extractOnly, priority);
        } else if (entry.priority() != priority) {
            StorageUiState state=StorageUiAdapter.snapshot(this.controller,isOpen(),this.scroll,visibleRows());
            StorageUiAdapter.dispatch(this.controller,state,
                    StorageUiAction.priority(StorageUiAdapter.key(entry),priority));
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

    private LinkedStorageEntry findEntry(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        for (LinkedStorageEntry entry : this.controller.getLinkedStorageEntries()) {
            if (entry != null && pos.equals(entry.pos())) {
                return entry;
            }
        }
        return null;
    }

    private boolean isEditingPriority(BlockPos pos) {
        return pos != null && pos.equals(this.editingPriorityPos)
                && this.priorityInput != null && this.priorityInput.isFocused();
    }

    private static int parsePriorityDraft(String value, int fallback) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return Mth.clamp(fallback, PRIORITY_MIN, PRIORITY_MAX);
        }
        try {
            return Mth.clamp(Integer.parseInt(value), PRIORITY_MIN, PRIORITY_MAX);
        } catch (NumberFormatException ignored) {
            return Mth.clamp(fallback, PRIORITY_MIN, PRIORITY_MAX);
        }
    }

    private static int priorityBoxX(int rowX, int rowW) {
        return StorageWindowLayout.priorityX(rowX,rowW);
    }

    private static int extractButtonX(int rowX, int rowW) {
        return StorageWindowLayout.extractX(rowX,rowW);
    }

    private static int unlinkButtonX(int rowX, int rowW) {
        return StorageWindowLayout.unlinkX(rowX,rowW);
    }

    private static int controlY(int rowY) {
        return StorageWindowLayout.controlY(rowY);
    }

    private static String formatPos(LinkedStorageEntry entry) {
        if (entry == null || !entry.worldAvailable()) {
            return Component.translatable("screen.rtsbuilding.storage_links.position_na").getString();
        }
        BlockPos pos = entry.pos();
        if (pos == null) {
            return "? ? ?";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void renderScrollbar(GuiGraphics g, int totalRows, int x, int y, int h) {
        int maxScroll = maxScroll(this.controller.getLinkedStorageEntries());
        if (maxScroll <= 0) {
            return;
        }
        g.fill(x, y, x + SCROLLBAR_W, y + h, 0xAA101820);
        g.fill(x + 1, y + 1, x + SCROLLBAR_W - 1, y + h - 1, 0x88303B47);
        int thumbH = Math.max(14, h * visibleRows() / Math.max(1, totalRows));
        int thumbY = y + (h - thumbH) * this.scroll / maxScroll;
        g.fill(x + 1, thumbY, x + SCROLLBAR_W - 1, thumbY + thumbH, 0xFF8EA9C4);
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("linked_storage", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
