package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiAction;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.client.config.GuiUtils;

import java.util.Collections;
import java.util.List;

/**
 * 显示活动工作流、进度和行级动作的浮动窗口。
 *
 * <p>窗口仍负责可见性、持久化和命令分发；行级几何、命中、chrome 与状态色由
 * Core/Kit 共享边界负责，避免生产和离屏预览各自维护一份实现。</p>
 */
public final class RtsWorkflowPanel extends RtsWindowPanel {
    private static final int PANEL_W = WorkflowWindowLayout.WINDOW_W;
    private static final int ROW_H = WorkflowWindowLayout.ROW_H;
    private static final int PADDING = WorkflowWindowLayout.PADDING;
    private int cachedVisibleRows = -1;

    public RtsWorkflowPanel() {
    }

    @Override
    protected ITextComponent getTitle() {
        return new TextComponentTranslation("screen.rtsbuilding.workflow.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
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
        return RtsClientUiStateStore.isShowWorkflowPanelEnabled()
                && hasDisplayableWorkflowContent();
    }

    @Override
    protected boolean shouldClipContent() {
        return false;
    }

    @Override
    protected boolean handleContentScroll(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        // 保留既有交互：工作流窗口没有滚动内容，不抢占镜头缩放。
        return false;
    }

    @Override
    public void renderOverlays(
            LegacyGuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (!this.open || !canShowWindow() || this.screen == null) return;
        WorkflowUiRow hovered =
                workflowAtProtectionButton(mouseX, mouseY);
        if (hovered == null) return;
        GuiUtils.drawHoveringText(
                Collections.singletonList(WorkflowResumeRenderSupport.text(
                        hovered.protectedWorkflow
                                ? "screen.rtsbuilding.workflow.allow_replace"
                                : "screen.rtsbuilding.workflow.keep")),
                mouseX, mouseY, this.screen.width, this.screen.height,
                300, this.screen.font());
    }

    @Override
    public void init(
            BuilderScreen screen,
            ClientRtsController controller) {
        super.init(screen, controller);
        this.draggable = true;
        this.resizable = false;
        this.closable = false;
        setOpen(true);
    }

    @Override
    public void render(
            LegacyGuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (!this.open || !canShowWindow()) {
            this.mouseHovering = false;
            return;
        }
        recomputeSize();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 根据当前可见行数调整窗口高度，不改变玩家保存的窗口位置。 */
    private void recomputeSize() {
        int visibleRows =
                WorkflowUiAdapter.snapshot(this.controller).rows.size();
        if (visibleRows == cachedVisibleRows) return;
        cachedVisibleRows = visibleRows;
        int totalHeight = WorkflowWindowLayout.totalHeight(
                getTitleBarHeight(),
                visibleRows);
        if (hasUserBoundsPreference()) {
            setBounds(
                    this.windowX,
                    this.windowY,
                    PANEL_W,
                    totalHeight);
        } else {
            computeDefaultPosition();
            setTransientBounds(
                    this.windowX,
                    this.windowY,
                    PANEL_W,
                    totalHeight);
        }
    }

    @Override
    protected void renderContent(
            LegacyGuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        WorkflowUiState state =
                WorkflowUiAdapter.snapshot(this.controller);
        WorkflowWindowLayout.Geometry geometry =
                workflowGeometry(state);
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(
                graphics,
                this.screen.font(),
                this.screen);
        for (int index = 0; index < state.rows.size(); index++) {
            WorkflowPanelRenderer.renderRow(
                    graphics,
                    this.screen.font(),
                    canvas,
                    geometry.rows.get(index),
                    state.rows.get(index),
                    mouseX,
                    mouseY);
        }
    }

    @Override
    protected void handleContentClick(
            double mouseX,
            double mouseY,
            int button) {
        if (button != 0) return;

        WorkflowUiState state =
                WorkflowUiAdapter.snapshot(this.controller);
        WorkflowWindowLayout.Hit hit =
                workflowGeometry(state).hitAt(mouseX, mouseY);
        if (hit == null) return;

        WorkflowUiRow row = state.rows.get(hit.rowIndex);
        WorkflowUiAction.Type action;
        switch (hit.control) {
            case PROTECT:
                action = WorkflowUiAction.Type.TOGGLE_PROTECTED;
                break;
            case ACTION:
                action = row.suspended
                        ? WorkflowUiAction.Type.RESUME_SUSPENDED
                        : WorkflowUiAction.Type.TOGGLE_PAUSED;
                break;
            case DELETE:
                action = WorkflowUiAction.Type.DELETE;
                break;
            default:
                return;
        }
        WorkflowUiAdapter.dispatch(
                this.controller,
                state,
                WorkflowUiAction.of(action, row.entryId));
    }

    private boolean hasDisplayableWorkflowContent() {
        return WorkflowUiAdapter.snapshot(this.controller).hasContent();
    }

    private WorkflowWindowLayout.Geometry workflowGeometry(
            WorkflowUiState state) {
        return WorkflowWindowLayout.geometry(
                contentX(),
                contentY() + PADDING,
                state.rows.size());
    }

    private WorkflowUiRow workflowAtProtectionButton(
            int mouseX,
            int mouseY) {
        WorkflowUiState state =
                WorkflowUiAdapter.snapshot(this.controller);
        WorkflowWindowLayout.Hit hit =
                workflowGeometry(state).hitAt(mouseX, mouseY);
        if (hit == null
                || hit.control != WorkflowWindowLayout.Control.PROTECT) {
            return null;
        }
        return state.rows.get(hit.rowIndex);
    }

    private final List<PersistableProperty> properties = Collections.singletonList(
            PersistableProperty.bounds("workflow", this));

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
