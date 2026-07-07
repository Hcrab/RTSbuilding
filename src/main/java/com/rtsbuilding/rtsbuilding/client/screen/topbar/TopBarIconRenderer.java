package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Renders pixel-art icons and texture-based icons for the top bar buttons.
 * <p>
 * This utility class provides two main entry points:
 * <ul>
 *   <li>{@link #renderIcon(TopBarTypes.TopBarButtonId, GuiGraphics, int, int, int, boolean, Font)} -
 *       A single dispatch method that selects and draws the correct icon for a given button type.</li>
 *   <li>{@link #topbarModeTexture(TopBarTypes.TopBarButtonId, boolean, boolean, boolean)} -
     *       Selects a {@link ResourceLocation} for texture-based icons (INTERACT, LINK, FUNNEL, ROTATE,
     *       QUICK_BUILD, QUEST_DETECT, CHUNK_VIEW, GEAR).</li>
 * </ul>
 * <p>
 * All methods are static and side-effect free. Instantiation is not allowed.
 *
 * @see TopBarTypes.TopBarButtonId
 */
public final class TopBarIconRenderer {
    private static final int RANGE_CULLING_ACTIVE_MARKER_HALF_WIDTH = 9;
    private static final int RANGE_CULLING_ACTIVE_MARKER_TOP_OFFSET = 8;
    private static final int RANGE_CULLING_ACTIVE_MARKER_HEIGHT = 2;

    // ======================== Public API ========================

    /**
     * Renders the pixel-art icon for the given button type at the specified center position.
     * <p>
     * This is the single public dispatch method. It delegates to the appropriate private
     * drawing method based on {@code id}. Callers no longer need to switch on button type
     * before calling this method.
     *
     * @param id     the button identifier whose icon should be drawn
     * @param g      the {@link GuiGraphics} used for rendering
     * @param cx     the X coordinate of the icon center
     * @param cy     the Y coordinate of the icon center
     * @param color  the base ARGB color for the icon outline or fill
     * @param active whether the button is in its active (toggled-on) state; affects accent colors
     * @param font   the {@link Font} used for text-based icons (DEBUG); may be {@code null} for
     *               purely pixel-art icons
     */
    public static void renderIcon(TopBarTypes.TopBarButtonId id, GuiGraphics g, int cx, int cy, int color, boolean active, Font font) {
        switch (id) {
            case INTERACT -> drawInteractModeIcon(g, cx, cy, color);
            case LINK -> drawLinkModeIcon(g, cx, cy, color, active);
            case FUNNEL -> drawFunnelModeIcon(g, cx, cy, color, active);
            case ROTATE -> drawRotateModeIcon(g, cx, cy, color);
            case QUICK_BUILD -> drawQuickBuildIcon(g, cx, cy, color, active);
            case QUEST_DETECT -> drawQuestCheckIcon(g, cx, cy, color);
            case CHUNK_VIEW -> drawChunkCurtainIcon(g, cx, cy, color, active);
            case RANGE_CULLING -> drawRangeCullingIcon(g, cx, cy, color, active);
            case DEBUG -> drawDebugIcon(g, cx, cy, color, font);
            case GEAR -> drawGearIcon(g, cx, cy, color);
            default -> {
                // No icon defined for this button type (e.g. GUIDE is rendered as plain text elsewhere)
            }
        }
    }

    /**
     * Selects the appropriate texture {@link ResourceLocation} for a top bar button
     * based on its current visual state.
     * <p>
     * Each button type has four texture variants: inactive, hover, active, and pressed.
     * The method resolves these from the constants defined in
     * {@link com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants}.
     * <p>
     * Buttons without a texture-based icon (e.g. DEBUG, GUIDE) return {@code null}.
     *
     * @param id      the button identifier
     * @param active  whether the button is in its activated/toggled state
     * @param hovered whether the mouse is hovering over the button
     * @param pressed whether the mouse button is pressed on this button
     * @return the {@link ResourceLocation} for the resolved texture, or {@code null} if
     *         this button type has no texture icon and should fall back to pixel-art drawing
     */
    public static ResourceLocation topbarModeTexture(TopBarTypes.TopBarButtonId id, boolean active, boolean hovered, boolean pressed) {
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

    // ======================== Private Pixel-Art Icon Methods ========================

    /**
     * Draws the INTERACT mode icon: a stepped arrow pointing from bottom-left toward top-right.
     * A small blue accent highlight appears at the tip when hovered/active.
     */
    private static void drawInteractModeIcon(GuiGraphics g, int cx, int cy, int color) {
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

    /**
     * Draws the LINK mode icon: two interlocking chain loop segments.
     * When active, the left loop gets a blue tint and the right loop a green tint.
     */
    private static void drawLinkModeIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int left = active ? 0xFF88BEF4 : color;
        int right = active ? 0xFF78B28C : color;
        drawMiniChainLoop(g, cx - 5, cy + 1, left);
        drawMiniChainLoop(g, cx + 5, cy - 1, right);
        g.fill(cx - 3, cy - 1, cx + 4, cy + 1, color);
        g.fill(cx - 2, cy, cx + 3, cy + 2, color);
    }

    /**
     * Draws the FUNNEL mode icon: a funnel / filter shape narrowing from a wide top
     * to a narrow tube at the bottom. When active, the top, middle, and tip use distinct
     * warm accent colors.
     */
    private static void drawFunnelModeIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
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

    /**
     * Draws the ROTATE mode icon: a circular rotation arrow with a hollow center.
     * The arrow loops clockwise with a small cutout in the middle.
     */
    private static void drawRotateModeIcon(GuiGraphics g, int cx, int cy, int color) {
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

    /**
     * Draws the QUICK BUILD icon: a T-shaped bracket representing a building structure
     * with a small flag extending to the upper-right. When active, the bracket gets
     * a warm yellow accent.
     */
    private static void drawQuickBuildIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int accent = active ? 0xFFFFC96B : color;
        g.fill(cx - 8, cy - 6, cx + 6, cy - 4, accent);
        g.fill(cx - 8, cy - 6, cx - 6, cy + 6, accent);
        g.fill(cx - 8, cy + 4, cx + 6, cy + 6, accent);
        g.fill(cx + 4, cy - 6, cx + 6, cy + 6, accent);
        g.fill(cx - 4, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
        g.fill(cx + 3, cy - 9, cx + 8, cy - 4, color);
        g.fill(cx + 5, cy - 7, cx + 10, cy - 2, color);
    }

    /**
     * Draws the QUEST DETECT icon: a checkmark / tick shape.
     */
    private static void drawQuestCheckIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 7, cy + 1, cx - 3, cy + 5, color);
        g.fill(cx - 4, cy + 4, cx, cy + 8, color);
        g.fill(cx - 1, cy + 1, cx + 3, cy + 5, color);
        g.fill(cx + 2, cy - 2, cx + 6, cy + 2, color);
        g.fill(cx + 5, cy - 5, cx + 9, cy - 1, color);
    }

    /**
     * Draws the CHUNK VIEW icon: a grid pattern representing chunk boundaries.
     * When active, the background gets a subtle blue glow.
     */
    private static void drawChunkCurtainIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int glow = active ? 0x4488BEF4 : 0x221D2530;
        g.fill(cx - 8, cy - 7, cx + 8, cy + 7, glow);
        g.fill(cx - 7, cy - 6, cx - 6, cy + 6, color);
        g.fill(cx - 1, cy - 6, cx, cy + 6, color);
        g.fill(cx + 5, cy - 6, cx + 6, cy + 6, color);
        g.fill(cx - 7, cy - 6, cx + 6, cy - 5, color);
        g.fill(cx - 7, cy, cx + 6, cy + 1, color);
        g.fill(cx - 7, cy + 6, cx + 6, cy + 7, color);
    }

    /**
     * 绘制范围剔除按钮：空心立方体表示被隐藏区域，斜线表示射线会穿透。
     */
    private static void drawRangeCullingIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int fill = active ? 0x884AA8FF : 0x221D2530;
        int slash = active ? 0xFFFFD45A : 0xFF88BEF4;
        if (active) {
            int markerY = cy + RANGE_CULLING_ACTIVE_MARKER_TOP_OFFSET;
            g.fill(cx - RANGE_CULLING_ACTIVE_MARKER_HALF_WIDTH,
                    markerY,
                    cx + RANGE_CULLING_ACTIVE_MARKER_HALF_WIDTH,
                    markerY + RANGE_CULLING_ACTIVE_MARKER_HEIGHT,
                    0xFFFFD45A);
        }
        g.fill(cx - 8, cy - 6, cx + 5, cy + 6, fill);
        g.hLine(cx - 8, cx + 4, cy - 6, color);
        g.hLine(cx - 8, cx + 4, cy + 6, color);
        g.vLine(cx - 8, cy - 6, cy + 6, color);
        g.vLine(cx + 5, cy - 6, cy + 6, color);
        g.hLine(cx - 4, cx + 8, cy - 9, color);
        g.vLine(cx + 8, cy - 9, cy + 3, color);
        g.fill(cx - 4, cy - 9, cx - 2, cy - 6, color);
        g.fill(cx + 5, cy + 3, cx + 8, cy + 6, color);
        g.fill(cx - 7, cy + 5, cx - 4, cy + 8, slash);
        g.fill(cx - 4, cy + 2, cx - 1, cy + 5, slash);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, slash);
        g.fill(cx + 2, cy - 4, cx + 5, cy - 1, slash);
        g.fill(cx + 5, cy - 7, cx + 8, cy - 4, slash);
    }

    /**
     * Draws the GEAR (settings) icon: a cog shape with four outer notches and a solid center.
     * The center contains a small dark hole.
     */
    private static void drawGearIcon(GuiGraphics g, int cx, int cy, int color) {
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

    /**
     * Draws the DEBUG icon: a rounded square with a letter "D" and a cyan glow.
     */
    private static void drawDebugIcon(GuiGraphics g, int cx, int cy, int color, Font font) {
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0x3328D4FF);
        g.fill(cx - 5, cy - 5, cx + 5, cy + 5, color);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
        g.drawCenteredString(font, "D", cx, cy - 4, 0xFF1B222C);
    }

    // ======================== Internal Helper ========================

    /**
     * Draws a small chain loop segment used by {@link #drawLinkModeIcon}.
     * Renders a rectangular ring with a dark center void.
     */
    private static void drawMiniChainLoop(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 5, cy - 4, cx + 5, cy - 2, color);
        g.fill(cx - 5, cy + 2, cx + 5, cy + 4, color);
        g.fill(cx - 5, cy - 3, cx - 3, cy + 3, color);
        g.fill(cx + 3, cy - 3, cx + 5, cy + 3, color);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
    }

    private TopBarIconRenderer() {
        // Utility class: prevent instantiation
    }
}
