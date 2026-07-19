package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsDeleteWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPauseWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanBlueprintResumePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanResumePlacementPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetWorkflowProtectedPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowProgressProcessor;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;

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

    private static final int PANEL_W = 220;
    private static final int ROW_H = 22;
    private static final int PADDING = 6;
    private static final int BTN_W = 16;
    private static final int BAR_H = 6;
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
    public void renderOverlays(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow() || this.screen == null) return;
        RtsWorkflowStatus hovered = workflowAtProtectionButton(mouseX, mouseY);
        if (hovered == null) return;
        g .setTooltipForNextFrame(this.screen.font(), Component.translatable(hovered.protectedWorkflow()
                        ? "screen.rtsbuilding.workflow.allow_replace"
                        : "screen.rtsbuilding.workflow.keep"),
                this.screen.toNativeGuiCoordinate(mouseX), this.screen.toNativeGuiCoordinate(mouseY));
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
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
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
        int visibleRows = getActiveCount() + getSuspendedCount();
        int totalRows = visibleRows;
        if (totalRows == cachedVisibleRows) return;
        cachedVisibleRows = totalRows;
        int contentH = PADDING + visibleRows * ROW_H + PADDING;
        int totalH = getTitleBarHeight() + 1 + contentH;
        if (hasUserBoundsPreference()) {
            setBounds(this.windowX, this.windowY, PANEL_W, totalH);
        } else {
            computeDefaultPosition();
            setTransientBounds(this.windowX, this.windowY, PANEL_W, totalH);
        }
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int baseX = contentX();
        int baseY = contentY() + PADDING;

        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        // Only iterate slots that the server considers valid (active + suspended), so stale
        // entries from recently completed workflows are not rendered.
        int count = Math.min(getActiveCount(), workflows.length);

        int rowY = baseY;

        // Render all occupied workflow entries (active + suspended)
        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status == null || !status.isActive()) continue;
            rowY = renderWorkflowRow(g, baseX, rowY, status, mouseX, mouseY);
        }
    }

    // ======================================================================
    //  Row rendering
    // ======================================================================

    private int renderWorkflowRow(GuiGraphicsExtractor g, int x, int y,
                                   RtsWorkflowStatus status,
                                   int mouseX, int mouseY) {
        Font font = this.screen.font();
        boolean suspended = status.suspended();
        boolean protectedWorkflow = status.protectedWorkflow();
        String label = RtsWorkflowProgressProcessor.formatLabel(status);
        String progress = RtsWorkflowProgressProcessor.formatProgressText(status);

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
            g .text(font, RtsClientUiUtil.trimToWidth(font, label, rowW - 8),
                    x + 4, y + 2, labelColor, false);

            // Dimmed progress bar
            int barX = x + 4;
            int barY = y + 12;
            int barW = rowW - 8;
            int fillW = RtsWorkflowProgressProcessor.computeFillWidth(status, barW);
            g.fill(barX, barY, barX + barW, barY + BAR_H, 0xAA303030);
            if (fillW > 0) {
                g.fill(barX, barY, barX + fillW, barY + BAR_H, barFill);
            }
            g.horizontalLine(barX, barX + barW, barY, protectedWorkflow ? 0xFF70B8D0 : 0xFF5A4A2A);
            g.horizontalLine(barX, barX + barW, barY + BAR_H, 0xFF0A0A05);
            g.verticalLine(barX, barY, barY + BAR_H, protectedWorkflow ? 0xFF70B8D0 : 0xFF5A4A2A);
            g.verticalLine(barX + barW, barY, barY + BAR_H, 0xFF0A0A05);
            g .text(font, RtsClientUiUtil.trimToWidth(font, progress, barW - 4),
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
            g .text(font, RtsClientUiUtil.trimToWidth(font, label, rowW - 8),
                    x + 4, y + 2, labelColor, false);

            // Progress bar
            int barX = x + 4;
            int barY = y + 12;
            int barW = rowW - 8;
            int fillW = RtsWorkflowProgressProcessor.computeFillWidth(status, barW);
            g.fill(barX, barY, barX + barW, barY + BAR_H, 0xAA202832);
            if (fillW > 0) {
                g.fill(barX, barY, barX + fillW, barY + BAR_H, barFill);
            }
            g.horizontalLine(barX, barX + barW, barY, protectedWorkflow ? 0xFF70B8D0 : 0xFF405064);
            g.horizontalLine(barX, barX + barW, barY + BAR_H, 0xFF0A0D12);
            g.verticalLine(barX, barY, barY + BAR_H, protectedWorkflow ? 0xFF70B8D0 : 0xFF405064);
            g.verticalLine(barX + barW, barY, barY + BAR_H, 0xFF0A0D12);

            // Progress text overlay
            g .text(font, RtsClientUiUtil.trimToWidth(font, progress, barW - 4),
                    barX + 2, barY + 1, 0xCCFFFFFF, false);

            boolean isPaused = status.paused();

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

    private void renderProtectionButton(GuiGraphicsExtractor g, Font font, int x, int y,
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

        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        // Only iterate slots within the active count to avoid responding to stale entries.
        int count = Math.min(getActiveCount(), workflows.length);

        int baseX = contentX();
        int rowY = contentY() + PADDING;

        // Check buttons for each workflow row
        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status == null || status.type() == null) continue;

            if (status.suspended()) {
                // 挂起工作流：不被覆盖 + ▶（恢复）+ ✖（删除）。
                int btnArea = BTN_W * 3 + 4;
                int rowW = PANEL_W - PADDING * 2 - btnArea - 2;
                int protectBtnX = baseX + rowW + 2;
                int resumeBtnX = protectBtnX + BTN_W + 2;
                int cancelBtnX = resumeBtnX + BTN_W + 2;
                if (isInside(mouseX, mouseY, protectBtnX, rowY, BTN_W, ROW_H)) {
                    RtsClientNetworkBridge.send(new C2SRtsSetWorkflowProtectedPayload(
                            status.entryId(), !status.protectedWorkflow()));
                    return;
                }
                if (isInside(mouseX, mouseY, resumeBtnX, rowY, BTN_W, ROW_H)) {
                    // ▶ Resume
                    if (status.type() == RtsWorkflowType.BLUEPRINT_BUILD) {
                        // 蓝图：扫描剩余材料需求，弹出材料清单面板
                        RtsClientNetworkBridge.send(new C2SRtsScanBlueprintResumePayload(status.entryId()));
                    } else {
                        // 范围放置：先扫描，再打开重启面板
                        RtsClientNetworkBridge.send(new C2SRtsScanResumePlacementPayload(status.entryId()));
                    }
                    return;
                }
                if (isInside(mouseX, mouseY, cancelBtnX, rowY, BTN_W, ROW_H)) {
                    // ✖ Cancel (delete) this workflow — 用 entryId 而非位置索引
                    RtsClientNetworkBridge.send(new C2SRtsDeleteWorkflowPayload(status.entryId()));
                    return;
                }
            } else {
                // 活动工作流：不被覆盖 + ⏸/▶（暂停/恢复）+ ✖（删除）。
                int btnArea = BTN_W * 3 + 4;
                int rowW = PANEL_W - PADDING * 2 - btnArea - 2;
                int protectBtnX = baseX + rowW + 2;
                int pauseBtnX = protectBtnX + BTN_W + 2;
                int deleteBtnX = pauseBtnX + BTN_W + 2;
                if (isInside(mouseX, mouseY, protectBtnX, rowY, BTN_W, ROW_H)) {
                    RtsClientNetworkBridge.send(new C2SRtsSetWorkflowProtectedPayload(
                            status.entryId(), !status.protectedWorkflow()));
                    return;
                }
                if (isInside(mouseX, mouseY, pauseBtnX, rowY, BTN_W, ROW_H)) {
                    RtsClientNetworkBridge.send(new C2SRtsPauseWorkflowPayload(status.entryId()));
                    return;
                }
                if (isInside(mouseX, mouseY, deleteBtnX, rowY, BTN_W, ROW_H)) {
                    RtsClientNetworkBridge.send(new C2SRtsDeleteWorkflowPayload(status.entryId()));
                    return;
                }
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

    private int getSuspendedCount() {
        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        int limit = Math.min(getActiveCount(), workflows.length);
        int count = 0;
        for (int i = 0; i < limit; i++) {
            RtsWorkflowStatus s = workflows[i];
            if (s != null && s.type() != null && s.suspended()) count++;
        }
        return count;
    }

    private boolean hasPending() {
        return this.controller.hasPendingJobs();
    }

    private boolean hasDisplayableWorkflowContent() {
        return getActiveCount() > 0 || getSuspendedCount() > 0 || hasPending();
    }

    private RtsWorkflowStatus workflowAtProtectionButton(int mouseX, int mouseY) {
        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        int count = Math.min(getActiveCount(), workflows.length);
        int baseX = contentX();
        int rowY = contentY() + PADDING;

        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status == null || status.type() == null) continue;
            int btnArea = BTN_W * 3 + 4;
            int rowW = PANEL_W - PADDING * 2 - btnArea - 2;
            int protectBtnX = baseX + rowW + 2;
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
