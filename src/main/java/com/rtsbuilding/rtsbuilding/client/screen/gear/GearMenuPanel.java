package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Settings window for RTS Builder.
 *
 * <p>The window chrome, close button, drag/resize behavior, and z-order are
 * owned by {@link RtsWindowPanel}. This class owns only the settings rows and
 * their player-facing actions.
 */
public final class GearMenuPanel extends RtsWindowPanel {
    private static final int LEGACY_DEFAULT_WINDOW_W = 300;
    private static final int LEGACY_DEFAULT_WINDOW_H = 284;
    private static final int DEFAULT_WINDOW_W = 380;
    private static final int MIN_WINDOW_W = 280;
    private static final int CONTENT_TOP_PADDING = 8;
    private static final int SECTION_HEADER_H = 22;
    private static final int SECTION_GAP = 6;
    private static final int SENSITIVITY_ROW_H = 46;
    private static final int SCALE_ROW_H = 34;
    private static final int SOUND_LIMIT_ROW_H = 38;
    private static final int SIMPLE_TOGGLE_ROW_H = 28;
    private static final int HINT_TOGGLE_ROW_H = 34;
    private static final int HINT_LINE_H = 10;
    private static final int HINT_EXPAND_BUTTON_SIZE = 12;

    private int scroll = 0;
    private boolean controlsExpanded = false;
    private boolean displayExpanded = false;
    private boolean helpersExpanded = false;
    private boolean soundExpanded = false;
    private boolean animationExpanded = false;
    private final Set<String> expandedHintKeys = new HashSet<>();
    private SensitivityControl draggingSensitivityControl = null;

    private enum SensitivityControl {
        PAN_DRAG("screen.rtsbuilding.settings.sensitivity.pan_drag"),
        ROTATE_VIEW("screen.rtsbuilding.settings.sensitivity.rotate_view"),
        KEYBOARD_MOVE("screen.rtsbuilding.settings.sensitivity.keyboard_move"),
        WHEEL_ZOOM("screen.rtsbuilding.settings.sensitivity.wheel_zoom");

        private final String labelKey;

        SensitivityControl(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = true;
    }

    public void open() {
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        int x = contentX();
        int y = contentY() + CONTENT_TOP_PADDING - this.scroll;
        int w = contentWidth();
        renderControls(g, mouseX, mouseY, x, y, w);
        renderScrollbar(g, x, contentY(), w, contentHeight());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            handleClick(mouseX, mouseY);
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return true;
        }
        int delta = scrollY > 0.0D ? -18 : 18;
        this.scroll = Mth.clamp(this.scroll + delta, 0, maxScroll);
        return true;
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.settings.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_WINDOW_W;
    }

    @Override
    protected int getDefaultHeight() {
        return GEAR_MENU_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return MIN_WINDOW_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return GEAR_MENU_MIN_H;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean legacyDefaultBounds = width == LEGACY_DEFAULT_WINDOW_W && height == LEGACY_DEFAULT_WINDOW_H;
        int restoredWidth = legacyDefaultBounds ? DEFAULT_WINDOW_W : width;
        int restoredHeight = legacyDefaultBounds ? GEAR_MENU_H : height;
        super.setBounds(x, y, restoredWidth, restoredHeight);
    }

    @Override
    protected int getMaxWindowWidth() {
        if (this.screen == null) {
            return super.getMaxWindowWidth();
        }
        int viewportLimit = Math.max(getMinWindowWidth(), (this.screen.width * 2) / 3);
        return Math.min(super.getMaxWindowWidth(), viewportLimit);
    }

    @Override
    protected int getMaxWindowHeight() {
        if (this.screen == null) {
            return super.getMaxWindowHeight();
        }
        int viewportLimit = Math.max(getMinWindowHeight(), (this.screen.height * 2) / 3);
        return Math.min(super.getMaxWindowHeight(), viewportLimit);
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Mth.clamp((this.screen.height - this.windowHeight) / 2,
                TOP_H + 6,
                Math.max(TOP_H + 6, this.screen.height - this.windowHeight - 8));
    }

