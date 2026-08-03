package com.rtsbuilding.rtsbuilding.client.screen.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiFloatAnimation;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiBevelOutlineRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.OverlayStyle;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
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

    private final UiFloatAnimation damageFlash =
            new UiFloatAnimation(SystemUiClock.INSTANCE, 0.0D);
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
        this.damageFlash.snapTo(1.0D);
        this.damageFlash.animateTo(0.0D, DAMAGE_FLASH_DURATION_MS, UiEasing.LINEAR);
    }

    public void renderDamageFlash(RtsGuiContext g) {
        double visibility = this.damageFlash.value();
        if (visibility <= 0.0D) {
            return;
        }
        g.fill(0, 0, this.screen.width, this.screen.height,
                OverlayStyle.damageFlash(visibility).toArgb());
    }

    public void updateNativeCursorVisibility(boolean hide) {
        Minecraft minecraft = this.screen.getMinecraft();
        if (minecraft == null) {
            this.nativeCursorHidden = false;
            this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            return;
        }
        long window = minecraft.getWindow().getWindow();
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
        long window = minecraft.getWindow().getWindow();
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

    public void renderHomeSelectionOverlay(RtsGuiContext g, int mouseX, int mouseY) {
        updateNativeCursorVisibility(false);
        int panelW = Math.min(360, this.screen.width - 24);
        int panelX = (this.screen.width - panelW) / 2;
        int panelY = 12;
        Component cooldown = Component.translatable("screen.rtsbuilding.home_select.cooldown");
        var cooldownLines = this.screen.font().split(cooldown, panelW - 20);
        int panelH = 58 + Math.max(1, cooldownLines.size()) * 10;
        UiChromeRenderer.frame(
                new MinecraftUiCanvas(g, this.screen.font(), this.screen),
                new UiRect(panelX, panelY, panelW, panelH),
                1.0D,
                OverlayStyle.HOME_BACKGROUND,
                OverlayStyle.HOME_BORDER_LIGHT,
                OverlayStyle.HOME_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                g, this.screen.font(),
                Component.translatable("screen.rtsbuilding.home_select.title").getString(),
                panelX + panelW / 2, panelY + 8, OverlayStyle.HOME_TITLE.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(
                g, this.screen.font(),
                Component.translatable("screen.rtsbuilding.home_select.area").getString(),
                panelX + panelW / 2, panelY + 22, OverlayStyle.HOME_AREA.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(
                g, this.screen.font(),
                Component.translatable("screen.rtsbuilding.home_select.confirm").getString(),
                panelX + panelW / 2, panelY + 34, OverlayStyle.HOME_CONFIRM.toArgb());
        int cooldownY = panelY + 46;
        for (var line : cooldownLines) {
            g.drawString(this.screen.font(), line,
                    panelX + (panelW - this.screen.font().width(line)) / 2,
                    cooldownY, OverlayStyle.HOME_GUIDE.toArgb(), false);
            cooldownY += 10;
        }
        BlockHitResult hit = this.screen.isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            RtsClientUiUtil.drawCenteredStringNoShadow(
                    g, this.screen.font(),
                    Component.translatable("screen.rtsbuilding.home_select.target",
                            pos.getX(), pos.getY(), pos.getZ()).getString(),
                    this.screen.width / 2, panelY + panelH + 14,
                    OverlayStyle.HOME_GUIDE.toArgb());
        }
    }

    public void renderQuestDetectPopup(RtsGuiContext g) {
        if (!this.controller.isQuestDetectPopupVisible()) {
            return;
        }
        int x = Mth.clamp((this.screen.width - QUEST_DETECT_POPUP_W) / 2, 8, Math.max(8, this.screen.width - QUEST_DETECT_POPUP_W - 8));
        int y = TOP_H + 8;
        drawPopupFrame(g, x, y, QUEST_DETECT_POPUP_W, QUEST_DETECT_POPUP_H);
        g.drawString(this.screen.font(),
                Component.translatable("screen.rtsbuilding.quest_scan.title"),
                x + 9, y + 7, OverlayStyle.POPUP_TITLE.toArgb(), false);
        byte phase = this.controller.getQuestDetectPhase();
        String status = questDetectStatusText(phase).getString();
        int statusColor = OverlayStyle.questStatus(
                phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR,
                phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE).toArgb();
        g.drawString(this.screen.font(), this.screen.trimToWidth(status, QUEST_DETECT_POPUP_W - 18), x + 9, y + 19, statusColor, false);
        int barX = x + 9;
        int barY = y + 34;
        int barW = QUEST_DETECT_POPUP_W - 18;
        int barH = 6;
        float progress = this.controller.getQuestDetectProgress();
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * progress)));
        int progressColor = OverlayStyle.questProgress(
                phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR,
                phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE).toArgb();
        g.fill(barX, barY, barX + barW, barY + barH,
                OverlayStyle.PROGRESS_TRACK.toArgb());
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, progressColor);
        }
        drawProgressBorder(g, barX, barY, barW, barH);
    }

    public void renderStorageScanPopup(RtsGuiContext g) {
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
        drawPopupFrame(g, x, y, popupW, STORAGE_SCAN_POPUP_H);
        Component label = Component.translatable(this.controller.isStorageScanRunning()
                ? "screen.rtsbuilding.storage_scan.scanning"
                : "screen.rtsbuilding.storage_scan.ready");
        g.drawString(this.screen.font(),
                this.screen.trimToWidth(label.getString(), popupW - 18),
                x + 9, y + 6, OverlayStyle.POPUP_TITLE.toArgb(), false);
        int barX = x + 9;
        int barY = y + 20;
        int barW = popupW - 18;
        int barH = 5;
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * this.controller.getStorageScanProgress())));
        g.fill(barX, barY, barX + barW, barY + barH,
                OverlayStyle.PROGRESS_TRACK.toArgb());
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH,
                    OverlayStyle.storageProgress(
                            this.controller.isStorageScanRunning()).toArgb());
        }
        drawProgressBorder(g, barX, barY, barW, barH);
    }

    private void drawPopupFrame(RtsGuiContext g, int x, int y, int width, int height) {
        UiChromeRenderer.frame(
                new MinecraftUiCanvas(g, this.screen.font(), this.screen),
                new UiRect(x, y, width, height),
                1.0D,
                OverlayStyle.POPUP_BACKGROUND,
                OverlayStyle.POPUP_BORDER_LIGHT,
                OverlayStyle.POPUP_BORDER_DARK);
    }

    private void drawProgressBorder(
            RtsGuiContext g, int x, int y, int width, int height) {
        UiBevelOutlineRenderer.outline(
                new MinecraftUiCanvas(g, this.screen.font(), this.screen),
                new UiRect(x, y, width, height),
                OverlayStyle.PROGRESS_BORDER_LIGHT,
                OverlayStyle.PROGRESS_BORDER_DARK);
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
