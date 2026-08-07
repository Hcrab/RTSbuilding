package com.rtsbuilding.rtsbuilding.client.screen.overlay;

import com.rtsbuilding.rtsbuilding.client.util.RtsUiFrameRenderer;

import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

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
    private RtsWindowPanel.ResizeCursor nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
    private long resizeEwCursor;
    private long resizeNsCursor;
    private long resizeNwseCursor;
    private long resizeNeswCursor;

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

    public void renderDamageFlash(GuiGraphicsExtractor g) {
        if (this.damageFlashStartMs < 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.damageFlashStartMs;
        if (elapsed >= DAMAGE_FLASH_DURATION_MS) {
            this.damageFlashStartMs = -1L;
            return;
        }
        float alpha = 1.0F - (float) elapsed / (float) DAMAGE_FLASH_DURATION_MS;
        int argb = ((int) (alpha * 128.0F) << 24) | RtsMainlineTheme.LEGACY_00FF0000.toArgb();
        g.fill(0, 0, this.screen.width, this.screen.height, argb);
    }

    public void updateNativeCursorVisibility(boolean hide) {
        Minecraft minecraft = this.screen.getMinecraft();
        if (minecraft == null) {
            this.nativeCursorHidden = false;
            this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            return;
        }
        long window = minecraft.getWindow().handle();
        if (hide) {
            if (this.nativeCursorStyle != RtsWindowPanel.ResizeCursor.DEFAULT) {
                GLFW.glfwSetCursor(window, 0L);
                this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            }
            if (this.nativeCursorHidden) {
                return;
            }
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            this.nativeCursorHidden = true;
            return;
        }
        updateNativeCursor(RtsWindowPanel.ResizeCursor.DEFAULT);
    }

    public void updateNativeCursor(RtsWindowPanel.ResizeCursor cursor) {
        Minecraft minecraft = this.screen.getMinecraft();
        if (minecraft == null) {
            this.nativeCursorHidden = false;
            this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            return;
        }
        long window = minecraft.getWindow().handle();
        if (this.nativeCursorHidden) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            this.nativeCursorHidden = false;
        }
        RtsWindowPanel.ResizeCursor safeCursor = cursor == null
                ? RtsWindowPanel.ResizeCursor.DEFAULT
                : cursor;
        if (safeCursor == this.nativeCursorStyle) {
            return;
        }
        GLFW.glfwSetCursor(window, cursorHandle(safeCursor));
        this.nativeCursorStyle = safeCursor;
    }

    private long cursorHandle(RtsWindowPanel.ResizeCursor cursor) {
        return switch (cursor) {
            case RESIZE_EW -> {
                if (this.resizeEwCursor == 0L) {
                    this.resizeEwCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR);
                }
                yield this.resizeEwCursor;
            }
            case RESIZE_NS -> {
                if (this.resizeNsCursor == 0L) {
                    this.resizeNsCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR);
                }
                yield this.resizeNsCursor;
            }
            case RESIZE_NWSE -> {
                if (this.resizeNwseCursor == 0L) {
                    this.resizeNwseCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
                }
                yield this.resizeNwseCursor;
            }
            case RESIZE_NESW -> {
                if (this.resizeNeswCursor == 0L) {
                    this.resizeNeswCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR);
                }
                yield this.resizeNeswCursor;
            }
            case DEFAULT -> 0L;
        };
    }

    public void renderHomeSelectionOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        updateNativeCursorVisibility(false);
        int panelW = Math.min(360, this.screen.width - 24);
        int panelX = (this.screen.width - panelW) / 2;
        int panelY = 12;
        Component cooldown = Component.translatable("screen.rtsbuilding.home_select.cooldown");
        var cooldownLines = this.screen.font().split(cooldown, panelW - 20);
        int panelH = 58 + Math.max(1, cooldownLines.size()) * 10;
        RtsUiFrameRenderer.frame(g, panelX, panelY, panelW, panelH, RtsMainlineTheme.LEGACY_CC101820.toArgb(), RtsMainlineTheme.LEGACY_FF6E8799.toArgb(), RtsMainlineTheme.LEGACY_FF0D1218.toArgb());
        g .centeredText(this.screen.font(), Component.translatable("screen.rtsbuilding.home_select.title"), panelX + panelW / 2, panelY + 8, RtsMainlineTheme.LEGACY_FFFFFF.toArgb());
        g .centeredText(this.screen.font(), Component.translatable("screen.rtsbuilding.home_select.area"), panelX + panelW / 2, panelY + 22, RtsMainlineTheme.LEGACY_D8E6F5.toArgb());
        g .centeredText(this.screen.font(), Component.translatable("screen.rtsbuilding.home_select.confirm"), panelX + panelW / 2, panelY + 34, RtsMainlineTheme.LEGACY_BFD2E6.toArgb());
        int cooldownY = panelY + 46;
        for (var line : cooldownLines) {
            g .text(this.screen.font(), line, panelX + (panelW - this.screen.font().width(line)) / 2, cooldownY, RtsMainlineTheme.LEGACY_FFE7C46A.toArgb());
            cooldownY += 10;
        }
        BlockHitResult hit = this.screen.isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            g .centeredText(this.screen.font(),
                    Component.translatable("screen.rtsbuilding.home_select.target", pos.getX(), pos.getY(), pos.getZ()),
                    this.screen.width / 2,
                    panelY + panelH + 14,
                    RtsMainlineTheme.LEGACY_FFE7C46A.toArgb());
        }
    }

    public void renderQuestDetectPopup(GuiGraphicsExtractor g) {
        if (!this.controller.isQuestDetectPopupVisible()) {
            return;
        }
        int x = Mth.clamp((this.screen.width - QUEST_DETECT_POPUP_W) / 2, 8, Math.max(8, this.screen.width - QUEST_DETECT_POPUP_W - 8));
        int y = TOP_H + 8;
        RtsUiFrameRenderer.frame(g, x, y, QUEST_DETECT_POPUP_W, QUEST_DETECT_POPUP_H, RtsMainlineTheme.LEGACY_EE151A22.toArgb(), RtsMainlineTheme.LEGACY_FF61758A.toArgb(), RtsMainlineTheme.LEGACY_FF0D1117.toArgb());
        g .text(this.screen.font(), Component.translatable("screen.rtsbuilding.quest_scan.title"), x + 9, y + 7, RtsMainlineTheme.LEGACY_F2F7FF.toArgb(), false);
        byte phase = this.controller.getQuestDetectPhase();
        String status = questDetectStatusText(phase).getString();
        int statusColor = phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                ? RtsMainlineTheme.LEGACY_FFFFB0B0.toArgb()
                : phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE
                        ? RtsMainlineTheme.LEGACY_FFE7C46A.toArgb()
                        : RtsMainlineTheme.LEGACY_FFCFE3F7.toArgb();
        g .text(this.screen.font(), this.screen.trimToWidth(status, QUEST_DETECT_POPUP_W - 18), x + 9, y + 19, statusColor, false);
        int barX = x + 9;
        int barY = y + 34;
        int barW = QUEST_DETECT_POPUP_W - 18;
        int barH = 6;
        float progress = this.controller.getQuestDetectProgress();
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * progress)));
        int progressColor = phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                ? RtsMainlineTheme.LEGACY_FFE07070.toArgb()
                : phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE
                        ? RtsMainlineTheme.LEGACY_FF78B28C.toArgb()
                        : RtsMainlineTheme.LEGACY_FF88BEF4.toArgb();
        g.fill(barX, barY, barX + barW, barY + barH, RtsMainlineTheme.LEGACY_AA202832.toArgb());
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, progressColor);
        }
        g.horizontalLine(barX, barX + barW, barY, RtsMainlineTheme.LEGACY_FF405064.toArgb());
        g.horizontalLine(barX, barX + barW, barY + barH, RtsMainlineTheme.LEGACY_FF0A0D12.toArgb());
        g.verticalLine(barX, barY, barY + barH, RtsMainlineTheme.LEGACY_FF405064.toArgb());
        g.verticalLine(barX + barW, barY, barY + barH, RtsMainlineTheme.LEGACY_FF0A0D12.toArgb());
    }

    public void renderStorageScanPopup(GuiGraphicsExtractor g) {
        if (!this.controller.isStorageScanPopupVisible()) {
            return;
        }
        if (!this.controller.isStorageScanRunning() && !RtsClientUiStateStore.isShowStorageReadyPopupEnabled()) {
            return;
        }
        BottomPanelLayoutTypes.BottomPanelLayout layout = this.bottomPanel.resolveBottomPanelLayout();
        int popupW = Math.min(STORAGE_SCAN_POPUP_W, Math.max(96, this.screen.width - 16));
        int x = Mth.clamp(
                layout.panelX() + (layout.panelW() - popupW) / 2,
                8,
                Math.max(8, this.screen.width - popupW - 8));
        int y = Math.max(TOP_H + 8, layout.panelY() - STORAGE_SCAN_POPUP_H - 6);
        RtsUiFrameRenderer.frame(g, x, y, popupW, STORAGE_SCAN_POPUP_H, RtsMainlineTheme.LEGACY_EE151A22.toArgb(), RtsMainlineTheme.LEGACY_FF61758A.toArgb(), RtsMainlineTheme.LEGACY_FF0D1117.toArgb());
        Component label = Component.translatable(this.controller.isStorageScanRunning()
                ? "screen.rtsbuilding.storage_scan.scanning"
                : "screen.rtsbuilding.storage_scan.ready");
        g .text(this.screen.font(), this.screen.trimToWidth(label.getString(), popupW - 18), x + 9, y + 6, RtsMainlineTheme.LEGACY_F2F7FF.toArgb(), false);
        int barX = x + 9;
        int barY = y + 20;
        int barW = popupW - 18;
        int barH = 5;
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * this.controller.getStorageScanProgress())));
        g.fill(barX, barY, barX + barW, barY + barH, RtsMainlineTheme.LEGACY_AA202832.toArgb());
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH,
                    this.controller.isStorageScanRunning() ? RtsMainlineTheme.LEGACY_FF88BEF4.toArgb() : RtsMainlineTheme.LEGACY_FF78B28C.toArgb());
        }
        g.horizontalLine(barX, barX + barW, barY, RtsMainlineTheme.LEGACY_FF405064.toArgb());
        g.horizontalLine(barX, barX + barW, barY + barH, RtsMainlineTheme.LEGACY_FF0A0D12.toArgb());
        g.verticalLine(barX, barY, barY + barH, RtsMainlineTheme.LEGACY_FF405064.toArgb());
        g.verticalLine(barX + barW, barY, barY + barH, RtsMainlineTheme.LEGACY_FF0A0D12.toArgb());
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
