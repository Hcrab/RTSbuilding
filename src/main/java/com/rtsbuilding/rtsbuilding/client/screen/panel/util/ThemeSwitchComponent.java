package com.rtsbuilding.rtsbuilding.client.screen.panel.util;

import com.rtsbuilding.rtsbuilding.client.screen.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 主题开关组件——渲染一个滑块式亮暗主题切换开关，支持悬浮高亮和滑动动画。
 *
 * <p>使用双主题拼接贴图，横向左右各半（左=暗色，右=明亮），
 * 纵向 4 帧状态：默认、悬浮默认、切换、悬浮切换。
 * 滑块与滑条均独立支持交叉淡入淡出动画。</p>
 */
public class ThemeSwitchComponent {

    // ======================== 公开常量 ========================

    /** 开关整体尺寸（宽=高） */
    public static final int SIZE = 32;
    /** 滑块尺寸 */
    public static final int SLIDER_SIZE = 16;

    // ======================== 开关贴图参数 ========================

    /** switch_ground.png：64×128，横向左右各半（左=暗色，右=明亮），纵向 4 个 32×32 状态 */
    private static final ResourceLocation SWITCH_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/switch_ground.png");
    private static final int SWITCH_TEX_W = 64;
    private static final int SWITCH_TEX_H = 128;
    private static final int SWITCH_STATE_H = 32;

    /** switch_slider.png：32×64，横向左右各半（左=暗色，右=明亮），纵向 4 个 16×16 状态 */
    private static final ResourceLocation SLIDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/switch_slider.png");
    private static final int SLIDER_TEX_W = 32;
    private static final int SLIDER_TEX_H = 64;
    private static final int SLIDER_FRAME_W = 16;
    private static final int SLIDER_FRAME_H = 16;

    private static final int SLIDER_U_DARK  = 0;
    private static final int SLIDER_U_LIGHT = 16;

    private static final int SLIDER_V_DEFAULT       = 0;
    private static final int SLIDER_V_HOVER_DEFAULT  = 16;
    private static final int SLIDER_V_TOGGLED        = 32;
    private static final int SLIDER_V_HOVER_TOGGLED  = 48;

    private static final int U_DARK          = 0;   // 左侧：暗色主题
    private static final int U_LIGHT          = 32;  // 右侧：明亮主题

    private static final int V_DEFAULT       = 0;   // 默认状态
    private static final int V_HOVER_DEFAULT  = 32;  // 悬浮默认状态
    private static final int V_TOGGLED        = 64;  // 切换状态
    private static final int V_HOVER_TOGGLED  = 96;  // 悬浮切换状态

    // ======================== 动画 ========================

    /** 悬浮态平滑动画器（120ms） */
    private final SmoothAnimator hoverAnim = AnimationFactory.createHoverAnim();
    /** 上一帧悬浮状态 */
    private boolean lastHovered;

    /** 滑块滑动动画器（200ms） */
    private final SmoothAnimator slideAnim = AnimationFactory.createSlideAnim();
    /**
     * 上一帧主题状态——延迟到首次 {@link #render} 时初始化。
     * <p>为什么不在构造时捕获 {@code ThemeManager.isLightMode()}？
     * 因为 {@link RtsScreenUiStateManager#load()} 在 {@code init()} 中恢复持久化主题，
     * 而 ThemeSwitchComponent 在构造时就被创建了，那时持久化数据还没加载。
     * 若此时捕获 {@code lastLightMode}，等 load() 恢复后首次渲染就会误判为「主题变了」，
     * 触发不必要的滑动动画，产生视觉闪烁。</p>
     */
    private boolean lastLightMode;
    /** 是否已完成首次初始化（延迟到首次 render 时） */
    private boolean initialized;

    // ======================== 构造 ========================

    public ThemeSwitchComponent() {
    }

    // ======================== 渲染 ========================

