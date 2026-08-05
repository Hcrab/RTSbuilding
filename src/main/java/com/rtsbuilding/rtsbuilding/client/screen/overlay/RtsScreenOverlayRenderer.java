package com.rtsbuilding.rtsbuilding.client.screen.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
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
import net.minecraft.client.resources.I18n;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;

import java.nio.IntBuffer;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 绘制 BuilderScreen 所有轻量顶层覆盖物，并管理不参与业务状态的原生鼠标样式。
 *
 * <p>1.12 使用 LWJGL2，没有 GLFW 的标准缩放光标。本类惰性创建等价的本地像素光标；
 * 隐藏预览光标时使用透明 native cursor，不调用 {@link Mouse#setGrabbed(boolean)}，因此
 * 不会改变鼠标坐标、抓取状态或 RTS 拖拽语义。
 */
public final class RtsScreenOverlayRenderer {
    private final BuilderScreen screen;
    private final ClientRtsController controller;
    private final ScreenCursorPicker cursorPicker;
    private final BottomPanel bottomPanel;

    private final UiFloatAnimation damageFlash =
            new UiFloatAnimation(SystemUiClock.INSTANCE, 0.0D);
    private boolean nativeCursorHidden;
    private RtsWindowPanel.ResizeCursor nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
    private Cursor hiddenCursor;
    private Cursor resizeEwCursor;
    private Cursor resizeNsCursor;
    private Cursor resizeNwseCursor;
    private Cursor resizeNeswCursor;

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

    public void renderDamageFlash(LegacyGuiGraphics graphics) {
        double visibility = this.damageFlash.value();
        if (visibility > 0.0D) {
            graphics.fill(0, 0, this.screen.width, this.screen.height,
                    OverlayStyle.damageFlash(visibility).toArgb());
        }
    }

    public void updateNativeCursorVisibility(boolean hide) {
        if (!canSetNativeCursor()) {
            resetCursorState();
            return;
        }
        if (hide) {
            if (this.nativeCursorHidden) {
                return;
            }
            Cursor cursor = hiddenCursor();
            if (cursor != null && setNativeCursor(cursor)) {
                this.nativeCursorHidden = true;
                this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            }
            return;
        }
        updateNativeCursor(RtsWindowPanel.ResizeCursor.DEFAULT);
    }

    public void updateNativeCursor(RtsWindowPanel.ResizeCursor cursor) {
        if (!canSetNativeCursor()) {
            resetCursorState();
            return;
        }
        RtsWindowPanel.ResizeCursor safeCursor = cursor == null
                ? RtsWindowPanel.ResizeCursor.DEFAULT : cursor;
        if (!this.nativeCursorHidden && safeCursor == this.nativeCursorStyle) {
            return;
        }
        Cursor nativeCursor = safeCursor == RtsWindowPanel.ResizeCursor.DEFAULT
                ? null : resizeCursor(safeCursor);
        if (safeCursor != RtsWindowPanel.ResizeCursor.DEFAULT && nativeCursor == null) {
            return;
        }
        if (setNativeCursor(nativeCursor)) {
            this.nativeCursorHidden = false;
            this.nativeCursorStyle = safeCursor;
        }
    }

    private boolean canSetNativeCursor() {
        Minecraft minecraft = this.screen.getMinecraft();
        return minecraft != null && Mouse.isCreated();
    }

    private void resetCursorState() {
        this.nativeCursorHidden = false;
        this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
    }

    private static boolean setNativeCursor(Cursor cursor) {
        try {
            Mouse.setNativeCursor(cursor);
            return true;
        } catch (LWJGLException | RuntimeException unavailable) {
            return false;
        }
    }

    private Cursor hiddenCursor() {
        if (this.hiddenCursor == null) {
            this.hiddenCursor = createCursor(null);
        }
        return this.hiddenCursor;
    }

    private Cursor resizeCursor(RtsWindowPanel.ResizeCursor style) {
        switch (style) {
            case RESIZE_EW:
                if (this.resizeEwCursor == null) this.resizeEwCursor = createCursor(style);
                return this.resizeEwCursor;
            case RESIZE_NS:
                if (this.resizeNsCursor == null) this.resizeNsCursor = createCursor(style);
                return this.resizeNsCursor;
            case RESIZE_NWSE:
                if (this.resizeNwseCursor == null) this.resizeNwseCursor = createCursor(style);
                return this.resizeNwseCursor;
            case RESIZE_NESW:
                if (this.resizeNeswCursor == null) this.resizeNeswCursor = createCursor(style);
                return this.resizeNeswCursor;
            case DEFAULT:
            default:
                return null;
        }
    }

    private static Cursor createCursor(RtsWindowPanel.ResizeCursor style) {
        try {
            int minimum = Math.max(1, Cursor.getMinCursorSize());
            int maximum = Math.max(minimum, Cursor.getMaxCursorSize());
            int size = Math.min(maximum, Math.max(minimum, 16));
            IntBuffer pixels = BufferUtils.createIntBuffer(size * size);
            int center = size / 2;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels.put(cursorPixel(style, x, y, center, size));
                }
            }
            pixels.flip();
            return new Cursor(size, size, center, center, 1, pixels, null);
        } catch (LWJGLException | RuntimeException unavailable) {
            return null;
        }
    }

    private static int cursorPixel(
            RtsWindowPanel.ResizeCursor style, int x, int y, int center, int size) {
        if (style == null) {
            return OverlayStyle.CURSOR_TRANSPARENT.toArgb();
        }
        int dx = x - center;
        int dy = y - center;
        boolean line;
        switch (style) {
            case RESIZE_EW:
                line = dy == 0 && Math.abs(dx) <= center - 2;
                line |= Math.abs(dx) >= center - 4 && Math.abs(dx) <= center - 2
                        && Math.abs(dy) == center - 2 - Math.abs(dx);
                break;
            case RESIZE_NS:
                line = dx == 0 && Math.abs(dy) <= center - 2;
                line |= Math.abs(dy) >= center - 4 && Math.abs(dy) <= center - 2
                        && Math.abs(dx) == center - 2 - Math.abs(dy);
                break;
            case RESIZE_NWSE:
                line = dx == dy && Math.abs(dx) <= center - 2;
                break;
            case RESIZE_NESW:
                line = dx == -dy && Math.abs(dx) <= center - 2;
                break;
            default:
                line = false;
        }
        return (line ? OverlayStyle.CURSOR_LINE : OverlayStyle.CURSOR_TRANSPARENT).toArgb();
    }

    public void renderHomeSelectionOverlay(LegacyGuiGraphics graphics, int mouseX, int mouseY) {
        updateNativeCursorVisibility(false);
        int panelW = Math.max(1, Math.min(360, this.screen.width - 24));
        int panelX = (this.screen.width - panelW) / 2;
        int panelY = 12;
        List<String> cooldownLines = this.screen.font().listFormattedStringToWidth(
                text("screen.rtsbuilding.home_select.cooldown"), panelW - 20);
        int panelH = 58 + Math.max(1, cooldownLines.size()) * 10;
        UiChromeRenderer.frame(
                new MinecraftUiCanvas(graphics, this.screen.font(), this.screen),
                new UiRect(panelX, panelY, panelW, panelH), 1.0D,
                OverlayStyle.HOME_BACKGROUND,
                OverlayStyle.HOME_BORDER_LIGHT,
                OverlayStyle.HOME_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, this.screen.font(), text("screen.rtsbuilding.home_select.title"),
                panelX + panelW / 2, panelY + 8, OverlayStyle.HOME_TITLE.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, this.screen.font(), text("screen.rtsbuilding.home_select.area"),
                panelX + panelW / 2, panelY + 22, OverlayStyle.HOME_AREA.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, this.screen.font(), text("screen.rtsbuilding.home_select.confirm"),
                panelX + panelW / 2, panelY + 34, OverlayStyle.HOME_CONFIRM.toArgb());
        int cooldownY = panelY + 46;
        for (String line : cooldownLines) {
            graphics.drawString(this.screen.font(), line,
                    panelX + (panelW - this.screen.font().getStringWidth(line)) / 2,
                    cooldownY, OverlayStyle.HOME_GUIDE.toArgb(), false);
            cooldownY += 10;
        }
        RayTraceResult hit = this.screen.isWorldArea(mouseX, mouseY)
                ? this.cursorPicker.pickBlockHit() : null;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            RtsClientUiUtil.drawCenteredStringNoShadow(
                    graphics, this.screen.font(),
                    text("screen.rtsbuilding.home_select.target", pos.getX(), pos.getY(), pos.getZ()),
                    this.screen.width / 2, panelY + panelH + 14,
                    OverlayStyle.HOME_GUIDE.toArgb());
        }
    }

    public void renderQuestDetectPopup(LegacyGuiGraphics graphics) {
        if (!this.controller.isQuestDetectPopupVisible()) {
            return;
        }
        int x = MathHelper.clamp((this.screen.width - QUEST_DETECT_POPUP_W) / 2,
                8, Math.max(8, this.screen.width - QUEST_DETECT_POPUP_W - 8));
        int y = TOP_H + 8;
        drawPopupFrame(graphics, x, y, QUEST_DETECT_POPUP_W, QUEST_DETECT_POPUP_H);
        graphics.drawString(this.screen.font(), text("screen.rtsbuilding.quest_scan.title"),
                x + 9, y + 7, OverlayStyle.POPUP_TITLE.toArgb(), false);
        byte phase = this.controller.getQuestDetectPhase();
        String status = questDetectStatusText(phase);
        int statusColor = OverlayStyle.questStatus(
                phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR,
                phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE).toArgb();
        graphics.drawString(this.screen.font(),
                this.screen.trimToWidth(status, QUEST_DETECT_POPUP_W - 18),
                x + 9, y + 19, statusColor, false);
        int barX = x + 9;
        int barY = y + 34;
        int barW = QUEST_DETECT_POPUP_W - 18;
        int barH = 6;
        float progress = this.controller.getQuestDetectProgress();
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * progress)));
        int progressColor = OverlayStyle.questProgress(
                phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR,
                phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE).toArgb();
        graphics.fill(barX, barY, barX + barW, barY + barH,
                OverlayStyle.PROGRESS_TRACK.toArgb());
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + barH, progressColor);
        }
        drawProgressBorder(graphics, barX, barY, barW, barH);
    }

    public void renderStorageScanPopup(LegacyGuiGraphics graphics) {
        if (!this.controller.isStorageScanPopupVisible()) {
            return;
        }
        if (!this.controller.isStorageScanRunning()
                && !RtsClientUiStateStore.isShowStorageReadyPopupEnabled()) {
            return;
        }
        BottomPanelLayoutTypes.BottomPanelLayout layout =
                this.bottomPanel.resolveBottomPanelLayout();
        int popupW = Math.min(STORAGE_SCAN_POPUP_W, Math.max(96, this.screen.width - 16));
        int x = MathHelper.clamp(
                layout.panelX() + (layout.panelW() - popupW) / 2,
                8, Math.max(8, this.screen.width - popupW - 8));
        int y = Math.max(TOP_H + 8, layout.panelY() - STORAGE_SCAN_POPUP_H - 6);
        drawPopupFrame(graphics, x, y, popupW, STORAGE_SCAN_POPUP_H);
        String label = text(this.controller.isStorageScanRunning()
                ? "screen.rtsbuilding.storage_scan.scanning"
                : "screen.rtsbuilding.storage_scan.ready");
        graphics.drawString(this.screen.font(),
                this.screen.trimToWidth(label, popupW - 18),
                x + 9, y + 6, OverlayStyle.POPUP_TITLE.toArgb(), false);
        int barX = x + 9;
        int barY = y + 20;
        int barW = popupW - 18;
        int barH = 5;
        int fillW = Math.max(0, Math.min(barW,
                Math.round(barW * this.controller.getStorageScanProgress())));
        graphics.fill(barX, barY, barX + barW, barY + barH,
                OverlayStyle.PROGRESS_TRACK.toArgb());
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + barH,
                    OverlayStyle.storageProgress(this.controller.isStorageScanRunning()).toArgb());
        }
        drawProgressBorder(graphics, barX, barY, barW, barH);
    }

    private void drawPopupFrame(
            LegacyGuiGraphics graphics, int x, int y, int width, int height) {
        UiChromeRenderer.frame(
                new MinecraftUiCanvas(graphics, this.screen.font(), this.screen),
                new UiRect(x, y, width, height), 1.0D,
                OverlayStyle.POPUP_BACKGROUND,
                OverlayStyle.POPUP_BORDER_LIGHT,
                OverlayStyle.POPUP_BORDER_DARK);
    }

    private void drawProgressBorder(
            LegacyGuiGraphics graphics, int x, int y, int width, int height) {
        UiBevelOutlineRenderer.outline(
                new MinecraftUiCanvas(graphics, this.screen.font(), this.screen),
                new UiRect(x, y, width, height),
                OverlayStyle.PROGRESS_BORDER_LIGHT,
                OverlayStyle.PROGRESS_BORDER_DARK);
    }

    private String questDetectStatusText(byte phase) {
        int scanned = this.controller.getQuestDetectScannedTasks();
        int total = Math.max(scanned, this.controller.getQuestDetectTotalTasks());
        int completed = this.controller.getQuestDetectCompletedTasks();
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
            return text("screen.rtsbuilding.quest_scan.scanning");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE) {
            if (completed > 0) {
                return text(completed == 1
                        ? "screen.rtsbuilding.quest_scan.completed_one"
                        : "screen.rtsbuilding.quest_scan.completed_many", completed);
            }
            return text(total > 0
                    ? "screen.rtsbuilding.quest_scan.none_completed"
                    : "screen.rtsbuilding.quest_scan.no_item_tasks");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE) {
            return text("screen.rtsbuilding.quest_scan.unavailable");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR) {
            return text("screen.rtsbuilding.quest_scan.failed");
        }
        return text("screen.rtsbuilding.quest_scan.ready");
    }

    private static String text(String key, Object... args) {
        return I18n.format(key, args);
    }
}
