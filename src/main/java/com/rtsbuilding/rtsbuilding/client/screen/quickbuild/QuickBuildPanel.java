package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * Windowed quick-build panel for shape selection, fill mode, and rotation.
 *
 * <p>The panel keeps the existing Forge quick-build art path and gameplay
 * actions. Only the shell changes: {@link RtsWindowPanel} owns dragging,
 * closing, stacking, and input capture so the panel behaves like the other RTS
 * floating tools.
 */
public final class QuickBuildPanel extends RtsWindowPanel {
    private static final int RIGHT_COL_X = 88;
    private static final int SHAPE_ROW_PITCH = QUICK_BUILD_SHAPE_SLOT + 6;
    private static final int BOTTOM_INFO_H = 30;

    private static final ClientRtsController.BuildShape[] SHAPES = {
            ClientRtsController.BuildShape.BLOCK,
            ClientRtsController.BuildShape.LINE,
            ClientRtsController.BuildShape.SQUARE,
            ClientRtsController.BuildShape.WALL,
            ClientRtsController.BuildShape.CIRCLE,
            ClientRtsController.BuildShape.BOX
    };

    private static final String[] SHAPE_TOOLTIP_KEYS = {
            "screen.rtsbuilding.tooltip.shape_block",
            "screen.rtsbuilding.tooltip.shape_line",
            "screen.rtsbuilding.tooltip.shape_square",
            "screen.rtsbuilding.tooltip.shape_wall",
            "screen.rtsbuilding.tooltip.shape_circle",
            "screen.rtsbuilding.tooltip.shape_box"
    };

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = false;
        setOpen(true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.windowHeight = QUICK_BUILD_PANEL_H + (shouldShowBottomInfo() ? BOTTOM_INFO_H : 0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow()) {
            return;
        }
        for (int i = 0; i < SHAPES.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = this.windowX + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = contentY() + 20 + (row * SHAPE_ROW_PITCH);
            if (inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
                g.renderTooltip(screen.font(), Component.translatable(SHAPE_TOOLTIP_KEYS[i]), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.windowX;
        int bodyY = contentY();
        int shapeTitleY = bodyY + 5;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.shape"),
                x + 10, shapeTitleY, 0xD8E3EE, false);

        for (int i = 0; i < SHAPES.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = x + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = bodyY + 20 + (row * SHAPE_ROW_PITCH);
            boolean selected = SHAPES[i] == this.controller.getBuildShape();
            boolean hover = inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT);
            int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
            RtsClientUiUtil.drawPanelFrame(g, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                    bg, 0xFF647B92, 0xFF0D1117);
            drawShapeTexture(g, SHAPES[i], selected ? "active" : (hover ? "hover" : "inactive"), slotX, slotY);
        }

        int rightX = x + RIGHT_COL_X;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.fill"),
                rightX, shapeTitleY, 0xD8E3EE, false);
        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = bodyY + 20 + (i * 38);
            ShapeBuildTypes.ShapeFillMode mode = modes.get(i);
            boolean selected = screen.getShapeFillMode() == mode;
            boolean hover = inside(mouseX, mouseY, rightX, rowY, 84, 20);
            int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
            RtsClientUiUtil.drawPanelFrame(g, rightX, rowY, 84, 20, bg, 0xFF647B92, 0xFF0D1117);
            g.fill(rightX + 4, rowY + 5, rightX + 12, rowY + 13, 0xAA111820);
            if (selected) {
                g.fill(rightX + 6, rowY + 7, rightX + 10, rowY + 11, 0xFF78B28C);
            }
            g.drawString(screen.font(), screen.fillModeLabel(mode), rightX + 18, rowY + 6, 0xF2F7FF, false);
        }

