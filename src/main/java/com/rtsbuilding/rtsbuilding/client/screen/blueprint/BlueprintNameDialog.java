package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;

import net.minecraft.util.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintCaptureGeometry.capturePreviewSummaryLine;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelLayout.nameDialogLayout;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.*;

/**
 * Renders the blueprint save/rename modal and classifies mouse clicks.
 */
final class BlueprintNameDialog {
    private static final int BUTTON_H = 14;
    private static final int TITLE_H = 20;
    private static final int CLOSE_SIZE = 14;

    private BlueprintNameDialog() {
    }

    static void render(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY,
            boolean capture, String value, BlueprintEntry currentEntry, BlockPos capturePointA, BlockPos capturePointB,
            long captureBlockCount) {
        BlueprintPanelLayout.NameDialogLayout layout = nameDialogLayout(screenW, screenH, capture);
        g.fill(0, 0, screenW, screenH, RtsMainlineTheme.LEGACY_66000000.toArgb());
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), RtsMainlineTheme.LEGACY_EE121922.toArgb(), RtsMainlineTheme.LEGACY_FF6E8799.toArgb(), RtsMainlineTheme.LEGACY_FF0B0E13.toArgb());
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1, layout.y() + TITLE_H, RtsMainlineTheme.LEGACY_CC233345.toArgb());
        String title = capture
                ? text("screen.rtsbuilding.blueprints.name_dialog_capture_title")
                : text("screen.rtsbuilding.blueprints.name_dialog_rename_title");
        g .text(font, trim(font, title, layout.w() - 36), layout.x() + 8, layout.y() + 6, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb(), false);
        int closeX = closeX(layout);
        drawButton(g, font, closeX, layout.y() + 3, CLOSE_SIZE, CLOSE_SIZE, "x",
                inside(mouseX, mouseY, closeX, layout.y() + 3, CLOSE_SIZE, CLOSE_SIZE));

        int textY = layout.y() + 30;
        if (capture) {
            g .text(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_title"), layout.w() - 20),
                    layout.x() + 10, textY, RtsMainlineTheme.LEGACY_FFCDEBFF.toArgb(), false);
            textY += 12;
            g .text(font, trim(font, capturePreviewSummaryLine(capturePointA, capturePointB, captureBlockCount),
                    layout.w() - 20),
                    layout.x() + 10, textY, RtsMainlineTheme.LEGACY_FFB8FFB8.toArgb(), false);
        } else if (currentEntry != null) {
            g .text(font, trim(font, text("screen.rtsbuilding.blueprints.name_dialog_current", currentEntry.name()),
                    layout.w() - 20), layout.x() + 10, textY, RtsMainlineTheme.LEGACY_FF9EACB9.toArgb(), false);
        }

        g .text(font, text("screen.rtsbuilding.blueprints.name_dialog_label"), layout.inputX(), layout.inputY() - 11,
                RtsMainlineTheme.LEGACY_FFB7CDE2.toArgb(), false);
        drawFrame(g, layout.inputX(), layout.inputY(), layout.inputW(), 18, RtsMainlineTheme.LEGACY_DD05070B.toArgb(), RtsMainlineTheme.LEGACY_FF8BA4B8.toArgb(), RtsMainlineTheme.LEGACY_FF0B0E13.toArgb());
        String displayValue = value + ((Util.getMillis() / 500L) % 2L == 0L ? "_" : "");
        g .text(font, trim(font, displayValue, layout.inputW() - 8), layout.inputX() + 4, layout.inputY() + 5,
                RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb(), false);

        drawButton(g, font, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_confirm"),
                inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H));
        drawButton(g, font, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_cancel"),
                inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H));
    }

    static void renderContent(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY,
            boolean capture, String value, BlueprintEntry currentEntry, BlockPos capturePointA, BlockPos capturePointB,
            long captureBlockCount) {
        int textY = y + RtsMainlineLayout.D10;
        if (capture) {
            g .text(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_title"), w - RtsMainlineLayout.D20),
                    x + RtsMainlineLayout.D10, textY, RtsMainlineTheme.LEGACY_FFCDEBFF.toArgb(), false);
            textY += 12;
            g .text(font, trim(font, capturePreviewSummaryLine(capturePointA, capturePointB, captureBlockCount),
                    w - RtsMainlineLayout.D20), x + RtsMainlineLayout.D10, textY, RtsMainlineTheme.LEGACY_FFB8FFB8.toArgb(), false);
        } else if (currentEntry != null) {
            g .text(font, trim(font, text("screen.rtsbuilding.blueprints.name_dialog_current", currentEntry.name()),
                    w - RtsMainlineLayout.D20), x + RtsMainlineLayout.D10, textY, RtsMainlineTheme.LEGACY_FF9EACB9.toArgb(), false);
        }

        NameContentLayout layout = contentLayout(x, y, w, h);
        g .text(font, text("screen.rtsbuilding.blueprints.name_dialog_label"), layout.inputX(),
                layout.inputY() - 11, RtsMainlineTheme.LEGACY_FFB7CDE2.toArgb(), false);
        drawFrame(g, layout.inputX(), layout.inputY(), layout.inputW(), 18,
                RtsMainlineTheme.LEGACY_DD05070B.toArgb(), RtsMainlineTheme.LEGACY_FF8BA4B8.toArgb(), RtsMainlineTheme.LEGACY_FF0B0E13.toArgb());
        String displayValue = value + ((Util.getMillis() / 500L) % 2L == 0L ? "_" : "");
        g .text(font, trim(font, displayValue, layout.inputW() - 8),
                layout.inputX() + 4, layout.inputY() + 5, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb(), false);

        drawButton(g, font, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_confirm"),
                inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H));
        drawButton(g, font, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_cancel"),
                inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H));
    }

    static ClickResult click(double mouseX, double mouseY, int screenW, int screenH, boolean capture) {
        BlueprintPanelLayout.NameDialogLayout layout = nameDialogLayout(screenW, screenH, capture);
        if (inside(mouseX, mouseY, closeX(layout), layout.y() + 3, CLOSE_SIZE, CLOSE_SIZE)) {
            return ClickResult.CANCEL;
        }
        if (inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H)) {
            return ClickResult.CONFIRM;
        }
        if (inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H)
                || !inside(mouseX, mouseY, layout.x(), layout.y(), layout.w(), layout.h())) {
            return ClickResult.CANCEL;
        }
        return ClickResult.NONE;
    }

    static ClickResult clickContent(double mouseX, double mouseY, int x, int y, int w, int h) {
        NameContentLayout layout = contentLayout(x, y, w, h);
        if (inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H)) {
            return ClickResult.CONFIRM;
        }
        if (inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H)) {
            return ClickResult.CANCEL;
        }
        return ClickResult.NONE;
    }

    enum ClickResult {
        NONE,
        CONFIRM,
        CANCEL
    }

    private static int closeX(BlueprintPanelLayout.NameDialogLayout layout) {
        return layout.x() + layout.w() - CLOSE_SIZE - 4;
    }

    private static NameContentLayout contentLayout(int x, int y, int w, int h) {
        int inputX = x + RtsMainlineLayout.D10;
        int inputW = Math.max(80, w - RtsMainlineLayout.D20);
        int cancelW = 58;
        int confirmW = 70;
        int buttonY = y + h - RtsMainlineLayout.D24;
        int inputY = Math.max(y + RtsMainlineLayout.D36, buttonY - 28);
        int cancelX = x + w - cancelW - 10;
        int confirmX = cancelX - confirmW - 6;
        return new NameContentLayout(inputX, inputY, inputW, confirmX, confirmW, cancelX, cancelW, buttonY);
    }

    private record NameContentLayout(
            int inputX,
            int inputY,
            int inputW,
            int confirmX,
            int confirmW,
            int cancelX,
            int cancelW,
            int buttonY) {
    }
}
