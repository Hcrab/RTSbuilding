package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * 形状轮盘面板。
 * <p>
 * 独立的形状轮盘面板组件，处理形状轮盘的渲染、输入和状态管理。
 * 由 {@link BuilderScreen} 统一调度生命周期。
 */
public final class ShapeWheelPanel {

    private boolean open = false;
    private int centerX = 0;
    private int centerY = 0;
    private boolean openedByAlt = false;

    private BuilderScreen screen;
    private ClientRtsController controller;

    // ── Shape wheel slot record (internal data carrier) ──

    /**
     * A single slot in the shape wheel.
     * Maps a {@link ClientRtsController.BuildShape} to its on-screen position.
     */
    private record Slot(ClientRtsController.BuildShape shape, int x, int y) {}

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ── 公开查询方法 ──

    public boolean isOpen() {
        return this.open;
    }

    public boolean isOpenedByAlt() {
        return this.openedByAlt;
    }

    public void open(double mouseX, double mouseY, boolean byAlt) {
        this.open = true;
        this.openedByAlt = byAlt;
        int minX = SHAPE_WHEEL_RADIUS + SHAPE_WHEEL_SLOT;
        int maxX = Math.max(minX, screen.width - SHAPE_WHEEL_RADIUS - SHAPE_WHEEL_SLOT);
        int minY = TOP_H + SHAPE_WHEEL_RADIUS + SHAPE_WHEEL_SLOT;
        int maxY = Math.max(minY, screen.getBottomY() - SHAPE_WHEEL_RADIUS - SHAPE_WHEEL_SLOT);
        this.centerX = Mth.clamp((int) Math.round(mouseX), minX, maxX);
        this.centerY = Mth.clamp((int) Math.round(mouseY), minY, maxY);
    }

    public void close() {
        this.open = false;
        this.openedByAlt = false;
    }

    // ── 输入方法 ──

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ClientRtsController.BuildShape picked = resolveOption(mouseX, mouseY);
            if (picked != null) {
                this.controller.setBuildShape(picked);
                ensureFillModeForShape(picked);
                screen.clearShapeBuildSession();
                screen.persistUiState();
            }
            close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            close();
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(double scrollY) {
        if (!this.open) {
            return false;
        }
        if (scrollY > 0.0D) {
            this.controller.cycleBuildShape(-1);
        } else if (scrollY < 0.0D) {
            this.controller.cycleBuildShape(1);
        }
        ensureFillModeForShape(this.controller.getBuildShape());
        screen.clearShapeBuildSession();
        screen.persistUiState();
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!this.open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        ClientRtsController.BuildShape shape = shapeFromNumberKey(keyCode);
        if (shape != null) {
            this.controller.setBuildShape(shape);
            ensureFillModeForShape(this.controller.getBuildShape());
            screen.clearShapeBuildSession();
            close();
            screen.persistUiState();
            return true;
        }
        return true;
    }

    /**
     * 处理 Alt 拖出形状轮盘的生命周期。
     * 返回 true 表示形状已选中，调用者应关闭轮盘。
     */
    public boolean handleAltRelease(double mouseX, double mouseY) {
        if (!this.openedByAlt || !this.open) {
            return false;
        }
        ClientRtsController.BuildShape picked = resolveOption(mouseX, mouseY);
        if (picked != null) {
            this.controller.setBuildShape(picked);
            ensureFillModeForShape(picked);
            screen.clearShapeBuildSession();
        }
        close();
        return true;
    }

