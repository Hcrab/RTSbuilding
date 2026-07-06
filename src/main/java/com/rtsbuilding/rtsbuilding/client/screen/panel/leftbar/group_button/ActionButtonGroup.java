package com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.group_button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 操作按钮组——包含 bind_button（绑定/拖拽）、direction_rotationbutton（方向旋转）
 * 和 item_pickup_button（物品拾取）。
 *
 * <p>该组为第二组（group 1），位于选择组（{@link SelectButtonGroup}）下方。</p>
 */
public final class ActionButtonGroup extends AbstractButtonGroup {

    /** bind.png 贴图路径 */
    private static final ResourceLocation BIND_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/bind.png");
    /** direction_rotation.png 贴图路径 */
    private static final ResourceLocation DIRECTION_ROTATE_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/direction_rotation.png");
    /** item_pickup.png 贴图路径 */
    private static final ResourceLocation ITEM_PICKUP_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/item_pickup.png");

    // ======================== 位置背景贴图 ========================

    /** down_button.png —— 首位按钮背景 */
    private static final ResourceLocation DOWN_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/down_button.png");
    /** middle_button.png —— 中间按钮背景 */
    private static final ResourceLocation MIDDLE_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/middle_button.png");
    /** up_button.png —— 末位按钮背景 */
    private static final ResourceLocation UP_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/up_button.png");

    /** only_button.png —— 单按钮独立背景 */
    private static final ResourceLocation ONLY_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/only_button.png");

    /** 在非交互模式下是否隐藏容器绑定按钮（索引 0） */
    private boolean showBindButton = true;

    /** 交互模式下是否隐藏方块旋转按钮（索引 1） */
    private boolean showRotateButton = true;

    /** 蓝图模式下仅显示漏斗按钮（索引 2） */
    private boolean blueprintMode = false;

    // ======================== 单按钮背景缓存 ========================

    /** only_button 的 TextureInfo */
    private final TextureInfo onlyTexInfo;
    /** only_button 的三态精灵区域 [normal, hovered, selected] */
    private final SpriteRegion[] onlyRegions;

    // ----- 浮窗提示 -----
    private final TooltipController bindBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();
    private final TooltipController dirRotateBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();
    private final TooltipController itemPickupBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();

    public ActionButtonGroup() {
        super(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, true,
                DOWN_BG, MIDDLE_BG, UP_BG,
                BIND_BTN, DIRECTION_ROTATE_BTN, ITEM_PICKUP_BTN);
        // 初始化单按钮背景缓存
        this.onlyTexInfo = new TextureInfo(
                ONLY_BG, AbstractButtonGroup.TEX_W, AbstractButtonGroup.TEX_H,
                TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                TextureInfo.FilterMode.PIXEL);
        this.onlyRegions = new SpriteRegion[3];
        this.onlyRegions[0] = new SpriteRegion(onlyTexInfo, 0, 0,                           AbstractButtonGroup.HALF_W, AbstractButtonGroup.STATE_H);
        this.onlyRegions[1] = new SpriteRegion(onlyTexInfo, 0, AbstractButtonGroup.STATE_H, AbstractButtonGroup.HALF_W, AbstractButtonGroup.STATE_H);
        this.onlyRegions[2] = new SpriteRegion(onlyTexInfo, 0, AbstractButtonGroup.STATE_H * 2, AbstractButtonGroup.HALF_W, AbstractButtonGroup.STATE_H);
    }

    /**
     * 设置是否显示容器绑定按钮（索引 0）。
     * 仅在交互模式（click_button 选中）下应显示，选择模式下隐藏。
     */
    public void setShowBindButton(boolean show) {
        this.showBindButton = show;
        if (!show) {
            selected[0] = false;
        }
    }

    /**
     * 设置是否显示方块旋转按钮（索引 1）。
     * 选择模式下显示，交互模式下隐藏。
     */
    public void setShowRotateButton(boolean show) {
        this.showRotateButton = show;
        if (!show) {
            selected[1] = false;
        }
    }

