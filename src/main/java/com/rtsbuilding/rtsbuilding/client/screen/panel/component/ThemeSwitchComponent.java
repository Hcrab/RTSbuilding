package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 通用开关组件——渲染一个滑块式开/关切换按钮，支持悬浮高亮和滑动动画。
 *
 * <p>与贴图绑定，滑条背景和滑块根据当前主题（亮/暗）自动选择贴图半区，
 * 开关状态由外部 {@code on} 参数控制。
 * 点击区域在渲染时自动缓存，点击检测无需重复传入坐标。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * ThemeSwitchComponent toggle = new ThemeSwitchComponent();
 *
 * // 渲染（自动缓存点击区域）
 * toggle.render(g, mouseX, mouseY, toggleX, toggleY, isOn);
 *
 * // 点击检测（无需 x/y）
 * if (toggle.handleClick(mouseX, mouseY)) {
 *     toggleState();
 * }
 * }</pre>
 */
public class ThemeSwitchComponent {

    // ======================== 公开常量 ========================

    /** 开关整体尺寸（宽=高） */
    public static final int SIZE = 32;
    /** 滑块尺寸 */
    public static final int SLIDER_SIZE = 16;

    // ======================== 开关贴图参数 ========================

    /** switch_ground.png：48×32，水平双主题（左暗右亮），纵向 2 状态（0-16=关、16-32=开），不使用九宫格 */
    private static final ResourceLocation SWITCH_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/switch_ground.png");
    private static final int SWITCH_TEX_W = 48;
    private static final int SWITCH_TEX_H = 32;
    private static final int SWITCH_FRAME_H = 16;

    /** base_ui_5.png：32×48，横向左右各半（左=暗色，右=明亮），纵向 0-16 常态、16-32 悬浮 */
    private static final ResourceLocation SLIDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_5.png");
    private static final int SLIDER_TEX_W = 32;
    private static final int SLIDER_TEX_H = 48;
    private static final int SLIDER_FRAME_W = 16;
    private static final int SLIDER_FRAME_H = 16;

    private static final int SLIDER_U_DARK  = 0;
    private static final int SLIDER_U_LIGHT = 16;

    private static final int SLIDER_V_DEFAULT       = 0;
    private static final int SLIDER_V_HOVER_DEFAULT  = 16;



    // ======================== 预计算 TextureInfo 常量 ========================

