package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * Windowed quick-build panel for Forge 1.20.1.
 *
 * <p>This adapts the mainline Quick Build/Range Destroy workflow to the older
 * Forge UI stack: the panel uses the local RTS window shell and panel-frame
 * drawing helpers, while the gameplay path still flows through
 * {@link ClientRtsController}, {@link com.rtsbuilding.rtsbuilding.client.screen.ScreenShapeController},
 * and the server mining queue.
 */
public final class QuickBuildPanel extends RtsWindowPanel {
    private static final int RIGHT_COL_X = 88;
    private static final int SHAPE_ROW_PITCH = QUICK_BUILD_SHAPE_SLOT + 6;
    private static final int BOTTOM_INFO_H = 30;
    private static final int MODE_TOGGLE_H = 18;
    private static final int MODE_TOGGLE_GAP = 4;
    private static final int SHAPE_TOP = 45;
    private static final int FILL_BUTTON_H = 20;
    private static final int PROTECTION_ROW_H = 18;

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

    private Mode mode = Mode.BUILD;
    private boolean destroyChainSelected = true;
    private boolean toolProtectionExpanded;
    private int chainDestroyLimit = 64;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = false;
        setOpen(true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int base = mode == Mode.DESTROY ? 262 : QUICK_BUILD_PANEL_H;
        this.windowHeight = base + (shouldShowBottomInfo() ? BOTTOM_INFO_H : 0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow()) {
            return;
        }
        if (mode == Mode.DESTROY && inside(mouseX, mouseY, shapeSlotX(0), shapeSlotY(0), QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
            g.renderTooltip(screen.font(), Component.translatable("screen.rtsbuilding.tooltip.shape_chain"), mouseX, mouseY);
            return;
        }
        int offset = mode == Mode.DESTROY ? 1 : 0;
        for (int i = 0; i < SHAPES.length; i++) {
            int slotIndex = i + offset;
            int slotX = shapeSlotX(slotIndex);
            int slotY = shapeSlotY(slotIndex);
            if (inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
                g.renderTooltip(screen.font(), Component.translatable(SHAPE_TOOLTIP_KEYS[i]), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderModeButtons(g, mouseX, mouseY);
        renderShapeSection(g, mouseX, mouseY);
        renderRightSection(g, mouseX, mouseY);
        renderBottomInfo(g);
    }

    private void renderModeButtons(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.windowX + 8;
        int y = contentY() + 5;
        int w = (this.windowWidth - 20 - MODE_TOGGLE_GAP) / 2;
        renderButton(g, mouseX, mouseY, x, y, w, MODE_TOGGLE_H,
                screen.text("screen.rtsbuilding.quick_build.mode_build"), mode == Mode.BUILD);
        renderButton(g, mouseX, mouseY, x + w + MODE_TOGGLE_GAP, y, w, MODE_TOGGLE_H,
                screen.text("screen.rtsbuilding.quick_build.mode_destroy"), mode == Mode.DESTROY);
    }

    private void renderShapeSection(GuiGraphics g, int mouseX, int mouseY) {
        int titleY = contentY() + 30;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.shape"),
                this.windowX + 10, titleY, 0xD8E3EE, false);

        int index = 0;
        if (mode == Mode.DESTROY) {
            renderChainShapeButton(g, mouseX, mouseY, index++);
        }
        for (ClientRtsController.BuildShape shape : SHAPES) {
            int slotX = shapeSlotX(index);
            int slotY = shapeSlotY(index);
            boolean selected = !isRangeDestroyChainMode() && shape == this.controller.getBuildShape();
            boolean hover = inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT);
            int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
            RtsClientUiUtil.drawPanelFrame(g, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                    bg, 0xFF647B92, 0xFF0D1117);
            drawShapeTexture(g, shape, selected ? "active" : (hover ? "hover" : "inactive"), slotX, slotY);
            index++;
        }
    }

    private void renderChainShapeButton(GuiGraphics g, int mouseX, int mouseY, int index) {
        int slotX = shapeSlotX(index);
        int slotY = shapeSlotY(index);
        boolean selected = isRangeDestroyChainMode();
        boolean hover = inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT);
        int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
        RtsClientUiUtil.drawPanelFrame(g, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                bg, 0xFF647B92, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), "C", slotX + QUICK_BUILD_SHAPE_SLOT / 2, slotY + 12,
                selected ? 0xE8FFE8 : 0xD8E3EE);
    }

