package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.routing.PointerCapture;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButton;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiState;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiStateBlendAnimationSet;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Orchestrates the top bar panel: builds the button layout, renders all buttons
 * (both icon-only and text-based), handles mouse clicks that dispatch to the
 * appropriate mode/action, and renders the two-line status bar below the buttons.
 * <p>
 * This panel is owned by {@link BuilderScreen} and is the single point of contact
 * between the screen layer and the top bar UI data. It holds no direct rendering
 * state of its own — the appearance of each button is computed fresh every frame.
 * <p>
 * <b>Key responsibilities:</b>
 * <ul>
 *   <li>Layout construction ({@link #buildTopBarButtonLayouts()})</li>
 *   <li>Button rendering ({@link #render(LegacyGuiGraphics, int, int)})</li>
 *   <li>Click dispatch ({@link #handleClick(double, double)})</li>
 *   <li>Status bar text composition</li>
 * </ul>
 *
 * @see TopBarTypes.TopBarButtonId
 * @see TopBarTypes.TopBarButtonLayout
 * @see TopBarIconRenderer
 */
public final class TopBarPanel {
    private static final int PRIMARY_MOUSE_BUTTON = 0;

    /**
     * Maps the current {@link BuilderMode} to a high-level action category used
     * for highlighting the corresponding mode button in the top bar.
     */
    public enum TopAction {
        INTERACT,
        LINK,
        FUNNEL,
        ROTATE
    }

    private BuilderScreen screen;
    private ClientRtsController controller;
    private final UiStateBlendAnimationSet<TopBarTypes.TopBarButtonId,
            TopBarIconRenderer.VisualState> iconTransitions =
            new UiStateBlendAnimationSet<>(SystemUiClock.INSTANCE,
                    Arrays.asList(TopBarTypes.TopBarButtonId.values()),
                    TopBarIconRenderer.visualStates(),
                    90L, UiEasing.EASE_IN_OUT_QUAD);
    private final PointerCapture<TopBarTypes.TopBarButtonId> pointerCapture = new PointerCapture<>();

    // ======================== Lifecycle ========================

    /**
     * Initialises this panel with references to the owning screen and controller.
     * Must be called once before any render or click methods are invoked.
     *
     * @param screen     the owning {@link BuilderScreen}
     * @param controller the active {@link ClientRtsController}
     */
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ======================== Render ========================

    /**
     * Renders the top bar: all mode/action buttons followed by a two-line
     * status bar showing the current mode, storage link status, and
     * shape-editing state.
     */
    public void render(LegacyGuiGraphics g, int mouseX, int mouseY) {
        screen.ensureFillModeForShape(this.controller.getBuildShape());
        TopBarUiState state = TopBarUiAdapter.snapshot(screen, controller);
        List<TopBarTypes.TopBarButtonLayout> topButtons = buildTopBarButtonLayouts(state);
        for (TopBarTypes.TopBarButtonLayout button : topButtons) {
            drawTopButton(g, mouseX, mouseY, button);
        }
        renderTopGuideHint(g, topButtons);

        // ---- Status bar row 1: mode ----
        String modeText = screen.text("screen.rtsbuilding.status.mode",
                screen.text(modeTranslationKey(state.mode)));

        String linked = state.storageLinked
                ? screen.text("screen.rtsbuilding.status.storage_linked", state.linkedStorageName)
                : screen.text("screen.rtsbuilding.status.storage_not_linked");
        String row1 = modeText;

        // ---- Status bar row 2: storage, auto-store, funnel, active workflow hint ----
        String row2 = linked + (state.autoStoreMinedDrops
                ? "    " + screen.text("screen.rtsbuilding.status.auto_store_on")
                : "    " + screen.text("screen.rtsbuilding.status.auto_store_off"))
                + "    " + screen.text("screen.rtsbuilding.status.funnel",
                        screen.text(state.funnelEnabled ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"))
                + (state.shapeStatus.isEmpty() ? "" : "    " + state.shapeStatus)
                + (state.pendingGuiBindSlot >= 0 ? "    " + screen.text(
                        "screen.rtsbuilding.status.gui_bind_armed", state.pendingGuiBindSlot + 1) : "");

        TopBarLayout.Status status = TopBarLayout.status(screen.width);
        String visibleRow2 = screen.trimToWidth(row2, status.width());
        g.drawString(screen.font(), screen.trimToWidth(row1, status.width()), status.x(), status.row1Y(),
                RtsMainlineTheme.PRIMARY_TEXT.toArgb(), false);
        g.drawString(screen.font(), visibleRow2, status.x(), status.row2Y(),
                (state.storageLinked ? RtsMainlineTheme.STATUS_LINKED
                        : RtsMainlineTheme.STATUS_UNLINKED).toArgb(), false);
        renderContextualModeTip(g, status, visibleRow2, state.mode);
        renderHoveredTooltip(g, mouseX, mouseY, topButtons);
    }

    /**
     * 在第二行右侧空白处绘制当前模式的操作提示。空间不足时整段隐藏，
     * 避免提示与储存状态在高 UI 缩放或较长翻译下互相覆盖。
     */
    private void renderContextualModeTip(LegacyGuiGraphics g, TopBarLayout.Status status,
                                         String visibleRow2, TopBarUiState.Mode mode) {
        String key = modeTipKey(mode);
        if (key.isEmpty()) {
            return;
        }
        String tip = screen.text(key);
        int tipX = TopBarLayout.contextualHintX(
                status,
                screen.font().getStringWidth(visibleRow2),
                screen.font().getStringWidth(tip),
                12);
        if (tipX >= 0) {
            g.drawString(screen.font(), tip, tipX, status.row2Y(),
                    RtsMainlineTheme.STATUS_LINKED.toArgb(), false);
        }
    }

    static String modeTipKey(BuilderMode mode) {
        return mode == BuilderMode.FUNNEL ? "screen.rtsbuilding.mode_tip.funnel" : "";
    }

    static String modeTipKey(TopBarUiState.Mode mode) {
        return mode == TopBarUiState.Mode.FUNNEL ? "screen.rtsbuilding.mode_tip.funnel" : "";
    }

    // ======================== Click Handling ========================

    /**
     * Checks whether the mouse click falls within any top bar button and dispatches
     * the corresponding action. Also closes the gear menu if the click lands outside
     * all buttons.
     *
     * @param mouseX the X coordinate of the mouse click
     * @param mouseY the Y coordinate of the mouse click
     * @return {@code true} if a button was hit (click consumed), {@code false} otherwise
     */
    public boolean handleClick(double mouseX, double mouseY) {
        if (mouseY < TopBarLayout.BUTTON_Y || mouseY > TopBarLayout.BUTTON_Y + TOP_BUTTON_H) {
            return false;
        }

        TopBarUiState state = TopBarUiAdapter.snapshot(screen, controller);
        for (TopBarTypes.TopBarButtonLayout button : buildTopBarButtonLayouts(state)) {
            if (!UiRect.contains(button.x(), TopBarLayout.BUTTON_Y, button.width(), TOP_BUTTON_H,
                    mouseX, mouseY)) {
                continue;
            }
            if (!this.pointerCapture.capture(PRIMARY_MOUSE_BUTTON, button.id())) {
                return true;
            }
            boolean handled = TopBarUiAdapter.dispatch(TopBarUiAction.click(coreId(button.id())),
                    screen, controller, button.x() + button.width() / 2,
                    TopBarLayout.BUTTON_Y + TOP_BUTTON_H);
            if (!handled) {
                this.pointerCapture.release(PRIMARY_MOUSE_BUTTON);
            }
            return handled;
        }
        return false;
    }

    /**
     * 顶栏占用的屏幕区域始终拦截指针输入，避免滚轮、拖动或未命中图标的点击继续传给世界相机。
     */
    public boolean capturesPointer(double mouseX, double mouseY) {
        return mouseX >= 0.0D && mouseX < screen.width
                && mouseY >= 0.0D && mouseY < TOP_H;
    }

    /** 顶栏没有滚动行为，但位于顶栏上的滚轮事件必须被消费。 */
    public boolean handleScroll(double mouseX, double mouseY) {
        return capturesPointer(mouseX, mouseY);
    }

    // ======================== Layout Builder ========================

    /**
     * Builds the ordered list of all top bar button layouts for the current frame.
     * <p>
     * Buttons are arranged left-to-right: mode buttons first (INTERACT, LINK,
     * FUNNEL, ROTATE — each gated by progression), then a separator, then action
     * buttons (QUICK_BUILD, QUEST_DETECT, CHUNK_VIEW, GUIDE), then a
     * right-aligned GEAR button.
     * <p>
     * Mode buttons track their active state via {@link #topActionForMode()}.
     * Action buttons track their active state separately (open/visible toggles).
     *
     * @return a new list of {@link TopBarTypes.TopBarButtonLayout}s for this frame
     */
    public List<TopBarTypes.TopBarButtonLayout> buildTopBarButtonLayouts() {
        return buildTopBarButtonLayouts(TopBarUiAdapter.snapshot(screen, controller));
    }

    private List<TopBarTypes.TopBarButtonLayout> buildTopBarButtonLayouts(TopBarUiState state) {
        List<TopBarTypes.TopBarButtonLayout> layouts = new ArrayList<>();
        boolean quickBuild = visible(state, TopBarUiButtonId.QUICK_BUILD);
        boolean questDetect = visible(state, TopBarUiButtonId.QUEST_DETECT);
        boolean rangeCulling = visible(state, TopBarUiButtonId.RANGE_CULLING);
        boolean developer = visible(state, TopBarUiButtonId.DEVELOPER);
        TopBarLayout.Buttons positions = TopBarLayout.buttons(
                screen.width, TOP_MODE_BUTTON_W, TOP_ICON_BUTTON_W, TOP_BUTTON_GAP,
                quickBuild, questDetect, rangeCulling, developer);

        for (TopBarUiButton coreButton : state.buttons) {
            if (!coreButton.visible) continue;
            TopBarTypes.TopBarButtonId id = productionId(coreButton.id);
            int width = coreButton.id.modeButton ? TOP_MODE_BUTTON_W : TOP_ICON_BUTTON_W;
            layouts.add(new TopBarTypes.TopBarButtonLayout(id, positions.x(id), width,
                    "", true, coreButton.active));
        }
        return layouts;
    }

    /** release 只清理最初按下的所有者；移入其他按钮不会伪造 pressed 状态。 */
    public void mouseReleased(int button) {
        this.pointerCapture.release(button);
    }

    /** 切屏、关闭与失焦时清理瞬时按下所有权，避免下一次打开仍显示按下态。 */
    public void clearTransientInputState() {
        this.pointerCapture.clear();
    }

    // ======================== Button Rendering ========================

    /**
     * Routes the rendering of a single top bar button to the appropriate
     * method based on whether it is icon-only or text-based.
     */
    private void drawTopButton(LegacyGuiGraphics g, int mouseX, int mouseY,
                               TopBarTypes.TopBarButtonLayout button) {
        drawTopIconButton(g, mouseX, mouseY, button);
    }

    /**
     * Renders an icon-only top bar button. Tries a texture icon first via
     * {@link TopBarIconRenderer#topbarModeTexture(TopBarTypes.TopBarButtonId, boolean, boolean, boolean)};
     * if no texture is available, draws a pixel-art icon via
     * {@link TopBarIconRenderer#renderIcon}.
     * <p>
     * The button background colour changes based on active, pressed, and hovered states.
     */
    private void drawTopIconButton(LegacyGuiGraphics g, int mouseX, int mouseY,
                                   TopBarTypes.TopBarButtonLayout button) {
        int x = button.x();
        int y = TopBarLayout.BUTTON_Y;
        int w = button.width();
        boolean hovered = UiRect.contains(x, y, w, TOP_BUTTON_H, mouseX, mouseY);
        boolean pressed = hovered
                && this.pointerCapture.ownerOf(PRIMARY_MOUSE_BUTTON) == button.id();

        TopBarIconRenderer.renderBlended(
                g, button.id(), x + (w - TOP_BUTTON_H) / 2, y, TOP_BUTTON_H,
                TopBarIconRenderer.visualState(button.active(), hovered, pressed),
                iconTransitions, Config.isUiAnimationsEnabled());
    }

    /** 顶栏正式按钮统一从四语言键显示 Tooltip，避免图标含义依赖猜测。 */
    private void renderHoveredTooltip(LegacyGuiGraphics g, int mouseX, int mouseY,
                                      List<TopBarTypes.TopBarButtonLayout> buttons) {
        for (TopBarTypes.TopBarButtonLayout button : buttons) {
            if (UiRect.contains(button.x(), TopBarLayout.BUTTON_Y, button.width(), TOP_BUTTON_H,
                    mouseX, mouseY)) {
                String tooltip = I18n.format(TopBarIconRenderer.tooltipKey(button.id()));
                GuiUtils.drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY,
                        screen.width, screen.height, 300, screen.font());
                return;
            }
        }
    }

    /**
     * Delegates guide hint rendering below the top bar to {@link BuilderScreen}.
     */
    private void renderTopGuideHint(LegacyGuiGraphics g,
                                    List<TopBarTypes.TopBarButtonLayout> topButtons) {
        screen.renderTopGuideHint(g, topButtons);
    }

    // ======================== Helpers ========================

    /**
     * Maps the current {@link BuilderMode} to a {@link TopAction} category,
     * used to determine which mode button should appear active/highlighted.
     *
     * @return the resolved {@link TopAction} (defaults to {@link TopAction#INTERACT})
     */
    public TopAction topActionForMode() {
        if (screen.isBlueprintPlacementModeLocked()) {
            return TopAction.INTERACT;
        }
        switch (this.controller.getMode()) {
            case INTERACT: return TopAction.INTERACT;
            case LINK_STORAGE: return TopAction.LINK;
            case FUNNEL: return TopAction.FUNNEL;
            case ROTATE: return TopAction.ROTATE;
            default: return TopAction.INTERACT;
        }
    }

    private static boolean visible(TopBarUiState state, TopBarUiButtonId id) {
        TopBarUiButton button = state.button(id);
        return button != null && button.visible;
    }

    private static TopBarUiButtonId coreId(TopBarTypes.TopBarButtonId id) {
        return TopBarUiButtonId.valueOf(id.name());
    }

    private static TopBarTypes.TopBarButtonId productionId(TopBarUiButtonId id) {
        return TopBarTypes.TopBarButtonId.valueOf(id.name());
    }

    private static String modeTranslationKey(TopBarUiState.Mode mode) {
        switch (mode) {
            case INTERACT: return "screen.rtsbuilding.mode.interact";
            case LINK_STORAGE: return "screen.rtsbuilding.mode.link_storage";
            case FUNNEL: return "screen.rtsbuilding.mode.funnel";
            case CAMERA: return "screen.rtsbuilding.mode.camera";
            case ROTATE: return "screen.rtsbuilding.mode.rotate";
            case IDLE: return "screen.rtsbuilding.mode.idle";
            default: return "screen.rtsbuilding.mode.idle";
        }
    }

}
