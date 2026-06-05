package com.rtsbuilding.rtsbuilding.client.screen;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.DAMAGE_FLASH_DURATION_MS;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.QUEST_DETECT_POPUP_H;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.QUEST_DETECT_POPUP_W;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.STORAGE_SCAN_POPUP_H;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.STORAGE_SCAN_POPUP_W;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.TOP_H;

/**
 * Renders lightweight overlays owned by the RTS builder screen.
 *
 * <p>This class owns transient visual overlay state such as the damage flash and
 * native cursor visibility, plus small top-level popups. It intentionally does
 * not own the main panel render order, modal dialogs, input routing, storage
 * overlay behavior, or gameplay mutation. Those remain in their existing
 * mainline owners while PR #71's renderer-split direction is absorbed safely.
 */
public final class RtsScreenOverlayRenderer {
    private final BuilderScreen screen;
    private final ClientRtsController controller;
    private final ScreenCursorPicker cursorPicker;
    private final BottomPanel bottomPanel;

    private long damageFlashStartMs = -1L;
    private boolean nativeCursorHidden = false;

    public RtsScreenOverlayRenderer(
            BuilderScreen screen,
            ClientRtsController controller,
            ScreenCursorPicker cursorPicker,
            BottomPanel bottomPanel) {
        this.screen = screen;
        this.controller = controller;
        this.cursorPicker = cursorPicker;
        this.bottomPanel = bottomPanel;
    }

    public void triggerDamageFlash() {
        this.damageFlashStartMs = System.currentTimeMillis();
    }

    public void renderDamageFlash(GuiGraphics g) {
        if (this.damageFlashStartMs < 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.damageFlashStartMs;
        if (elapsed >= DAMAGE_FLASH_DURATION_MS) {
            this.damageFlashStartMs = -1L;
            return;
        }
        float alpha = 1.0F - (float) elapsed / (float) DAMAGE_FLASH_DURATION_MS;
        int argb = ((int) (alpha * 128.0F) << 24) | 0x00FF0000;
        g.fill(0, 0, this.screen.width, this.screen.height, argb);
    }

    public void updateNativeCursorVisibility(boolean hide) {
        Minecraft minecraft = this.screen.getMinecraft();
        if (minecraft == null) {
            this.nativeCursorHidden = false;
            return;
        }
        long window = minecraft.getWindow().getWindow();
        if (hide == this.nativeCursorHidden) {
            return;
        }
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, hide ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
        this.nativeCursorHidden = hide;
    }

    public void renderHomeSelectionOverlay(GuiGraphics g, int mouseX, int mouseY) {
        updateNativeCursorVisibility(false);
        int panelW = Math.min(360, this.screen.width - 24);
        int panelX = (this.screen.width - panelW) / 2;
        int panelY = 12;
        Component cooldown = Component.translatable("screen.rtsbuilding.home_select.cooldown");
        var cooldownLines = this.screen.font().split(cooldown, panelW - 20);
        int panelH = 58 + Math.max(1, cooldownLines.size()) * 10;
        RtsClientUiUtil.drawPanelFrame(g, panelX, panelY, panelW, panelH, 0xCC101820, 0xFF6E8799, 0xFF0D1218);
        g.drawCenteredString(this.screen.font(), Component.translatable("screen.rtsbuilding.home_select.title"), panelX + panelW / 2, panelY + 8, 0xFFFFFF);
        g.drawCenteredString(this.screen.font(), Component.translatable("screen.rtsbuilding.home_select.area"), panelX + panelW / 2, panelY + 22, 0xD8E6F5);
        g.drawCenteredString(this.screen.font(), Component.translatable("screen.rtsbuilding.home_select.confirm"), panelX + panelW / 2, panelY + 34, 0xBFD2E6);
        int cooldownY = panelY + 46;
        for (var line : cooldownLines) {
            g.drawString(this.screen.font(), line, panelX + (panelW - this.screen.font().width(line)) / 2, cooldownY, 0xFFE7C46A);
            cooldownY += 10;
        }
        BlockHitResult hit = this.screen.isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            g.drawCenteredString(this.screen.font(),
                    Component.translatable("screen.rtsbuilding.home_select.target", pos.getX(), pos.getY(), pos.getZ()),
                    this.screen.width / 2,
                    panelY + panelH + 14,
                    0xFFE7C46A);
        }
    }

