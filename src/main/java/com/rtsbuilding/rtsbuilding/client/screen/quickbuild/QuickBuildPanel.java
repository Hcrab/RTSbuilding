package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
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
 * 快速建造面板：形状选择 + 填充模式 + 旋转控制。
 * <p>
 * 继承 {@link RtsWindowPanel} 获得窗口能力。
 * 向后兼容 {@code isQuickBuildOpen() / setQuickBuildOpen() / toggleOpen()}。
 */
public final class QuickBuildPanel extends RtsWindowPanel {

    /** 右侧列（填充/旋转）相对于窗口左边缘的偏移 */
    private static final int RIGHT_COL_X = 88;

    /** 形状按钮行间距 */
    private static final int SHAPE_ROW_PITCH = QUICK_BUILD_SHAPE_SLOT + 6;

    // ======================== 面板尺寸 ========================
    private static final int QUICK_BUILD_PANEL_W = 178;
    private static final int QUICK_BUILD_PANEL_H = 156;
    private static final int QUICK_BUILD_PANEL_MIN_H = 156;

    /** 底部提示文字区域额外高度 */
    private static final int BOTTOM_INFO_H = 30;

    // ======================== 精灵图参数 ========================
    private static final int SHEET_W = 450;
    private static final int SHEET_H = 900;
    private static final int STATE_H = 450;

    // ======================== 形状定义 ========================
    private static final ClientRtsController.BuildShape[] SHAPES = {
            ClientRtsController.BuildShape.BLOCK,
            ClientRtsController.BuildShape.LINE,
            ClientRtsController.BuildShape.SQUARE,
            ClientRtsController.BuildShape.WALL,
            ClientRtsController.BuildShape.CIRCLE,
            ClientRtsController.BuildShape.BOX
    };

    /** 各形状按钮对应的悬浮提示翻译键 */
    private static final String[] SHAPE_TOOLTIP_KEYS = {
            "screen.rtsbuilding.tooltip.shape_block",
            "screen.rtsbuilding.tooltip.shape_line",
            "screen.rtsbuilding.tooltip.shape_square",
            "screen.rtsbuilding.tooltip.shape_wall",
            "screen.rtsbuilding.tooltip.shape_circle",
            "screen.rtsbuilding.tooltip.shape_box"
    };

    /** 各形状按钮对应的精灵图纹理 */
    private static final ResourceLocation[] SHAPE_TEXTURES = {
            QUICK_BUILD_SINGLE_BLOCK,
            QUICK_BUILD_LINE_BLOCK,
            QUICK_BUILD_SQUARE_BLOCK,
            QUICK_BUILD_WALL_BLOCK,
            QUICK_BUILD_CIRCLE_BLOCK,
            QUICK_BUILD_BOX_BLOCK
    };

    // ======================== 实例 ========================
    private WindowButton[] shapeButtons;
    private WindowButton[] fillModeButtons;

    /** 缓存的形状，用于检测 fill mode 是否需要重建 */
    private ClientRtsController.BuildShape lastFillShape;

