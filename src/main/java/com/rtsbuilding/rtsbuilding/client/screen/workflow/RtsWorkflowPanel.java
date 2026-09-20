package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiAction;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowUiHitTarget;
import com.rtsbuilding.rtsbuilding.uikit.scroll.UiScrollModel;
import com.rtsbuilding.rtsbuilding.uikit.scroll.UiVisibleRange;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 显示活动工作流、进度和行级动作的浮动窗口。
 *
 * <p>窗口仍负责可见性、持久化和命令分发；行级几何、命中、chrome 与状态色由
 * Core/Kit 共享边界负责，避免生产和离屏预览各自维护一份实现。列表视口只保留有限行高，
 * 超出协议容量的活动项通过滚动查看，避免一次同步把窗口撑出屏幕。</p>
 */
public final class RtsWorkflowPanel extends RtsWindowPanel {
    private static final int PANEL_W = WorkflowWindowLayout.WINDOW_W;
    private static final int ROW_H = WorkflowWindowLayout.ROW_H;
    private static final int PADDING = WorkflowWindowLayout.PADDING;
    private static final int DEFAULT_VISIBLE_ROWS = 8;

    private final UiScrollModel scrollModel = new UiScrollModel(0.0D, 0.0D);
    private int cachedVisibleRows = -1;
    private WorkflowUiState cachedState;
    private WorkflowWindowLayout.Geometry cachedGeometry;
    /** 最后真正绘制过的身份快照；输入不能以刷新后的局部 row index 代替它。 */
    private WorkflowUiState lastDrawnState;
    private WorkflowWindowLayout.Geometry lastDrawnGeometry;
    private int lastDrawnWindowX;
    private int lastDrawnWindowY;
    private int lastDrawnWindowWidth;
    private int lastDrawnWindowHeight;
    private int lastDrawnScreenWidth;
    private int lastDrawnScreenHeight;
    private double lastDrawnScrollOffset;