    public void renderQuestDetectPopup(GuiGraphics g) {
        if (!this.controller.isQuestDetectPopupVisible()) {
            return;
        }
        int x = Mth.clamp((this.screen.width - QUEST_DETECT_POPUP_W) / 2, 8, Math.max(8, this.screen.width - QUEST_DETECT_POPUP_W - 8));
        int y = TOP_H + 8;
        RtsClientUiUtil.drawPanelFrame(g, x, y, QUEST_DETECT_POPUP_W, QUEST_DETECT_POPUP_H, 0xEE151A22, 0xFF61758A, 0xFF0D1117);
        g.drawString(this.screen.font(), Component.translatable("screen.rtsbuilding.quest_scan.title"), x + 9, y + 7, 0xF2F7FF, false);
        byte phase = this.controller.getQuestDetectPhase();
        String status = questDetectStatusText(phase).getString();
        int statusColor = phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                ? 0xFFFFB0B0
                : phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE
                        ? 0xFFE7C46A
                        : 0xFFCFE3F7;
        g.drawString(this.screen.font(), this.screen.trimToWidth(status, QUEST_DETECT_POPUP_W - 18), x + 9, y + 19, statusColor, false);
        int barX = x + 9;
        int barY = y + 34;
        int barW = QUEST_DETECT_POPUP_W - 18;
        int barH = 6;
        float progress = this.controller.getQuestDetectProgress();
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * progress)));
        int progressColor = phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                ? 0xFFE07070
                : phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE
                        ? 0xFF78B28C
                        : 0xFF88BEF4;
        g.fill(barX, barY, barX + barW, barY + barH, 0xAA202832);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, progressColor);
        }
        g.hLine(barX, barX + barW, barY, 0xFF405064);
        g.hLine(barX, barX + barW, barY + barH, 0xFF0A0D12);
        g.vLine(barX, barY, barY + barH, 0xFF405064);
        g.vLine(barX + barW, barY, barY + barH, 0xFF0A0D12);
    }

    public void renderStorageScanPopup(GuiGraphics g) {
        if (!this.controller.isStorageScanPopupVisible()) {
            return;
        }
        BottomPanelLayoutTypes.BottomPanelLayout layout = this.bottomPanel.resolveBottomPanelLayout();
        int popupW = Math.min(STORAGE_SCAN_POPUP_W, Math.max(96, this.screen.width - 16));
        int x = Mth.clamp(
                layout.panelX() + (layout.panelW() - popupW) / 2,
                8,
                Math.max(8, this.screen.width - popupW - 8));
        int y = Math.max(TOP_H + 8, layout.panelY() - STORAGE_SCAN_POPUP_H - 6);
        RtsClientUiUtil.drawPanelFrame(g, x, y, popupW, STORAGE_SCAN_POPUP_H, 0xEE151A22, 0xFF61758A, 0xFF0D1117);
        Component label = Component.translatable(this.controller.isStorageScanRunning()
                ? "screen.rtsbuilding.storage_scan.scanning"
                : "screen.rtsbuilding.storage_scan.ready");
        g.drawString(this.screen.font(), this.screen.trimToWidth(label.getString(), popupW - 18), x + 9, y + 6, 0xF2F7FF, false);
        int barX = x + 9;
        int barY = y + 20;
        int barW = popupW - 18;
        int barH = 5;
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * this.controller.getStorageScanProgress())));
        g.fill(barX, barY, barX + barW, barY + barH, 0xAA202832);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH,
                    this.controller.isStorageScanRunning() ? 0xFF88BEF4 : 0xFF78B28C);
        }
        g.hLine(barX, barX + barW, barY, 0xFF405064);
        g.hLine(barX, barX + barW, barY + barH, 0xFF0A0D12);
        g.vLine(barX, barY, barY + barH, 0xFF405064);
        g.vLine(barX + barW, barY, barY + barH, 0xFF0A0D12);
    }

    private Component questDetectStatusText(byte phase) {
        int scanned = this.controller.getQuestDetectScannedTasks();
        int total = Math.max(scanned, this.controller.getQuestDetectTotalTasks());
        int completed = this.controller.getQuestDetectCompletedTasks();
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
            return Component.translatable("screen.rtsbuilding.quest_scan.scanning");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE) {
            if (completed > 0) {
                return completed == 1
                        ? Component.translatable("screen.rtsbuilding.quest_scan.completed_one")
                        : Component.translatable("screen.rtsbuilding.quest_scan.completed_many", completed);
            }
            return total > 0
                    ? Component.translatable("screen.rtsbuilding.quest_scan.none_completed")
                    : Component.translatable("screen.rtsbuilding.quest_scan.no_item_tasks");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE) {
            return Component.translatable("screen.rtsbuilding.quest_scan.unavailable");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR) {
            return Component.translatable("screen.rtsbuilding.quest_scan.failed");
        }
        return Component.translatable("screen.rtsbuilding.quest_scan.ready");
    }
}