    private void renderRightSection(GuiGraphics g, int mouseX, int mouseY) {
        int rightX = this.windowX + RIGHT_COL_X;
        int titleY = contentY() + 30;
        if (isRangeDestroyChainMode()) {
            renderChainLimitControls(g, mouseX, mouseY, rightX, titleY);
            renderProtectionControls(g, mouseX, mouseY, rightX, titleY + 72);
            return;
        }
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.fill"),
                rightX, titleY, 0xD8E3EE, false);
        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = contentY() + SHAPE_TOP + (i * FILL_BUTTON_H);
            ShapeBuildTypes.ShapeFillMode fillMode = modes.get(i);
            boolean selected = screen.getShapeFillMode() == fillMode;
            renderFillModeButton(g, mouseX, mouseY, rightX, rowY, fillMode, selected);
        }
        int nextY = contentY() + SHAPE_TOP + (modes.size() * FILL_BUTTON_H) + 10;
        if (mode == Mode.DESTROY) {
            renderProtectionControls(g, mouseX, mouseY, rightX, nextY);
        } else {
            renderRotationControls(g, mouseX, mouseY, rightX, contentY() + 134);
        }
    }

    private void renderFillModeButton(GuiGraphics g, int mouseX, int mouseY, int x, int y,
            ShapeBuildTypes.ShapeFillMode fillMode, boolean selected) {
        boolean hover = inside(mouseX, mouseY, x, y, 84, FILL_BUTTON_H);
        int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
        RtsClientUiUtil.drawPanelFrame(g, x, y, 84, FILL_BUTTON_H, bg, 0xFF647B92, 0xFF0D1117);
        g.fill(x + 4, y + 5, x + 12, y + 13, 0xAA111820);
        if (selected) {
            g.fill(x + 6, y + 7, x + 10, y + 11, 0xFF78B28C);
        }
        g.drawString(screen.font(), screen.fillModeLabel(fillMode), x + 18, y + 6, 0xF2F7FF, false);
    }

    private void renderChainLimitControls(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.ultimine.limit"), x, y, 0xD8E3EE, false);
        renderButton(g, mouseX, mouseY, x, y + 16, 20, 18, "-", false);
        RtsClientUiUtil.drawPanelFrame(g, x + 24, y + 16, 48, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), Integer.toString(this.chainDestroyLimit), x + 48, y + 21, 0xF2F7FF);
        renderButton(g, mouseX, mouseY, x + 76, y + 16, 20, 18, "+", false);
        int processed = controller.getUltimineProgressProcessed();
        int total = controller.getUltimineProgressTotal();
        int barY = y + 42;
        RtsClientUiUtil.drawPanelFrame(g, x, barY, 96, 10, 0xAA101820, 0xFF647B92, 0xFF0D1117);
        if (processed >= 0 && total > 0) {
            int fillW = Mth.clamp((int) Math.round((processed / (double) total) * 94.0D), 1, 94);
            g.fill(x + 1, barY + 1, x + 1 + fillW, barY + 9, 0xFF78B28C);
        }
    }

    private void renderProtectionControls(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        renderButton(g, mouseX, mouseY, x, y, 96, PROTECTION_ROW_H,
                (this.toolProtectionExpanded ? "v " : "> ") + screen.text("screen.rtsbuilding.quick_build.protection_settings"),
                false);
        if (!this.toolProtectionExpanded) {
            return;
        }
        renderButton(g, mouseX, mouseY, x, y + 22, 96, PROTECTION_ROW_H,
                toggleLabel(controller.isRangeDestroyToolProtectionEnabled(), "screen.rtsbuilding.quick_build.tool_protection"),
                controller.isRangeDestroyToolProtectionEnabled());
        renderButton(g, mouseX, mouseY, x, y + 44, 96, PROTECTION_ROW_H,
                toggleLabel(controller.isRangeDestroyToolReplacementEnabled(), "screen.rtsbuilding.quick_build.tool_replacement"),
                controller.isRangeDestroyToolReplacementEnabled());
    }

    private void renderRotationControls(GuiGraphics g, int mouseX, int mouseY, int rightX, int rotY) {
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.rotation"),
                rightX, rotY, 0xD8E3EE, false);
        renderButton(g, mouseX, mouseY, rightX, rotY + 10, 20, 18, "-", false);
        RtsClientUiUtil.drawPanelFrame(g, rightX + 24, rotY + 10, 56, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), screen.getShapeRotateDegrees() + "deg",
                rightX + 52, rotY + 15, 0xF2F7FF);
        renderButton(g, mouseX, mouseY, rightX + 84, rotY + 10, 20, 18, "+", false);
    }

    private void renderButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
        int text = active ? 0xE8FFE8 : 0xF2F7FF;
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, active ? 0xFF5FE36C : 0xFF647B92, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label, x + w / 2, y + (h - screen.font().lineHeight) / 2, text);
    }

    private void renderBottomInfo(GuiGraphics g) {
        if (!shouldShowBottomInfo()) {
            return;
        }
        int x = this.windowX;
        int dividerY = this.windowY + (mode == Mode.DESTROY ? 262 : QUICK_BUILD_PANEL_H);
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

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        int modeX = this.windowX + 8;
        int modeY = contentY() + 5;
        int modeW = (this.windowWidth - 20 - MODE_TOGGLE_GAP) / 2;
        if (inside(mouseX, mouseY, modeX, modeY, modeW, MODE_TOGGLE_H)) {
            this.mode = Mode.BUILD;
            screen.clearShapeBuildSession();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, mouseY, modeX + modeW + MODE_TOGGLE_GAP, modeY, modeW, MODE_TOGGLE_H)) {
            this.mode = Mode.DESTROY;
            if (this.destroyChainSelected) {
                screen.clearShapeBuildSession();
            }
            screen.persistUiState();
            return;
        }

        int offset = mode == Mode.DESTROY ? 1 : 0;
        if (mode == Mode.DESTROY && inside(mouseX, mouseY, shapeSlotX(0), shapeSlotY(0), QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
            this.destroyChainSelected = true;
            screen.clearShapeBuildSession();
            screen.persistUiState();
            return;
        }
        for (int i = 0; i < SHAPES.length; i++) {
            int slotIndex = i + offset;
            if (inside(mouseX, mouseY, shapeSlotX(slotIndex), shapeSlotY(slotIndex), QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
                this.destroyChainSelected = false;
                this.controller.setBuildShape(SHAPES[i]);
                screen.ensureFillModeForShape(SHAPES[i]);
                screen.clearShapeBuildSession();
                screen.persistUiState();
                return;
            }
        }

        int rightX = this.windowX + RIGHT_COL_X;
        if (isRangeDestroyChainMode()) {
            int y = contentY() + 30;
            if (inside(mouseX, mouseY, rightX, y + 16, 20, 18)) {
                setChainDestroyLimit(this.chainDestroyLimit - 1);
                screen.persistUiState();
                return;
            }
            if (inside(mouseX, mouseY, rightX + 76, y + 16, 20, 18)) {
                setChainDestroyLimit(this.chainDestroyLimit + 1);
                screen.persistUiState();
                return;
            }
            handleProtectionClick(mouseX, mouseY, rightX, y + 72);
            return;
        }

        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = contentY() + SHAPE_TOP + (i * FILL_BUTTON_H);
            if (inside(mouseX, mouseY, rightX, rowY, 84, FILL_BUTTON_H)) {
                screen.setShapeFillMode(modes.get(i));
                screen.persistUiState();
                return;
            }
        }
        if (mode == Mode.DESTROY) {
            handleProtectionClick(mouseX, mouseY, rightX, contentY() + SHAPE_TOP + (modes.size() * FILL_BUTTON_H) + 10);
            return;
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

    private void handleProtectionClick(double mouseX, double mouseY, int x, int y) {
        if (inside(mouseX, mouseY, x, y, 96, PROTECTION_ROW_H)) {
            this.toolProtectionExpanded = !this.toolProtectionExpanded;
            screen.persistUiState();
            return;
        }
        if (!this.toolProtectionExpanded) {
            return;
        }
        if (inside(mouseX, mouseY, x, y + 22, 96, PROTECTION_ROW_H)) {
            controller.setRangeDestroyToolProtectionEnabled(!controller.isRangeDestroyToolProtectionEnabled());
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, mouseY, x, y + 44, 96, PROTECTION_ROW_H)) {
            controller.setRangeDestroyToolReplacementEnabled(!controller.isRangeDestroyToolReplacementEnabled());
            screen.persistUiState();
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

    public boolean isRangeDestroyMode() {
        return this.mode == Mode.DESTROY;
    }

    public boolean isRangeDestroyChainMode() {
        return isRangeDestroyMode() && this.destroyChainSelected;
    }

    public String getQuickBuildModeName() {
        return this.mode.name();
    }

    public void setQuickBuildModeName(String modeName) {
        try {
            this.mode = Mode.valueOf(modeName == null ? Mode.BUILD.name() : modeName.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            this.mode = Mode.BUILD;
        }
    }

    public boolean isDestroyChainSelected() {
        return this.destroyChainSelected;
    }

    public void setDestroyChainSelected(boolean selected) {
        this.destroyChainSelected = selected;
    }

    public int getChainDestroyLimit() {
        return this.chainDestroyLimit;
    }

    public void setChainDestroyLimit(int limit) {
        this.chainDestroyLimit = Mth.clamp(limit, ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT);
    }

    public PanelLayouts.QuickBuildPanelLayout resolveLayout() {
        if (!isOpen() || !canShowWindow()) {
            return null;
        }
        return new PanelLayouts.QuickBuildPanelLayout(this.windowX, this.windowY, this.windowWidth, this.windowHeight);
    }

    private int shapeSlotX(int index) {
        int col = index % 2;
        return this.windowX + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
    }

    private int shapeSlotY(int index) {
        int row = index / 2;
        return contentY() + SHAPE_TOP + (row * SHAPE_ROW_PITCH);
    }

    private String toggleLabel(boolean active, String key) {
        return (active ? "[x] " : "[ ] ") + screen.text(key);
    }

    private boolean shouldShowBottomInfo() {
        if (mode == Mode.DESTROY) {
            return false;
        }
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

    private enum Mode {
        BUILD,
        DESTROY
    }
}
