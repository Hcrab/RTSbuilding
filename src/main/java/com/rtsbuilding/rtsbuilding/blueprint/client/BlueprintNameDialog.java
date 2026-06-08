package com.rtsbuilding.rtsbuilding.blueprint.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintCaptureGeometry.capturePreviewSummaryLine;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelLayout.nameDialogLayout;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.drawButton;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.drawFrame;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.inside;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.text;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.trim;

/**
 * Renders the blueprint save/rename modal and classifies mouse clicks.
 */
final class BlueprintNameDialog {
    private static final int BUTTON_H = 14;
    private static final int TITLE_H = 20;
    private static final int CLOSE_SIZE = 14;

    private BlueprintNameDialog() {
    }

    static void render(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY,
            boolean capture, String value, BlueprintEntry currentEntry, BlockPos capturePointA, BlockPos capturePointB,
            long captureBlockCount) {
        BlueprintPanelLayout.NameDialogLayout layout = nameDialogLayout(screenW, screenH, capture);
        g.fill(0, 0, screenW, screenH, 0x66000000);
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), 0xEE121922, 0xFF6E8799, 0xFF0B0E13);
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1, layout.y() + TITLE_H, 0xCC233345);
        String title = capture
                ? text("screen.rtsbuilding.blueprints.name_dialog_capture_title")
                : text("screen.rtsbuilding.blueprints.name_dialog_rename_title");
        g.drawString(font, trim(font, title, layout.w() - 36), layout.x() + 8, layout.y() + 6, 0xFFEAF2FF, false);
        int closeX = closeX(layout);
        drawButton(g, font, closeX, layout.y() + 3, CLOSE_SIZE, CLOSE_SIZE, "x",
                inside(mouseX, mouseY, closeX, layout.y() + 3, CLOSE_SIZE, CLOSE_SIZE));

        int textY = layout.y() + 30;
        if (capture) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_title"), layout.w() - 20),
                    layout.x() + 10, textY, 0xFFCDEBFF, false);
            textY += 12;
            g.drawString(font, trim(font, capturePreviewSummaryLine(capturePointA, capturePointB, captureBlockCount),
                    layout.w() - 20),
                    layout.x() + 10, textY, 0xFFB8FFB8, false);
        } else if (currentEntry != null) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.name_dialog_current", currentEntry.name()),
                    layout.w() - 20), layout.x() + 10, textY, 0xFF9EACB9, false);
        }

        g.drawString(font, text("screen.rtsbuilding.blueprints.name_dialog_label"), layout.inputX(), layout.inputY() - 11,
                0xFFB7CDE2, false);
        drawFrame(g, layout.inputX(), layout.inputY(), layout.inputW(), 18, 0xDD05070B, 0xFF8BA4B8, 0xFF0B0E13);
        String displayValue = value + ((Util.getMillis() / 500L) % 2L == 0L ? "_" : "");
        g.drawString(font, trim(font, displayValue, layout.inputW() - 8), layout.inputX() + 4, layout.inputY() + 5,
                0xFFEAF2FF, false);

        drawButton(g, font, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_confirm"),
                inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H));
        drawButton(g, font, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_cancel"),
                inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H));
    }

    static void renderContent(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY,
            boolean capture, String value, BlueprintEntry currentEntry, BlockPos capturePointA, BlockPos capturePointB,
            long captureBlockCount) {
        int textY = y + 10;
        if (capture) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_title"), w - 20),
                    x + 10, textY, 0xFFCDEBFF, false);
            textY += 12;
            g.drawString(font, trim(font, capturePreviewSummaryLine(capturePointA, capturePointB, captureBlockCount),
                    w - 20), x + 10, textY, 0xFFB8FFB8, false);
        } else if (currentEntry != null) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.name_dialog_current", currentEntry.name()),
                    w - 20), x + 10, textY, 0xFF9EACB9, false);
        }

        NameContentLayout layout = contentLayout(x, y, w, h);
        g.drawString(font, text("screen.rtsbuilding.blueprints.name_dialog_label"), layout.inputX(),
                layout.inputY() - 11, 0xFFB7CDE2, false);
        drawFrame(g, layout.inputX(), layout.inputY(), layout.inputW(), 18,
                0xDD05070B, 0xFF8BA4B8, 0xFF0B0E13);
        String displayValue = value + ((Util.getMillis() / 500L) % 2L == 0L ? "_" : "");
        g.drawString(font, trim(font, displayValue, layout.inputW() - 8),
                layout.inputX() + 4, layout.inputY() + 5, 0xFFEAF2FF, false);

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
        int inputX = x + 10;
        int inputW = Math.max(80, w - 20);
        int cancelW = 58;
        int confirmW = 70;
        int buttonY = y + h - 24;
        int inputY = Math.max(y + 36, buttonY - 28);
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