    public RtsWorkflowPanel() {
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
        return WorkflowWindowLayout.totalHeight(
                getTitleBarHeight(), DEFAULT_VISIBLE_ROWS);
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen == null) return;
        this.windowX = Math.max(8, this.screen.width - PANEL_W - 8);
        this.windowY = this.screen.topBarBottomY() + 14;
    }

    @Override
    protected boolean canShowWindow() {
        if (!RtsClientUiStateStore.isShowWorkflowPanelEnabled()) {
            return false;
        }
        return this.cachedState != null
                ? this.cachedState.hasContent()
                : hasDisplayableWorkflowContent();
    }

    @Override
    protected boolean shouldClipContent() {
        return true;
    }

    @Override
    protected boolean handleContentScroll(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        refreshLayout();
        if (cachedState == null || !workflowViewport().contains(mouseX, mouseY)) {
            return false;
        }
        if (scrollY == 0.0D || !scrollModel.canScroll()) {
            return true;
        }
        // Minecraft 的正滚轮方向表示向上，因此列表偏移向上减少。
        boolean changed = scrollModel.scrollBy(-scrollY * ROW_H);
        if (changed) {
            cachedGeometry = workflowGeometry(cachedState);
            clearLastDrawnSnapshot();
        }
        return true;
    }

    @Override
    public void renderOverlays(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (!this.open || this.screen == null) return;
        if (!canShowWindow() || !lastDrawnLayoutIsCurrent()) return;
        if (!workflowViewport().contains(mouseX, mouseY)) return;

        WorkflowUiHitTarget.Target target = WorkflowUiHitTarget.resolve(
                lastDrawnGeometry, lastDrawnState.rows, mouseX, mouseY);
        if (!target.hit()) return;
        WorkflowUiRow hovered = lastDrawnState.rows.get(target.rowIndex());
        if (target.control() == WorkflowWindowLayout.Control.PROTECT) {
            if (hovered != null) {
                graphics.renderTooltip(
                        this.screen.font(),
                        Component.translatable(
                                hovered.protectedWorkflow
                                        ? "screen.rtsbuilding.workflow.allow_replace"
                                        : "screen.rtsbuilding.workflow.keep"),
                        mouseX,
                        mouseY);
            }
            return;
        }

        if (hovered == null || !hovered.hasDetails()) return;
        WorkflowDetailTooltipRenderer.render(
                graphics,
                this.screen.font(),
                this.screen.width,
                this.screen.height,
                hovered,
                lastDrawnGeometry.rowGeometryAt(target.rowIndex()).row);
    }

    @Override
    public void init(
            BuilderScreen screen,
            ClientRtsController controller) {
        super.init(screen, controller);
        this.draggable = true;
        this.resizable = false;
        this.closable = false;
        this.cachedState = null;
        this.cachedGeometry = null;
        clearLastDrawnSnapshot();
        this.scrollModel.setOffset(0.0D);
        setOpen(true);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        refreshLayout();
        if (!this.open || !canShowWindow()) {
            this.mouseHovering = false;
            clearLastDrawnSnapshot();
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 根据活动行数选择紧凑高度；最多预留固定行数，剩余工作流通过滚动查看。 */
    private void recomputeSize(WorkflowUiState state) {
        int desiredRows = Math.min(
                DEFAULT_VISIBLE_ROWS,
                Math.max(1, state.rows.size()));
        if (this.screen != null && !hasUserBoundsPreference()) {
            computeDefaultPosition();
            int availableHeight = Math.max(1, this.screen.height - this.windowY - 4);
            int fixedHeight = getTitleBarHeight() + 1 + PADDING * 2;
            int screenRows = Math.max(1, (availableHeight - fixedHeight) / ROW_H);
            desiredRows = Math.min(desiredRows, screenRows);
        }
        if (desiredRows == cachedVisibleRows && hasInitializedBounds()) return;
        cachedVisibleRows = desiredRows;
        int totalHeight = WorkflowWindowLayout.totalHeight(
                getTitleBarHeight(), desiredRows);
        if (hasUserBoundsPreference()) {
            // 玩家自己的窗口尺寸是滚动视口，不因任务波动而被覆盖。
            return;
        }
        computeDefaultPosition();
        setTransientBounds(
                this.windowX,
                this.windowY,
                PANEL_W,
                totalHeight);
    }

    @Override
    protected void renderContent(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        ensureLayout();
        if (cachedState == null || cachedGeometry == null) {
            clearLastDrawnSnapshot();
            return;
        }
        rememberLastDrawnSnapshot();
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(
                graphics,
                this.screen.font(),
                this.screen);
        UiRect viewport = workflowViewport();
        if (cachedState.rows.isEmpty()) {
            if (cachedState.pendingJobs) {
                graphics.drawString(
                        this.screen.font(),
                        Component.translatable(
                                "screen.rtsbuilding.workflow.pending").getString(),
                        (int) viewport.getX() + PADDING,
                        (int) viewport.getY() + PADDING,
                        UiThemeRuntime.color(UiThemeToken.TEXT_PRIMARY).toArgb(),
                        false);
            }
            return;
        }
        if (viewport.isEmpty()) return;

        graphics.enableScissor(
                (int) viewport.getX(),
                (int) viewport.getY(),
                (int) viewport.right(),
                (int) viewport.bottom());
        try {
            for (int localIndex = 0;
                    localIndex < cachedGeometry.rows.size();
                    localIndex++) {
                int rowIndex = cachedGeometry.firstRowIndex + localIndex;
                if (rowIndex < 0 || rowIndex >= cachedState.rows.size()) continue;
                WorkflowWindowLayout.RowGeometry rowGeometry =
                        cachedGeometry.rows.get(localIndex);
                WorkflowUiRow row = cachedState.rows.get(rowIndex);
                WorkflowPanelRenderer.renderRow(
                        graphics,
                        this.screen.font(),
                        canvas,
                        rowGeometry,
                        row,
                        mouseX,
                        mouseY);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    protected void handleContentClick(
            double mouseX,
            double mouseY,
            int button) {
        if (button != 0) return;
        if (!lastDrawnLayoutIsCurrent()) return;
        if (!workflowViewport().contains(mouseX, mouseY)) return;
        WorkflowUiHitTarget.Target target = WorkflowUiHitTarget.resolve(
                lastDrawnGeometry, lastDrawnState.rows, mouseX, mouseY);
        if (!target.hit()) return;
        WorkflowUiRow drawnRow = lastDrawnState.rows.get(target.rowIndex());
        if (drawnRow == null) return;
        WorkflowUiState latestState = WorkflowUiAdapter.snapshot(this.controller);
        WorkflowUiRow row = WorkflowUiHitTarget.findByEntryId(
                latestState.rows, target.entryId());
        if (target.isBody()) {
            WorkflowUiRow detailRow = row == null ? drawnRow : row;
            if (this.screen != null) {
                this.screen.getWorkflowDetailPanel().openFor(detailRow);
            }
            return;
        }
        // 原条目已消失时不把同一像素位置发给刷新后的替补条目。
        if (row == null) return;
        WorkflowUiAction.Type action;
        switch (target.control()) {
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
                latestState,
                WorkflowUiAction.of(action, target.entryId()));
    }

    private boolean hasDisplayableWorkflowContent() {
        return this.controller != null
                && WorkflowUiAdapter.snapshot(this.controller).hasContent();
    }

    /** 每个渲染帧或输入事件只在这里读取一次真实工作流快照。 */
    private void refreshLayout() {
        if (this.controller == null || this.screen == null) return;
        this.cachedState = WorkflowUiAdapter.snapshot(this.controller);
        recomputeSize(this.cachedState);
        this.cachedGeometry = workflowGeometry(this.cachedState);
    }

    /** 复用当前帧快照，仅重算受窗口尺寸/滚动影响的几何。 */
    private void ensureLayout() {
        if (this.cachedState == null || this.cachedGeometry == null) {
            refreshLayout();
            return;
        }
        recomputeSize(this.cachedState);
        this.cachedGeometry = workflowGeometry(this.cachedState);
    }

    private WorkflowWindowLayout.Geometry workflowGeometry(
            WorkflowUiState state) {
        UiRect viewport = workflowViewport();
        double viewportHeight = Math.max(1.0D, viewport.getHeight());
        this.scrollModel.setExtents(state.rows.size() * (double) ROW_H, viewportHeight);
        UiVisibleRange visible = UiVisibleRange.calculate(
                state.rows.size(), ROW_H, viewportHeight,
                this.scrollModel.getOffset(), 0);
        int firstIndex = visible.getFirstInclusive();
        int rowCount = visible.getLastExclusive() - firstIndex;
        int firstRowY = (int) Math.round(
                viewport.getY() - this.scrollModel.getOffset()
                        + firstIndex * (double) ROW_H);
        return WorkflowWindowLayout.geometry(
                contentX(), firstRowY, rowCount, firstIndex);
    }

    private UiRect workflowViewport() {
        return new UiRect(
                contentX(),
                contentY() + PADDING,
                Math.max(0, contentWidth()),
                Math.max(0, contentHeight() - PADDING * 2));
    }

    private void rememberLastDrawnSnapshot() {
        this.lastDrawnState = this.cachedState;
        this.lastDrawnGeometry = this.cachedGeometry;
        this.lastDrawnWindowX = this.windowX;
        this.lastDrawnWindowY = this.windowY;
        this.lastDrawnWindowWidth = this.windowWidth;
        this.lastDrawnWindowHeight = this.windowHeight;
        this.lastDrawnScreenWidth = this.screen == null ? -1 : this.screen.width;
        this.lastDrawnScreenHeight = this.screen == null ? -1 : this.screen.height;
        this.lastDrawnScrollOffset = this.scrollModel.getOffset();
    }

    private void clearLastDrawnSnapshot() {
        this.lastDrawnState = null;
        this.lastDrawnGeometry = null;
    }

    private boolean lastDrawnLayoutIsCurrent() {
        return this.lastDrawnState != null
                && this.lastDrawnGeometry != null
                && this.lastDrawnWindowX == this.windowX
                && this.lastDrawnWindowY == this.windowY
                && this.lastDrawnWindowWidth == this.windowWidth
                && this.lastDrawnWindowHeight == this.windowHeight
                && this.screen != null
                && this.lastDrawnScreenWidth == this.screen.width
                && this.lastDrawnScreenHeight == this.screen.height
                && Double.compare(
                        this.lastDrawnScrollOffset, this.scrollModel.getOffset()) == 0;
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("workflow", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
