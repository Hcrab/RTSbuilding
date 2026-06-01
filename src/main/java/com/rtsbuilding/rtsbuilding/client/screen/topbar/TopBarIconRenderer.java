package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * 顶部栏按钮图标渲染器。
 * <p>
 * 负责绘制顶部栏中各个模式/功能按钮的像素图标，
 * 以及管理模式按钮的纹理资源选择逻辑。
 * 所有方法均为静态工具方法，无副作用。
 */
public final class TopBarIconRenderer {

    // ======================== 纹理选择 ========================

    /**
     * 根据按钮状态返回对应的纹理 ResourceLocation。
     *
     * @param id      按钮标识
     * @param active  是否激活
     * @param hovered 是否悬停
     * @param pressed 是否按下
     * @return 纹理路径，若无纹理则返回 null（使用像素绘制）
     */
    public static ResourceLocation topbarModeTexture(TopBarButtonId id, boolean active, boolean hovered, boolean pressed) {
        String state = active ? "active" : pressed ? "pressed" : hovered ? "hover" : "inactive";
        return switch (id) {
            case INTERACT -> switch (state) {
                case "active" -> TOPBAR_INTERACT_ACTIVE;
                case "pressed" -> TOPBAR_INTERACT_PRESSED;
                case "hover" -> TOPBAR_INTERACT_HOVER;
                default -> TOPBAR_INTERACT_INACTIVE;
            };
            case LINK -> switch (state) {
                case "active" -> TOPBAR_LINK_ACTIVE;
                case "pressed" -> TOPBAR_LINK_PRESSED;
                case "hover" -> TOPBAR_LINK_HOVER;
                default -> TOPBAR_LINK_INACTIVE;
            };
            case FUNNEL -> switch (state) {
                case "active" -> TOPBAR_FUNNEL_ACTIVE;
                case "pressed" -> TOPBAR_FUNNEL_PRESSED;
                case "hover" -> TOPBAR_FUNNEL_HOVER;
                default -> TOPBAR_FUNNEL_INACTIVE;
            };
            case ROTATE -> switch (state) {
                case "active" -> TOPBAR_ROTATE_ACTIVE;
                case "pressed" -> TOPBAR_ROTATE_PRESSED;
                case "hover" -> TOPBAR_ROTATE_HOVER;
                default -> TOPBAR_ROTATE_INACTIVE;
            };
            case QUICK_BUILD -> switch (state) {
                case "active" -> TOPBAR_QUICK_BUILD_ACTIVE;
                case "pressed" -> TOPBAR_QUICK_BUILD_PRESSED;
                case "hover" -> TOPBAR_QUICK_BUILD_HOVER;
                default -> TOPBAR_QUICK_BUILD_INACTIVE;
            };
            case ULTIMINE -> switch (state) {
                case "active" -> TOPBAR_ULTIMINE_ACTIVE;
                case "pressed" -> TOPBAR_ULTIMINE_PRESSED;
                case "hover" -> TOPBAR_ULTIMINE_HOVER;
                default -> TOPBAR_ULTIMINE_INACTIVE;
            };
            case QUEST_DETECT -> switch (state) {
                case "active" -> TOPBAR_QUEST_DETECT_ACTIVE;
                case "pressed" -> TOPBAR_QUEST_DETECT_PRESSED;
                case "hover" -> TOPBAR_QUEST_DETECT_HOVER;
                default -> TOPBAR_QUEST_DETECT_INACTIVE;
            };
            case CHUNK_VIEW -> switch (state) {
                case "active" -> TOPBAR_CHUNK_VIEW_ACTIVE;
                case "pressed" -> TOPBAR_CHUNK_VIEW_PRESSED;
                case "hover" -> TOPBAR_CHUNK_VIEW_HOVER;
                default -> TOPBAR_CHUNK_VIEW_INACTIVE;
            };
            case GEAR -> switch (state) {
                case "active" -> TOPBAR_GEAR_ACTIVE;
                case "pressed" -> TOPBAR_GEAR_PRESSED;
                case "hover" -> TOPBAR_GEAR_HOVER;
                default -> TOPBAR_GEAR_INACTIVE;
            };
            default -> null;
        };
    }

