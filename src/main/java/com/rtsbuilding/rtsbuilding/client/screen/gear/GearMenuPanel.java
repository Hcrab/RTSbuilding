package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

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

    private int scroll = 0;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = true;
    }

    public void open() {
        this.scroll = 0;
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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

    @Override
    protected void onBoundsChanged() {
        if (this.screen != null) {
            this.screen.persistUiState();
        }
    }

    private void renderControls(GuiGraphics g, int mouseX, int mouseY, int x, int controlsY, int w) {
        drawSettingsSection(g, x + 8, controlsY, w - 16, GEAR_MENU_CONTENT_H,
                Component.translatable("screen.rtsbuilding.settings.controls").getString());
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.sensitivity"),
                x + 16, controlsY + 20, 0xC8D3DF, false);
        g.drawString(screen.font(), this.controller.getInputSensitivityLabel(),
                x + w - 60, controlsY + 20, 0xEAF4FF, false);

        int trackX = x + 16;
        int trackY = controlsY + 42;
        int trackW = w - 32;
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF07090D);
        g.fill(trackX + 1, trackY + 1, trackX + trackW - 1, trackY + 3, 0xFF313946);
        int presetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        int knobX = trackX + (int) Math.round((this.controller.getInputSensitivityIndex()
                / (double) Math.max(1, presetCount - 1)) * trackW);
        g.fill(knobX - 3, trackY - 5, knobX + 4, trackY + 8, 0xFF5FE36C);
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.slow"),
                trackX, trackY + 10, 0xB5C1CE, false);
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.fast"),
                trackX + trackW - 24, trackY + 10, 0xB5C1CE, false);

        int scaleButtonY = controlsY + 70;
        int minusX = x + w - 124;
        int valueX = minusX + 26;
        int plusX = valueX + 60;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.ui_scale"),
                x + 16, scaleButtonY + 7, 0xC8D3DF, false);
        drawGearMenuRow(g, mouseX, mouseY, minusX, scaleButtonY, 22, 22, "-", false);
        RtsClientUiUtil.drawPanelFrame(g, valueX, scaleButtonY, 56, 22, 0xCC1A232E, 0xFF566B80, 0xFF0D1218);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), rtsGuiScaleLabel(),
                valueX + 28, scaleButtonY + 7, 0xEAF4FF);
        drawGearMenuRow(g, mouseX, mouseY, plusX, scaleButtonY, 22, 22, "+", false);

        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.auto_store"),
                x + 16, controlsY + 118, 0xC8D3DF, false);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, controlsY + 112, 76, 22,
                this.controller.isAutoStoreMinedDrops(),
                text(this.controller.isAutoStoreMinedDrops() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));

        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 140,
                "screen.rtsbuilding.settings.storage_refresh_quiet",
                "screen.rtsbuilding.settings.storage_refresh_quiet.hint",
                RtsClientUiStateStore.isStorageRefreshQuietEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 176,
                "screen.rtsbuilding.settings.storage_auto_refresh",
                "screen.rtsbuilding.settings.storage_auto_refresh.hint",
                RtsClientUiStateStore.isStorageAutoRefreshEnabled());

        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.head_start"),
                x + 16, controlsY + 218, 0xC8D3DF, false);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, controlsY + 212, 76, 22,
                this.controller.isStartCameraAtPlayerHead(),
                text(this.controller.isStartCameraAtPlayerHead() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));

        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 240,
                "screen.rtsbuilding.settings.placed_recovery",
                "screen.rtsbuilding.settings.placed_recovery.hint",
                this.controller.isAllowPlacedBlockRecovery());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 276,
                "screen.rtsbuilding.settings.debug_button",
                "screen.rtsbuilding.settings.debug_button.hint",
                this.debugButtonVisible());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 312,
                "screen.rtsbuilding.settings.container_overlay",
                "screen.rtsbuilding.settings.container_overlay.hint",
                RtsClientUiStateStore.isContainerOverlayEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 348,
                "screen.rtsbuilding.settings.shift_import",
                "screen.rtsbuilding.settings.shift_import.hint",
                RtsClientUiStateStore.isOverlayShiftImportEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 384,
                "screen.rtsbuilding.settings.pan_drag_x_invert",
                "screen.rtsbuilding.settings.pan_drag_x_invert.hint",
                this.controller.isInvertPanDragX());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 420,
                "screen.rtsbuilding.settings.pan_drag_y_invert",
                "screen.rtsbuilding.settings.pan_drag_y_invert.hint",
                this.controller.isInvertPanDragY());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 456,
                "screen.rtsbuilding.settings.smooth_camera",
                "screen.rtsbuilding.settings.smooth_camera.hint",
                this.controller.isSmoothCamera());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 492,
                "screen.rtsbuilding.settings.damage_sound",
                "screen.rtsbuilding.settings.damage_sound.hint",
                this.controller.isDamageSoundEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 528,
                "screen.rtsbuilding.settings.damage_auto_return",
                "screen.rtsbuilding.settings.damage_auto_return.hint",
                this.controller.isDamageAutoReturnEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 564,
                "screen.rtsbuilding.settings.bd_network",
                "screen.rtsbuilding.settings.bd_network.hint",
                this.controller.isBdNetworkEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 600,
                "screen.rtsbuilding.settings.block_ghost_preview",
                "screen.rtsbuilding.settings.block_ghost_preview.hint",
                Config.isBlockGhostPreviewEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 636,
                "screen.rtsbuilding.settings.wireframe_preview",
                "screen.rtsbuilding.settings.wireframe_preview.hint",
                Config.isWireframePreviewEnabled());
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, controlsY + 672,
                "screen.rtsbuilding.settings.range_destroy_skeleton",
                "screen.rtsbuilding.settings.range_destroy_skeleton.hint",
                Config.isRangeDestroySkeletonEnabled());
    }

    private void drawSettingsToggleWithHint(GuiGraphics g, int mouseX, int mouseY, int x, int w, int rowY,
            String labelKey, String hintKey, boolean active) {
        String label = trimToWidth(text(labelKey), w - 126);
        g.drawString(screen.font(), label, x + 16, rowY + 2, 0xC8D3DF, false);
        g.drawString(screen.font(), trimToWidth(text(hintKey), w - 126), x + 16, rowY + 13, 0x9FB0C2, false);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, rowY + 4, 76, 22, active,
                text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));
    }

    private void handleClick(double mouseX, double mouseY) {
        int x = contentX();
        int w = contentWidth();
        int controlsY = contentY() + CONTENT_TOP_PADDING;
        double contentMouseY = mouseY + this.scroll;

        if (inside(mouseX, contentMouseY, x + 16, controlsY + 34, w - 32, 24)) {
            this.controller.setInputSensitivityByFraction(calcSensitivityFraction(mouseX, x, w));
            return;
        }
        int scaleButtonY = controlsY + 70;
        int minusX = x + w - 124;
        int plusX = minusX + 86;
        if (inside(mouseX, contentMouseY, minusX, scaleButtonY, 22, 22)) {
            adjustRtsGuiScale(-RTS_GUI_SCALE_STEP);
            return;
        }
        if (inside(mouseX, contentMouseY, plusX, scaleButtonY, 22, 22)) {
            adjustRtsGuiScale(RTS_GUI_SCALE_STEP);
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 104, w - 24, 28)) {
            this.controller.toggleAutoStoreMinedDrops();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 136, w - 24, 34)) {
            RtsClientUiStateStore.setStorageRefreshQuietEnabled(!RtsClientUiStateStore.isStorageRefreshQuietEnabled());
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 172, w - 24, 34)) {
            RtsClientUiStateStore.setStorageAutoRefreshEnabled(!RtsClientUiStateStore.isStorageAutoRefreshEnabled());
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 204, w - 24, 36)) {
            this.controller.toggleStartCameraAtPlayerHead();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 236, w - 24, 34)) {
            this.controller.toggleAllowPlacedBlockRecovery();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 272, w - 24, 34)) {
            screen.toggleDebugButton();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 308, w - 24, 34)) {
            RtsClientUiStateStore.setContainerOverlayEnabled(!RtsClientUiStateStore.isContainerOverlayEnabled());
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 344, w - 24, 34)) {
            RtsClientUiStateStore.setOverlayShiftImportEnabled(!RtsClientUiStateStore.isOverlayShiftImportEnabled());
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 380, w - 24, 34)) {
            this.controller.toggleInvertPanDragX();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 416, w - 24, 34)) {
            this.controller.toggleInvertPanDragY();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 452, w - 24, 34)) {
            this.controller.toggleSmoothCamera();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 488, w - 24, 34)) {
            this.controller.toggleDamageSoundEnabled();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 524, w - 24, 34)) {
            this.controller.toggleDamageAutoReturnEnabled();
            screen.persistUiState();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 560, w - 24, 34)) {
            this.controller.toggleBdNetworkEnabled();
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 596, w - 24, 34)) {
            Config.setBlockGhostPreviewEnabled(!Config.isBlockGhostPreviewEnabled());
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 632, w - 24, 34)) {
            Config.setWireframePreviewEnabled(!Config.isWireframePreviewEnabled());
            return;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 668, w - 24, 34)) {
            Config.setRangeDestroySkeletonEnabled(!Config.isRangeDestroySkeletonEnabled());
        }
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

    private boolean debugButtonVisible() {
        return screen.isDebugButtonVisible();
    }

    private String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String trimToWidth(String text, int maxWidth) {
        return RtsClientUiUtil.trimToWidth(screen.font(), text, maxWidth);
    }

    private int maxScroll() {
        return Math.max(0, GEAR_MENU_CONTENT_H + CONTENT_TOP_PADDING - contentHeight());
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int w, int h) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackX = x + w - 7;
        int trackH = Math.max(1, h);
        g.fill(trackX, y + 2, trackX + 2, y + h - 2, 0x88313A46);
        int totalH = GEAR_MENU_CONTENT_H + CONTENT_TOP_PADDING;
        int thumbH = Math.max(18, (int) Math.round(trackH * (trackH / (double) Math.max(trackH, totalH))));
        int thumbY = y + (int) Math.round((trackH - thumbH) * (this.scroll / (double) maxScroll));
        g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xCC8AA0B8);
    }

    private void drawSettingsSection(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.drawString(screen.font(), title, x + 2, y, 0xF4F7FF, false);
        RtsClientUiUtil.drawPanelFrame(g, x, y + 12, w, h - 12, 0xDD111720, 0xFF384351, 0xFF080B10);
    }

    private void drawToggleButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
            boolean active, String label) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? (hover ? 0xDD45BA53 : 0xDD329A42) : (hover ? 0xDD3D4957 : 0xDD28313C);
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, active ? 0xFF8EF19A : 0xFF68788A, 0xFF10151B);
        int switchX = active ? x + w - 26 : x + 6;
        g.fill(switchX, y + 4, switchX + 18, y + h - 4, active ? 0xFF72F07A : 0xFF788696);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label, x + w / 2, y + 7, 0xF7FBFF);
    }

    private void drawGearMenuRow(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
            String label, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xCC2D7C4B : (hover ? 0xCC334054 : 0xCC26303D);
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, 0xFF6A8299, 0xFF0E1116);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), trimToWidth(label, w - 10),
                x + w / 2, y + 7, 0xF2F6FB);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
