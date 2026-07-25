package com.rtsbuilding.rtsbuilding.client.screen.funnel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiAction;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.FunnelBufferChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.FunnelBufferLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.FunnelBufferStyle;
import net.minecraft.client.gui.GuiGraphics;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

public final class FunnelBufferPanel {
    private BuilderScreen screen;
    private ClientRtsController controller;
    private boolean funnelBufferVisible = true;
    private int hoveredEntry = -1;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        int panelY = FunnelBufferLayout.panelY(TOP_H);
        int panelH = screen.getFloatingPanelAvailableHeight(panelY);
        int capacity = FunnelBufferLayout.visibleRows(Math.max(20, panelH));
        FunnelBufferLayout.Geometry geometry =
                FunnelBufferLayout.geometry(screen.width, TOP_H, panelH);
        FunnelUiState state = FunnelUiAdapter.snapshot(controller, funnelBufferVisible,
                capacity, hoveredEntry);
        if (!state.shouldRender()) {
            return;
        }

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);
        FunnelBufferChromeRenderer.renderToggle(canvas, geometry.toggle, state.panelVisible);
        int toggleX = (int) geometry.toggle.getX();
        int toggleY = (int) geometry.toggle.getY();
        g.drawCenteredString(screen.font(), "BUFFER",
                toggleX + FunnelBufferLayout.TOGGLE_W / 2, toggleY + 4,
                FunnelBufferStyle.PRIMARY_TEXT.toArgb());

        if (!state.panelVisible || !geometry.panelRenderable) {
            return;
        }

        FunnelBufferChromeRenderer.renderPanel(canvas, geometry.panel);
        int panelX = (int) geometry.panel.getX();
        g.drawString(screen.font(), "Funnel Buffer", panelX + 6, panelY + 4,
                FunnelBufferStyle.TITLE_TEXT.toArgb());

        FunnelBufferLayout.Hit hover =
                geometry.hitAt(mouseX, mouseY, state.visibleEntries.size(), true);
        for (int i = 0; i < state.visibleEntries.size(); i++) {
            FunnelUiEntry row = state.visibleEntries.get(i);
            int entryIndex = row.sourceIndex;
            UiRect rowBounds = geometry.row(i);
            UiRect slotBounds = geometry.slot(i);
            boolean hovered = hover.target == FunnelBufferLayout.Target.ROW
                    && hover.visibleRowIndex == i;
            FunnelBufferChromeRenderer.renderRow(canvas, rowBounds, slotBounds, hovered);
            int rowX = (int) rowBounds.getX();
            int rowY = (int) rowBounds.getY();
            int rowW = (int) rowBounds.getWidth();
            int slotX = (int) slotBounds.getX();
            int slotY = (int) slotBounds.getY();
            g.renderItem(controller.getFunnelBufferEntries().get(entryIndex).stack(), slotX + 1, slotY + 1);
            g.drawString(screen.font(), RtsClientUiUtil.trimToWidth(screen.font(), row.label, rowW - 30),
                    rowX + 24, rowY + 3, FunnelBufferStyle.PRIMARY_TEXT.toArgb());
            g.drawString(screen.font(), "x" + RtsClientUiUtil.compactCount(row.count),
                    rowX + 24, rowY + 12, FunnelBufferStyle.COUNT_TEXT.toArgb());

            if (hovered) {
                screen.setHoveredFunnelBufferEntry(entryIndex);
                this.hoveredEntry = FunnelUiReducer.apply(state,
                        FunnelUiAction.hover(entryIndex)).hoveredSourceIndex;
            }
        }

        if (state.totalEntries == 0) {
            g.drawString(screen.font(), "empty", panelX + 6, panelY + 20,
                    FunnelBufferStyle.EMPTY_TEXT.toArgb());
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
            funnelBufferVisible = FunnelUiReducer.apply(state, FunnelUiAction.toggle()).panelVisible;
            return true;
        }
        return hit.target == FunnelBufferLayout.Target.ROW
                || hit.target == FunnelBufferLayout.Target.PANEL;
    }

    public int getHoveredEntry() {
        return this.hoveredEntry;
    }

    public void setHoveredEntry(int index) {
        this.hoveredEntry = index;
    }

    public void resetHoveredEntry() {
        this.hoveredEntry = -1;
    }
}
