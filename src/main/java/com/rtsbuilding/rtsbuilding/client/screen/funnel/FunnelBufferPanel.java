package com.rtsbuilding.rtsbuilding.client.screen.funnel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.FunnelBufferEntry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiAction;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.FunnelBufferLayout;
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
        FunnelUiState state = FunnelUiAdapter.snapshot(controller, funnelBufferVisible,
                capacity, hoveredEntry);
        if (!state.shouldRender()) {
            return;
        }

        int toggleX = FunnelBufferLayout.toggleX(screen.width);
        int toggleY = FunnelBufferLayout.toggleY(TOP_H);
        int toggleBg = state.panelVisible ? 0xAA2C4E3D : 0xAA2A2D36;
        g.fill(toggleX, toggleY, toggleX + FunnelBufferLayout.TOGGLE_W,
                toggleY + FunnelBufferLayout.TOGGLE_H, toggleBg);
        g.drawCenteredString(screen.font(), "BUFFER",
                toggleX + FunnelBufferLayout.TOGGLE_W / 2, toggleY + 4, 0xFFFFFF);

        if (!state.panelVisible) {
            return;
        }

        int panelX = FunnelBufferLayout.panelX(screen.width);
        if (panelH < 20) {
            return;
        }
        g.fill(panelX, panelY, panelX + FunnelBufferLayout.PANEL_W, panelY + panelH, 0xAA17191F);
        g.drawString(screen.font(), "Funnel Buffer", panelX + 6, panelY + 4, 0xF0F0F0);

        int listY = panelY + 16;
        for (int i = 0; i < state.visibleEntries.size(); i++) {
            FunnelUiEntry row = state.visibleEntries.get(i);
            int entryIndex = row.sourceIndex;
            int rowY = listY + i * FunnelBufferLayout.ROW_H;
            int rowX = panelX + 4;
            int rowW = FunnelBufferLayout.PANEL_W - 8;
            g.fill(rowX, rowY, rowX + rowW,
                    rowY + FunnelBufferLayout.ROW_H - 2, 0x88303845);

            int slotX = rowX + 2;
            int slotY = rowY + 2;
            g.fill(slotX, slotY, slotX + 18, slotY + 18, 0xAA1E222A);
            g.renderItem(controller.getFunnelBufferEntries().get(entryIndex).stack(), slotX + 1, slotY + 1);
            g.drawString(screen.font(), RtsClientUiUtil.trimToWidth(screen.font(), row.label, rowW - 30),
                    rowX + 24, rowY + 3, 0xFFFFFF);
            g.drawString(screen.font(), "x" + RtsClientUiUtil.compactCount(row.count),
                    rowX + 24, rowY + 12, 0xFFDFAE);

            if (inside(mouseX, mouseY, rowX, rowY, rowW, FunnelBufferLayout.ROW_H - 2)) {
                screen.setHoveredFunnelBufferEntry(entryIndex);
                this.hoveredEntry = FunnelUiReducer.apply(state,
                        FunnelUiAction.hover(entryIndex)).hoveredSourceIndex;
                g.fill(rowX, rowY, rowX + rowW, rowY + FunnelBufferLayout.ROW_H - 2, 0x33FFFFFF);
            }
        }

        if (state.totalEntries == 0) {
            g.drawString(screen.font(), "empty", panelX + 6, panelY + 20, 0x99B4BCC8);
        }
    }

    public boolean handleClick(double mouseX, double mouseY) {
        int panelY = FunnelBufferLayout.panelY(TOP_H);
        int panelH = screen.getFloatingPanelAvailableHeight(panelY);
        FunnelUiState state = FunnelUiAdapter.snapshot(controller, funnelBufferVisible,
                FunnelBufferLayout.visibleRows(Math.max(20, panelH)), hoveredEntry);
        if (!state.shouldRender()) {
            return false;
        }

        int toggleX = FunnelBufferLayout.toggleX(screen.width);
        int toggleY = FunnelBufferLayout.toggleY(TOP_H);
        if (inside(mouseX, mouseY, toggleX, toggleY,
                FunnelBufferLayout.TOGGLE_W, FunnelBufferLayout.TOGGLE_H)) {
            funnelBufferVisible = FunnelUiReducer.apply(state, FunnelUiAction.toggle()).panelVisible;
            return true;
        }
        if (!funnelBufferVisible) {
            return false;
        }

        int panelX = FunnelBufferLayout.panelX(screen.width);
        if (panelH < 20) {
            return false;
        }
        return inside(mouseX, mouseY, panelX, panelY, FunnelBufferLayout.PANEL_W, panelH);
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