    /**
     * 设置是否为蓝图模式。
     * 蓝图模式下只显示漏斗（item_pickup）按钮，其他按钮隐藏。
     */
    public void setBlueprintMode(boolean blueprint) {
        this.blueprintMode = blueprint;
        if (blueprint) {
            selected[0] = false;
            selected[1] = false;
        }
    }

    // ======================== 渲染（支持按钮条件隐藏） ========================

    /** 可见按钮总数 */
    private int visibleCount() {
        if (blueprintMode) return 1;
        return (showBindButton ? 1 : 0) + (showRotateButton ? 1 : 0) + 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        int n = patternTextures.length;
        int[] orig = { bgTypeForButton[0], bgTypeForButton[1], bgTypeForButton[2] };
        try {
            int vis = 0;
            int total = visibleCount();
            for (int i = 0; i < n; i++) {
                if (!isVisible(i)) continue;
                int by = originY + vis * (buttonSize + innerGap);
                if (hasBg) {
                    if (total == 1) {
                        renderOnlyBg(g, mouseX, mouseY, i, originX, by);
                    } else {
                        bgTypeForButton[i] = bgTypeForVisualIndex(vis);
                        renderSingleBg(g, mouseX, mouseY, i, originX, by);
                    }
                }
                renderSinglePattern(g, mouseX, mouseY, i, originX, by);
                vis++;
            }
        } finally {
            bgTypeForButton[0] = orig[0];
            bgTypeForButton[1] = orig[1];
            bgTypeForButton[2] = orig[2];
        }
    }

    /** 按钮索引 i 是否在视觉上可见 */
    private boolean isVisible(int i) {
        // 蓝图模式：只显示漏斗按钮（索引 2）
        if (blueprintMode) return i == 2;
        return switch (i) {
            case 0 -> showBindButton;
            case 1 -> showRotateButton;
            case 2 -> true;
            default -> false;
        };
    }

    /** 根据视觉索引分配背景类型：首位→up，末位→down，中间→middle */
    private int bgTypeForVisualIndex(int visIdx) {
        int total = visibleCount();
        if (visIdx == 0) return 2; // 首位→up
        if (visIdx == total - 1) return 0; // 末位→down
        return 1; // 中间→middle
    }

    /**
     * 绘制单按钮独立背景——当组内仅一个按钮可见时使用 only_button 贴图。
     */
    private void renderOnlyBg(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        float hoverT = this.hoverStates[index].update(hovering);

        SpriteRenderer.drawStateSprite(g,
                onlyRegions[0],     // normal
                onlyRegions[1],     // hovered
                onlyRegions[2],     // selected
                selected[index],
                hoverT,
                bx, by, buttonSize, buttonSize);
    }

    // ======================== 点击（支持按钮条件隐藏） ========================

    @Override
    public int mouseClicked(double mx, double my, int originX, int originY) {
        int vis = 0;
        for (int i = 0; i < patternTextures.length; i++) {
            if (!isVisible(i)) continue;
            int by = originY + vis * (buttonSize + innerGap);
            if (mx >= originX && mx < originX + buttonSize
                    && my >= by && my < by + buttonSize) {
                onButtonClick(i);
                return i;
            }
            vis++;
        }
        return -1;
    }

    /**
     * 切换绑定模式——模拟点击 bind_button（索引 0）。
     */
    public void toggleBindButton() {
        onButtonClick(0);
    }

    /**
     * 切换方向旋转模式——模拟点击 direction_rotationbutton（索引 1）。
     */
    public void toggleDirectionRotateButton() {
        onButtonClick(1);
    }

    /**
     * 切换物品拾取模式——模拟点击 item_pickup_button（索引 2）。
     */
    public void toggleItemPickupButton() {
        onButtonClick(2);
    }

    @Override
    protected void onButtonClick(int index) {
        // 点击已选中的按钮 → 关闭；点击未选中的 → 选中
        if (selected[index]) {
            selected[index] = false;
        } else {
            java.util.Arrays.fill(selected, false);
            selected[index] = true;
        }
    }