    // ======================== 图标绘制方法 ========================

    /** 绘制交互模式图标（阶梯状箭头） */
    public static void drawInteractModeIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 7, cy - 8, cx - 5, cy + 7, color);
        g.fill(cx - 5, cy - 6, cx - 3, cy + 5, color);
        g.fill(cx - 3, cy - 4, cx - 1, cy + 3, color);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, color);
        g.fill(cx + 2, cy, cx + 5, cy + 3, color);
        g.fill(cx - 2, cy + 3, cx + 1, cy + 8, color);
        g.fill(cx + 1, cy + 6, cx + 4, cy + 8, color);
        g.fill(cx + 4, cy - 7, cx + 7, cy - 4, 0x6688BEF4);
        g.fill(cx + 5, cy - 6, cx + 6, cy - 5, color);
    }

    /** 绘制链接模式图标（双链环） */
    public static void drawLinkModeIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int left = active ? 0xFF88BEF4 : color;
        int right = active ? 0xFF78B28C : color;
        drawMiniChainLoop(g, cx - 5, cy + 1, left);
        drawMiniChainLoop(g, cx + 5, cy - 1, right);
        g.fill(cx - 3, cy - 1, cx + 4, cy + 1, color);
        g.fill(cx - 2, cy, cx + 3, cy + 2, color);
    }

    /** 绘制漏斗模式图标（漏斗形状） */
    public static void drawFunnelModeIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int top = active ? 0xFFFFC472 : color;
        int mid = active ? 0xFF78B28C : color;
        int tip = active ? 0xFF88BEF4 : color;
        g.fill(cx - 8, cy - 7, cx + 8, cy - 5, top);
        g.fill(cx - 7, cy - 5, cx + 7, cy - 3, top);
        g.fill(cx - 5, cy - 3, cx + 5, cy - 1, mid);
        g.fill(cx - 3, cy - 1, cx + 3, cy + 1, mid);
        g.fill(cx - 1, cy + 1, cx + 1, cy + 7, tip);
        g.fill(cx + 1, cy + 5, cx + 4, cy + 7, tip);
    }

    /** 绘制旋转模式图标（旋转箭头） */
    public static void drawRotateModeIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 5, cy - 7, cx + 5, cy - 5, color);
        g.fill(cx + 4, cy - 6, cx + 7, cy - 2, color);
        g.fill(cx + 6, cy - 2, cx + 8, cy + 1, color);
        g.fill(cx + 3, cy - 8, cx + 8, cy - 5, color);
        g.fill(cx + 5, cy - 4, cx + 8, cy - 1, color);
        g.fill(cx - 8, cy - 1, cx - 6, cy + 3, color);
        g.fill(cx - 7, cy + 3, cx - 4, cy + 6, color);
        g.fill(cx - 5, cy + 5, cx + 5, cy + 7, color);
        g.fill(cx - 8, cy + 4, cx - 3, cy + 7, color);
        g.fill(cx - 8, cy + 1, cx - 5, cy + 4, color);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF1B222C);
        g.hLine(cx - 3, cx + 3, cy - 3, color);
        g.hLine(cx - 3, cx + 3, cy + 3, color);
        g.vLine(cx - 3, cy - 3, cy + 3, color);
        g.vLine(cx + 3, cy - 3, cy + 3, color);
    }

    /** 绘制快速建造图标（T 形加小旗） */
    public static void drawQuickBuildIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int accent = active ? 0xFFFFC96B : color;
        g.fill(cx - 8, cy - 6, cx + 6, cy - 4, accent);
        g.fill(cx - 8, cy - 6, cx - 6, cy + 6, accent);
        g.fill(cx - 8, cy + 4, cx + 6, cy + 6, accent);
        g.fill(cx + 4, cy - 6, cx + 6, cy + 6, accent);
        g.fill(cx - 4, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
        g.fill(cx + 3, cy - 9, cx + 8, cy - 4, color);
        g.fill(cx + 5, cy - 7, cx + 10, cy - 2, color);
    }

    /** 绘制连锁挖掘图标（镐头） */
    public static void drawUltimineIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int head = active ? 0xFF78B28C : color;
        g.fill(cx - 8, cy - 7, cx - 1, cy - 5, head);
        g.fill(cx - 6, cy - 5, cx + 1, cy - 3, head);
        g.fill(cx + 1, cy - 3, cx + 3, cy - 1, color);
        g.fill(cx + 2, cy - 1, cx + 5, cy + 7, color);
        g.fill(cx + 5, cy - 8, cx + 7, cy - 3, 0xFFFFC96B);
        g.fill(cx + 3, cy - 6, cx + 9, cy - 5, 0xFFFFC96B);
    }

    /** 绘制任务检测图标（对勾形状） */
    public static void drawQuestCheckIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 7, cy + 1, cx - 3, cy + 5, color);
        g.fill(cx - 4, cy + 4, cx, cy + 8, color);
        g.fill(cx - 1, cy + 1, cx + 3, cy + 5, color);
        g.fill(cx + 2, cy - 2, cx + 6, cy + 2, color);
        g.fill(cx + 5, cy - 5, cx + 9, cy - 1, color);
    }

    /** 绘制区块分隔线图标（网格状） */
    public static void drawChunkCurtainIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int glow = active ? 0x4488BEF4 : 0x221D2530;
        g.fill(cx - 8, cy - 7, cx + 8, cy + 7, glow);
        g.fill(cx - 7, cy - 6, cx - 6, cy + 6, color);
        g.fill(cx - 1, cy - 6, cx, cy + 6, color);
        g.fill(cx + 5, cy - 6, cx + 6, cy + 6, color);
        g.fill(cx - 7, cy - 6, cx + 6, cy - 5, color);
        g.fill(cx - 7, cy, cx + 6, cy + 1, color);
        g.fill(cx - 7, cy + 6, cx + 6, cy + 7, color);
    }

    /** 绘制齿轮图标（设置） */
    public static void drawGearIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 2, cy - 8, cx + 2, cy - 5, color);
        g.fill(cx - 2, cy + 5, cx + 2, cy + 8, color);
        g.fill(cx - 8, cy - 2, cx - 5, cy + 2, color);
        g.fill(cx + 5, cy - 2, cx + 8, cy + 2, color);
        g.fill(cx - 6, cy - 6, cx - 3, cy - 3, color);
        g.fill(cx + 3, cy - 6, cx + 6, cy - 3, color);
        g.fill(cx - 6, cy + 3, cx - 3, cy + 6, color);
        g.fill(cx + 3, cy + 3, cx + 6, cy + 6, color);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, color);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF1B222C);
    }

    /** 绘制调试图标 */
    public static void drawDebugIcon(GuiGraphics g, int cx, int cy, int color, net.minecraft.client.gui.Font font) {
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0x3328D4FF);
        g.fill(cx - 5, cy - 5, cx + 5, cy + 5, color);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
        g.drawCenteredString(font, "D", cx, cy - 4, 0xFF1B222C);
    }

    // ======================== 内部辅助绘制 ========================

    /** 绘制迷你链环 */
    private static void drawMiniChainLoop(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 5, cy - 4, cx + 5, cy - 2, color);
        g.fill(cx - 5, cy + 2, cx + 5, cy + 4, color);
        g.fill(cx - 5, cy - 3, cx - 3, cy + 3, color);
        g.fill(cx + 3, cy - 3, cx + 5, cy + 3, color);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
    }

    private TopBarIconRenderer() {
        // 工具类，禁止实例化
    }
}
