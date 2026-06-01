package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * 齿轮菜单（设置）面板。
 * <p>
 * 独立的齿轮菜单面板组件，处理设置面板的渲染、输入和状态管理。
 * 由 {@link BuilderScreen} 统一调度生命周期。
 */
public final class GearMenuPanel {

    // ── 状态 ──
    private boolean open = false;
    private int scroll = 0;

    private BuilderScreen screen;
    private ClientRtsController controller;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ── 输入方法 ──

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open) {
            return false;
        }
        if (button == 0) {
            if (handleClick(mouseX, mouseY)) {
                return true;
            }
            this.open = false;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.open || !isInside(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = maxScroll(menuHeight());
        if (maxScroll <= 0) {
            return true;
        }
        int delta = scrollY > 0.0D ? -18 : 18;
        this.scroll = Mth.clamp(this.scroll + delta, 0, maxScroll);
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!this.open) {
            return false;
        }
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            this.open = false;
        }
        return true;
    }

    // ── 公开查询方法 ──

    public boolean isOpen() {
        return this.open;
    }

    public void open() {
        this.open = true;
        this.scroll = 0;
    }

    public void close() {
        this.open = false;
    }

    // ── 渲染 ──

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open) {
            return;
        }
        int w = Math.min(300, screen.width - 24);
        int h = menuHeight();
        int x = (screen.width - w) / 2;
        int y = (screen.height - h) / 2;
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll(h));
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, 0xF0181D25, 0xFF6D7C90, 0xFF0A0D12);
        g.fill(x + 3, y + 3, x + w - 3, y + 24, 0xFF2A303A);
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.title"), x + 12, y + 10, 0xF4F7FF);
        RtsClientUiUtil.drawPanelFrame(g, x + w - 44, y + 6, 16, 16, 0xCC3D516D, 0xFF8FA4BF, 0xFF0D1218);
        g.drawCenteredString(screen.font(), "i", x + w - 36, y + 10, 0xDDE8F4);
        RtsClientUiUtil.drawPanelFrame(g, x + w - 24, y + 6, 16, 16, 0xCC3D516D, 0xFF8FA4BF, 0xFF0D1218);
        g.drawCenteredString(screen.font(), "x", x + w - 16, y + 10, 0xDDE8F4);

        int viewportTop = viewportTop(y);
        int viewportBottom = viewportBottom(y, h);
        enableScissor(g, x + 8, viewportTop, x + w - 8, viewportBottom);
        renderControls(g, mouseX, mouseY, x, y, w);
        g.disableScissor();
        renderScrollbar(g, x, y, w, h);
    }

    private void renderControls(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        int controlsY = contentY(y);
        drawSettingsSection(g, x + 8, controlsY, w - 16, GEAR_MENU_CONTENT_H,
                Component.translatable("screen.rtsbuilding.settings.controls").getString());
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.sensitivity"), x + 16, controlsY + 20, 0xC8D3DF);
        g.drawString(screen.font(), this.controller.getInputSensitivityLabel(), x + w - 60, controlsY + 20, 0xEAF4FF);
        int trackX = x + 16;
        int trackY = controlsY + 42;
        int trackW = w - 32;
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF07090D);
        g.fill(trackX + 1, trackY + 1, trackX + trackW - 1, trackY + 3, 0xFF313946);
        int presetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        int knobX = trackX + (int) Math.round((this.controller.getInputSensitivityIndex() / (double) Math.max(1, presetCount - 1)) * trackW);
        g.fill(knobX - 3, trackY - 5, knobX + 4, trackY + 8, 0xFF5FE36C);
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.slow"), trackX, trackY + 10, 0xB5C1CE);
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.fast"), trackX + trackW - 24, trackY + 10, 0xB5C1CE);

        int scaleButtonY = controlsY + 70;
        int minusX = x + w - 124;
        int valueX = minusX + 26;
        int plusX = valueX + 60;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.ui_scale"), x + 16, scaleButtonY + 7, 0xC8D3DF);
        drawGearMenuRow(g, mouseX, mouseY, minusX, scaleButtonY, 22, 22, "-", false);
        RtsClientUiUtil.drawPanelFrame(g, valueX, scaleButtonY, 56, 22, 0xCC1A232E, 0xFF566B80, 0xFF0D1218);
        g.drawCenteredString(screen.font(), rtsGuiScaleLabel(), valueX + 28, scaleButtonY + 7, 0xEAF4FF);
        drawGearMenuRow(g, mouseX, mouseY, plusX, scaleButtonY, 22, 22, "+", false);

        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.auto_store"), x + 16, controlsY + 118, 0xC8D3DF);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, controlsY + 112, 76, 22, this.controller.isAutoStoreMinedDrops(),
                text(this.controller.isAutoStoreMinedDrops() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));

        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.settings.head_start"), x + 16, controlsY + 146, 0xC8D3DF);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, controlsY + 140, 76, 22, this.controller.isStartCameraAtPlayerHead(),
                text(this.controller.isStartCameraAtPlayerHead() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));

        int placedRecoveryY = controlsY + 168;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, placedRecoveryY,
                "screen.rtsbuilding.settings.placed_recovery",
                "screen.rtsbuilding.settings.placed_recovery.hint",
                this.controller.isAllowPlacedBlockRecovery());

        int debugButtonY = controlsY + 204;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, debugButtonY,
                "screen.rtsbuilding.settings.debug_button",
                "screen.rtsbuilding.settings.debug_button.hint",
                this.debugButtonVisible());

        int overlayToggleY = controlsY + 240;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, overlayToggleY,
                "screen.rtsbuilding.settings.container_overlay",
                "screen.rtsbuilding.settings.container_overlay.hint",
                RtsClientUiStateStore.isContainerOverlayEnabled());

        int panDragXToggleY = controlsY + 276;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, panDragXToggleY,
                "screen.rtsbuilding.settings.pan_drag_x_invert",
                "screen.rtsbuilding.settings.pan_drag_x_invert.hint",
                this.controller.isInvertPanDragX());

        int panDragYToggleY = controlsY + 312;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, panDragYToggleY,
                "screen.rtsbuilding.settings.pan_drag_y_invert",
                "screen.rtsbuilding.settings.pan_drag_y_invert.hint",
                this.controller.isInvertPanDragY());

        int smoothCameraToggleY = controlsY + 348;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, smoothCameraToggleY,
                "screen.rtsbuilding.settings.smooth_camera",
                "screen.rtsbuilding.settings.smooth_camera.hint",
                this.controller.isSmoothCamera());

        int damageSoundToggleY = controlsY + 384;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, damageSoundToggleY,
                "screen.rtsbuilding.settings.damage_sound",
                "screen.rtsbuilding.settings.damage_sound.hint",
                this.controller.isDamageSoundEnabled());

        int damageAutoReturnToggleY = controlsY + 420;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, damageAutoReturnToggleY,
                "screen.rtsbuilding.settings.damage_auto_return",
                "screen.rtsbuilding.settings.damage_auto_return.hint",
                this.controller.isDamageAutoReturnEnabled());

        int bdNetworkToggleY = controlsY + 456;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, bdNetworkToggleY,
                "screen.rtsbuilding.settings.bd_network",
                "screen.rtsbuilding.settings.bd_network.hint",
                this.controller.isBdNetworkEnabled());
    }

    private void drawSettingsToggleWithHint(GuiGraphics g, int mouseX, int mouseY, int x, int w, int rowY,
            String labelKey, String hintKey, boolean active) {
        String label = trimToWidth(text(labelKey), w - 126);
        g.drawString(screen.font(), label, x + 16, rowY + 2, 0xC8D3DF);
        g.drawString(screen.font(), trimToWidth(text(hintKey), w - 126), x + 16, rowY + 13, 0x9FB0C2);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, rowY + 4, 76, 22, active,
                text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));
    }

    // ── 辅助方法 ──

    private boolean handleClick(double mouseX, double mouseY) {
        int w = Math.min(300, screen.width - 24);
        int h = menuHeight();
        int x = (screen.width - w) / 2;
        int y = (screen.height - h) / 2;
        if (!inside(mouseX, mouseY, x, y, w, h)) {
            return false;
        }

        if (inside(mouseX, mouseY, x + w - 24, y + 6, 16, 16)) {
            this.open = false;
            return true;
        }
        if (inside(mouseX, mouseY, x + w - 44, y + 6, 16, 16)) {
            this.open = false;
            // Open guide for settings
            return true;
        }
        int viewportTop = viewportTop(y);
        int viewportBottom = viewportBottom(y, h);
        if (!inside(mouseX, mouseY, x + 8, viewportTop, w - 16, viewportBottom - viewportTop)) {
            return true;
        }
        double contentMouseY = mouseY + this.scroll;
        int controlsY = y + 30;
        if (inside(mouseX, contentMouseY, x + 16, controlsY + 34, w - 32, 24)) {
            this.controller.setInputSensitivityByFraction(calcSensitivityFraction(mouseX, x, w));
            return true;
        }
        int scaleButtonY = controlsY + 70;
        int minusX = x + w - 124;
        int plusX = minusX + 86;
        if (inside(mouseX, contentMouseY, minusX, scaleButtonY, 22, 22)) {
            adjustRtsGuiScale(-RTS_GUI_SCALE_STEP);
            return true;
        }
        if (inside(mouseX, contentMouseY, plusX, scaleButtonY, 22, 22)) {
            adjustRtsGuiScale(RTS_GUI_SCALE_STEP);
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 104, w - 24, 28)) {
            this.controller.toggleAutoStoreMinedDrops();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 132, w - 24, 36)) {
            this.controller.toggleStartCameraAtPlayerHead();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 164, w - 24, 34)) {
            this.controller.toggleAllowPlacedBlockRecovery();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 200, w - 24, 34)) {
            screen.toggleDebugButton();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 236, w - 24, 34)) {
            RtsClientUiStateStore.setContainerOverlayEnabled(!RtsClientUiStateStore.isContainerOverlayEnabled());
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 272, w - 24, 34)) {
            this.controller.toggleInvertPanDragX();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 308, w - 24, 34)) {
            this.controller.toggleInvertPanDragY();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 344, w - 24, 34)) {
            this.controller.toggleSmoothCamera();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 380, w - 24, 34)) {
            this.controller.toggleDamageSoundEnabled();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 416, w - 24, 34)) {
            this.controller.toggleDamageAutoReturnEnabled();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 452, w - 24, 34)) {
            this.controller.toggleBdNetworkEnabled();
            return true;
        }
        return true;
    }

    private double calcSensitivityFraction(double mouseX, int menuX, int menuW) {
        int w = Math.min(300, screen.width - 24);
        int x = (screen.width - w) / 2;
        int trackX = x + 16;
        int trackW = w - 32;
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

    private int menuHeight() {
        return Mth.clamp(Math.min(GEAR_MENU_H, screen.height - 24), GEAR_MENU_MIN_H, GEAR_MENU_H);
    }

    private int maxScroll(int menuH) {
        int viewportH = Math.max(1, viewportBottom(0, menuH) - viewportTop(0));
        return Math.max(0, GEAR_MENU_CONTENT_H + 8 - viewportH);
    }

    private int viewportTop(int menuY) {
        return menuY + 30;
    }

    private int viewportBottom(int menuY, int menuH) {
        return menuY + menuH - 8;
    }

    private int contentY(int menuY) {
        return viewportTop(menuY) - this.scroll;
    }

    private boolean isInside(double mouseX, double mouseY) {
        int w = Math.min(300, screen.width - 24);
        int h = menuHeight();
        int x = (screen.width - w) / 2;
        int y = (screen.height - h) / 2;
        return inside(mouseX, mouseY, x, y, w, h);
    }

    // ── 渲染辅助方法 ──

    private void renderScrollbar(GuiGraphics g, int x, int y, int w, int h) {
        int maxScroll = maxScroll(h);
        if (maxScroll <= 0) {
            return;
        }
        int viewportTop = viewportTop(y);
        int viewportBottom = viewportBottom(y, h);
        int trackX = x + w - 7;
        int trackH = Math.max(1, viewportBottom - viewportTop);
        g.fill(trackX, viewportTop, trackX + 2, viewportBottom, 0x88313A46);
        int thumbH = Math.max(18, (int) Math.round(trackH * (trackH / (double) (GEAR_MENU_CONTENT_H + 12))));
        int thumbY = viewportTop + (int) Math.round((trackH - thumbH) * (this.scroll / (double) maxScroll));
        g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xCC8AA0B8);
    }

    private void drawSettingsSection(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.drawString(screen.font(), title, x + 2, y, 0xF4F7FF);
        RtsClientUiUtil.drawPanelFrame(g, x, y + 12, w, h - 12, 0xDD111720, 0xFF384351, 0xFF080B10);
    }

    private void drawToggleButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, boolean active, String label) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? (hover ? 0xDD45BA53 : 0xDD329A42) : (hover ? 0xDD3D4957 : 0xDD28313C);
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, active ? 0xFF8EF19A : 0xFF68788A, 0xFF10151B);
        int switchX = active ? x + w - 26 : x + 6;
        g.fill(switchX, y + 4, switchX + 18, y + h - 4, active ? 0xFF72F07A : 0xFF788696);
        g.drawCenteredString(screen.font(), label, x + w / 2, y + 7, 0xF7FBFF);
    }

    private void drawGearMenuRow(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xCC2D7C4B : (hover ? 0xCC334054 : 0xCC26303D);
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, bg, 0xFF6A8299, 0xFF0E1116);
        g.drawCenteredString(screen.font(), trimToWidth(label, w - 10), x + w / 2, y + 7, 0xF2F6FB);
    }

    private void enableScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
