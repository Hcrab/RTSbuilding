package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelectionPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.InteractionTargetPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.*;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.FloatingTooltip;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 渲染设置折叠分区——在设置面板中管理"渲染设置"分区的渲染和交互。
 */
public class RenderingSection extends SettingsSection {

    private static final double ALPHA_MIN = 0.02;
    private static final double ALPHA_MAX = 0.80;

    /** 颜色块与标签间距 */
    private static final int COLOR_BLOCK_GAP = 4;

    /** 重置按钮与主控件的间距 */
    private static final int RESET_BTN_GAP = 4;

    // ======================== 行号（布局顺序）=======================
    //
    //  行号反映自然视觉排列，条件行紧跟在被依赖行下方。
    //  渲染时手动跳过条件行（而非依赖固定行索引），后续行自动上移。
    // =============================================================

    private static final int ROW_FLOW = 0;
    private static final int ROW_SMOOTH = 1;
    private static final int ROW_UI_SMOOTH = 2;
    private static final int ROW_DEPTH = 3;          // 主依赖条目
    private static final int ROW_BARRIER_COLOR = 5;
    private static final int ROW_TARGET_COLOR = 6;
    private static final int ROW_SELECTION_COLOR = 7;
    /** 始终可见的行数（穿透层及以上的行） */
    private static final int ALWAYS_VISIBLE_ROW_COUNT = 4;
    /** 始终可见 + 屏障颜色 + 交互目标颜色 + 框选颜色高度（4 + 3 行） */
    private static final int MIN_CONTENT_H = 146;  // 7 * 20 + 6
    /** 依赖穿透层的条件行高度（透明度标签+滑条合并为一行） */
    private static final int EXTRA_ROWS_H = 20;   // 1 * 20

    private final ThemeSwitchComponent depthToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent flowToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent smoothToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent uiSmoothToggle = new ThemeSwitchComponent();
    private final ScaleSliderComponent alphaSlider = new ScaleSliderComponent();
    /** 透明度滑条轨道位置缓存 */
    private final SliderTrack alphaTrack = new SliderTrack();

    /** 调色盘按钮组件（屏障颜色） */
    private final ColorPickerButton colorPickerButton = new ColorPickerButton();
    /** 屏障颜色源 */
    private final ColorGroup barrierColorGroup = ColorGroup.single("渲染设置", "屏障颜色", new ColorSource() {
        @Override
        public int getColor() { return BoundaryPass.barrierColor; }
        @Override
        public void setColor(int color) { BoundaryPass.barrierColor = color; }
    });
    /** 颜色块组件（屏障颜色） */
    private final ColorBlockComponent colorBlock = new ColorBlockComponent();

    /** 屏障颜色块位置缓存（用于浮窗悬停检测） */
    private int barrierBlockX, barrierBlockY;

    /** 屏障颜色浮窗 */
    private final FloatingTooltip barrierTooltip = new FloatingTooltip();

    /** 调色盘按钮组件（交互目标颜色） */
    private final ColorPickerButton targetColorPickerButton = new ColorPickerButton();
    /** 交互目标颜色组（方块 + 实体两个命名颜色） */
    private final ColorGroup targetColorGroup = new ColorGroup("渲染设置", List.of(
            new ColorSlot("方块目标颜色", new ColorSource() {
                @Override
                public int getColor() { return InteractionTargetPass.blockTargetColor; }
                @Override
                public void setColor(int color) { InteractionTargetPass.blockTargetColor = color; }
            }),
            new ColorSlot("实体目标颜色", new ColorSource() {
                @Override
                public int getColor() { return InteractionTargetPass.entityTargetColor; }
                @Override
                public void setColor(int color) { InteractionTargetPass.entityTargetColor = color; }
            })
    ));
    /** 颜色块组件（交互目标颜色） */
    private final ColorBlockComponent targetColorBlock = new ColorBlockComponent();

    /** 方块/实体色块位置缓存（用于浮窗悬停检测） */
    private int targetBlockX, entityBlockX, targetBlockY;