    // ======================== 初始化 ========================

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.open = true;
        this.resizable = false;
        createShapeButtons();
        this.lastFillShape = controller.getBuildShape();
    }

    private void createShapeButtons() {
        shapeButtons = new WindowButton[SHAPES.length];
        for (int i = 0; i < SHAPES.length; i++) {
            shapeButtons[i] = createShapeButton(i);
        }
    }

    /**
     * 创建指定索引的形状按钮，使用 WindowButton 内置纹理渲染。
     * 选中状态：始终显示下半（active）贴图；未选中：上半（inactive），悬停时切换至下半。
     */
    private WindowButton createShapeButton(int index) {
        boolean selected = controller.getBuildShape() == SHAPES[index];
        int normalV = selected ? STATE_H : 0;
        return new WindowButton(0, 0,
                QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                Component.empty(),
                SHAPE_TEXTURES[index],
                0, normalV,
                SHEET_W, STATE_H,
                STATE_H, STATE_H,
                SHEET_W, SHEET_H,
                btn -> {
                    this.controller.setBuildShape(SHAPES[index]);
                    screen.ensureFillModeForShape(SHAPES[index]);
                    screen.clearShapeBuildSession();
                    screen.persistUiState();
                    rebuildFillModeButtons();
                    rebuildAllShapeButtons();
                });
    }

    /** 当形状切换时刷新所有按钮贴图（选中/未选中状态）。 */
    private void rebuildAllShapeButtons() {
        for (int i = 0; i < SHAPES.length; i++) {
            shapeButtons[i] = createShapeButton(i);
        }
    }

    private void rebuildFillModeButtons() {
        this.lastFillShape = controller.getBuildShape();
        List<ShapeBuildTypes.ShapeFillMode> modes =
                ShapeGeometryUtil.availableFillModes(controller.getBuildShape());
        fillModeButtons = new WindowButton[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            final int idx = i;
            fillModeButtons[i] = new WindowButton(0, 0, 84, 16,
                    Component.literal(screen.fillModeLabel(modes.get(i))), btn -> {
                screen.setShapeFillMode(modes.get(idx));
                screen.persistUiState();
            });
        }
    }

    // ======================== 渲染 ========================

    /**
     * 动态调整窗口高度：底部信息显示时增加 {@value #BOTTOM_INFO_H}px。
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.windowHeight = QUICK_BUILD_PANEL_H + (shouldShowBottomInfo() ? BOTTOM_INFO_H : 0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow()) return;
        renderShapeTooltip(g, mouseX, mouseY);
    }

    private void renderShapeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < shapeButtons.length; i++) {
            WindowButton btn = shapeButtons[i];
            if (mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth()
                    && mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                g.renderTooltip(screen.font(), Component.translatable(SHAPE_TOOLTIP_KEYS[i]), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.windowX;
        int y = this.windowY;
        int bodyY = contentY();
        int shapeTitleY = bodyY + 5;

        // --- 形状模式 ---
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.shape"),
                x + 10, shapeTitleY, 0xD8E3EE, false);

        // --- 形状按钮 ---
        for (int i = 0; i < shapeButtons.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = x + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = bodyY + 20 + (row * SHAPE_ROW_PITCH);
            shapeButtons[i].setX(slotX);
            shapeButtons[i].setY(slotY);
            shapeButtons[i].render(g, mouseX, mouseY, partialTick);
        }

        // --- 填充模式 ---
        int rightX = x + RIGHT_COL_X;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.fill"),
                rightX, shapeTitleY, 0xD8E3EE, false);

        if (fillModeButtons == null || controller.getBuildShape() != lastFillShape) {
            rebuildFillModeButtons();
        }
        List<ShapeBuildTypes.ShapeFillMode> modes =
                ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < fillModeButtons.length; i++) {
            int rowY = bodyY + 28 + (i * SHAPE_ROW_PITCH); // 垂直居中对齐对应行的形状按钮
            fillModeButtons[i].setX(rightX);
            fillModeButtons[i].setY(rowY);
            fillModeButtons[i].render(g, mouseX, mouseY, partialTick);

            // 选中圆点
            boolean selected = screen.getShapeFillMode() == modes.get(i);
            g.fill(rightX + 4, rowY + 4, rightX + 12, rowY + 12, 0xAA111820);
            if (selected) {
                g.fill(rightX + 6, rowY + 6, rightX + 10, rowY + 10, 0xFF78B28C);
            }
        }

        // --- 底部提示文字（仅在选中物品时显示，使用面板扩展区域） ---
        if (shouldShowBottomInfo()) {
            // 分界线
            int dividerY = y + QUICK_BUILD_PANEL_H;
            g.fill(x + 6, dividerY - 1, x + windowWidth - 6, dividerY, 0xFF647B92);

            // 扩展区域中心线
            int centerY = dividerY + BOTTOM_INFO_H / 2;
            int textY = centerY - screen.font().lineHeight / 2;
            int itemY = centerY - 8;

            String costText = "x " + screen.currentShapeCostText();
            int textWidth = screen.font().width(costText);
            g.drawString(screen.font(), costText, x + 8, textY, 0xB8FFB8);

            // 渲染所选方块的物品图标，同时记录右侧边界
            ItemStack preview = resolveShapeBuildItem();
            int rightEdge = x + 8 + textWidth;
            if (!preview.isEmpty()) {
                int itemX = x + 8 + textWidth + 4;
                g.renderItem(preview, itemX, itemY);
                rightEdge = itemX + 16;
            }

            // 仓库库存检查：缺少数量，紧靠右侧（创造模式下跳过）
            boolean isCreative = screen.getMinecraft().player != null && screen.getMinecraft().player.isCreative();
            if (!isCreative) {
                String selectedId = controller.getSelectedItemId();
                if (!selectedId.isBlank()) {
                    try {
                        long needed = Long.parseLong(screen.currentShapeCostText());
                        long available = controller.getStorageTotalCount(selectedId);
                        long missing = needed - available;
                        if (missing > 0) {
                            String missText = screen.text("screen.rtsbuilding.quick_build.missing_blocks", missing);
                            int missTextX = rightEdge + 8;
                            g.drawString(screen.font(), missText, missTextX, textY, 0xFFB8B8);

                            if (!preview.isEmpty()) {
                                int missIconX = missTextX + screen.font().width(missText) + 4;
                                g.renderItem(preview, missIconX, itemY);
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    // ======================== 输入处理 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        // 委托给按钮处理
        for (WindowButton btn : shapeButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        if (fillModeButtons != null) {
            for (WindowButton btn : fillModeButtons) {
                if (btn.mouseClicked(mouseX, mouseY, button)) {
                    return;
                }
            }
        }
    }

    // ======================== 抽象方法实现 ========================

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
        return QUICK_BUILD_PANEL_W; // 固定宽度，不允许横向缩放
    }

    @Override
    protected int getMinWindowHeight() {
        return QUICK_BUILD_PANEL_MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        int y = TOP_H + 40;
        int availableH = screen.getFloatingPanelAvailableHeight(y);
        if (availableH >= QUICK_BUILD_PANEL_MIN_H) {
            this.windowHeight = QUICK_BUILD_PANEL_H;
        }
        this.windowX = screen.width - QUICK_BUILD_PANEL_W - 4;
        this.windowY = y;
    }

    @Override
    protected boolean canShowWindow() {
        return super.canShowWindow()
                && screen.hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE);
    }

    // ======================== 向后兼容 ========================

    /** @deprecated 改用 {@link #isOpen()} */
    @Deprecated
    public boolean isQuickBuildOpen() {
        return isOpen();
    }

    /** @deprecated 改用 {@link #setOpen(boolean)} */
    @Deprecated
    public void setQuickBuildOpen(boolean open) {
        setOpen(open);
    }

    /** @deprecated 改用 {@link #toggleOpen()} */
    @Deprecated
    public void toggleOpen() {
        super.toggleOpen();
    }

    /** 返回当前布局信息，供其他面板计算相对位置。 */
    public PanelLayouts.QuickBuildPanelLayout resolveLayout() {
        if (!isOpen() || !canShowWindow()) {
            return null;
        }
        return new PanelLayouts.QuickBuildPanelLayout(
                windowX, windowY, windowWidth, windowHeight);
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 是否显示底部提示文字。
     * 仅在玩家选中了可放置的方块物品时扩展面板并显示。
     */
    private boolean shouldShowBottomInfo() {
        ItemStack preview = resolveShapeBuildItem();
        return !preview.isEmpty() && preview.getItem() instanceof BlockItem;
    }

    /**
     * 解析当前用于形状建造的物品栈：
     * 优先返回 RTS 存储中选中的物品，其次返回玩家手持工具槽位的物品。
     */
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
}
