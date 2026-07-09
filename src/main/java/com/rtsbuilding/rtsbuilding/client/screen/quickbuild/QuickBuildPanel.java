package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;


import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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
    private static final int BOTTOM_INFO_H = 52;
    private static final int BOTTOM_TEXT_MAX_LINES = 3;
    private static final int MODE_TOGGLE_H = 18;
    private static final int MODE_TOGGLE_GAP = 4;
    private static final int SHAPE_TOP = 45;
    private static final int FILL_BUTTON_H = 20;
    private static final ResourceLocation SELECTION_DOT_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/general/mode_button.png");
    private static final int SHAPE_SHEET_W = 450;
    private static final int SHAPE_SHEET_H = 900;
    private static final int SHAPE_STATE_H = 450;
    private static final int CHAIN_SHEET_W = 450;
    private static final int CHAIN_SHEET_H = 900;
    private static final int CHAIN_STATE_H = 450;
    private static final int MODE_BUTTON_SHEET_W = 512;
    private static final int MODE_BUTTON_STATE_H = 512;
    private static final int MODE_BUTTON_SHEET_H = MODE_BUTTON_STATE_H * 3;

    private static final ClientRtsController.BuildShape[] SHAPES = {
            ClientRtsController.BuildShape.BLOCK,
            ClientRtsController.BuildShape.LINE,
            ClientRtsController.BuildShape.SQUARE,
            ClientRtsController.BuildShape.WALL,
            ClientRtsController.BuildShape.CIRCLE,
            ClientRtsController.BuildShape.CYLINDER,
            ClientRtsController.BuildShape.BALL,
            ClientRtsController.BuildShape.BOX
    };

    private static final String[] SHAPE_TOOLTIP_KEYS = {
            "screen.rtsbuilding.tooltip.shape_block",
            "screen.rtsbuilding.tooltip.shape_line",
            "screen.rtsbuilding.tooltip.shape_square",
            "screen.rtsbuilding.tooltip.shape_wall",
            "screen.rtsbuilding.tooltip.shape_circle",
            "screen.rtsbuilding.tooltip.shape_cylinder",
            "screen.rtsbuilding.tooltip.shape_ball",
            "screen.rtsbuilding.tooltip.shape_box"
    };

    private Mode mode = Mode.BUILD;
    private boolean destroyChainSelected = true;
    private int chainDestroyLimit = 64;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = false;
        setOpen(true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.windowHeight = currentBasePanelHeight() + (shouldShowBottomInfo() ? BOTTOM_INFO_H : 0);
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
        int w = modeButtonWidth();
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
        int vOffset = selected || hover ? CHAIN_STATE_H : 0;
        RtsTextureRenderer.drawTextureHighPrecision(
                g,
                QUICK_BUILD_CHAIN_BLOCK,
                slotX + 2,
                slotY + 2,
                QUICK_BUILD_SHAPE_SLOT - 4,
                QUICK_BUILD_SHAPE_SLOT - 4,
                0,
                vOffset,
                CHAIN_SHEET_W,
                CHAIN_STATE_H,
                CHAIN_SHEET_W,
                CHAIN_SHEET_H,
                0,
                0xFFFFFFFF);
    }

    private void renderRightSection(GuiGraphics g, int mouseX, int mouseY) {
        int rightX = this.windowX + RIGHT_COL_X;
        int titleY = contentY() + 30;
        if (isRangeDestroyChainMode()) {
            renderChainLimitControls(g, mouseX, mouseY, rightX, titleY);
            return;
        }
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.fill"),
                rightX, titleY, 0xD8E3EE, false);
        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = contentY() + SHAPE_TOP + (i * SHAPE_ROW_PITCH);
            ShapeBuildTypes.ShapeFillMode fillMode = modes.get(i);
            boolean selected = screen.getShapeFillMode() == fillMode;
            renderFillModeButton(g, mouseX, mouseY, rightX, rowY, fillMode, selected);
        }
        int nextY = contentY() + SHAPE_TOP + (modes.size() * SHAPE_ROW_PITCH);
        if (isLineOrWallShape()) {
            renderConnectToggle(g, mouseX, mouseY, rightX, nextY);
        }
    }

    private void renderConnectToggle(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        boolean connected = screen.getShapeController().isLineConnected();
        boolean hover = inside(mouseX, mouseY, x, y, 84, FILL_BUTTON_H);
        renderButton(g, mouseX, mouseY, x, y, 84, FILL_BUTTON_H,
                screen.text("screen.rtsbuilding.quick_build.connect"),
                connected);
        renderSelectionDot(g, x + 2, y + 2, connected, hover);
    }

    private void renderFillModeButton(GuiGraphics g, int mouseX, int mouseY, int x, int y,
            ShapeBuildTypes.ShapeFillMode fillMode, boolean selected) {
        boolean hover = inside(mouseX, mouseY, x, y, 84, FILL_BUTTON_H);
        int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
        RtsClientUiUtil.drawPanelFrame(g, x, y, 84, FILL_BUTTON_H, bg, 0xFF647B92, 0xFF0D1117);
        renderSelectionDot(g, x + 2, y + 2, selected, hover);
        g.drawString(screen.font(), screen.fillModeLabel(fillMode), x + 22, y + 6, 0xF2F7FF, false);
    }

    private void renderSelectionDot(GuiGraphics g, int x, int y, boolean selected, boolean hover) {
        int vOffset = selected ? MODE_BUTTON_STATE_H * 2 : (hover ? MODE_BUTTON_STATE_H : 0);
        RtsTextureRenderer.drawTextureHighPrecision(
                g,
                SELECTION_DOT_TEXTURE,
                x,
                y,
                16,
                16,
                0,
                vOffset,
                MODE_BUTTON_SHEET_W,
                MODE_BUTTON_STATE_H,
                MODE_BUTTON_SHEET_W,
                MODE_BUTTON_SHEET_H,
                0,
                0xFFFFFFFF);
    }

    private void renderChainLimitControls(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.chain_limit_label"), x, y, 0xD8E3EE, false);
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
        int dividerY = this.windowY + currentBasePanelHeight();
        g.fill(x + 6, dividerY - 1, x + this.windowWidth - 6, dividerY, 0xFF647B92);
        int textY = dividerY + 12;
        int itemY = textY - 4;
        if (mode == Mode.DESTROY) {
            String hintKey = isRangeDestroyChainMode()
                    ? "screen.rtsbuilding.quick_build.chain_hint"
                    : "screen.rtsbuilding.quick_build.destroy_hint";
            renderBottomInfoText(g, Component.translatable(hintKey, confirmKeyLabel(true)),
                    x + 8, textY, this.windowWidth - 16, 0xFFB8B8);
            return;
        }
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
        renderBottomInfoText(g,
                Component.translatable("screen.rtsbuilding.quick_build.build_hint", confirmKeyLabel(false)),
                x + 8,
                textY + screen.font().lineHeight + 3,
                this.windowWidth - 16,
                0xFFD8E8FF);
    }

    private void renderBottomInfoText(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = screen.font().split(text, Math.max(1, maxWidth));
        int lineCount = Math.min(BOTTOM_TEXT_MAX_LINES, lines.size());
        for (int i = 0; i < lineCount; i++) {
            g.drawString(screen.font(), lines.get(i), x, y + i * screen.font().lineHeight, color, false);
        }
    }

    private String confirmKeyLabel(boolean destroyMode) {
        return (destroyMode ? ClientKeyMappings.CONFIRM_BATCH_DESTROY : ClientKeyMappings.CONFIRM_BATCH_PLACE)
                .getTranslatedKeyMessage()
                .getString();
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        int modeX = this.windowX + 8;
        int modeY = contentY() + 5;
        int modeW = modeButtonWidth();
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
            return;
        }

        List<ShapeBuildTypes.ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = contentY() + SHAPE_TOP + (i * SHAPE_ROW_PITCH);
            if (inside(mouseX, mouseY, rightX, rowY, 84, FILL_BUTTON_H)) {
                screen.setShapeFillMode(modes.get(i));
                screen.persistUiState();
                return;
            }
        }
        int connectY = contentY() + SHAPE_TOP + (modes.size() * SHAPE_ROW_PITCH);
        if (isLineOrWallShape() && inside(mouseX, mouseY, rightX, connectY, 84, FILL_BUTTON_H)) {
            screen.getShapeController().setLineConnected(!screen.getShapeController().isLineConnected());
            screen.persistUiState();
            return;
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
        return super.canShowWindow() && screen.canUseQuickBuild();
    }

    @Override
    protected void onClose() {
        restoreSingleBlockCursor();
        screen.persistUiState();
    }

    private void restoreSingleBlockCursor() {
        if (this.controller != null) {
            this.controller.setBuildShape(ClientRtsController.BuildShape.BLOCK);
        }
        if (this.screen != null) {
            this.screen.clearShapeBuildSession();
        }
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

    private int currentBasePanelHeight() {
        return mode == Mode.DESTROY ? QUICK_BUILD_PANEL_H + SHAPE_ROW_PITCH : QUICK_BUILD_PANEL_H;
    }

    private int modeButtonWidth() {
        return (this.windowWidth - 16 - MODE_TOGGLE_GAP) / 2;
    }

    private boolean shouldShowBottomInfo() {
        if (mode == Mode.DESTROY) {
            return true;
        }
        ItemStack preview = resolveShapeBuildItem();
        return !preview.isEmpty() && preview.getItem() instanceof BlockItem;
    }

    private boolean isLineOrWallShape() {
        return this.controller.getBuildShape() == ClientRtsController.BuildShape.LINE
                || this.controller.getBuildShape() == ClientRtsController.BuildShape.WALL;
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
            case BLOCK -> QUICK_BUILD_SINGLE_BLOCK;
            case LINE -> QUICK_BUILD_LINE_BLOCK;
            case SQUARE -> QUICK_BUILD_SQUARE_BLOCK;
            case WALL -> QUICK_BUILD_WALL_BLOCK;
            case CIRCLE -> QUICK_BUILD_CIRCLE_BLOCK;
            case CYLINDER -> QUICK_BUILD_CYLINDER_BLOCK;
            case BALL -> QUICK_BUILD_BALL_BLOCK;
            case BOX -> QUICK_BUILD_BOX_BLOCK;
        };
        int vOffset = "active".equals(state) || "hover".equals(state) ? SHAPE_STATE_H : 0;
        RtsTextureRenderer.drawTextureHighPrecision(
                g,
                texture,
                x + 2,
                y + 2,
                QUICK_BUILD_SHAPE_SLOT - 4,
                QUICK_BUILD_SHAPE_SLOT - 4,
                0,
                vOffset,
                SHAPE_SHEET_W,
                SHAPE_STATE_H,
                SHAPE_SHEET_W,
                SHAPE_SHEET_H,
                0,
                0xFFFFFFFF);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private enum Mode {
        BUILD,
        DESTROY
    }
}