    /** 方块/实体目标颜色浮窗 */
    private final FloatingTooltip blockTargetTooltip = new FloatingTooltip();
    private final FloatingTooltip entityTargetTooltip = new FloatingTooltip();

    /** 调色盘按钮组件（框选颜色） */
    private final ColorPickerButton selectionColorPickerButton = new ColorPickerButton();

    // ======================== 重置按钮 ========================

    private final ResetButton flowResetBtn = new ResetButton();
    private final ResetButton smoothResetBtn = new ResetButton();
    private final ResetButton uiSmoothResetBtn = new ResetButton();
    private final ResetButton depthResetBtn = new ResetButton();
    private final ResetButton alphaResetBtn = new ResetButton();
    private final ResetButton barrierResetBtn = new ResetButton();
    private final ResetButton targetResetBtn = new ResetButton();
    private final ResetButton selectionResetBtn = new ResetButton();
    /** 框选颜色组（线框主色 + 间隙色 + 覆盖层） */
    private final ColorGroup selectionColorGroup = new ColorGroup("渲染设置", List.of(
            new ColorSlot("框选线框颜色", new ColorSource() {
                @Override
                public int getColor() { return BoxSelectionPass.selectionColor; }
                @Override
                public void setColor(int color) { BoxSelectionPass.selectionColor = color; }
            }),
            new ColorSlot("线框间隙颜色", new ColorSource() {
                @Override
                public int getColor() { return BoxSelectionPass.selectionGapColor; }
                @Override
                public void setColor(int color) { BoxSelectionPass.selectionGapColor = color; }
            }),
            new ColorSlot("覆盖层颜色", new ColorSource() {
                @Override
                public int getColor() { return BoxSelectionPass.previewOverlayColor; }
                @Override
                public void setColor(int color) { BoxSelectionPass.previewOverlayColor = color; }
            })
    ));
    /** 颜色块组件（框选颜色） */
    private final ColorBlockComponent selectionColorBlock = new ColorBlockComponent();
    /** 框选线框/间隙/覆盖层色块位置缓存 */
    private int selBlockX, selGapBlockX, selOverlayBlockX, selBlockY;
    /** 框选颜色浮窗 */
    private final FloatingTooltip selWireframeTooltip = new FloatingTooltip();
    private final FloatingTooltip selGapTooltip = new FloatingTooltip();
    private final FloatingTooltip selOverlayTooltip = new FloatingTooltip();

    /** 缓存的翻译文本（避免每帧 Component.translatable() */
    private String cachedFlowLabel;
    private String cachedSmoothLabel;
    private String cachedUiSmoothLabel;
    private String cachedDepthLabel;
    private String cachedAlphaLabel;
    private String cachedBarrierLabel;
    private String cachedTargetLabel;
    private String cachedSelectionLabel;

    /** 内容区高度平滑动画器 */
    private final SmoothAnimator heightAnim = AnimationFactory.createExpandAnim();
    private boolean lastDepthEnabled;

    public RenderingSection() {
        super("screen.rtsbuilding.settings.category.rendering");
        setExpanded(false);
        lastDepthEnabled = BoxSelectionPass.depthTestEnabled;
        heightAnim.snapTo(lastDepthEnabled ? 1.0f : 0.0f);

        // 初始化重置按钮回调
        flowResetBtn.setResetAction(() -> BoxSelectionPass.flowAnimationEnabled = true);
        smoothResetBtn.setResetAction(() -> CornerBracketRenderer.SmoothTarget.enabled = true);
        uiSmoothResetBtn.setResetAction(() -> SmoothAnimator.enabled = true);
        depthResetBtn.setResetAction(() -> BoxSelectionPass.depthTestEnabled = true);
        alphaResetBtn.setResetAction(() -> CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = 0.10f);
        barrierResetBtn.setResetAction(() -> BoundaryPass.barrierColor = 0xFFFFCC00);
        targetResetBtn.setResetAction(() -> {
            InteractionTargetPass.blockTargetColor = 0xFFF69C31;
            InteractionTargetPass.entityTargetColor = 0xFF4D99FF;
        });
        selectionResetBtn.setResetAction(() -> {
            BoxSelectionPass.selectionColor = 0xFFFFFFFF;
            BoxSelectionPass.selectionGapColor = 0xFF000000;
            BoxSelectionPass.previewOverlayColor = 0xFF4D80FF;
        });
    }

