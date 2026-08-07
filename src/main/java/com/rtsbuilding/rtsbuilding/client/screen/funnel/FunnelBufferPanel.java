package com.rtsbuilding.rtsbuilding.client.screen.funnel;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiAction;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.FunnelBufferChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.FunnelBufferLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.FunnelBufferStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

/**
 * 漏斗缓存的客户端面板。它只负责把 controller 的缓存投影到 Kit 几何与主题，
 * 不改变漏斗逻辑、远程 RTS 操作或网络授权。
 */
public final class FunnelBufferPanel {
    private BuilderScreen screen;
    private ClientRtsController controller;
    private boolean funnelBufferVisible = true;
    private int hoveredEntry = -1;
    private final UiControlAnimationState toggleAnimation =
            new UiControlAnimationState(SystemUiClock.INSTANCE);
    private final UiControlAnimationRegistry<Integer> rowAnimations =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 256);

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelY = FunnelBufferLayout.panelY(TOP_H);
        int panelH = screen.getFloatingPanelAvailableHeight(panelY);
        int capacity = FunnelBufferLayout.visibleRows(Math.max(20, panelH));
        FunnelBufferLayout.Geometry geometry =
                FunnelBufferLayout.geometry(screen.width, TOP_H, panelH);
        FunnelUiState state = FunnelUiAdapter.snapshot(
                controller, funnelBufferVisible, capacity, hoveredEntry);
        if (!state.shouldRender()) {
            return;
        }

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, screen.font(), screen);
        FunnelBufferLayout.Hit hover = geometry.hitAt(
                mouseX, mouseY, state.visibleEntries.size(), state.panelVisible);
        UiControlAnimationState.Snapshot toggleVisual = this.toggleAnimation.update(
                new UiControlState(
                        true, true,
                        hover.target == FunnelBufferLayout.Target.TOGGLE,
                        false, false, state.panelVisible,
                        false, false, ""),
                Config.isUiAnimationsEnabled());
        FunnelBufferChromeRenderer.renderToggle(
                canvas, geometry.toggle,
                toggleVisual.selection(), toggleVisual.hover());
        int toggleX = (int) geometry.toggle.getX();
        int toggleY = (int) geometry.toggle.getY();
        graphics.centeredText(screen.font(), "BUFFER",
                toggleX + FunnelBufferLayout.TOGGLE_W / 2, toggleY + 4,
                FunnelBufferStyle.PRIMARY_TEXT.toArgb());

        if (!state.panelVisible || !geometry.panelRenderable) {
            return;
        }

        FunnelBufferChromeRenderer.renderPanel(canvas, geometry.panel);
        int panelX = (int) geometry.panel.getX();
        graphics.text(screen.font(), "Funnel Buffer", panelX + 6, panelY + 4,
                FunnelBufferStyle.TITLE_TEXT.toArgb(), false);

        for (int index = 0; index < state.visibleEntries.size(); index++) {
            FunnelUiEntry row = state.visibleEntries.get(index);
            int sourceIndex = row.sourceIndex;
            UiRect rowBounds = geometry.row(index);
            UiRect slotBounds = geometry.slot(index);
            boolean hovered = hover.target == FunnelBufferLayout.Target.ROW
                    && hover.visibleRowIndex == index;
            double hoverProgress = this.rowAnimations.update(
                    sourceIndex,
                    new UiControlState(true, true, hovered, false, false,
                            false, false, false, ""),
                    Config.isUiAnimationsEnabled()).hover();
            FunnelBufferChromeRenderer.renderRow(
                    canvas, rowBounds, slotBounds, hoverProgress);
            int rowX = (int) rowBounds.getX();
            int rowY = (int) rowBounds.getY();
            int rowW = (int) rowBounds.getWidth();
            int slotX = (int) slotBounds.getX();
            int slotY = (int) slotBounds.getY();
            graphics.item(controller.getFunnelBufferEntries().get(sourceIndex).stack(),
                    slotX + 1, slotY + 1);
            graphics.text(screen.font(),
                    RtsClientUiUtil.trimToWidth(screen.font(), row.label, rowW - 30),
                    rowX + 24, rowY + 3, FunnelBufferStyle.PRIMARY_TEXT.toArgb(), false);
            graphics.text(screen.font(), "x" + RtsClientUiUtil.compactCount(row.count),
                    rowX + 24, rowY + 12, FunnelBufferStyle.COUNT_TEXT.toArgb(), false);

            if (hovered) {
                screen.setHoveredFunnelBufferEntry(sourceIndex);
                this.hoveredEntry = FunnelUiReducer.apply(
                        state, FunnelUiAction.hover(sourceIndex)).hoveredSourceIndex;
            }
        }

        if (state.totalEntries == 0) {
            graphics.text(screen.font(), "empty", panelX + 6, panelY + 20,
                    FunnelBufferStyle.EMPTY_TEXT.toArgb(), false);
        }
    }

    public boolean handleClick(double mouseX, double mouseY) {
        int panelY = FunnelBufferLayout.panelY(TOP_H);
        int panelH = screen.getFloatingPanelAvailableHeight(panelY);
        FunnelBufferLayout.Geometry geometry =
                FunnelBufferLayout.geometry(screen.width, TOP_H, panelH);
        FunnelUiState state = FunnelUiAdapter.snapshot(controller, funnelBufferVisible,
                FunnelBufferLayout.visibleRows(Math.max(20, panelH)), hoveredEntry);
        if (!state.shouldRender()) {
            return false;
        }

        FunnelBufferLayout.Hit hit = geometry.hitAt(
                mouseX, mouseY, state.visibleEntries.size(), state.panelVisible);
        if (hit.target == FunnelBufferLayout.Target.TOGGLE) {
            funnelBufferVisible = FunnelUiReducer.apply(
                    state, FunnelUiAction.toggle()).panelVisible;
            return true;
        }
        return hit.target == FunnelBufferLayout.Target.ROW
                || hit.target == FunnelBufferLayout.Target.PANEL;
    }

    public int getHoveredEntry() {
        return hoveredEntry;
    }

    /** BuilderScreen 的悬浮项查询会写入同一个 Core 快照索引。 */
    public void setHoveredEntry(int index) {
        this.hoveredEntry = index;
    }

    public void resetHoveredEntry() {
        this.hoveredEntry = -1;
    }
}