    /**
     * 渲染主题开关。
     *
     * @param g         渲染上下文
     * @param mouseX    鼠标 X（用于悬浮检测）
     * @param mouseY    鼠标 Y（用于悬浮检测）
     * @param switchX   开关左上角屏幕 X
     * @param switchY   开关左上角屏幕 Y
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int switchX, int switchY) {
        // 延迟初始化：此时持久化主题已经通过 load() 恢复，取到的值才是对的
        if (!initialized) {
            initialized = true;
            lastLightMode = ThemeManager.getInstance().isLightMode();
            // 直接跳转到最终位置，不播放动画
            slideAnim.snapTo(lastLightMode ? (float) SLIDER_FRAME_W : 0.0f);
        }

        boolean lightMode = ThemeManager.getInstance().isLightMode();

        // ---------- 滑块 X 偏移（滑条背景内）----------
        if (lightMode != lastLightMode) {
            lastLightMode = lightMode;
            slideAnim.start(lightMode ? (float) SLIDER_FRAME_W : 0.0f);
        }
        slideAnim.tick();
        float slideOffset = slideAnim.getValue();

        int sliderX = switchX + Math.round(slideOffset);
        int sliderY = switchY + (SIZE - SLIDER_FRAME_W) / 2;

        // ---------- 悬浮检测 ----------
        boolean hovered = mouseX >= sliderX && mouseX < sliderX + SLIDER_FRAME_W
                && mouseY >= sliderY && mouseY < sliderY + SLIDER_FRAME_W;

        if (hovered != lastHovered) {
            lastHovered = hovered;
            hoverAnim.start(hovered ? 1.0f : 0.0f);
        }
        hoverAnim.tick();
        float hoverT = hoverAnim.getValue();

        // ---------- 渲染滑条背景 ----------
        int bgU = lightMode ? U_LIGHT : U_DARK;
        int bgVDefault = lightMode ? V_TOGGLED : V_DEFAULT;
        int bgVHover   = lightMode ? V_HOVER_TOGGLED : V_HOVER_DEFAULT;

        if (SWITCH_TEXTURE != null) {
            renderSwitchFrame(g, SWITCH_TEXTURE,
                    switchX, switchY, SIZE, SWITCH_STATE_H,
                    bgU, bgVDefault, bgVHover, hoverT,
                    SWITCH_TEX_W, SWITCH_TEX_H);
        }

        // ---------- 渲染滑块 ----------
        int sliderU = lightMode ? SLIDER_U_LIGHT : SLIDER_U_DARK;
        int slVDefault = lightMode ? SLIDER_V_TOGGLED : SLIDER_V_DEFAULT;
        int slVHover   = lightMode ? SLIDER_V_HOVER_TOGGLED : SLIDER_V_HOVER_DEFAULT;

        if (SLIDER_TEXTURE != null) {
            renderSwitchFrame(g, SLIDER_TEXTURE,
                    sliderX, sliderY, SLIDER_FRAME_W, SLIDER_FRAME_H,
                    sliderU, slVDefault, slVHover, hoverT,
                    SLIDER_TEX_W, SLIDER_TEX_H);
        }
    }

    // ======================== 交互 ========================

    /**
     * 检测是否点击了滑块区域，若命中则切换主题。
     *
     * @param mouseX    鼠标 X
     * @param mouseY    鼠标 Y
     * @param switchX   开关左上角屏幕 X
     * @param switchY   开关左上角屏幕 Y
     * @return true 如果点击了滑块
     */
    public boolean handleClick(double mouseX, double mouseY, int switchX, int switchY) {
        boolean lightMode = ThemeManager.getInstance().isLightMode();
        int sliderX = lightMode ? switchX + SLIDER_FRAME_W : switchX;
        int sliderY = switchY + (SIZE - SLIDER_FRAME_W) / 2;

        if (mouseX >= sliderX && mouseX < sliderX + SLIDER_FRAME_W
                && mouseY >= sliderY && mouseY < sliderY + SLIDER_FRAME_W) {
            ThemeManager.getInstance().toggle();
            return true;
        }
        return false;
    }

    // ======================== 内部工具 ========================

    /**
     * 渲染开关贴图的一帧，支持悬浮态的交叉淡入淡出。
     */
    private static void renderSwitchFrame(GuiGraphics g, ResourceLocation tex,
                                          int screenX, int screenY, int sw, int sh,
                                          int u, int vDefault, int vHover, float t,
                                          int texW, int texH) {
        Runnable normalRender = () -> RtsClientUiUtil.drawPixelImage(g, tex, screenX, screenY, sw, sh,
                u, vDefault, sw, sh, texW, texH);
        Runnable hoverRender = () -> RtsClientUiUtil.drawPixelImage(g, tex, screenX, screenY, sw, sh,
                u, vHover, sw, sh, texW, texH);
        RtsClientUiUtil.renderCrossFade(t, normalRender, hoverRender);
    }
}
