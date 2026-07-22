package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiAction;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

/**
 * A movable window panel showing active workflows, progress bars, delete buttons,
 * and a submit-pending button for suspended placement jobs.
 *
 * <p>Extends {@link RtsWindowPanel} so it integrates with the floating window layer
 * (dragging, z-order, consistent chrome).  The panel auto-sizes to fit content and
 * hides when no workflows or pending jobs exist.</p>
 */
public final class RtsWorkflowPanel extends RtsWindowPanel {

    private static final int PANEL_W = WorkflowWindowLayout.WINDOW_W;
    private static final int ROW_H = WorkflowWindowLayout.ROW_H;
    private static final int PADDING = WorkflowWindowLayout.PADDING;
    private static final int BTN_W = WorkflowWindowLayout.BUTTON_W;
    private static final int BAR_H = WorkflowWindowLayout.BAR_H;
    private static final int FOOTER_H = 18;
    private static final long SHOW_DELAY_MS = 1_000L;

    private int cachedVisibleRows = -1;
    private final WorkflowPanelVisibilityGate visibilityGate = new WorkflowPanelVisibilityGate(SHOW_DELAY_MS);

    public RtsWorkflowPanel() {
    }

    // ======================================================================
    //  RtsWindowPanel abstract methods
    // ======================================================================

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.workflow.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        // Estimate: 1 row + padding + title bar + border
        return getTitleBarHeight() + 1 + PADDING + ROW_H + PADDING;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen == null) return;
        this.windowX = Math.max(8, this.screen.width - PANEL_W - 8);
        this.windowY = this.screen.topBarBottomY() + 14;
    }

    @Override
    protected boolean canShowWindow() {
        boolean candidateVisible = RtsClientUiStateStore.isShowWorkflowPanelEnabled()
                && hasDisplayableWorkflowContent();
        return this.visibilityGate.canShow(candidateVisible, System.currentTimeMillis());
    }

    @Override
    protected boolean shouldClipContent() {
        return false; // No scrollable content, no clipping needed
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false; // Don't consume scroll events (allow camera zoom through)
    }

    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow() || this.screen == null) return;
        RtsWorkflowStatus hovered = workflowAtProtectionButton(mouseX, mouseY);
        if (hovered == null) return;
        g.renderTooltip(this.screen.font(), Component.translatable(hovered.protectedWorkflow()
                ? "screen.rtsbuilding.workflow.allow_replace"
                : "screen.rtsbuilding.workflow.keep"), mouseX, mouseY);
    }

    // ======================================================================
    //  Render
    // ======================================================================

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.draggable = true;
        this.resizable = false;
        this.closable = false;
        setOpen(true); // Always available; visibility handled by canShowWindow()
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.open || !canShowWindow()) {
            this.mouseHovering = false;
            return;
        }
        recomputeSize();
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Dynamically resizes the window to fit the visible rows.
     * Called before every render frame.
     */
    private void recomputeSize() {
        int visibleRows = WorkflowUiAdapter.snapshot(this.controller).rows.size();
        if (visibleRows == cachedVisibleRows) return;
        cachedVisibleRows = visibleRows;
        int totalH = WorkflowWindowLayout.totalHeight(getTitleBarHeight(), visibleRows);
        if (hasUserBoundsPreference()) {
            setBounds(this.windowX, this.windowY, PANEL_W, totalH);
        } else {
            computeDefaultPosition();
            setTransientBounds(this.windowX, this.windowY, PANEL_W, totalH);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int baseX = contentX();
        int baseY = contentY() + PADDING;

        WorkflowUiState state = WorkflowUiAdapter.snapshot(this.controller);
        int rowY = baseY;
        for (WorkflowUiRow row:state.rows) {
            rowY = renderWorkflowRow(g, baseX, rowY, row, mouseX, mouseY);
        }
    }

    // ======================================================================
    //  Row rendering
    // ======================================================================

    private int renderWorkflowRow(GuiGraphics g, int x, int y,
                                   WorkflowUiRow status,
                                   int mouseX, int mouseY) {
        Font font = this.screen.font();
        boolean suspended = status.suspended;
        boolean protectedWorkflow = status.protectedWorkflow;
        String label = status.label;
        String progress = status.progressText;

        if (suspended) {
            // 挂起工作流：不被覆盖 + 恢复 + 删除。
            int btnArea = BTN_W * 3 + 4;
            int rowW = PANEL_W - PADDING * 2 - btnArea - 2;
            boolean hovered = isInside(mouseX, mouseY, x, y, rowW, ROW_H);

            int bg = protectedWorkflow
                    ? (hovered ? 0xBB3F6E86 : 0xAA315B70)
                    : (hovered ? 0xAA4A3A1A : 0xAA2A2820);
            int border = protectedWorkflow ? 0xFFA8E8FF : 0xFF8A7A4A;
            int labelColor = protectedWorkflow ? 0xFFEAFBFF : 0xFFE7C46A;
            int barFill = protectedWorkflow ? 0xDDA8E8FF : 0xAA8A7A3A;

            RtsClientUiUtil.drawPanelFrame(g, x, y, rowW, ROW_H,
                    bg, border, 0xFF0D0D0A);
            g.drawString(font, RtsClientUiUtil.trimToWidth(font, label, rowW - 8),
                    x + 4, y + 2, labelColor, false);

            // Dimmed progress bar
            int barX = x + 4;
            int barY = y + 12;
            int barW = rowW - 8;
            int fillW = (int)Math.round(status.progress()*barW);
            g.fill(barX, barY, barX + barW, barY + BAR_H, 0xAA303030);
            if (fillW > 0) {
                g.fill(barX, barY, barX + fillW, barY + BAR_H, barFill);
            }
            g.hLine(barX, barX + barW, barY, protectedWorkflow ? 0xFF70B8D0 : 0xFF5A4A2A);
            g.hLine(barX, barX + barW, barY + BAR_H, 0xFF0A0A05);
            g.vLine(barX, barY, barY + BAR_H, protectedWorkflow ? 0xFF70B8D0 : 0xFF5A4A2A);
            g.vLine(barX + barW, barY, barY + BAR_H, 0xFF0A0A05);
            g.drawString(font, RtsClientUiUtil.trimToWidth(font, progress, barW - 4),
                    barX + 2, barY + 1, 0xAAFFFFFF, false);

            int protectBtnX = x + rowW + 2;
            renderProtectionButton(g, font, protectBtnX, y, protectedWorkflow, mouseX, mouseY);

            // 恢复按钮（▶）— 位于保护和删除之间。
            int resumeBtnX = protectBtnX + BTN_W + 2;
            boolean resumeHovered = isInside(mouseX, mouseY, resumeBtnX, y, BTN_W, ROW_H);
            int resumeBg = resumeHovered ? 0xCC3AA156 : 0xCC2C873F;
            RtsClientUiUtil.drawPanelFrame(g, resumeBtnX, y, BTN_W, ROW_H,
                    resumeBg, 0xFF74E88C, 0xFF123A1D);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "▶",
                    resumeBtnX + BTN_W / 2, y + 4, 0xFFFFFF);

            // Cancel button (✖) — second from right
            int cancelBtnX = resumeBtnX + BTN_W + 2;
            boolean cancelHovered = isInside(mouseX, mouseY, cancelBtnX, y, BTN_W, ROW_H);
            int cancelBg = cancelHovered ? 0xCCB04A4A : 0xAA4A2A2A;
            RtsClientUiUtil.drawPanelFrame(g, cancelBtnX, y, BTN_W, ROW_H,
                    cancelBg, 0xFFC07070, 0xFF1A0D0D);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "✖",
                    cancelBtnX + BTN_W / 2, y + 4, 0xFFFFFF);
        } else {
            // 活动工作流：不被覆盖 + 暂停/恢复 + 删除。
            int btnArea = BTN_W * 3 + 4;
            int rowW = PANEL_W - PADDING * 2 - btnArea - 2;
            boolean hovered = isInside(mouseX, mouseY, x, y, rowW, ROW_H);

            int bg = protectedWorkflow
                    ? (hovered ? 0xBB3F6E86 : 0xAA315B70)
                    : (hovered ? 0xAA2A3A4A : 0xAA1A222C);
            int border = protectedWorkflow ? 0xFFA8E8FF : 0xFF5E738A;
            int labelColor = protectedWorkflow ? 0xFFEAFBFF : 0xEAF2FF;
            int barFill = protectedWorkflow ? 0xDDA8E8FF : 0xFF88BEF4;

            RtsClientUiUtil.drawPanelFrame(g, x, y, rowW, ROW_H,
                    bg, border, 0xFF0D1117);
            g.drawString(font, RtsClientUiUtil.trimToWidth(font, label, rowW - 8),
                    x + 4, y + 2, labelColor, false);

            // Progress bar
            int barX = x + 4;
            int barY = y + 12;
            int barW = rowW - 8;
            int fillW = (int)Math.round(status.progress()*barW);
            g.fill(barX, barY, barX + barW, barY + BAR_H, 0xAA202832);
            if (fillW > 0) {
                g.fill(barX, barY, barX + fillW, barY + BAR_H, barFill);
            }
            g.hLine(barX, barX + barW, barY, protectedWorkflow ? 0xFF70B8D0 : 0xFF405064);
            g.hLine(barX, barX + barW, barY + BAR_H, 0xFF0A0D12);
            g.vLine(barX, barY, barY + BAR_H, protectedWorkflow ? 0xFF70B8D0 : 0xFF405064);
            g.vLine(barX + barW, barY, barY + BAR_H, 0xFF0A0D12);

            // Progress text overlay
            g.drawString(font, RtsClientUiUtil.trimToWidth(font, progress, barW - 4),
                    barX + 2, barY + 1, 0xCCFFFFFF, false);

            boolean isPaused = status.paused;

            int protectBtnX = x + rowW + 2;
            renderProtectionButton(g, font, protectBtnX, y, protectedWorkflow, mouseX, mouseY);

            // 暂停/恢复按钮（⏸/▶）— 位于保护和删除之间。
            int pauseBtnX = protectBtnX + BTN_W + 2;
            boolean pauseHovered = isInside(mouseX, mouseY, pauseBtnX, y, BTN_W, ROW_H);
            int pauseBg;
            int pauseBorder;
            if (isPaused) {
                // Resume — green
                pauseBg = pauseHovered ? 0xCC3AA156 : 0xCC2C873F;
                pauseBorder = 0xFF74E88C;
            } else {
                // Pause — amber
                pauseBg = pauseHovered ? 0xCCA07A2A : 0xCC705A1A;
                pauseBorder = 0xFFE7C46A;
            }
            RtsClientUiUtil.drawPanelFrame(g, pauseBtnX, y, BTN_W, ROW_H,
                    pauseBg, pauseBorder, 0xFF1A2A1A);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, font, isPaused ? "▶" : "⏸",
                    pauseBtnX + BTN_W / 2, y + 4, 0xFFFFFF);

            // Delete button (✖) — second from right
            int deleteBtnX = pauseBtnX + BTN_W + 2;
            boolean deleteHovered = isInside(mouseX, mouseY, deleteBtnX, y, BTN_W, ROW_H);
            int deleteBg = deleteHovered ? 0xCCB04A4A : 0xAA4A2A2A;
            RtsClientUiUtil.drawPanelFrame(g, deleteBtnX, y, BTN_W, ROW_H,
                    deleteBg, 0xFFC07070, 0xFF1A0D0D);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "✖",
                    deleteBtnX + BTN_W / 2, y + 4, 0xFFFFFF);
        }

        return y + ROW_H;
    }

    private void renderProtectionButton(GuiGraphics g, Font font, int x, int y,
                                        boolean protectedWorkflow, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, x, y, BTN_W, ROW_H);
        int bg;
        int border;
        int textColor;
        String label;
        if (protectedWorkflow) {
            bg = hovered ? 0xD36FC7E8 : 0xCC4DAFD8;
            border = 0xFFA8E8FF;
            textColor = 0xFFFFFF;
            label = "◆";
        } else {
            bg = hovered ? 0xAA3A4A5A : 0xAA263442;
            border = 0xFF5E738A;
            textColor = 0xDDEBFF;
            label = "◇";
        }
        RtsClientUiUtil.drawPanelFrame(g, x, y, BTN_W, ROW_H, bg, border, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, label,
                x + BTN_W / 2, y + 4, textColor);
    }

    // ======================================================================
    //  Click handling
    // ======================================================================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return; // Left click only

        WorkflowUiState state = WorkflowUiAdapter.snapshot(this.controller);
        int baseX = contentX();
        int rowY = contentY() + PADDING;
        for (WorkflowUiRow row : state.rows) {
            if (isInside(mouseX, mouseY, WorkflowWindowLayout.protectX(baseX), rowY, BTN_W, ROW_H)) {
                WorkflowUiAdapter.dispatch(this.controller, state,
                        WorkflowUiAction.of(WorkflowUiAction.Type.TOGGLE_PROTECTED, row.entryId));
                return;
            }
            if (isInside(mouseX, mouseY, WorkflowWindowLayout.actionX(baseX), rowY, BTN_W, ROW_H)) {
                WorkflowUiAction.Type action = row.suspended
                        ? WorkflowUiAction.Type.RESUME_SUSPENDED
                        : WorkflowUiAction.Type.TOGGLE_PAUSED;
                WorkflowUiAdapter.dispatch(this.controller, state, WorkflowUiAction.of(action, row.entryId));
                return;
            }
            if (isInside(mouseX, mouseY, WorkflowWindowLayout.deleteX(baseX), rowY, BTN_W, ROW_H)) {
                WorkflowUiAdapter.dispatch(this.controller, state,
                        WorkflowUiAction.of(WorkflowUiAction.Type.DELETE, row.entryId));
                return;
            }
            rowY += ROW_H;
        }
    }

    // ======================================================================
    //  Helpers
    // ======================================================================

    private int getActiveCount() {
        return this.controller.getWorkflowActiveCount();
    }

    private boolean hasDisplayableWorkflowContent() {
        return WorkflowUiAdapter.snapshot(this.controller).hasContent();
    }

    private RtsWorkflowStatus workflowAtProtectionButton(int mouseX, int mouseY) {
        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        int count = Math.min(getActiveCount(), workflows.length);
        int baseX = contentX();
        int rowY = contentY() + PADDING;

        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status == null || status.type() == null) continue;
            int protectBtnX = WorkflowWindowLayout.protectX(baseX);
            if (isInside(mouseX, mouseY, protectBtnX, rowY, BTN_W, ROW_H)) {
                return status;
            }
            rowY += ROW_H;
        }
        return null;
    }

    private static boolean isInside(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("workflow", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