    // ── 渲染 ──

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open) {
            return;
        }
        g.fill(0, TOP_H, screen.width, screen.getBottomY(), 0x55000000);
        int ringR = SHAPE_WHEEL_RADIUS + 16;
        g.fill(
                this.centerX - ringR,
                this.centerY - ringR,
                this.centerX + ringR,
                this.centerY + ringR,
                0x77141920);
        g.fill(
                this.centerX - 22,
                this.centerY - 22,
                this.centerX + 22,
                this.centerY + 22,
                0xAA0B1016);

        Font font = screen.font();
        for (Slot slot : collectSlots()) {
            int x = slot.x();
            int y = slot.y();
            boolean hover = isInside(mouseX, mouseY, x, y, SHAPE_WHEEL_SLOT, SHAPE_WHEEL_SLOT);
            boolean selected = slot.shape() == this.controller.getBuildShape();
            int bg = selected ? 0xCC3A6E57 : (hover ? 0xCC385065 : 0xAA1B232E);
            g.fill(x, y, x + SHAPE_WHEEL_SLOT, y + SHAPE_WHEEL_SLOT, bg);
            g.hLine(x, x + SHAPE_WHEEL_SLOT, y, 0xFF5B7085);
            g.hLine(x, x + SHAPE_WHEEL_SLOT, y + SHAPE_WHEEL_SLOT, 0xFF0C0F13);
            g.vLine(x, y, y + SHAPE_WHEEL_SLOT, 0xFF5B7085);
            g.vLine(x + SHAPE_WHEEL_SLOT, y, y + SHAPE_WHEEL_SLOT, 0xFF0C0F13);
            g.drawCenteredString(font, shapeShortLabel(slot.shape()), x + SHAPE_WHEEL_SLOT / 2, y + 7, 0xFFFFFF);
        }

        g.drawCenteredString(font, "Build Shape", this.centerX, this.centerY - 10, 0xEAF5FF);
        g.drawCenteredString(font, "ALT release/LMB: select   Wheel/1-5: cycle",
                this.centerX, this.centerY + 30, 0xB7CDE2);

        ClientRtsController.BuildShape hover = resolveOption(mouseX, mouseY);
        if (hover != null) {
            g.drawCenteredString(font, screen.shapeLabel(hover), this.centerX, this.centerY + 42, 0xFFFFFF);
        }
    }

    // ── 内部方法 ──

    private List<Slot> collectSlots() {
        List<Slot> slots = new ArrayList<>(6);
        ClientRtsController.BuildShape[] shapes = new ClientRtsController.BuildShape[] {
                ClientRtsController.BuildShape.BLOCK,
                ClientRtsController.BuildShape.LINE,
                ClientRtsController.BuildShape.SQUARE,
                ClientRtsController.BuildShape.WALL,
                ClientRtsController.BuildShape.CIRCLE,
                ClientRtsController.BuildShape.BOX
        };
        for (int i = 0; i < shapes.length; i++) {
            double angle = (-Math.PI / 2.0D) + ((Math.PI * 2.0D) * (i / (double) shapes.length));
            int cx = this.centerX + (int) Math.round(Math.cos(angle) * SHAPE_WHEEL_RADIUS);
            int cy = this.centerY + (int) Math.round(Math.sin(angle) * SHAPE_WHEEL_RADIUS);
            slots.add(new Slot(
                    shapes[i],
                    cx - (SHAPE_WHEEL_SLOT / 2),
                    cy - (SHAPE_WHEEL_SLOT / 2)));
        }
        return slots;
    }

    public ClientRtsController.BuildShape resolveOption(double mouseX, double mouseY) {
        for (Slot slot : collectSlots()) {
            if (isInside(mouseX, mouseY, slot.x(), slot.y(), SHAPE_WHEEL_SLOT, SHAPE_WHEEL_SLOT)) {
                return slot.shape();
            }
        }
        return null;
    }

    private static ClientRtsController.BuildShape shapeFromNumberKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1 -> ClientRtsController.BuildShape.BLOCK;
            case GLFW.GLFW_KEY_2 -> ClientRtsController.BuildShape.LINE;
            case GLFW.GLFW_KEY_3 -> ClientRtsController.BuildShape.SQUARE;
            case GLFW.GLFW_KEY_4 -> ClientRtsController.BuildShape.WALL;
            case GLFW.GLFW_KEY_5 -> ClientRtsController.BuildShape.CIRCLE;
            case GLFW.GLFW_KEY_6 -> ClientRtsController.BuildShape.BOX;
            default -> null;
        };
    }

    private static String shapeShortLabel(ClientRtsController.BuildShape shape) {
        if (shape == null) {
            return "B";
        }
        return switch (shape) {
            case BLOCK -> "B";
            case LINE -> "L";
            case SQUARE -> "SQ";
            case WALL -> "W";
            case CIRCLE -> "C";
            case BOX -> "CU";
        };
    }

    private void ensureFillModeForShape(ClientRtsController.BuildShape shape) {
        screen.ensureFillModeForShape(shape);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
