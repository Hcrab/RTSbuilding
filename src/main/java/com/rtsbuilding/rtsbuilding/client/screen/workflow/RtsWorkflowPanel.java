package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowProgressProcessor;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 显示服务器工作流进度的小窗口。
 *
 * <p>这个面板只负责窗口、行渲染和暂停/继续/删除按钮，不拥有工作流业务状态；
 * 状态仍然由 {@link ClientRtsController} 和服务端同步链路提供。</p>
 */
public final class RtsWorkflowPanel extends RtsWindowPanel {
    private static final int ROW_H = 30;
    private static final int BUTTON_W = 16;
    private static final int BUTTON_H = 14;
    private static final int GAP = 4;
    private static final int DEFAULT_WINDOW_W = 196;
    private static final int DEFAULT_WINDOW_H = 152;
    private static final int TOP_BAR_GAP = 14;

    private int scrollOffset;

    public RtsWorkflowPanel() {
        this.closable = false;
        this.draggable = true;
        this.resizable = false;
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        setOpen(true);
    }

    @Override
    protected boolean canShowWindow() {
        return super.canShowWindow()
                && RtsClientUiStateStore.isShowWorkflowPanelEnabled()
                && (this.controller.hasActiveWorkflow() || this.controller.hasPendingJobs());
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<RtsWorkflowStatus> workflows = this.controller.getActiveWorkflows();
        if (workflows.isEmpty()) {
            g.drawString(this.screen.font(), Component.translatable("screen.rtsbuilding.workflow.empty"),
                    contentX() + 6, contentY() + 8, 0xAAB8C8, false);
            return;
        }

        clampScroll(workflows.size());
        int visibleRows = Math.max(1, contentHeight() / ROW_H);
        int end = Math.min(workflows.size(), this.scrollOffset + visibleRows);
        for (int row = this.scrollOffset; row < end; row++) {
            renderRow(g, workflows.get(row), row - this.scrollOffset, mouseX, mouseY);
        }
    }

    private void renderRow(GuiGraphics g, RtsWorkflowStatus status, int visibleIndex, int mouseX, int mouseY) {
        int x = contentX() + 3;
        int y = contentY() + visibleIndex * ROW_H + 2;
        int width = contentWidth() - 6;
        boolean hover = inside(mouseX, mouseY, x, y, width, ROW_H - 4);
        int fill = status.paused() ? 0xD03E3324 : status.suspended() ? 0xD02C3545 : 0xCC202833;
        RtsClientUiUtil.drawPanelFrame(g, x, y, width, ROW_H - 4, hover ? fill + 0x00101010 : fill,
                0xFF5C6C7F, 0xFF10151D);

        int actionRight = x + width - 5;
        int deleteX = actionRight - BUTTON_W;
        int pauseX = deleteX - GAP - BUTTON_W;
        int textMax = Math.max(28, pauseX - x - 12);
        String label = RtsClientUiUtil.trimToWidth(this.screen.font(),
                RtsWorkflowProgressProcessor.formatLabel(status), textMax);
        g.drawString(this.screen.font(), label, x + 6, y + 5,
                status.hasMissingItems() ? 0xFFFFD58A : 0xEAF2FF, false);

        String progress = RtsWorkflowProgressProcessor.formatProgressText(status);
        g.drawString(this.screen.font(), progress, x + 6, y + 17, 0xAAB8C8, false);

        int barX = x + 54;
        int barY = y + 18;
        int barW = Math.max(20, pauseX - barX - 6);
        g.fill(barX, barY, barX + barW, barY + 5, 0xFF10151D);
        int fillW = RtsWorkflowProgressProcessor.computeFillWidth(status, barW);
        g.fill(barX, barY, barX + fillW, barY + 5, status.hasFailures() ? 0xFFE06A5F : 0xFF64C27B);

        drawActionButton(g, pauseX, y + 6, status.paused() || status.suspended() ? "R" : "P", mouseX, mouseY);
        drawActionButton(g, deleteX, y + 6, "X", mouseX, mouseY);
    }

    private void drawActionButton(GuiGraphics g, int x, int y, String text, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, BUTTON_W, BUTTON_H);
        RtsClientUiUtil.drawPanelFrame(g, x, y, BUTTON_W, BUTTON_H,
                hover ? 0xCC3E536A : 0xAA273241, hover ? 0xFF9FC5FF : 0xFF6D7C90, 0xFF111720);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, this.screen.font(), text,
                x + BUTTON_W / 2, y + 3, 0xEAF2FF);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        List<RtsWorkflowStatus> workflows = this.controller.getActiveWorkflows();
        if (button != 0 || workflows.isEmpty()) {
            return;
        }
        int visibleIndex = ((int) mouseY - contentY()) / ROW_H;
        int row = this.scrollOffset + visibleIndex;
        if (visibleIndex < 0 || row < 0 || row >= workflows.size()) {
            return;
        }
        RtsWorkflowStatus status = workflows.get(row);
        int x = contentX() + 3;
        int width = contentWidth() - 6;
        int actionRight = x + width - 5;
        int deleteX = actionRight - BUTTON_W;
        int pauseX = deleteX - GAP - BUTTON_W;
        int y = contentY() + visibleIndex * ROW_H + 8;
        if (inside(mouseX, mouseY, deleteX, y, BUTTON_W, BUTTON_H)) {
            RtsClientPacketGateway.sendDeleteWorkflow(status.entryId());
        } else if (inside(mouseX, mouseY, pauseX, y, BUTTON_W, BUTTON_H)) {
            if (status.suspended()) {
                RtsClientPacketGateway.sendScanResumePlacement(status.entryId());
            } else {
                RtsClientPacketGateway.sendPauseWorkflow(status.entryId());
            }
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = Math.max(1, contentHeight() / ROW_H);
        int max = Math.max(0, this.controller.getActiveWorkflows().size() - visibleRows);
        this.scrollOffset = Mth.clamp(this.scrollOffset - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.workflow.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_WINDOW_W;
    }

    @Override
    protected int getDefaultHeight() {
        return DEFAULT_WINDOW_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(4, this.screen.width - this.windowWidth - 8);
        this.windowY = this.screen.topBarBottomY() + TOP_BAR_GAP;
    }

    private void clampScroll(int size) {
        int visibleRows = Math.max(1, contentHeight() / ROW_H);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, Math.max(0, size - visibleRows));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
