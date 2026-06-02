package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

public final class ShapeContextPanel {
    private BuilderScreen screen;
    private ClientRtsController controller;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            return;
        }
        screen.ensureFillModeForShape(shape);

        int panelX = screen.width - SHAPE_CONTEXT_PANEL_W - SHAPE_CONTEXT_PANEL_X_MARGIN;
        int panelY = SHAPE_CONTEXT_PANEL_Y;
        int panelW = SHAPE_CONTEXT_PANEL_W;
        int panelH = 122;
        if (screen.getFloatingPanelAvailableHeight(panelY) < panelH) {
            return;
        }

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA111820);
        g.hLine(panelX, panelX + panelW, panelY, 0xFF5B7085);
        g.hLine(panelX, panelX + panelW, panelY + panelH, 0xFF0C0F13);
        g.vLine(panelX, panelY, panelY + panelH, 0xFF5B7085);
        g.vLine(panelX + panelW, panelY, panelY + panelH, 0xFF0C0F13);

        g.drawString(screen.font(), "Shape Context", panelX + 8, panelY + 6, 0xEAF5FF);
        g.drawString(screen.font(), BuilderScreen.shapeDimensionLabel(shape) + " Shape", panelX + 8, panelY + 18, 0xA9C7E8);
        g.drawString(screen.font(), "Fill Attribute", panelX + 8, panelY + 30, 0xA9C7E8);

        int rowY = panelY + 42;
        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        for (ShapeBuildTypes.ShapeFillMode mode : modes) {
            boolean selected = mode == screen.getShapeFillMode();
            boolean hover = inside(mouseX, mouseY, panelX + 8, rowY, panelW - 16, SHAPE_CONTEXT_ROW_H);
            int bg = selected ? 0xAA2E6A50 : (hover ? 0x88334A5F : 0x66303A45);
            g.fill(panelX + 8, rowY, panelX + panelW - 8, rowY + SHAPE_CONTEXT_ROW_H, bg);
            g.drawString(screen.font(), screen.fillModeLabel(mode), panelX + 12, rowY + 3, selected ? 0xFFFFFF : 0xDCE7F3);
            rowY += SHAPE_CONTEXT_ROW_H + 3;
        }

        g.drawString(screen.font(), "Size: " + screen.currentShapeSizeText(), panelX + 8, panelY + panelH - 48, 0xB8FFB8);
        g.drawString(screen.font(), "Cost: " + screen.currentShapeCostText() + " blocks", panelX + 8, panelY + panelH - 36, 0xB8FFB8);
        g.drawString(screen.font(), "ALT Hold: Shape Radial", panelX + 8, panelY + panelH - 24, 0xB7CDE2);
        g.drawString(screen.font(), "Ctrl+Z / Ctrl+Y", panelX + 8, panelY + panelH - 13, 0xB7CDE2);

        int leftX = SHAPE_CONTEXT_PANEL_X_MARGIN;
        int leftW = 180;
        int leftH = 66;
        g.fill(leftX, panelY, leftX + leftW, panelY + leftH, 0x99101822);
        g.hLine(leftX, leftX + leftW, panelY, 0xFF4E6075);
        g.hLine(leftX, leftX + leftW, panelY + leftH, 0xFF0C0F13);
        g.vLine(leftX, panelY, panelY + leftH, 0xFF4E6075);
        g.vLine(leftX + leftW, panelY, panelY + leftH, 0xFF0C0F13);
        g.drawString(screen.font(), "Build Flow", leftX + 8, panelY + 6, 0xEAF5FF);
        g.drawString(screen.font(), "A -> B -> (C for Cube) -> Confirm", leftX + 8, panelY + 20, 0xC7D8EA);
        g.drawString(screen.font(), screen.pendingShapeStatusText(), leftX + 8, panelY + 34, 0xB8FFB8);
        g.drawString(screen.font(), "Rot/Fill updates preview live", leftX + 8, panelY + 48, 0xB7CDE2);
    }

    public boolean handleClick(double mouseX, double mouseY) {
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            return false;
        }
        screen.ensureFillModeForShape(shape);
        int panelX = screen.width - SHAPE_CONTEXT_PANEL_W - SHAPE_CONTEXT_PANEL_X_MARGIN;
        int panelY = SHAPE_CONTEXT_PANEL_Y;
        int panelW = SHAPE_CONTEXT_PANEL_W;
        int panelH = 122;
        if (screen.getFloatingPanelAvailableHeight(panelY) < panelH) {
            return false;
        }
        if (!inside(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            return false;
        }

        int rowY = panelY + 42;
        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        for (ShapeBuildTypes.ShapeFillMode mode : modes) {
            if (inside(mouseX, mouseY, panelX + 8, rowY, panelW - 16, SHAPE_CONTEXT_ROW_H)) {
                screen.setShapeFillMode(mode);
                return true;
            }
            rowY += SHAPE_CONTEXT_ROW_H + 3;
        }
        return true;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
