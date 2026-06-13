package com.rtsbuilding.rtsbuilding.client.screen.funnel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.FunnelBufferEntry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

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
        if (controller.getMode() != BuilderMode.FUNNEL && controller.getFunnelBufferEntries().isEmpty()) {
            return;
        }

        int toggleX = screen.width - FUNNEL_BUFFER_TOGGLE_W - 8;
        int toggleY = TOP_H + 6;
        int toggleBg = funnelBufferVisible ? 0xAA2C4E3D : 0xAA2A2D36;
        g.fill(toggleX, toggleY, toggleX + FUNNEL_BUFFER_TOGGLE_W, toggleY + FUNNEL_BUFFER_TOGGLE_H, toggleBg);
        g.drawCenteredString(screen.font(), "BUFFER", toggleX + FUNNEL_BUFFER_TOGGLE_W / 2, toggleY + 4, 0xFFFFFF);

        if (!funnelBufferVisible) {
            return;
        }

        int panelX = screen.width - FUNNEL_BUFFER_PANEL_W - 8;
        int panelY = TOP_H + 26;
        int panelH = screen.getFloatingPanelAvailableHeight(panelY);
        if (panelH < 20) {
            return;
        }
        g.fill(panelX, panelY, panelX + FUNNEL_BUFFER_PANEL_W, panelY + panelH, 0xAA17191F);
        g.drawString(screen.font(), "Funnel Buffer", panelX + 6, panelY + 4, 0xF0F0F0);

        List<FunnelBufferEntry> entries = controller.getFunnelBufferEntries();
        int listY = panelY + 16;
        int rows = Math.max(1, (panelH - 20) / FUNNEL_BUFFER_ROW_H);
        for (int i = 0; i < rows; i++) {
            int entryIndex = i;
            int rowY = listY + i * FUNNEL_BUFFER_ROW_H;
            if (entryIndex >= entries.size()) {
                break;
            }
            var entry = entries.get(entryIndex);
            int rowX = panelX + 4;
            int rowW = FUNNEL_BUFFER_PANEL_W - 8;
            g.fill(rowX, rowY, rowX + rowW, rowY + FUNNEL_BUFFER_ROW_H - 2, 0x88303845);

            int slotX = rowX + 2;
            int slotY = rowY + 2;
            g.fill(slotX, slotY, slotX + 18, slotY + 18, 0xAA1E222A);
            g.renderItem(entry.stack(), slotX + 1, slotY + 1);
            g.drawString(screen.font(), RtsClientUiUtil.trimToWidth(screen.font(), entry.stack().getHoverName().getString(), rowW - 30), rowX + 24, rowY + 3, 0xFFFFFF);
            g.drawString(screen.font(), "x" + RtsClientUiUtil.compactCount(entry.count()), rowX + 24, rowY + 12, 0xFFDFAE);

            if (inside(mouseX, mouseY, rowX, rowY, rowW, FUNNEL_BUFFER_ROW_H - 2)) {
                screen.setHoveredFunnelBufferEntry(entryIndex);
                this.hoveredEntry = entryIndex;
                g.fill(rowX, rowY, rowX + rowW, rowY + FUNNEL_BUFFER_ROW_H - 2, 0x33FFFFFF);
            }
        }

        if (entries.isEmpty()) {
            g.drawString(screen.font(), "empty", panelX + 6, panelY + 20, 0x99B4BCC8);
        }
    }

    public boolean handleClick(double mouseX, double mouseY) {
        if (controller.getMode() != BuilderMode.FUNNEL && controller.getFunnelBufferEntries().isEmpty()) {
            return false;
        }

        int toggleX = screen.width - FUNNEL_BUFFER_TOGGLE_W - 8;
        int toggleY = TOP_H + 6;
        if (inside(mouseX, mouseY, toggleX, toggleY, FUNNEL_BUFFER_TOGGLE_W, FUNNEL_BUFFER_TOGGLE_H)) {
            funnelBufferVisible = !funnelBufferVisible;
            return true;
        }
        if (!funnelBufferVisible) {
            return false;
        }

        int panelX = screen.width - FUNNEL_BUFFER_PANEL_W - 8;
        int panelY = TOP_H + 26;
        int panelH = screen.getFloatingPanelAvailableHeight(panelY);
        if (panelH < 20) {
            return false;
        }
        return inside(mouseX, mouseY, panelX, panelY, FUNNEL_BUFFER_PANEL_W, panelH);
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
