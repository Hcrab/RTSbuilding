package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.layout.QuickBuildPanelLayout;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

public final class QuickBuildPanel {
    private BuilderScreen screen;
    private ClientRtsController controller;
    private boolean quickBuildOpen = true;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        QuickBuildPanelLayout layout = resolveLayout();
        if (layout == null) {
            return;
        }
        int x = layout.x();
        int y = layout.y();
        int panelH = layout.h();
        RtsClientUiUtil.drawPanelFrame(g, x, y, QUICK_BUILD_PANEL_W, panelH, 0xEE161C24, 0xFF6C839A, 0xFF0D1117);
        g.fill(x + 1, y + 1, x + QUICK_BUILD_PANEL_W - 1, y + 20, 0xCC233345);
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.title"), x + 8, y + 6, 0xF2F7FF);

        int shapeTitleY = y + 30;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.shape"), x + 8, shapeTitleY, 0xD8E3EE);
        ClientRtsController.BuildShape[] shapes = {
                ClientRtsController.BuildShape.BLOCK,
                ClientRtsController.BuildShape.LINE,
                ClientRtsController.BuildShape.SQUARE,
                ClientRtsController.BuildShape.WALL,
                ClientRtsController.BuildShape.CIRCLE,
                ClientRtsController.BuildShape.BOX
        };
        for (int i = 0; i < shapes.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = x + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = y + 40 + (row * (QUICK_BUILD_SHAPE_SLOT + 6));
            boolean hover = inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT);
            boolean selected = shapes[i] == this.controller.getBuildShape();
            int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
            RtsClientUiUtil.drawPanelFrame(g, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT, bg, 0xFF647B92, 0xFF0D1117);
            drawShapeTexture(g, shapes[i], selected ? "active" : (hover ? "hover" : "inactive"), slotX, slotY);
        }

        int rightX = x + 88;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.fill"), rightX, shapeTitleY, 0xD8E3EE);
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = y + 42 + (i * 20);
            ShapeFillMode mode = modes.get(i);
            boolean selected = screen.getShapeFillMode() == mode;
            boolean hover = inside(mouseX, mouseY, rightX, rowY, 84, 16);
            int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
            RtsClientUiUtil.drawPanelFrame(g, rightX, rowY, 84, 16, bg, 0xFF647B92, 0xFF0D1117);
            g.fill(rightX + 4, rowY + 4, rightX + 12, rowY + 12, 0xAA111820);
            if (selected) {
                g.fill(rightX + 6, rowY + 6, rightX + 10, rowY + 10, 0xFF78B28C);
            }
            g.drawString(screen.font(), screen.fillModeLabel(mode), rightX + 18, rowY + 4, 0xF2F7FF);
        }

        int rotY = y + 120;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.rotation"), rightX, rotY, 0xD8E3EE);
        RtsClientUiUtil.drawPanelFrame(g, rightX, rotY + 10, 20, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(screen.font(), "-", rightX + 10, rotY + 15, 0xFFFFFF);
        RtsClientUiUtil.drawPanelFrame(g, rightX + 24, rotY + 10, 56, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(screen.font(), screen.getShapeRotateDegrees() + "deg", rightX + 52, rotY + 15, 0xF2F7FF);
        RtsClientUiUtil.drawPanelFrame(g, rightX + 84, rotY + 10, 20, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(screen.font(), "+", rightX + 94, rotY + 15, 0xFFFFFF);

        if (panelH >= QUICK_BUILD_PANEL_H - 20) {
            String materialCost = screen.text("screen.rtsbuilding.quick_build.materials", screen.currentShapeCostText());
            g.drawString(screen.font(), materialCost, x + 8, y + QUICK_BUILD_PANEL_H - 34, 0xB8FFB8);
            g.drawString(screen.font(), "Selection persists automatically", x + 8, y + QUICK_BUILD_PANEL_H - 18, 0xAFC0D3);
        }
    }

    public boolean handleClick(double mouseX, double mouseY) {
        QuickBuildPanelLayout layout = resolveLayout();
        if (layout == null || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        int x = layout.x();
        int y = layout.y();
        ClientRtsController.BuildShape[] shapes = {
                ClientRtsController.BuildShape.BLOCK,
                ClientRtsController.BuildShape.LINE,
                ClientRtsController.BuildShape.SQUARE,
                ClientRtsController.BuildShape.WALL,
                ClientRtsController.BuildShape.CIRCLE,
                ClientRtsController.BuildShape.BOX
        };
        for (int i = 0; i < shapes.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = x + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = y + 40 + (row * (QUICK_BUILD_SHAPE_SLOT + 6));
            if (inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
                this.controller.setBuildShape(shapes[i]);
                screen.ensureFillModeForShape(shapes[i]);
                screen.clearShapeBuildSession();
                screen.persistUiState();
                return true;
            }
        }

        int rightX = x + 88;
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = y + 42 + (i * 20);
            if (inside(mouseX, mouseY, rightX, rowY, 84, 16)) {
                screen.setShapeFillMode(modes.get(i));
                screen.persistUiState();
                return true;
            }
        }

        int rotY = y + 120;
        if (inside(mouseX, mouseY, rightX, rotY + 10, 20, 18)) {
            screen.rotateShapeByStep(-1);
            return true;
        }
        if (inside(mouseX, mouseY, rightX + 84, rotY + 10, 20, 18)) {
            screen.rotateShapeByStep(1);
            return true;
        }
        return true;
    }

    public boolean isQuickBuildOpen() {
        return this.quickBuildOpen;
    }

    public void setQuickBuildOpen(boolean open) {
        this.quickBuildOpen = open;
    }

    public void toggleOpen() {
        this.quickBuildOpen = !this.quickBuildOpen;
    }

    public QuickBuildPanelLayout resolveLayout() {
        if (!this.quickBuildOpen || !screen.hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE)) {
            return null;
        }
        int y = TOP_H + 10;
        int availableH = screen.getFloatingPanelAvailableHeight(y);
        if (availableH < QUICK_BUILD_PANEL_MIN_H) {
            return null;
        }
        int panelH = Math.min(QUICK_BUILD_PANEL_H, availableH);
        int maxX = Math.max(4, screen.width - QUICK_BUILD_PANEL_W - 4);
        int x = Mth.clamp(screen.width - QUICK_BUILD_PANEL_W - 10, 4, maxX);
        return new QuickBuildPanelLayout(x, y, QUICK_BUILD_PANEL_W, panelH);
    }

    private void drawShapeTexture(GuiGraphics g, ClientRtsController.BuildShape shape, String state, int x, int y) {
        ResourceLocation texture = switch (shape) {
            case BLOCK -> "active".equals(state) ? SHAPE_BLOCK_ACTIVE : ("hover".equals(state) ? SHAPE_BLOCK_HOVER : SHAPE_BLOCK_INACTIVE);
            case LINE -> "active".equals(state) ? SHAPE_LINE_ACTIVE : ("hover".equals(state) ? SHAPE_LINE_HOVER : SHAPE_LINE_INACTIVE);
            case SQUARE -> "active".equals(state) ? SHAPE_SQUARE_ACTIVE : ("hover".equals(state) ? SHAPE_SQUARE_HOVER : SHAPE_SQUARE_INACTIVE);
            case WALL -> "active".equals(state) ? SHAPE_WALL_ACTIVE : ("hover".equals(state) ? SHAPE_WALL_HOVER : SHAPE_WALL_INACTIVE);
            case CIRCLE -> "active".equals(state) ? SHAPE_CIRCLE_ACTIVE : ("hover".equals(state) ? SHAPE_CIRCLE_HOVER : SHAPE_CIRCLE_INACTIVE);
            case BOX -> "active".equals(state) ? SHAPE_BOX_ACTIVE : ("hover".equals(state) ? SHAPE_BOX_HOVER : SHAPE_BOX_INACTIVE);
        };
        g.blit(texture, x + 2, y + 2, 0, 0, 28, 28, 32, 32);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
