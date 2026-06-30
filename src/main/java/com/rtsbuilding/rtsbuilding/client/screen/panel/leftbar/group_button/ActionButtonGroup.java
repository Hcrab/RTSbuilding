package com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.group_button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.util.*;
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

    /** bind_button.png 贴图路径 */
    private static final ResourceLocation BIND_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/default_button/bind_button.png");
    /** direction_rotationbutton.png 贴图路径 */
    private static final ResourceLocation DIRECTION_ROTATE_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/default_button/direction_rotationbutton.png");
    /** item_pickup_button.png 贴图路径 */
    private static final ResourceLocation ITEM_PICKUP_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/default_button/item_pickup_button.png");

    // ----- 浮窗提示 -----
    private final FloatingTooltip bindBtnTooltip = new FloatingTooltip();
    private final FloatingTooltip dirRotateBtnTooltip = new FloatingTooltip();
    private final FloatingTooltip itemPickupBtnTooltip = new FloatingTooltip();

    public ActionButtonGroup() {
        super(BIND_BTN, DIRECTION_ROTATE_BTN, ITEM_PICKUP_BTN);
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
        // bind_button（索引 0）
        boolean hover0 = mouseX >= originX && mouseX < originX + buttonSize
                && mouseY >= originY && mouseY < originY + buttonSize;
        bindBtnTooltip.tick();
        bindBtnTooltip.update(hover0, false);

        // direction_rotationbutton（索引 1）
        boolean hover1 = mouseX >= originX && mouseX < originX + buttonSize
                && mouseY >= originY + buttonSize && mouseY < originY + buttonSize * 2;
        dirRotateBtnTooltip.tick();
        dirRotateBtnTooltip.update(hover1, false);

        // item_pickup_button（索引 2）
        boolean hover2 = mouseX >= originX && mouseX < originX + buttonSize
                && mouseY >= originY + buttonSize * 2 && mouseY < originY + buttonSize * 3;
        itemPickupBtnTooltip.tick();
        itemPickupBtnTooltip.update(hover2, false);
    }

    /** 在覆盖层阶段渲染 tooltip，定位在按钮右侧 */
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = SmoothAnimator.scaleColor(textColor, 0.6f);

        // bind_button
        if (bindBtnTooltip.shouldRender()) {
            String keyText = RtsKeyMappings.TOGGLE_BIND_MODE_KEY.getTranslatedKeyMessage().getString();
            String text = Component.translatable("tooltip.rtsbuilding.left.bind_button").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.bind_button.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            renderTooltipRight(g, bindBtnTooltip,
                    originX, originY, buttonSize, buttonSize,
                    text, textColor, shortcutColor, screenW, screenH);
        }

        // direction_rotationbutton
        if (dirRotateBtnTooltip.shouldRender()) {
            String keyText = RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY.getTranslatedKeyMessage().getString();
            String text = Component.translatable("tooltip.rtsbuilding.left.direction_rotate").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.direction_rotate.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            renderTooltipRight(g, dirRotateBtnTooltip,
                    originX, originY + buttonSize, buttonSize, buttonSize,
                    text, textColor, shortcutColor, screenW, screenH);
        }

        // item_pickup_button
        if (itemPickupBtnTooltip.shouldRender()) {
            String keyText = RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY.getTranslatedKeyMessage().getString();
            String text = Component.translatable("tooltip.rtsbuilding.left.item_pickup").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.item_pickup.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            renderTooltipRight(g, itemPickupBtnTooltip,
                    originX, originY + buttonSize * 2, buttonSize, buttonSize,
                    text, textColor, shortcutColor, screenW, screenH);
        }
    }

    /** 在按钮右侧渲染浮窗 */
    private static void renderTooltipRight(GuiGraphics g, FloatingTooltip tooltip,
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
        RtsClientUiUtil.drawNineSliceFloatingPanel(g, tipX, tipY, tipW, tipH);

        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            int lineColor = (i == lines.length - 1) ? shortcutColor : color;
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            RtsClientUiUtil.drawUiText(g, lines[i], 0, 0, lineColor);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