    @Override
    protected int getContentRowCount() {
        return ALWAYS_VISIBLE_ROW_COUNT + 4; // 始终可见4行 + 屏障1行 + 目标1行 + 框选1行 + 条件行1行
    }

    @Override
    protected int getEffectiveContentHeight() {
        // 动态计算：始终可见行（含屏障颜色）+ 条件行高度（由动画控制）
        return MIN_CONTENT_H + Math.round(EXTRA_ROWS_H * heightAnim.getValue());
    }

    /** 基于 cursorY 渲染标签行（替代父类的 renderLabel + 固定行索引） */
    private void renderRowLabel(GuiGraphics g, String text, int x, int lineY) {
        RtsClientUiUtil.drawUiText(g, text, x + LEFT_PAD, lineY + 2, getTextColor());
    }

    /** 基于 cursorY 渲染开关行——开关中心对齐文字中心，右侧带重置按钮 */
    private void renderRowToggle(GuiGraphics g, int mx, int my, int x, int w, int lineY,
                                  ThemeSwitchComponent toggle, boolean state, ResetButton resetBtn) {
        int textCenterY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int toggleY = textCenterY - ThemeSwitchComponent.SIZE / 2;
        // 开关左移以给重置按钮留空间
        int toggleX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP - ThemeSwitchComponent.SIZE;
        toggle.render(g, mx, my, toggleX, toggleY, state);
        // 重置按钮在右边缘
        int resetX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE;
        int resetY = textCenterY - ResetButton.BTN_SIZE / 2;
        resetBtn.render(g, mx, my, resetX, resetY);
    }