        int rotY = bodyY + 134;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.rotation"),
                rightX, rotY, 0xD8E3EE, false);
        RtsClientUiUtil.drawPanelFrame(g, rightX, rotY + 10, 20, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), "-", rightX + 10, rotY + 15, 0xFFFFFF);
        RtsClientUiUtil.drawPanelFrame(g, rightX + 24, rotY + 10, 56, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), screen.getShapeRotateDegrees() + "deg",
                rightX + 52, rotY + 15, 0xF2F7FF);
        RtsClientUiUtil.drawPanelFrame(g, rightX + 84, rotY + 10, 20, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), "+", rightX + 94, rotY + 15, 0xFFFFFF);

        if (shouldShowBottomInfo()) {
            int dividerY = this.windowY + QUICK_BUILD_PANEL_H;
            g.fill(x + 6, dividerY - 1, x + this.windowWidth - 6, dividerY, 0xFF647B92);
            int centerY = dividerY + BOTTOM_INFO_H / 2;
            int textY = centerY - screen.font().lineHeight / 2;
            int itemY = centerY - 8;
            String costText = "x " + screen.currentShapeCostText();
            g.drawString(screen.font(), costText, x + 8, textY, 0xB8FFB8, false);
            ItemStack preview = resolveShapeBuildItem();
            int rightEdge = x + 8 + screen.font().width(costText);
            if (!preview.isEmpty()) {
                int itemX = rightEdge + 4;
                g.renderItem(preview, itemX, itemY);
                rightEdge = itemX + 16;
            }
            boolean creative = screen.getMinecraft().player != null && screen.getMinecraft().player.isCreative();
            if (!creative) {
                drawMissingBlocksHint(g, preview, rightEdge, textY, itemY);
            }
        }
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        for (int i = 0; i < SHAPES.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = this.windowX + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = contentY() + 20 + (row * SHAPE_ROW_PITCH);
            if (inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
                this.controller.setBuildShape(SHAPES[i]);
                screen.ensureFillModeForShape(SHAPES[i]);
                screen.clearShapeBuildSession();
                screen.persistUiState();
                return;
            }
        }

        int rightX = this.windowX + RIGHT_COL_X;
        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = contentY() + 20 + (i * 38);
            if (inside(mouseX, mouseY, rightX, rowY, 84, 20)) {
                screen.setShapeFillMode(modes.get(i));
                screen.persistUiState();
                return;
            }
        }

        int rotY = contentY() + 134;
        if (inside(mouseX, mouseY, rightX, rotY + 10, 20, 18)) {
            screen.rotateShapeByStep(-1);
            return;
        }
        if (inside(mouseX, mouseY, rightX + 84, rotY + 10, 20, 18)) {
            screen.rotateShapeByStep(1);
        }
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.quick_build.title");
    }

    @Override
    protected int getDefaultWidth() {
        return QUICK_BUILD_PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return QUICK_BUILD_PANEL_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return QUICK_BUILD_PANEL_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return QUICK_BUILD_PANEL_MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(4, screen.width - QUICK_BUILD_PANEL_W - 10);
        this.windowY = TOP_H + 10;
    }

    @Override
    protected boolean canShowWindow() {
        return super.canShowWindow() && screen.hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE);
    }

    @Override
    protected void onClose() {
        screen.persistUiState();
    }

    public boolean isQuickBuildOpen() {
        return isOpen();
    }

    public void setQuickBuildOpen(boolean open) {
        setOpen(open);
    }

    public PanelLayouts.QuickBuildPanelLayout resolveLayout() {
        if (!isOpen() || !canShowWindow()) {
            return null;
        }
        return new PanelLayouts.QuickBuildPanelLayout(this.windowX, this.windowY, this.windowWidth, this.windowHeight);
    }

    private boolean shouldShowBottomInfo() {
        ItemStack preview = resolveShapeBuildItem();
        return !preview.isEmpty() && preview.getItem() instanceof BlockItem;
    }

    private ItemStack resolveShapeBuildItem() {
        ItemStack selected = controller.getSelectedItemPreview();
        if (!selected.isEmpty()) {
            return selected;
        }
        var mc = screen.getMinecraft();
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        return mc.player.getInventory().getItem(mc.player.getInventory().selected);
    }

    private void drawMissingBlocksHint(GuiGraphics g, ItemStack preview, int rightEdge, int textY, int itemY) {
        String selectedId = controller.getSelectedItemId();
        if (selectedId == null || selectedId.isBlank()) {
            return;
        }
        try {
            long needed = Long.parseLong(screen.currentShapeCostText());
            long available = controller.getStorageTotalCount(selectedId);
            long missing = needed - available;
            if (missing <= 0) {
                return;
            }
            String missText = screen.text("screen.rtsbuilding.quick_build.missing_blocks", missing);
            int missTextX = rightEdge + 8;
            g.drawString(screen.font(), missText, missTextX, textY, 0xFFB8B8, false);
            if (!preview.isEmpty()) {
                g.renderItem(preview, missTextX + screen.font().width(missText) + 4, itemY);
            }
        } catch (NumberFormatException ignored) {
        }
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
