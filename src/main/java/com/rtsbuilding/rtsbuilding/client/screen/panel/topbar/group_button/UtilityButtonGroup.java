package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.group_button;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup.DebugMenuPopup;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 工具按钮组——管理"区块显示"和"右侧设置"两个按钮。
 *
 * <p>纹理索引：0=btn_right（组内右侧）、1=chunk_display（组内左侧）。
 * btn_right 展开 Debug 弹出菜单，chunk_display 切换辅助显示。</p>
 *
 * <p>额外渲染：btn_right 内部绘制折叠箭头旋转动画；
 * chunk_display 带快捷键浮窗。</p>
 */
public final class UtilityButtonGroup extends AbstractButtonGroup {

    // ======================== 位置背景贴图 ========================

    /** up_button.png —— 首位（左侧）按钮背景 */
    private static final ResourceLocation DOWN_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/down_button.png");
    /** middle_button.png —— 中间按钮背景 */
    private static final ResourceLocation MIDDLE_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/middle_button.png");
    /** up_button.png —— 末位（右侧）按钮背景 */
    private static final ResourceLocation UP_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/up_button.png");

    private static final ResourceLocation CHUNK_DISPLAY =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button/chunk_display.png");

    // 折叠箭头（仅 btn_right 使用）
    private static final ResourceLocation FOLD_ARROW =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/base/fold_arrow.png");
    private static final int FOLD_ARROW_HALF_W = 512;
    private static final int FOLD_ARROW_STATE_H = 512;
    /** 折叠箭头贴图文件总宽度（双主题翻倍） */
    private static final int FOLD_ARROW_TEX_W = 1024;
    /** 折叠箭头贴图文件总高度 */
    private static final int FOLD_ARROW_TEX_H = 1024;
    private static final int FOLD_ARROW_SIZE = 8;

    /** 折叠箭头贴图元数据（避免每帧 new） */
    private static final TextureInfo FOLD_ARROW_TEX_INFO = new TextureInfo(
            FOLD_ARROW, FOLD_ARROW_TEX_W, FOLD_ARROW_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private final DebugMenuPopup debugPopup;

    // ----- chunk_display -----
    private final TooltipController chunkBtnTooltip = TooltipController.builder().build();

    // ----- 折叠箭头 -----
    private final FloatAnimation arrowRotateAnim = AnimationFactory.newExpandAnim();
    private boolean prevArrowActive;

    public UtilityButtonGroup(DebugMenuPopup debugPopup) {
        // HORIZONTAL 方向，顶栏按钮大小 14px，0间隙，有背景层
        super(Direction.HORIZONTAL, TopBarLayoutHelper.BTN_SIZE, DEFAULT_INNER_GAP, true,
                DOWN_BG, MIDDLE_BG, UP_BG,
                null, CHUNK_DISPLAY);
        this.debugPopup = debugPopup;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        // 同步选中态
        selected[0] = debugPopup != null && debugPopup.isOpen();            // btn_right
        selected[1] = debugPopup != null && debugPopup.isDebugOverlayEnabled(); // chunk_display
        super.render(g, mouseX, mouseY, group);
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        tickChunkTooltip(mouseX, mouseY, group);
        renderFoldArrow(g, group);
    }

    // ======================== 快捷键浮窗（chunk_display） ========================

    private void tickChunkTooltip(int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        var rect = group.rect(1);
        boolean hovered = rect.contains(mouseX, mouseY);
        boolean popupOpen = debugPopup != null && debugPopup.isOpen();
                chunkBtnTooltip.update(hovered, popupOpen);
    }

    /**
     * 在覆盖层渲染阶段绘制浮窗，确保浮窗在所有 UI 之上。
     */
    public void renderTooltipOverlay(GuiGraphics g, TopBarLayoutHelper.ButtonGroup group,
                                      int screenW, int screenH) {
        if (!chunkBtnTooltip.shouldRender()) return;

        var rect = group.rect(1);
        String keyText = RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY.getTranslatedKeyMessage().getString();
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
        String text = Component.translatable("tooltip.rtsbuilding.debug.overlay").getString() + "\n"
                + Component.translatable("tooltip.rtsbuilding.debug.overlay.desc").getString() + "\n"
                + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        chunkBtnTooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                text, textColor, shortcutColor, screenW, screenH);
    }

    // ======================== 折叠箭头（btn_right） ========================

    private void renderFoldArrow(GuiGraphics g, TopBarLayoutHelper.ButtonGroup group) {
        var rect = group.rect(0);
        boolean arrowActive = debugPopup != null && debugPopup.isOpen();
        if (arrowActive != prevArrowActive) {
            prevArrowActive = arrowActive;
            arrowRotateAnim.start(arrowActive ? 1.0f : 0.0f);
        }
        arrowRotateAnim.tick();

        int arrowX = rect.x() + (rect.width() - FOLD_ARROW_SIZE) / 2;
        int arrowY = rect.y() + (rect.height() - FOLD_ARROW_SIZE) / 2;
        g.pose().pushPose();
        float halfArrow = FOLD_ARROW_SIZE / 2.0f;
        g.pose().translate(arrowX + halfArrow, arrowY + halfArrow, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(arrowRotateAnim.getValue() * 90.0f));
        g.pose().translate(-halfArrow, -halfArrow, 0);
        SpriteRegion arrowRegion = new SpriteRegion(
                FOLD_ARROW_TEX_INFO, 0, 0, FOLD_ARROW_HALF_W, FOLD_ARROW_STATE_H).withTheme();
        SpriteRenderer.drawSprite(g, arrowRegion, 0, 0, FOLD_ARROW_SIZE, FOLD_ARROW_SIZE);
        g.pose().popPose();
    }

    // ======================== 点击处理 ========================

    @Override
    protected void onButtonClick(int index) {
        if (index == 0) {
            // btn_right：切换 Debug 弹出菜单
            if (debugPopup != null) debugPopup.toggle();
        } else {
            // chunk_display：切换辅助显示
            if (debugPopup != null) debugPopup.toggleDebugOverlay();
        }
    }

    // ======================== 弹窗锚点 ========================

    /** 获取 Debug 弹出菜单锚定矩形（btn_right 的位置） */
    public TopBarLayoutHelper.Rect getPopupAnchor(TopBarLayoutHelper.ButtonGroup group) {
        return group.rect(0);
    }
}