    /** 基于 cursorY 渲染标签 + 滑条（以中位线为界，左侧文字右侧滑条+重置） */
    private void renderRowSlider(GuiGraphics g, int mx, int my, int x, int w, int lineY,
                                  String label, ScaleSliderComponent slider, SliderTrack trackPos,
                                  double min, double max, double value, ResetButton resetBtn) {
        RtsClientUiUtil.drawUiText(g, label, x + LEFT_PAD, lineY + 2, getTextColor());
        int centerY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int controlStart = midControlX(x, w);
        trackPos.trackX = controlStart;
        trackPos.trackY = centerY - 2;
        // 轨道从中位线到重置按钮左侧
        int trackMaxW = (x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP) - controlStart;
        trackPos.trackW = Mth.clamp(trackMaxW, 20, trackMaxW);
        trackPos.slider = slider;
        slider.render(g, mx, my, trackPos.trackX, trackPos.trackY, trackPos.trackW, min, max, value);
        // 重置按钮在右边缘
        int resetX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE;
        int resetY = centerY - ResetButton.BTN_SIZE / 2;
        resetBtn.render(g, mx, my, resetX, resetY);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {
        // 驱动内容区高度动画
        heightAnim.tick();
        boolean depthOn = BoxSelectionPass.depthTestEnabled;
        if (depthOn != lastDepthEnabled) {
            lastDepthEnabled = depthOn;
            heightAnim.start(depthOn ? 1.0f : 0.0f);
        }

        int lineH = getLineHeight();
        // cursorY：基于当前绘制位置，条件行跳过时后续行自动上移
        int cursorY = y + 4;

        // ---- 始终可见行 ----

        renderRowLabel(g, getFlowLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, flowToggle, BoxSelectionPass.flowAnimationEnabled, flowResetBtn);
        cursorY += lineH;

        renderRowLabel(g, getSmoothLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, smoothToggle, CornerBracketRenderer.SmoothTarget.enabled, smoothResetBtn);
        cursorY += lineH;

        renderRowLabel(g, getUiSmoothLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, uiSmoothToggle, SmoothAnimator.enabled, uiSmoothResetBtn);
        cursorY += lineH;

        // 穿透层（主依赖条目）
        renderRowLabel(g, getDepthLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, depthToggle, BoxSelectionPass.depthTestEnabled, depthResetBtn);
        cursorY += lineH;

        // ---- 条件行：线框透明度（紧跟在穿透层下方，条件满足时才渲染和占位）----
        if (depthOn) {
            String alphaLabel = getAlphaLabel()
                    + String.format(java.util.Locale.ROOT, "：%.0f%%", CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA * 100);
            renderRowSlider(g, mouseX, mouseY, x, w, cursorY, alphaLabel,
                    alphaSlider, alphaTrack, ALPHA_MIN, ALPHA_MAX,
                    CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, alphaResetBtn);
            cursorY += lineH;
        }

        // ---- 屏障颜色（始终可见，自动跟随 cursorY）----
        String barrierLabel = getBarrierLabel();
        renderRowLabel(g, barrierLabel, x, cursorY);

        int barrierLabelW = Minecraft.getInstance().font.width(barrierLabel);
        int textCenterY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int barrierBlockX = x + LEFT_PAD + barrierLabelW + COLOR_BLOCK_GAP;
        int barrierBlockY = textCenterY - ColorBlockComponent.DEFAULT_SIZE / 2;
        // 缓存屏障色块位置用于浮窗检测
        this.barrierBlockX = barrierBlockX;
        this.barrierBlockY = barrierBlockY;
        colorBlock.render(g, barrierBlockX, barrierBlockY, BoundaryPass.barrierColor);

        // 调色盘按钮左移以给重置按钮留空间
        int barrierPickerX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP - ColorPickerButton.BTN_SIZE;
        int barrierPickerY = textCenterY - ColorPickerButton.BTN_SIZE / 2;
        colorPickerButton.render(g, mouseX, mouseY, barrierPickerX, barrierPickerY);

        // 屏障颜色重置按钮
        int barrierResetX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE;
        int barrierResetY = textCenterY - ResetButton.BTN_SIZE / 2;
        barrierResetBtn.render(g, mouseX, mouseY, barrierResetX, barrierResetY);
        cursorY += lineH;

        // ---- 交互目标颜色（始终可见，自动跟随 cursorY）----
        String targetLabel = getTargetLabel();
        renderRowLabel(g, targetLabel, x, cursorY);

        int targetLabelW = Minecraft.getInstance().font.width(targetLabel);
        int targetTextCenterY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int targetBlockX = x + LEFT_PAD + targetLabelW + COLOR_BLOCK_GAP;
        int targetBlockY = targetTextCenterY - ColorBlockComponent.DEFAULT_SIZE / 2;
        // 缓存目标色块位置用于浮窗检测
        this.targetBlockX = targetBlockX;
        this.targetBlockY = targetBlockY;

        // 方块色块 + 实体色块（仅展示，切换在调色盘面板内完成）
        targetColorBlock.render(g, targetBlockX, targetBlockY, InteractionTargetPass.blockTargetColor);
        int entityBlockX = targetBlockX + ColorBlockComponent.DEFAULT_SIZE + 2;
        this.entityBlockX = entityBlockX;
        targetColorBlock.render(g, entityBlockX, targetBlockY, InteractionTargetPass.entityTargetColor);

        // 调色盘按钮左移以给重置按钮留空间
        int targetPickerX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP - ColorPickerButton.BTN_SIZE;
        int targetPickerY = targetTextCenterY - ColorPickerButton.BTN_SIZE / 2;
        targetColorPickerButton.render(g, mouseX, mouseY, targetPickerX, targetPickerY);

        // 交互目标颜色重置按钮
        int targetResetX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE;
        int targetResetY = targetTextCenterY - ResetButton.BTN_SIZE / 2;
        targetResetBtn.render(g, mouseX, mouseY, targetResetX, targetResetY);
        cursorY += lineH;

        // ---- 框选颜色（始终可见，自动跟随 cursorY）----
        String selLabel = getSelectionLabel();
        renderRowLabel(g, selLabel, x, cursorY);

        int selLabelW = Minecraft.getInstance().font.width(selLabel);
        int selTextCenterY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int selBlockX = x + LEFT_PAD + selLabelW + COLOR_BLOCK_GAP;
        int selBlockY = selTextCenterY - ColorBlockComponent.DEFAULT_SIZE / 2;
        this.selBlockX = selBlockX;
        this.selBlockY = selBlockY;

        selectionColorBlock.render(g, selBlockX, selBlockY, BoxSelectionPass.selectionColor);
        int selGapBlockX = selBlockX + ColorBlockComponent.DEFAULT_SIZE + 2;
        this.selGapBlockX = selGapBlockX;
        selectionColorBlock.render(g, selGapBlockX, selBlockY, BoxSelectionPass.selectionGapColor);
        int selOverlayBlockX = selGapBlockX + ColorBlockComponent.DEFAULT_SIZE + 2;
        this.selOverlayBlockX = selOverlayBlockX;
        selectionColorBlock.render(g, selOverlayBlockX, selBlockY, BoxSelectionPass.previewOverlayColor);

        // 调色盘按钮左移以给重置按钮留空间
        int selPickerX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP - ColorPickerButton.BTN_SIZE;
        int selPickerY = selTextCenterY - ColorPickerButton.BTN_SIZE / 2;
        selectionColorPickerButton.render(g, mouseX, mouseY, selPickerX, selPickerY);

        // 框选颜色重置按钮
        int selResetX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE;
        int selResetY = selTextCenterY - ResetButton.BTN_SIZE / 2;
        selectionResetBtn.render(g, mouseX, mouseY, selResetX, selResetY);
    }

    /** 渲染色块浮窗提示——由 GearMenuPanel 在 scissor 解除后调用 */
    public void renderColorTooltips(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;
        int textColor = getTextColor();
        int shortcutColor = 0xFF999999;
        int bs = ColorBlockComponent.DEFAULT_SIZE;
        int padH = 6, padV = 3;

        boolean onBarrier = mouseX >= barrierBlockX && mouseX < barrierBlockX + bs
                && mouseY >= barrierBlockY && mouseY < barrierBlockY + bs;
        barrierTooltip.update(onBarrier, false);
        if (barrierTooltip.shouldRender()) {
            barrierTooltip.renderBelowButton(g, barrierBlockX, barrierBlockY, bs, bs,
                    padH, padV, getBarrierTooltipText(), textColor, shortcutColor,
                    screen.width, screen.height);
        }

        boolean onBlockTarget = mouseX >= targetBlockX && mouseX < targetBlockX + bs
                && mouseY >= targetBlockY && mouseY < targetBlockY + bs;
        blockTargetTooltip.update(onBlockTarget, false);
        if (blockTargetTooltip.shouldRender()) {
            blockTargetTooltip.renderBelowButton(g, targetBlockX, targetBlockY, bs, bs,
                    padH, padV, getBlockTargetTooltipText(), textColor, shortcutColor,
                    screen.width, screen.height);
        }

        boolean onEntityTarget = mouseX >= entityBlockX && mouseX < entityBlockX + bs
                && mouseY >= targetBlockY && mouseY < targetBlockY + bs;
        entityTargetTooltip.update(onEntityTarget, false);
        if (entityTargetTooltip.shouldRender()) {
            entityTargetTooltip.renderBelowButton(g, entityBlockX, targetBlockY, bs, bs,
                    padH, padV, getEntityTargetTooltipText(), textColor, shortcutColor,
                    screen.width, screen.height);
        }

        // 框选线框颜色浮窗
        boolean onSelWire = mouseX >= selBlockX && mouseX < selBlockX + bs
                && mouseY >= selBlockY && mouseY < selBlockY + bs;
        selWireframeTooltip.update(onSelWire, false);
        if (selWireframeTooltip.shouldRender()) {
            selWireframeTooltip.renderBelowButton(g, selBlockX, selBlockY, bs, bs,
                    padH, padV, getSelWireframeTooltipText(), textColor, shortcutColor,
                    screen.width, screen.height);
        }

        // 线框间隙颜色浮窗
        boolean onSelGap = mouseX >= selGapBlockX && mouseX < selGapBlockX + bs
                && mouseY >= selBlockY && mouseY < selBlockY + bs;
        selGapTooltip.update(onSelGap, false);
        if (selGapTooltip.shouldRender()) {
            selGapTooltip.renderBelowButton(g, selGapBlockX, selBlockY, bs, bs,
                    padH, padV, getSelGapTooltipText(), textColor, shortcutColor,
                    screen.width, screen.height);
        }

        // 覆盖层颜色浮窗
        boolean onSelOverlay = mouseX >= selOverlayBlockX && mouseX < selOverlayBlockX + bs
                && mouseY >= selBlockY && mouseY < selBlockY + bs;
        selOverlayTooltip.update(onSelOverlay, false);
        if (selOverlayTooltip.shouldRender()) {
            selOverlayTooltip.renderBelowButton(g, selOverlayBlockX, selBlockY, bs, bs,
                    padH, padV, getSelOverlayTooltipText(), textColor, shortcutColor,
                    screen.width, screen.height);
        }
    }

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        if (smoothToggle.handleClick(mouseX, mouseY)) {
            CornerBracketRenderer.SmoothTarget.enabled = !CornerBracketRenderer.SmoothTarget.enabled;
            return true;
        }
        if (flowToggle.handleClick(mouseX, mouseY)) {
            BoxSelectionPass.flowAnimationEnabled = !BoxSelectionPass.flowAnimationEnabled;
            return true;
        }
        if (uiSmoothToggle.handleClick(mouseX, mouseY)) {
            SmoothAnimator.enabled = !SmoothAnimator.enabled;
            return true;
        }
        if (depthToggle.handleClick(mouseX, mouseY)) {
            BoxSelectionPass.depthTestEnabled = !BoxSelectionPass.depthTestEnabled;
            return true;
        }

        // 重置按钮点击——在所有行检测，每个按钮自缓存区域
        if (flowResetBtn.handleClick(mouseX, mouseY)) return true;
        if (smoothResetBtn.handleClick(mouseX, mouseY)) return true;
        if (uiSmoothResetBtn.handleClick(mouseX, mouseY)) return true;
        if (depthResetBtn.handleClick(mouseX, mouseY)) return true;
        if (alphaResetBtn.handleClick(mouseX, mouseY)) return true;
        if (barrierResetBtn.handleClick(mouseX, mouseY)) return true;
        if (targetResetBtn.handleClick(mouseX, mouseY)) return true;
        if (selectionResetBtn.handleClick(mouseX, mouseY)) return true;

        if (lineIndex == ROW_BARRIER_COLOR) {
            if (colorPickerButton.handleClick(mouseX, mouseY)) {
                return true;
            }
        }
        if (lineIndex == ROW_TARGET_COLOR) {
            // 调色盘按钮点击——面板打开后显示两个命名的色块，在面板内切换编辑目标
            if (targetColorPickerButton.handleClick(mouseX, mouseY)) {
                return true;
            }
        }
        if (lineIndex == ROW_SELECTION_COLOR) {
            if (selectionColorPickerButton.handleClick(mouseX, mouseY)) {
                return true;
            }
        }
        if (BoxSelectionPass.depthTestEnabled) {
            Double newVal = alphaSlider.handleClick(mouseX, mouseY,
                    alphaTrack.trackX, alphaTrack.trackY, alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
            if (newVal != null) {
                CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue();
                return true;
            }
        }
        return false;
    }

    // ======================== 滑条拖拽 ========================

    /**
     * 注入调色盘面板引用，由 ColorPickerButton 代理。
     */
    public void setColorPickerPanel(ColorPickerPanel panel) {
        this.colorPickerButton.setColorPickerPanel(panel);
        this.colorPickerButton.setColorGroup(barrierColorGroup);
        this.targetColorPickerButton.setColorPickerPanel(panel);
        this.targetColorPickerButton.setColorGroup(targetColorGroup);
        this.selectionColorPickerButton.setColorPickerPanel(panel);
        this.selectionColorPickerButton.setColorGroup(selectionColorGroup);
    }

    /**
     * 设置调色盘按钮的父面板引用（唤出调色盘的面板）。
     * <p>点击调色盘按钮时自动通过 {@link RtsPanel#openChild(RtsPanel)} 建立父子关系，
     * 使父面板关闭时自动关闭调色盘面板。</p>
     */
    public void setColorPickerButtonParent(RtsPanel parent) {
        this.colorPickerButton.setParentPanel(parent);
        this.targetColorPickerButton.setParentPanel(parent);
        this.selectionColorPickerButton.setParentPanel(parent);
    }

    public boolean isSliderDragging() {
        return BoxSelectionPass.depthTestEnabled && alphaSlider.isDragging();
    }

    public void handleSliderDrag(double mouseX) {
        if (alphaSlider.isDragging() && alphaTrack.trackW > 0) {
            double val = alphaSlider.handleDrag(mouseX, alphaTrack.trackX, alphaTrack.trackW,
                    ALPHA_MIN, ALPHA_MAX);
            CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = (float) val;
        }
    }

    public void endSliderDrag() {
        alphaSlider.endDrag();
    }

    public boolean handleSliderScroll(double mouseX, double mouseY, double scrollY) {
        Double newVal = alphaSlider.handleScroll(mouseX, mouseY, scrollY,
                alphaTrack.trackX, alphaTrack.trackY, alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
        if (newVal != null) {
            CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue();
            return true;
        }
        return false;
    }

    // ======================== 缓存翻译文本（惰性初始化，避免每帧 Component.translatable()）=======================

    private String getFlowLabel() {
        if (cachedFlowLabel == null) cachedFlowLabel = Component.translatable("screen.rtsbuilding.settings.flow_animation").getString();
        return cachedFlowLabel;
    }

    private String getSmoothLabel() {
        if (cachedSmoothLabel == null) cachedSmoothLabel = Component.translatable("screen.rtsbuilding.settings.smooth_animation").getString();
        return cachedSmoothLabel;
    }

    private String getUiSmoothLabel() {
        if (cachedUiSmoothLabel == null) cachedUiSmoothLabel = Component.translatable("screen.rtsbuilding.settings.ui_smooth_animation").getString();
        return cachedUiSmoothLabel;
    }

    private String getDepthLabel() {
        if (cachedDepthLabel == null) cachedDepthLabel = Component.translatable("screen.rtsbuilding.settings.depth_test").getString();
        return cachedDepthLabel;
    }

    private String getAlphaLabel() {
        if (cachedAlphaLabel == null) cachedAlphaLabel = Component.translatable("screen.rtsbuilding.settings.overlay_alpha").getString();
        return cachedAlphaLabel;
    }

    private String getBarrierLabel() {
        if (cachedBarrierLabel == null) cachedBarrierLabel = Component.translatable("screen.rtsbuilding.settings.barrier_color").getString();
        return cachedBarrierLabel;
    }

    private String getTargetLabel() {
        if (cachedTargetLabel == null) cachedTargetLabel = Component.translatable("screen.rtsbuilding.settings.target_color").getString();
        return cachedTargetLabel;
    }

    private String getSelectionLabel() {
        if (cachedSelectionLabel == null) cachedSelectionLabel = Component.translatable("screen.rtsbuilding.settings.selection_color").getString();
        return cachedSelectionLabel;
    }

    // ======================== 浮窗文字 ========================

    private String getBarrierTooltipText() {
        return "屏障颜色\n用于标记区块边界的屏障线框颜色";
    }

    private String getBlockTargetTooltipText() {
        return "方块线框颜色\n点击模式下悬停方块的角支架线框颜色";
    }

    private String getEntityTargetTooltipText() {
        return "实体线框颜色\n点击模式下悬停实体的角支架线框颜色";
    }

    private String getSelWireframeTooltipText() {
        return "框选线框颜色\n选择模式下虚线框的主色段颜色";
    }

    private String getSelGapTooltipText() {
        return "线框间隙颜色\n选择模式下虚线框的间隙段颜色";
    }

    private String getSelOverlayTooltipText() {
        return "覆盖层颜色\n选择模式下预览阶段的半透明填充颜色";
    }
}
