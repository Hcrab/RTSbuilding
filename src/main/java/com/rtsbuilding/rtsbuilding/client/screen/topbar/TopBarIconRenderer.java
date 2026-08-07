package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiStateBlendAnimationSet;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Renders pixel-art icons and texture-based icons for the top bar buttons.
 * <p>
 * This utility class provides two main entry points:
 * <ul>
 *   <li>{@link #renderIcon(TopBarTypes.TopBarButtonId, GuiGraphicsExtractor, int, int, int, boolean, Font)} -
 *       A single dispatch method that selects and draws the correct icon for a given button type.</li>
 *   <li>{@link #topbarModeTexture(TopBarTypes.TopBarButtonId, boolean, boolean, boolean)} -
     *       Selects a {@link Identifier} for texture-based icons (INTERACT, LINK, FUNNEL, ROTATE,
     *       QUICK_BUILD, QUEST_DETECT, CHUNK_VIEW, GEAR).</li>
 * </ul>
 * <p>
 * All methods are static and side-effect free. Instantiation is not allowed.
 *
 * @see TopBarTypes.TopBarButtonId
 */
public final class TopBarIconRenderer {
    private static final double MIN_VISIBLE_WEIGHT = 0.001D;
    private static final List<UiTextureState> VISUAL_STATES =
            Collections.unmodifiableList(Arrays.asList(UiTextureState.values()));
    // ======================== Public API ========================

    /**
     * 在 26.1 Extractor 中按状态权重提交现有像素图，不复制按钮轮廓或命中区域。
     *
     * @return 是否至少有一张状态贴图被提交；false 时调用方绘制主题化矢量回退。
     */
    public static boolean renderBlended(
            GuiGraphicsExtractor graphics,
            TopBarTypes.TopBarButtonId id,
            int x, int y, int size,
            UiTextureState target,
            UiStateBlendAnimationSet<TopBarTypes.TopBarButtonId, UiTextureState> transitions,
            boolean animationsEnabled) {
        transitions.update(id, target, animationsEnabled);
        boolean rendered = false;
        for (UiTextureState state : VISUAL_STATES) {
            double weight = transitions.weight(id, state);
            if (weight <= MIN_VISIBLE_WEIGHT) {
                continue;
            }
            Identifier texture = texture(id, state);
            if (texture == null) {
                continue;
            }
            RtsTextureRenderer.drawTextureHighPrecision(
                    graphics, texture, x, y, size, size,
                    0.0F, 0.0F, size, size, size, size, 0.0F,
                    tint(state, weight));
            rendered = true;
        }
        return rendered;
    }

    public static List<UiTextureState> visualStates() {
        return VISUAL_STATES;
    }

    public static UiTextureState visualState(boolean active, boolean hovered, boolean pressed) {
        if (pressed) {
            return UiTextureState.PRESSED;
        }
        if (active) {
            return UiTextureState.ACTIVE;
        }
        return hovered ? UiTextureState.HOVER : UiTextureState.INACTIVE;
    }

    public static Identifier texture(TopBarTypes.TopBarButtonId id, UiTextureState state) {
        return switch (state) {
            case HOVER -> topbarModeTexture(id, false, true, false);
            case PRESSED -> topbarModeTexture(id, false, false, true);
            case ACTIVE -> topbarModeTexture(id, true, false, false);
            case INACTIVE -> topbarModeTexture(id, false, false, false);
        };
    }

    private static int tint(UiTextureState state, double weight) {
        int color = UiThemeRuntime.manager().active().renderMode()
                == UiThemeRenderMode.LEGACY_DIRECT ? RtsMainlineTheme.LEGACY_FFFFFFFF.toArgb() : switch (state) {
                    case ACTIVE -> RtsMainlineTheme.CONTROL_SELECTED_BACKGROUND.toArgb();
                    case PRESSED -> RtsMainlineTheme.CONTROL_PRESSED_BACKGROUND.toArgb();
                    case HOVER -> RtsMainlineTheme.CONTROL_HOVER_BACKGROUND.toArgb();
                    case INACTIVE -> RtsMainlineTheme.CONTROL_IDLE_BACKGROUND.toArgb();
                };
        int alpha = (int) Math.round((color >>> 24 & 0xFF)
                * Math.max(0.0D, Math.min(1.0D, weight)));
        return alpha << 24 | color & RtsMainlineTheme.LEGACY_00FFFFFF.toArgb();
    }

    /**
     * Renders the pixel-art icon for the given button type at the specified center position.
     * <p>
     * This is the single public dispatch method. It delegates to the appropriate private
     * drawing method based on {@code id}. Callers no longer need to switch on button type
     * before calling this method.
     *
     * @param id     the button identifier whose icon should be drawn
     * @param g      the {@link GuiGraphicsExtractor} used for rendering
     * @param cx     the X coordinate of the icon center
     * @param cy     the Y coordinate of the icon center
     * @param color  the base ARGB color for the icon outline or fill
     * @param active whether the button is in its active (toggled-on) state; affects accent colors
     * @param font   the {@link Font} used for text-based icons; may be {@code null} for
     *               purely pixel-art icons
     */
    /**
     * 兼容旧调用方的纹理图标入口；正式顶栏图标始终从四态资源提交。
     */
    public static void renderIcon(TopBarTypes.TopBarButtonId id, GuiGraphicsExtractor g,
                                  int cx, int cy, int color, boolean active, Font font) {
        Identifier icon = texture(id, active ? UiTextureState.ACTIVE : UiTextureState.INACTIVE);
        if (icon == null) {
            return;
        }
        int size = 18;
        RtsTextureRenderer.drawTextureHighPrecision(
                g, icon, cx - size / 2.0F, cy - size / 2.0F,
                size, size, 0.0F, 0.0F, size, size, size, size, 0.0F,
                color == 0 ? RtsMainlineTheme.LEGACY_FFFFFFFF.toArgb() : color);
    }
    /**
     * Selects the appropriate texture {@link Identifier} for a top bar button
     * based on its current visual state.
     * <p>
     * Each button type has four texture variants: inactive, hover, active, and pressed.
     * The method resolves these from the constants defined in
     * {@link com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants}.
     * <p>
     * Buttons without a texture-based icon (e.g. GUIDE) return {@code null}.
     *
     * @param id      the button identifier
     * @param active  whether the button is in its activated/toggled state
     * @param hovered whether the mouse is hovering over the button
     * @param pressed whether the mouse button is pressed on this button
     * @return the {@link Identifier} for the resolved texture, or {@code null} if
     *         this button type has no texture icon and should fall back to pixel-art drawing
     */
    public static Identifier topbarModeTexture(TopBarTypes.TopBarButtonId id, boolean active, boolean hovered, boolean pressed) {
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
            case RANGE_CULLING -> switch (state) {
                case "active" -> TOPBAR_RANGE_CULLING_ACTIVE;
                case "pressed" -> TOPBAR_RANGE_CULLING_PRESSED;
                case "hover" -> TOPBAR_RANGE_CULLING_HOVER;
                default -> TOPBAR_RANGE_CULLING_INACTIVE;
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

    private TopBarIconRenderer() {
        // Utility class: prevent instantiation
    }
}