    /** 刷新 tooltip 状态——由 LeftSidebarPanel.render() 每帧调用 */
    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        int vis = 0;
        for (int i = 0; i < patternTextures.length; i++) {
            if (!isVisible(i)) {
                // 隐藏的按钮强制关闭 tooltip
                getTooltip(i).update(false, false);
                continue;
            }
            int by = originY + vis * (buttonSize + innerGap);
            boolean hover = mouseX >= originX && mouseX < originX + buttonSize
                    && mouseY >= by && mouseY < by + buttonSize;
            getTooltip(i).update(hover, false);
            vis++;
        }
    }

    /** 获取按钮索引对应的 tooltip 控制器 */
    private TooltipController getTooltip(int i) {
        return switch (i) {
            case 0 -> bindBtnTooltip;
            case 1 -> dirRotateBtnTooltip;
            case 2 -> itemPickupBtnTooltip;
            default -> throw new IndexOutOfBoundsException("Unexpected index: " + i);
        };
    }

    /** 在覆盖层阶段渲染 tooltip，定位在按钮右侧 */
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);

        int vis = 0;
        for (int i = 0; i < patternTextures.length; i++) {
            if (!isVisible(i)) continue;
            int by = originY + vis * (buttonSize + innerGap);
            TooltipController tooltip = getTooltip(i);
            if (tooltip.shouldRender()) {
                renderSingleTooltip(g, tooltip, i, originX, by, textColor, shortcutColor, screenW, screenH);
            }
            vis++;
        }
    }

    /** 渲染单个按钮的 tooltip */
    private void renderSingleTooltip(GuiGraphics g, TooltipController tooltip, int index,
                                      int btnX, int btnY, int textColor, int shortcutColor,
                                      int screenW, int screenH) {
        String text;
        if (index == 0) {
            String keyText = RtsKeyMappings.TOGGLE_BIND_MODE_KEY.getTranslatedKeyMessage().getString();
            text = Component.translatable("tooltip.rtsbuilding.left.bind_button").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.bind_button.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        } else if (index == 1) {
            String keyText = RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY.getTranslatedKeyMessage().getString();
            text = Component.translatable("tooltip.rtsbuilding.left.direction_rotate").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.direction_rotate.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        } else {
            String keyText = RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY.getTranslatedKeyMessage().getString();
            text = Component.translatable("tooltip.rtsbuilding.left.item_pickup").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.item_pickup.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        }
        renderTooltipRight(g, tooltip, btnX, btnY, buttonSize, buttonSize,
                text, textColor, shortcutColor, screenW, screenH);
    }

    /** 在按钮右侧渲染浮窗 */
    private static void renderTooltipRight(GuiGraphics g, TooltipController tooltip,
                                            int btnX, int btnY, int btnW, int btnH,
                                            String text, int color, int shortcutColor,
                                            int screenW, int screenH) {
        float alpha = tooltip.getAlpha();
        var font = Minecraft.getInstance().font;

        String[] lines = text.split("\n");
        int lineHeight = font.lineHeight;
        int lineGap = 1;
        float scaledLineH = lineHeight * 0.75f;
        float scaledLineGap = lineGap * 0.75f;
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        int padH = 6, padV = 3;
        int tipW = (int)(maxLineW * 0.75f) + padH * 2;
        int tipH = (int)(scaledLineH * lines.length + scaledLineGap * (lines.length - 1)) + padV * 2;

        // 定位到按钮右侧
        int tipX = btnX + btnW + 2;
        int tipY = btnY;
        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, Math.min(tipY, screenH - tipH));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        SpriteRenderer.drawNineSliceFloatingPanel(g, tipX, tipY, tipW, tipH);

        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            int lineColor = (i == lines.length - 1) ? shortcutColor : color;
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            TextRenderer.draw(g, lines[i], 0, 0, lineColor);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