    /** 开关背景贴图元数据（水平双主题，通过 halfWidth() 获取帧宽 24） */
    private static final TextureInfo SWITCH_TEX_INFO = new TextureInfo(
            SWITCH_TEXTURE, SWITCH_TEX_W, SWITCH_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 开关滑块贴图元数据（避免每帧 new） */
    private static final TextureInfo SLIDER_TEX_INFO = new TextureInfo(
            SLIDER_TEXTURE, SLIDER_TEX_W, SLIDER_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    // ======================== 内部状态 ========================

    /** 交互区域水平内缩像素（左右各缩多少） */
    private static final int HITBOX_INSET_H = 0;
    /** 交互区域垂直内缩像素（上下各缩多少） */
    private static final int HITBOX_INSET_V = 9;

    /** 交互区域尺寸 */
    private static final int AREA_W = SIZE - HITBOX_INSET_H * 2;
    private static final int AREA_H = SIZE - HITBOX_INSET_V * 2;

    /** 点击区域缓存坐标 */
    private int areaX, areaY;

    /** 悬浮状态管理器 */
    private final HoverStateManager hoverState = new HoverStateManager();

    /** 滑块滑动动画器（200ms） */
    private final FloatAnimation slideAnim;
    private boolean lastOn;

    public ThemeSwitchComponent() {
        this.slideAnim = AnimationFactory.newSlideAnim();
        this.slideAnim.snapTo(-1.0f);
    }

    // ======================== 渲染 ========================

    /**
     * 渲染开关按钮，同时自动缓存点击区域。
     *
     * @param g         渲染上下文
     * @param mouseX    鼠标 X（用于悬浮检测）
     * @param mouseY    鼠标 Y（用于悬浮检测）
     * @param switchX   开关左上角屏幕 X
     * @param switchY   开关左上角屏幕 Y
     * @param on        当前开关状态（true=开启）
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int switchX, int switchY, boolean on) {
        // 缓存点击区域（水平内缩 HITBOX_INSET_H、垂直内缩 HITBOX_INSET_V 像素）
        this.areaX = switchX + HITBOX_INSET_H;
        this.areaY = switchY + HITBOX_INSET_V;

        boolean lightMode = ThemeManager.getInstance().isLightMode();

        // ---------- 滑块 X 偏移 ----------
        if (on != lastOn) {
            lastOn = on;
            slideAnim.start(on ? (float) (SLIDER_FRAME_W + 1) : -1.0f);
        }
        slideAnim.tick();
        float slideOffset = slideAnim.getValue();

        int sliderX = switchX + Math.round(slideOffset);

        // ---------- 悬浮检测 ----------
        boolean hovered = mouseX >= areaX && mouseX < areaX + AREA_W
                && mouseY >= areaY && mouseY < areaY + AREA_H;

        float hoverT = this.hoverState.update(hovered);

        // ---------- 渲染滑条背景（等比缩放，按 24:16 = 3:2 比例，避免拉伸）---------
        int bgHalfW = SWITCH_TEX_INFO.halfWidth();  // = 24
        int bgH = SIZE * SWITCH_FRAME_H / bgHalfW;  // = 32*16/24 = 21
        int bgY = switchY + (SIZE - bgH) / 2;        // 背景垂直居中

        if (SWITCH_TEXTURE != null) {
            SpriteRegion bgRegion = new SpriteRegion(SWITCH_TEX_INFO, 0, 0, bgHalfW, SWITCH_FRAME_H);
            SpriteRenderer.drawSprite(g, bgRegion.withThemeAndVOffset(on ? SWITCH_FRAME_H : 0),
                    switchX, bgY, SIZE, bgH);
        }

        // ---------- 渲染滑块（在背景范围内垂直居中）---------
        int sliderY = bgY + (bgH - SLIDER_FRAME_H) / 2;
        int sliderU = lightMode ? SLIDER_U_LIGHT : SLIDER_U_DARK;
        int slVDefault = SLIDER_V_DEFAULT;
        int slVHover   = SLIDER_V_HOVER_DEFAULT;

        if (SLIDER_TEXTURE != null) {
            renderSwitchFrame(g, SLIDER_TEX_INFO,
                    sliderX, sliderY, SLIDER_FRAME_W, SLIDER_FRAME_H,
                    sliderU, slVDefault, slVHover, hoverT);
        }
    }

    // ======================== 交互 ========================

    /**
     * 检测是否点击了开关区域（坐标来自上次 render 的缓存）。
     *
     * @param mouseX    鼠标 X
     * @param mouseY    鼠标 Y
     * @return true 如果点击了开关区域（调用方应切换状态）
     */
    public boolean handleClick(double mouseX, double mouseY) {
        return mouseX >= areaX && mouseX < areaX + AREA_W
                && mouseY >= areaY && mouseY < areaY + AREA_H;
    }

    // ======================== 内部工具 ========================

    private static void renderSwitchFrame(GuiGraphics g, TextureInfo texInfo,
                                          int screenX, int screenY, int sw, int sh,
                                          int u, int vDefault, int vHover, float t) {
        SpriteRegion normal = new SpriteRegion(texInfo, u, vDefault, sw, sh);
        SpriteRegion hovered = new SpriteRegion(texInfo, u, vHover, sw, sh);
        CrossFadeRenderer.render(t,
                () -> SpriteRenderer.drawSprite(g, normal, screenX, screenY, sw, sh),
                () -> SpriteRenderer.drawSprite(g, hovered, screenX, screenY, sw, sh));
    }
}