    private void renderControls(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int controlsY, int w) {
        int rowY = controlsY;
        rowY = drawSectionHeader(g, mouseX, mouseY, x, w, rowY,
                "screen.rtsbuilding.settings.category.controls", this.controlsExpanded);
        if (this.controlsExpanded) {
            for (SensitivityControl control : SensitivityControl.values()) {
                drawSensitivityRow(g, rowY, x, w, control);
                rowY += SENSITIVITY_ROW_H;
            }
            drawSimpleToggleRow(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.head_start",
                    this.controller.isStartCameraAtPlayerHead());
            rowY += SIMPLE_TOGGLE_ROW_H;
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.pan_drag_x_invert",
                    "screen.rtsbuilding.settings.pan_drag_x_invert.hint",
                    this.controller.isInvertPanDragX());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_x_invert.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.pan_drag_y_invert",
                    "screen.rtsbuilding.settings.pan_drag_y_invert.hint",
                    this.controller.isInvertPanDragY());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_y_invert.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.keyboard_batch_confirm",
                    "screen.rtsbuilding.settings.keyboard_batch_confirm.hint",
                    Config.isKeyboardBatchConfirmEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.keyboard_batch_confirm.hint");
        }
        rowY += SECTION_GAP;

        rowY = drawSectionHeader(g, mouseX, mouseY, x, w, rowY,
                "screen.rtsbuilding.settings.category.display", this.displayExpanded);
        if (this.displayExpanded) {
            drawScaleRow(g, mouseX, mouseY, rowY, x, w);
            rowY += SCALE_ROW_H;
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.player_status_overlay",
                    "screen.rtsbuilding.settings.player_status_overlay.hint",
                    this.controller.isPlayerStatusOverlayEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.player_status_overlay.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.container_overlay",
                    "screen.rtsbuilding.settings.container_overlay.hint",
                    RtsClientUiStateStore.isContainerOverlayEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.container_overlay.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.shift_import",
                    "screen.rtsbuilding.settings.shift_import.hint",
                    RtsClientUiStateStore.isOverlayShiftImportEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.shift_import.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.show_storage_ready_popup",
                    "screen.rtsbuilding.settings.show_storage_ready_popup.hint",
                    RtsClientUiStateStore.isShowStorageReadyPopupEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_storage_ready_popup.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.show_workflow_panel",
                    "screen.rtsbuilding.settings.show_workflow_panel.hint",
                    RtsClientUiStateStore.isShowWorkflowPanelEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_workflow_panel.hint");
        }
        rowY += SECTION_GAP;

        rowY = drawSectionHeader(g, mouseX, mouseY, x, w, rowY,
                "screen.rtsbuilding.settings.category.helpers", this.helpersExpanded);
        if (this.helpersExpanded) {
            drawSimpleToggleRow(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.auto_store",
                    this.controller.isAutoStoreMinedDrops());
            rowY += SIMPLE_TOGGLE_ROW_H;
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.storage_refresh_quiet",
                    "screen.rtsbuilding.settings.storage_refresh_quiet.hint",
                    RtsClientUiStateStore.isStorageRefreshQuietEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_refresh_quiet.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.storage_auto_refresh",
                    "screen.rtsbuilding.settings.storage_auto_refresh.hint",
                    RtsClientUiStateStore.isStorageAutoRefreshEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_auto_refresh.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.placed_recovery",
                    "screen.rtsbuilding.settings.placed_recovery.hint",
                    this.controller.isAllowPlacedBlockRecovery());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placed_recovery.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.tool_protection",
                    "screen.rtsbuilding.settings.tool_protection.hint",
                    this.controller.isToolProtectionEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.tool_protection.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.damage_auto_return",
                    "screen.rtsbuilding.settings.damage_auto_return.hint",
                    this.controller.isDamageAutoReturnEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_auto_return.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.bd_network",
                    "screen.rtsbuilding.settings.bd_network.hint",
                    this.controller.isBdNetworkEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.bd_network.hint");
        }
        rowY += SECTION_GAP;

        rowY = drawSectionHeader(g, mouseX, mouseY, x, w, rowY,
                "screen.rtsbuilding.settings.category.sound", this.soundExpanded);
        if (this.soundExpanded) {
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.rts_sounds",
                    "screen.rtsbuilding.settings.rts_sounds.hint",
                    RtsClientUiStateStore.isRtsSoundsEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.rts_sounds.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.break_sounds",
                    "screen.rtsbuilding.settings.break_sounds.hint",
                    RtsClientUiStateStore.isRtsBreakSoundsEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.break_sounds.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.damage_sound",
                    "screen.rtsbuilding.settings.damage_sound.hint",
                    this.controller.isDamageSoundEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_sound.hint");
            drawSoundLimitRow(g, mouseX, mouseY, rowY, x, w);
            rowY += SOUND_LIMIT_ROW_H;
        }
        rowY += SECTION_GAP;

        rowY = drawSectionHeader(g, mouseX, mouseY, x, w, rowY,
                "screen.rtsbuilding.settings.category.animation", this.animationExpanded);
        if (this.animationExpanded) {
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.smooth_camera",
                    "screen.rtsbuilding.settings.smooth_camera.hint",
                    this.controller.isSmoothCamera());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.smooth_camera.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.placement_block_ghost_preview",
                    "screen.rtsbuilding.settings.placement_block_ghost_preview.hint",
                    Config.isPlacementBlockGhostPreviewEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_block_ghost_preview.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.place_block_ghost_animation",
                    "screen.rtsbuilding.settings.place_block_ghost_animation.hint",
                    Config.isPlaceBlockGhostAnimationEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_block_ghost_animation.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.destroy_block_ghost_animation",
                    "screen.rtsbuilding.settings.destroy_block_ghost_animation.hint",
                    Config.isDestroyBlockGhostAnimationEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_block_ghost_animation.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.placement_wireframe_preview",
                    "screen.rtsbuilding.settings.placement_wireframe_preview.hint",
                    Config.isPlacementWireframePreviewEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_wireframe_preview.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.place_wireframe_animation",
                    "screen.rtsbuilding.settings.place_wireframe_animation.hint",
                    Config.isPlaceWireframeAnimationEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_wireframe_animation.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.destroy_wireframe_animation",
                    "screen.rtsbuilding.settings.destroy_wireframe_animation.hint",
                    Config.isDestroyWireframeAnimationEnabled());
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_wireframe_animation.hint");
            drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.range_destroy_skeleton",
                    "screen.rtsbuilding.settings.range_destroy_skeleton.hint",
                    Config.isRangeDestroySkeletonEnabled());
        }
    }

    private int drawSectionHeader(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int w, int y,
            String titleKey, boolean expanded) {
        boolean hover = inside(mouseX, mouseY, x + 8, y, w - 16, SECTION_HEADER_H);
        int bg = hover ? 0xCC2C3948 : 0xCC202A35;
        RtsClientUiUtil.drawPanelFrame(g, x + 8, y, w - 16, SECTION_HEADER_H,
                bg, 0xFF596D82, 0xFF0B1016);
        g .text(screen.font(), expanded ? "v" : ">", x + 16, y + 7, 0xFFEAF4FF, false);
        g .text(screen.font(), trimToWidth(text(titleKey), w - 58), x + 31, y + 7, 0xFFF4F7FF, false);
        return y + SECTION_HEADER_H;
    }

    private void drawSensitivityRow(GuiGraphicsExtractor g, int rowY, int x, int w, SensitivityControl control) {
        g .text(screen.font(), Component.translatable(control.labelKey),
                x + 16, rowY + 5, 0xFFC8D3DF, false);
        g .text(screen.font(), sensitivityLabel(control),
                x + w - 60, rowY + 5, 0xFFEAF4FF, false);

        int trackX = x + 16;
        int trackY = rowY + 24;
        int trackW = w - 32;
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF07090D);
        g.fill(trackX + 1, trackY + 1, trackX + trackW - 1, trackY + 3, 0xFF313946);
        int presetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        int knobX = trackX + (int) Math.round((sensitivityIndex(control)
                / (double) Math.max(1, presetCount - 1)) * trackW);
        g.fill(knobX - 3, trackY - 5, knobX + 4, trackY + 8, 0xFF5FE36C);
    }

    private void drawScaleRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int rowY, int x, int w) {
        int minusX = x + w - 124;
        int valueX = minusX + 26;
        int plusX = valueX + 60;
        g .text(screen.font(), Component.translatable("screen.rtsbuilding.settings.ui_scale"),
                x + 16, rowY + 8, 0xFFC8D3DF, false);
        drawGearMenuRow(g, mouseX, mouseY, minusX, rowY + 6, 22, 22, "-", false);
        RtsClientUiUtil.drawPanelFrame(g, valueX, rowY + 6, 56, 22, 0xCC1A232E, 0xFF566B80, 0xFF0D1218);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), rtsGuiScaleLabel(),
                valueX + 28, rowY + 13, 0xFFEAF4FF);
        drawGearMenuRow(g, mouseX, mouseY, plusX, rowY + 6, 22, 22, "+", false);
    }

    private void drawSoundLimitRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int rowY, int x, int w) {
        int minusX = x + w - 124;
        int valueX = minusX + 26;
        int plusX = valueX + 60;
        g .text(screen.font(), trimToWidth(
                        text("screen.rtsbuilding.settings.block_sounds_per_tick"), w - 156),
                x + 16, rowY + 3, 0xFFC8D3DF, false);
        g .text(screen.font(), trimToWidth(
                        text("screen.rtsbuilding.settings.block_sounds_per_tick.hint"), w - 156),
                x + 16, rowY + 18, 0xFF9FB0C2, false);
        drawGearMenuRow(g, mouseX, mouseY, minusX, rowY + 8, 22, 22, "-", false);
        RtsClientUiUtil.drawPanelFrame(g, valueX, rowY + 8, 56, 22,
                0xCC1A232E, 0xFF566B80, 0xFF0D1218);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(),
                Integer.toString(RtsClientUiStateStore.getRtsBlockSoundsPerTick()),
                valueX + 28, rowY + 15, 0xFFEAF4FF);
        drawGearMenuRow(g, mouseX, mouseY, plusX, rowY + 8, 22, 22, "+", false);
    }

    private void drawSimpleToggleRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int w, int rowY,
            String labelKey, boolean active) {
        g .text(screen.font(), trimToWidth(text(labelKey), w - 126),
                x + 16, rowY + 9, 0xFFC8D3DF, false);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, rowY + 4, 76, 22, active,
                text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));
    }

    private void drawSettingsToggleWithHint(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int w, int rowY,
            String labelKey, String hintKey, boolean active) {
        boolean expandable = hintCanExpand(x, w, hintKey);
        boolean expanded = expandable && this.expandedHintKeys.contains(hintKey);
        int hintX = hintTextX(x, expandable);
        int hintW = hintTextMaxWidth(x, w, expandable);
        String label = trimToWidth(text(labelKey), w - 116);
        g .text(screen.font(), label, x + 16, rowY + 2, 0xFFC8D3DF, false);
        if (expandable) {
            drawHintExpandButton(g, mouseX, mouseY, x, rowY, expanded);
        }
        if (expanded) {
            List<FormattedCharSequence> lines = wrappedHintLines(x, w, hintKey);
            for (int i = 0; i < lines.size(); i++) {
                g .text(screen.font(), lines.get(i), hintX, rowY + 13 + i * HINT_LINE_H, 0xFF9FB0C2, false);
            }
        } else {
            g .text(screen.font(), trimToWidth(text(hintKey), hintW), hintX, rowY + 13, 0xFF9FB0C2, false);
        }
        drawToggleButton(g, mouseX, mouseY, x + w - 92, rowY + 4, 76, 22, active,
                text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));
    }

    private void handleClick(double mouseX, double mouseY) {
        int x = contentX();
        int w = contentWidth();
        int rowY = contentY() + CONTENT_TOP_PADDING;
        double contentMouseY = mouseY + this.scroll;

        if (inside(mouseX, contentMouseY, x + 8, rowY, w - 16, SECTION_HEADER_H)) {
            this.controlsExpanded = !this.controlsExpanded;
            clampScroll();
            screen.persistUiState();
            return;
        }
        rowY += SECTION_HEADER_H;
        if (this.controlsExpanded) {
            for (SensitivityControl control : SensitivityControl.values()) {
                if (inside(mouseX, contentMouseY, x + 16, rowY + 16, w - 32, 22)) {
                    setSensitivityByFraction(control, calcSensitivityFraction(mouseX, x, w));
                    this.draggingSensitivityControl = control;
                    return;
                }
                rowY += SENSITIVITY_ROW_H;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24, SIMPLE_TOGGLE_ROW_H)) {
                this.controller.toggleStartCameraAtPlayerHead();
                screen.persistUiState();
                return;
            }
            rowY += SIMPLE_TOGGLE_ROW_H;
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.pan_drag_x_invert.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_x_invert.hint"))) {
                this.controller.toggleInvertPanDragX();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_x_invert.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.pan_drag_y_invert.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_y_invert.hint"))) {
                this.controller.toggleInvertPanDragY();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_y_invert.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.keyboard_batch_confirm.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.keyboard_batch_confirm.hint"))) {
                Config.setKeyboardBatchConfirmEnabled(!Config.isKeyboardBatchConfirmEnabled());
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.keyboard_batch_confirm.hint");
        }
        rowY += SECTION_GAP;

        if (inside(mouseX, contentMouseY, x + 8, rowY, w - 16, SECTION_HEADER_H)) {
            this.displayExpanded = !this.displayExpanded;
            clampScroll();
            screen.persistUiState();
            return;
        }
        rowY += SECTION_HEADER_H;
        if (this.displayExpanded) {
            int minusX = x + w - 124;
            int plusX = minusX + 86;
            if (inside(mouseX, contentMouseY, minusX, rowY + 6, 22, 22)) {
                adjustRtsGuiScale(-RTS_GUI_SCALE_STEP);
                return;
            }
            if (inside(mouseX, contentMouseY, plusX, rowY + 6, 22, 22)) {
                adjustRtsGuiScale(RTS_GUI_SCALE_STEP);
                return;
            }
            rowY += SCALE_ROW_H;
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.player_status_overlay.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.player_status_overlay.hint"))) {
                this.controller.togglePlayerStatusOverlayEnabled();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.player_status_overlay.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.container_overlay.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.container_overlay.hint"))) {
                screen.toggleContainerOverlayEnabled();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.container_overlay.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.shift_import.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.shift_import.hint"))) {
                screen.toggleOverlayShiftImportEnabled();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.shift_import.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.show_storage_ready_popup.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_storage_ready_popup.hint"))) {
                screen.toggleShowStorageReadyPopup();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_storage_ready_popup.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.show_workflow_panel.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_workflow_panel.hint"))) {
                screen.toggleShowWorkflowPanelEnabled();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_workflow_panel.hint");
        }
        rowY += SECTION_GAP;

        if (inside(mouseX, contentMouseY, x + 8, rowY, w - 16, SECTION_HEADER_H)) {
            this.helpersExpanded = !this.helpersExpanded;
            clampScroll();
            screen.persistUiState();
            return;
        }
        rowY += SECTION_HEADER_H;
        if (this.helpersExpanded) {
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24, SIMPLE_TOGGLE_ROW_H)) {
                this.controller.toggleAutoStoreMinedDrops();
                screen.persistUiState();
                return;
            }
            rowY += SIMPLE_TOGGLE_ROW_H;
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.storage_refresh_quiet.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_refresh_quiet.hint"))) {
                screen.toggleStorageRefreshQuietEnabled();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_refresh_quiet.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.storage_auto_refresh.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_auto_refresh.hint"))) {
                screen.toggleStorageAutoRefreshEnabled();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_auto_refresh.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.placed_recovery.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placed_recovery.hint"))) {
                this.controller.toggleAllowPlacedBlockRecovery();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placed_recovery.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.tool_protection.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.tool_protection.hint"))) {
                this.controller.toggleToolProtectionEnabled();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.tool_protection.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.damage_auto_return.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_auto_return.hint"))) {
                this.controller.toggleDamageAutoReturnEnabled();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_auto_return.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.bd_network.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.bd_network.hint"))) {
                this.controller.toggleBdNetworkEnabled();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.bd_network.hint");
        }
        rowY += SECTION_GAP;

        if (inside(mouseX, contentMouseY, x + 8, rowY, w - 16, SECTION_HEADER_H)) {
            this.soundExpanded = !this.soundExpanded;
            clampScroll();
            screen.persistUiState();
            return;
        }
        rowY += SECTION_HEADER_H;
        if (this.soundExpanded) {
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.rts_sounds.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.rts_sounds.hint"))) {
                RtsClientUiStateStore.setRtsSoundsEnabled(!RtsClientUiStateStore.isRtsSoundsEnabled());
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.rts_sounds.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.break_sounds.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.break_sounds.hint"))) {
                RtsClientUiStateStore.setRtsBreakSoundsEnabled(
                        !RtsClientUiStateStore.isRtsBreakSoundsEnabled());
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.break_sounds.hint");
            if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                    "screen.rtsbuilding.settings.damage_sound.hint")) {
                return;
            }
            if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                    hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_sound.hint"))) {
                this.controller.toggleDamageSoundEnabled();
                screen.persistUiState();
                return;
            }
            rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_sound.hint");
            int minusX = x + w - 124;
            int plusX = minusX + 86;
            if (inside(mouseX, contentMouseY, minusX, rowY + 8, 22, 22)) {
                RtsClientUiStateStore.setRtsBlockSoundsPerTick(
                        RtsClientUiStateStore.getRtsBlockSoundsPerTick() - 1);
                return;
            }
            if (inside(mouseX, contentMouseY, plusX, rowY + 8, 22, 22)) {
                RtsClientUiStateStore.setRtsBlockSoundsPerTick(
                        RtsClientUiStateStore.getRtsBlockSoundsPerTick() + 1);
                return;
            }
            rowY += SOUND_LIMIT_ROW_H;
        }
        rowY += SECTION_GAP;

        if (inside(mouseX, contentMouseY, x + 8, rowY, w - 16, SECTION_HEADER_H)) {
            this.animationExpanded = !this.animationExpanded;
            clampScroll();
            screen.persistUiState();
            return;
        }
        rowY += SECTION_HEADER_H;
        if (!this.animationExpanded) {
            return;
        }
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.smooth_camera.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.smooth_camera.hint"))) {
            this.controller.toggleSmoothCamera();
            screen.persistUiState();
            return;
        }
        rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.smooth_camera.hint");
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.placement_block_ghost_preview.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_block_ghost_preview.hint"))) {
            Config.setPlacementBlockGhostPreviewEnabled(!Config.isPlacementBlockGhostPreviewEnabled());
            return;
        }
        rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_block_ghost_preview.hint");
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.place_block_ghost_animation.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_block_ghost_animation.hint"))) {
            Config.setPlaceBlockGhostAnimationEnabled(!Config.isPlaceBlockGhostAnimationEnabled());
            return;
        }
        rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_block_ghost_animation.hint");
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.destroy_block_ghost_animation.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_block_ghost_animation.hint"))) {
            Config.setDestroyBlockGhostAnimationEnabled(!Config.isDestroyBlockGhostAnimationEnabled());
            return;
        }
        rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_block_ghost_animation.hint");
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.placement_wireframe_preview.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_wireframe_preview.hint"))) {
            Config.setPlacementWireframePreviewEnabled(!Config.isPlacementWireframePreviewEnabled());
            return;
        }
        rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_wireframe_preview.hint");
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.place_wireframe_animation.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_wireframe_animation.hint"))) {
            Config.setPlaceWireframeAnimationEnabled(!Config.isPlaceWireframeAnimationEnabled());
            return;
        }
        rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_wireframe_animation.hint");
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.destroy_wireframe_animation.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_wireframe_animation.hint"))) {
            Config.setDestroyWireframeAnimationEnabled(!Config.isDestroyWireframeAnimationEnabled());
            return;
        }
        rowY += hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_wireframe_animation.hint");
        if (handleHintExpandClick(mouseX, contentMouseY, x, w, rowY,
                "screen.rtsbuilding.settings.range_destroy_skeleton.hint")) {
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, rowY, w - 24,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.range_destroy_skeleton.hint"))) {
            Config.setRangeDestroySkeletonEnabled(!Config.isRangeDestroySkeletonEnabled());
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingSensitivityControl != null && button == 0) {
            setSensitivityByFraction(this.draggingSensitivityControl,
                    calcSensitivityFraction(mouseX, contentX(), contentWidth()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingSensitivityControl != null) {
            this.draggingSensitivityControl = null;
            screen.persistUiState();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private String sensitivityLabel(SensitivityControl control) {
        return switch (control) {
            case PAN_DRAG -> this.controller.getPanDragSensitivityLabel();
            case ROTATE_VIEW -> this.controller.getRotateViewSensitivityLabel();
            case KEYBOARD_MOVE -> this.controller.getKeyboardMoveSensitivityLabel();
            case WHEEL_ZOOM -> this.controller.getWheelZoomSensitivityLabel();
        };
    }

    private int sensitivityIndex(SensitivityControl control) {
        return switch (control) {
            case PAN_DRAG -> this.controller.getPanDragSensitivityIndex();
            case ROTATE_VIEW -> this.controller.getRotateViewSensitivityIndex();
            case KEYBOARD_MOVE -> this.controller.getKeyboardMoveSensitivityIndex();
            case WHEEL_ZOOM -> this.controller.getWheelZoomSensitivityIndex();
        };
    }

    private void setSensitivityByFraction(SensitivityControl control, double fraction) {
        switch (control) {
            case PAN_DRAG -> this.controller.setPanDragSensitivityByFraction(fraction);
            case ROTATE_VIEW -> this.controller.setRotateViewSensitivityByFraction(fraction);
            case KEYBOARD_MOVE -> this.controller.setKeyboardMoveSensitivityByFraction(fraction);
            case WHEEL_ZOOM -> this.controller.setWheelZoomSensitivityByFraction(fraction);
        }
    }

    private boolean handleHintExpandClick(double mouseX, double mouseY, int x, int w, int rowY, String hintKey) {
        if (!hintCanExpand(x, w, hintKey)) {
            return false;
        }
        if (!inside(mouseX, mouseY, hintExpandButtonX(x), rowY + 12,
                HINT_EXPAND_BUTTON_SIZE, HINT_EXPAND_BUTTON_SIZE)) {
            return false;
        }
        if (!this.expandedHintKeys.remove(hintKey)) {
            this.expandedHintKeys.add(hintKey);
        }
        clampScroll();
        screen.persistUiState();
        return true;
    }

    private double calcSensitivityFraction(double mouseX, int menuX, int menuW) {
        int trackX = menuX + 16;
        int trackW = menuW - 32;
        return (mouseX - trackX) / Math.max(1.0D, trackW);
    }

    private void adjustRtsGuiScale(double delta) {
        screen.adjustRtsGuiScale(delta);
    }

    private String rtsGuiScaleLabel() {
        return screen.rtsGuiScaleLabel();
    }

    private String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String trimToWidth(String text, int maxWidth) {
        return RtsClientUiUtil.trimToWidth(screen.font(), text, maxWidth);
    }

    private int maxScroll() {
        return Math.max(0, settingsContentHeight() + CONTENT_TOP_PADDING - contentHeight());
    }

    private void clampScroll() {
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
    }

    private int settingsContentHeight() {
        int x = contentX();
        int w = contentWidth();
        int height = sectionHeight(this.controlsExpanded,
                (SENSITIVITY_ROW_H * SensitivityControl.values().length)
                        + SIMPLE_TOGGLE_ROW_H
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_x_invert.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.pan_drag_y_invert.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.keyboard_batch_confirm.hint"));
        height += SECTION_GAP;
        height += sectionHeight(this.displayExpanded,
                SCALE_ROW_H
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.player_status_overlay.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.container_overlay.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.shift_import.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_storage_ready_popup.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.show_workflow_panel.hint"));
        height += SECTION_GAP;
        height += sectionHeight(this.helpersExpanded,
                SIMPLE_TOGGLE_ROW_H
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_refresh_quiet.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.storage_auto_refresh.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placed_recovery.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.tool_protection.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_auto_return.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.bd_network.hint"));
        height += SECTION_GAP;
        height += sectionHeight(this.soundExpanded,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.rts_sounds.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.break_sounds.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.damage_sound.hint")
                        + SOUND_LIMIT_ROW_H);
        height += SECTION_GAP;
        height += sectionHeight(this.animationExpanded,
                hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.smooth_camera.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_block_ghost_preview.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_block_ghost_animation.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_block_ghost_animation.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.placement_wireframe_preview.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.place_wireframe_animation.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.destroy_wireframe_animation.hint")
                        + hintToggleRowHeight(x, w, "screen.rtsbuilding.settings.range_destroy_skeleton.hint"));
        return height;
    }

    private int sectionHeight(boolean expanded, int expandedContentHeight) {
        return SECTION_HEADER_H + (expanded ? expandedContentHeight : 0);
    }

    private void renderScrollbar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackX = x + w - 7;
        int trackH = Math.max(1, h);
        g.fill(trackX, y + 2, trackX + 2, y + h - 2, 0x88313A46);
        int totalH = settingsContentHeight() + CONTENT_TOP_PADDING;
        int thumbH = Math.max(18, (int) Math.round(trackH * (trackH / (double) Math.max(trackH, totalH))));
        int thumbY = y + (int) Math.round((trackH - thumbH) * (this.scroll / (double) maxScroll));
        g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xCC8AA0B8);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void drawToggleButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h,
            boolean active, String label) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? (hover ? 0xDD45BA53 : 0xDD329A42) : (hover ? 0xDD3D4957 : 0xDD28313C);
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, active ? 0xFF8EF19A : 0xFF68788A, 0xFF10151B);
        int switchX = active ? x + w - 26 : x + 6;
        g.fill(switchX, y + 4, switchX + 18, y + h - 4, active ? 0xFF72F07A : 0xFF788696);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label, x + w / 2, y + 7, 0xFFF7FBFF);
    }

    private int hintToggleRowHeight(int x, int w, String hintKey) {
        if (!hintCanExpand(x, w, hintKey) || !this.expandedHintKeys.contains(hintKey)) {
            return HINT_TOGGLE_ROW_H;
        }
        return Math.max(HINT_TOGGLE_ROW_H, 18 + wrappedHintLines(x, w, hintKey).size() * HINT_LINE_H);
    }

    private boolean hintCanExpand(int x, int w, String hintKey) {
        return screen.font().width(text(hintKey)) > hintTextMaxWidth(x, w, true);
    }

    private List<FormattedCharSequence> wrappedHintLines(int x, int w, String hintKey) {
        return screen.font().split(Component.translatable(hintKey), hintTextMaxWidth(x, w, true));
    }

    private int hintTextX(int x, boolean hasExpandButton) {
        return x + 16 + (hasExpandButton ? HINT_EXPAND_BUTTON_SIZE + 4 : 0);
    }

    private int hintTextMaxWidth(int x, int w, boolean hasExpandButton) {
        int toggleX = x + w - 92;
        return Math.max(24, toggleX - hintTextX(x, hasExpandButton) - 8);
    }

    private int hintExpandButtonX(int x) {
        return x + 16;
    }

    private void drawHintExpandButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int rowY, boolean expanded) {
        int buttonX = hintExpandButtonX(x);
        int buttonY = rowY + 12;
        boolean hover = inside(mouseX, mouseY, buttonX, buttonY,
                HINT_EXPAND_BUTTON_SIZE, HINT_EXPAND_BUTTON_SIZE);
        int bg = hover ? 0xCC334054 : 0xAA26303D;
        RtsClientUiUtil.drawPanelFrame(g, buttonX, buttonY,
                HINT_EXPAND_BUTTON_SIZE, HINT_EXPAND_BUTTON_SIZE, bg, 0xFF6A8299, 0xFF0E1116);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), expanded ? "v" : ">",
                buttonX + HINT_EXPAND_BUTTON_SIZE / 2, buttonY + 2, 0xFFDDEBFA);
    }

    private void drawGearMenuRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h,
            String label, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xCC2D7C4B : (hover ? 0xCC334054 : 0xCC26303D);
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, 0xFF6A8299, 0xFF0E1116);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), trimToWidth(label, w - 10),
                x + w / 2, y + 7, 0xFFF2F6FB);
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("settings", this),
            PersistableProperty.boolField(
                    "settings_controls_expanded",
                    state -> state.settings.controlsExpanded,
                    (state, v) -> state.settings.controlsExpanded = v,
                    () -> this.controlsExpanded,
                    v -> this.controlsExpanded = v),
            PersistableProperty.boolField(
                    "settings_display_expanded",
                    state -> state.settings.displayExpanded,
                    (state, v) -> state.settings.displayExpanded = v,
                    () -> this.displayExpanded,
                    v -> this.displayExpanded = v),
            PersistableProperty.boolField(
                    "settings_helpers_expanded",
                    state -> state.settings.helpersExpanded,
                    (state, v) -> state.settings.helpersExpanded = v,
                    () -> this.helpersExpanded,
                    v -> this.helpersExpanded = v),
            PersistableProperty.boolField(
                    "settings_sound_expanded",
                    state -> state.settings.soundExpanded,
                    (state, v) -> state.settings.soundExpanded = v,
                    () -> this.soundExpanded,
                    v -> this.soundExpanded = v),
            PersistableProperty.boolField(
                    "settings_animation_expanded",
                    state -> state.settings.animationExpanded,
                    (state, v) -> state.settings.animationExpanded = v,
                    () -> this.animationExpanded,
                    v -> this.animationExpanded = v)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
