package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButton;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
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
 *   <li>Button rendering ({@link #render(GuiGraphics, int, int)})</li>
 *   <li>Click dispatch ({@link #handleClick(double, double)})</li>
 *   <li>Status bar text composition</li>
 * </ul>
 *
 * @see TopBarTypes.TopBarButtonId
 * @see TopBarTypes.TopBarButtonLayout
 * @see TopBarIconRenderer
 */
public final class TopBarPanel {

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
    public void render(GuiGraphics g, int mouseX, int mouseY) {
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
                + (state.shapeStatus.isBlank() ? "" : "    " + state.shapeStatus)
                + (state.pendingGuiBindSlot >= 0 ? "    " + screen.text(
                        "screen.rtsbuilding.status.gui_bind_armed", state.pendingGuiBindSlot + 1) : "");

        TopBarLayout.Status status = TopBarLayout.status(screen.width);
        String visibleRow2 = screen.trimToWidth(row2, status.width());
        g.drawString(screen.font(), screen.trimToWidth(row1, status.width()), status.x(), status.row1Y(),
                0xF0F0F0, false);
        g.drawString(screen.font(), visibleRow2, status.x(), status.row2Y(),
                state.storageLinked ? 0xB8FFB8 : 0xFFD8AE, false);
        renderContextualModeTip(g, status, visibleRow2, state.mode);
    }

    /**
     * 在第二行右侧空白处绘制当前模式的操作提示。空间不足时整段隐藏，
     * 避免提示与储存状态在高 UI 缩放或较长翻译下互相覆盖。
     */
    private void renderContextualModeTip(GuiGraphics g, TopBarLayout.Status status,
                                         String visibleRow2, TopBarUiState.Mode mode) {
        String key = modeTipKey(mode);
        if (key.isBlank()) {
            return;
        }
        String tip = screen.text(key);
        int tipX = TopBarLayout.contextualHintX(
                status,
                screen.font().width(visibleRow2),
                screen.font().width(tip),
                12);
        if (tipX >= 0) {
            g.drawString(screen.font(), tip, tipX, status.row2Y(), 0xB8FFB8, false);
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
            if (!inside(mouseX, mouseY, button.x(), TopBarLayout.BUTTON_Y, button.width(), TOP_BUTTON_H)) {
                continue;
            }
            return TopBarUiAdapter.dispatch(TopBarUiAction.click(coreId(button.id())),
                    screen, controller, button.x() + button.width() / 2,
                    TopBarLayout.BUTTON_Y + TOP_BUTTON_H);
        }
        return false;
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

    /**
     * Checks whether the primary (left) mouse button is currently held down.
     * Used to compute the "pressed" visual state for icon buttons.
     */
    public boolean isPrimaryMouseDown() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    // ======================== Button Rendering ========================

    /**
     * Routes the rendering of a single top bar button to the appropriate
     * method based on whether it is icon-only or text-based.
     */
    private void drawTopButton(GuiGraphics g, int mouseX, int mouseY, TopBarTypes.TopBarButtonLayout button) {
        if (button.iconOnly()) {
            drawTopIconButton(g, mouseX, mouseY, button);
            return;
        }
        drawTopButtonSized(g, button.x(), button.label(), button.active(), button.width());
    }

    /**
     * Renders a text-based top bar button with background, border, and centered label.
     */
    private void drawTopButtonSized(GuiGraphics g, int x, String label, boolean active, int w) {
        int y = TopBarLayout.BUTTON_Y;
        int h = TOP_BUTTON_H;
        int bg = active ? 0xFF2E6A50 : 0xAA1F2329;
        g.fill(x, y, x + w, y + h, bg);
        g.hLine(x, x + w, y, 0xFF5B6673);
        g.hLine(x, x + w, y + h, 0xFF0D0E10);
        g.vLine(x, y, y + h, 0xFF5B6673);
        g.vLine(x + w, y, y + h, 0xFF0D0E10);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(),
                screen.trimToWidth(label, Math.max(6, w - 8)), x + w / 2, y + 8, 0xFFFFFF);
    }

    /**
     * Renders an icon-only top bar button. Tries a texture icon first via
     * {@link TopBarIconRenderer#topbarModeTexture(TopBarTypes.TopBarButtonId, boolean, boolean, boolean)};
     * if no texture is available, draws a pixel-art icon via
     * {@link TopBarIconRenderer#renderIcon}.
     * <p>
     * The button background colour changes based on active, pressed, and hovered states.
     */
    private void drawTopIconButton(GuiGraphics g, int mouseX, int mouseY, TopBarTypes.TopBarButtonLayout button) {
        int x = button.x();
        int y = TopBarLayout.BUTTON_Y;
        int w = button.width();
        int h = TOP_BUTTON_H;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        boolean pressed = hovered && isPrimaryMouseDown();

        int bg = 0xAA1F2329;
        int light = 0xFF5B6673;
        int dark = 0xFF0D0E10;
        int icon = 0xFFBDC9D6;
        if (button.active()) {
            bg = 0xFF2D6B47;
            light = 0xFF9AD2AE;
            icon = 0xFFF4FBF5;
        } else if (pressed) {
            bg = 0xFF1F5037;
            light = 0xFF6AA784;
            icon = 0xFFD9E3EF;
        } else if (hovered) {
            bg = 0xFF1D2530;
            light = 0xFF7A90AA;
            icon = 0xFFD9E3EF;
        }

        // Try texture-based icon first
        ResourceLocation textureIcon = TopBarIconRenderer.topbarModeTexture(
                button.id(), button.active(), hovered, pressed);
        if (textureIcon != null) {
            g.blit(textureIcon, x + (w - TOP_BUTTON_H) / 2, y, 0, 0,
                    TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H);
            return;
        }

        // Fall back to pixel-art background + icon
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, light, dark);
        if (pressed) {
            g.hLine(x + 1, x + w - 1, y + 1, dark);
            g.vLine(x + 1, y + 1, y + h - 1, dark);
        }

        // Draw the pixel-art icon at the button centre
        int cx = x + (w / 2);
        int cy = y + (h / 2);
        if (button.id() == TopBarTypes.TopBarButtonId.GUIDE
                || button.id() == TopBarTypes.TopBarButtonId.DEVELOPER) {
            String glyph = button.id() == TopBarTypes.TopBarButtonId.DEVELOPER ? "D" : "i";
            RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), glyph, cx, y + 7, icon);
        } else {
            TopBarIconRenderer.renderIcon(button.id(), g, cx, cy, icon, button.active(), screen.font());
        }
    }

    /**
     * Delegates guide hint rendering below the top bar to {@link BuilderScreen}.
     */
    private void renderTopGuideHint(GuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) {
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
        return switch (this.controller.getMode()) {
            case INTERACT -> TopAction.INTERACT;
            case LINK_STORAGE -> TopAction.LINK;
            case FUNNEL -> TopAction.FUNNEL;
            case ROTATE -> TopAction.ROTATE;
            default -> TopAction.INTERACT;
        };
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
        return switch (mode) {
            case INTERACT -> "screen.rtsbuilding.mode.interact";
            case LINK_STORAGE -> "screen.rtsbuilding.mode.link_storage";
            case FUNNEL -> "screen.rtsbuilding.mode.funnel";
            case CAMERA -> "screen.rtsbuilding.mode.camera";
            case ROTATE -> "screen.rtsbuilding.mode.rotate";
            case IDLE -> "screen.rtsbuilding.mode.idle";
        };
    }

    /**
     * Hit-test helper: checks whether a point (mouseX, mouseY) lies inside
     * a rectangle defined by (x, y, w, h).
     */
    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
