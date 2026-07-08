package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowProgressProcessor;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.TOP_H;

/**
 * 显示服务端工作流进度的浮动窗口。
 *
 * <p>这个面板只负责窗口、进度行和暂停/继续/删除按钮；工作流状态仍由
 * {@link ClientRtsController} 和服务端同步链路提供。这里的布局跟 1.21.1
 * 的稳定基线保持一致：自动贴合行数、不吞滚轮、默认显示在顶部右侧。</p>
 */
public final class RtsWorkflowPanel extends RtsWindowPanel {
    private static final int TITLE_BAR_H = 20;
    private static final int PANEL_W = 220;
    private static final int ROW_H = 22;
    private static final int PADDING = 6;
    private static final int BTN_W = 16;
    private static final int BAR_H = 6;
    private static final long SHOW_DELAY_MS = 1_000L;

    private int cachedVisibleRows = -1;
    private final WorkflowPanelVisibilityGate visibilityGate = new WorkflowPanelVisibilityGate(SHOW_DELAY_MS);

    public RtsWorkflowPanel() {
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.draggable = true;
        this.resizable = false;
        this.closable = false;
        setOpen(true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.open || !canShowWindow()) {
            return;
        }
        recomputeSize();
        super.render(g, mouseX, mouseY, partialTick);
    }

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
        return TITLE_BAR_H + 1 + PADDING + ROW_H + PADDING;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen == null) {
            return;
        }
        this.windowX = Math.max(8, this.screen.width - PANEL_W - 8);
        this.windowY = TOP_H + 8;
    }

    @Override
    protected boolean canShowWindow() {
        boolean candidateVisible = super.canShowWindow()
                && RtsClientUiStateStore.isShowWorkflowPanelEnabled()
                && hasDisplayableWorkflowContent();
        return this.visibilityGate.canShow(candidateVisible, System.currentTimeMillis());
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    private void recomputeSize() {
        int visibleRows = getVisibleWorkflowRows();
        if (visibleRows == this.cachedVisibleRows) {
            return;
        }
        this.cachedVisibleRows = visibleRows;
        int contentH = PADDING + visibleRows * ROW_H + PADDING;
        setBounds(this.windowX, this.windowY, PANEL_W, TITLE_BAR_H + 1 + contentH);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int baseX = contentX();
        int rowY = contentY() + PADDING;

        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        int count = Math.min(getActiveCount(), workflows.length);
        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status == null || !status.isActive()) {
                continue;
            }
            rowY = renderWorkflowRow(g, baseX, rowY, status, mouseX, mouseY);
        }
    }

    private int renderWorkflowRow(GuiGraphics g, int x, int y, RtsWorkflowStatus status, int mouseX, int mouseY) {
        Font font = this.screen.font();
        String label = RtsWorkflowProgressProcessor.formatLabel(status);
        String progress = RtsWorkflowProgressProcessor.formatProgressText(status);

        if (status.suspended()) {
            renderSuspendedRow(g, font, x, y, status, label, progress, mouseX, mouseY);
        } else {
            renderActiveRow(g, font, x, y, status, label, progress, mouseX, mouseY);
        }
        return y + ROW_H;
    }

    private void renderSuspendedRow(GuiGraphics g, Font font, int x, int y, RtsWorkflowStatus status,
                                    String label, String progress, int mouseX, int mouseY) {
        int buttonArea = BTN_W * 2 + 2;
        int rowW = PANEL_W - PADDING * 2 - buttonArea - 2;
        boolean hovered = isInside(mouseX, mouseY, x, y, rowW, ROW_H);

        RtsClientUiUtil.drawPanelFrame(g, x, y, rowW, ROW_H,
                hovered ? 0xAA4A3A1A : 0xAA2A2820,
                0xFF8A7A4A, 0xFF0D0D0A);
        g.drawString(font, label, x + 4, y + 2, 0xFFE7C46A, false);

        int barX = x + 4;
        int barY = y + 12;
        int barW = rowW - 8;
        drawProgressBar(g, font, status, progress, barX, barY, barW, 0xAA303030, 0xAA8A7A3A,
                0xFF5A4A2A, 0xFF0A0A05, 0xAAFFFFFF);

        int resumeBtnX = x + rowW + 2;
        drawActionButton(g, font, resumeBtnX, y, "▶", mouseX, mouseY,
                0xCC2C873F, 0xCC3AA156, 0xFF74E88C, 0xFF123A1D);

        int cancelBtnX = resumeBtnX + BTN_W + 2;
        drawActionButton(g, font, cancelBtnX, y, "✖", mouseX, mouseY,
                0xAA4A2A2A, 0xCCB04A4A, 0xFFC07070, 0xFF1A0D0D);
    }

    private void renderActiveRow(GuiGraphics g, Font font, int x, int y, RtsWorkflowStatus status,
                                 String label, String progress, int mouseX, int mouseY) {
        int buttonArea = BTN_W * 2 + 2;
        int rowW = PANEL_W - PADDING * 2 - buttonArea - 2;
        boolean hovered = isInside(mouseX, mouseY, x, y, rowW, ROW_H);

        RtsClientUiUtil.drawPanelFrame(g, x, y, rowW, ROW_H,
                hovered ? 0xAA2A3A4A : 0xAA1A222C,
                0xFF5E738A, 0xFF0D1117);
        g.drawString(font, label, x + 4, y + 2, 0xEAF2FF, false);

        int barX = x + 4;
        int barY = y + 12;
        int barW = rowW - 8;
        drawProgressBar(g, font, status, progress, barX, barY, barW, 0xAA202832, 0xFF88BEF4,
                0xFF405064, 0xFF0A0D12, 0xCCFFFFFF);

        boolean paused = status.paused();
        int pauseBtnX = x + rowW + 2;
        drawActionButton(g, font, pauseBtnX, y, paused ? "▶" : "⏸", mouseX, mouseY,
                paused ? 0xCC2C873F : 0xCC705A1A,
                paused ? 0xCC3AA156 : 0xCCA07A2A,
                paused ? 0xFF74E88C : 0xFFE7C46A,
                0xFF1A2A1A);

        int deleteBtnX = pauseBtnX + BTN_W + 2;
        drawActionButton(g, font, deleteBtnX, y, "✖", mouseX, mouseY,
                0xAA4A2A2A, 0xCCB04A4A, 0xFFC07070, 0xFF1A0D0D);
    }

    private static void drawProgressBar(GuiGraphics g, Font font, RtsWorkflowStatus status, String progress,
                                        int barX, int barY, int barW, int bg, int fill,
                                        int light, int dark, int textColor) {
        int fillW = RtsWorkflowProgressProcessor.computeFillWidth(status, barW);
        g.fill(barX, barY, barX + barW, barY + BAR_H, bg);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + BAR_H, fill);
        }
        g.hLine(barX, barX + barW, barY, light);
        g.hLine(barX, barX + barW, barY + BAR_H, dark);
        g.vLine(barX, barY, barY + BAR_H, light);
        g.vLine(barX + barW, barY, barY + BAR_H, dark);
        g.drawString(font, progress, barX + 2, barY + 1, textColor, false);
    }

    private static void drawActionButton(GuiGraphics g, Font font, int x, int y, String text, int mouseX, int mouseY,
                                         int normalBg, int hoverBg, int border, int shadow) {
        boolean hovered = isInside(mouseX, mouseY, x, y, BTN_W, ROW_H);
        RtsClientUiUtil.drawPanelFrame(g, x, y, BTN_W, ROW_H, hovered ? hoverBg : normalBg, border, shadow);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, text, x + BTN_W / 2, y + 4, 0xFFFFFF);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }

        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        int count = Math.min(getActiveCount(), workflows.length);
        int baseX = contentX();
        int rowY = contentY() + PADDING;

        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status == null || status.type() == null) {
                continue;
            }

            int buttonArea = BTN_W * 2 + 2;
            int rowW = PANEL_W - PADDING * 2 - buttonArea - 2;
            int firstButtonX = baseX + rowW + 2;
            int secondButtonX = firstButtonX + BTN_W + 2;

            if (status.suspended()) {
                if (isInside(mouseX, mouseY, firstButtonX, rowY, BTN_W, ROW_H)) {
                    RtsClientPacketGateway.sendScanResumePlacement(status.entryId());
                    return;
                }
                if (isInside(mouseX, mouseY, secondButtonX, rowY, BTN_W, ROW_H)) {
                    RtsClientPacketGateway.sendDeleteWorkflow(status.entryId());
                    return;
                }
            } else {
                if (isInside(mouseX, mouseY, firstButtonX, rowY, BTN_W, ROW_H)) {
                    RtsClientPacketGateway.sendPauseWorkflow(status.entryId());
                    return;
                }
                if (isInside(mouseX, mouseY, secondButtonX, rowY, BTN_W, ROW_H)) {
                    RtsClientPacketGateway.sendDeleteWorkflow(status.entryId());
                    return;
                }
            }

            rowY += ROW_H;
        }
    }

    private int getActiveCount() {
        return this.controller.getWorkflowActiveCount();
    }

    private int getVisibleWorkflowRows() {
        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        int limit = Math.min(getActiveCount(), workflows.length);
        int count = 0;
        for (int i = 0; i < limit; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status != null && status.isActive()) {
                count++;
            }
        }
        return count;
    }

    private int getSuspendedCount() {
        RtsWorkflowStatus[] workflows = this.controller.getWorkflowStatuses();
        int limit = Math.min(getActiveCount(), workflows.length);
        int count = 0;
        for (int i = 0; i < limit; i++) {
            RtsWorkflowStatus status = workflows[i];
            if (status != null && status.type() != null && status.suspended()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasPending() {
        return this.controller.hasPendingJobs();
    }

    private boolean hasDisplayableWorkflowContent() {
        return getActiveCount() > 0 || getSuspendedCount() > 0 || hasPending();
    }

    private static boolean isInside(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
