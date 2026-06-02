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

    private BlueprintNameDialog() {
    }

    static void render(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY,
            boolean capture, String value, BlueprintEntry currentEntry, BlockPos capturePointA, BlockPos capturePointB) {
        BlueprintPanelLayout.NameDialogLayout layout = nameDialogLayout(screenW, screenH, capture);
        g.fill(0, 0, screenW, screenH, 0x66000000);
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), 0xEE121922, 0xFF6E8799, 0xFF0B0E13);
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1, layout.y() + 26, 0xD8293440);
        String title = capture
                ? text("screen.rtsbuilding.blueprints.name_dialog_capture_title")
                : text("screen.rtsbuilding.blueprints.name_dialog_rename_title");
        g.drawString(font, trim(font, title, layout.w() - 24), layout.x() + 10, layout.y() + 9, 0xFFEAF2FF, false);

        int textY = layout.y() + 34;
        if (capture) {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_title"), layout.w() - 20),
                    layout.x() + 10, textY, 0xFFCDEBFF, false);
            textY += 12;
            g.drawString(font, trim(font, capturePreviewSummaryLine(capturePointA, capturePointB), layout.w() - 20),
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

    static ClickResult click(double mouseX, double mouseY, int screenW, int screenH, boolean capture) {
        BlueprintPanelLayout.NameDialogLayout layout = nameDialogLayout(screenW, screenH, capture);
        if (inside(mouseX, mouseY, layout.confirmX(), layout.buttonY(), layout.confirmW(), BUTTON_H)) {
            return ClickResult.CONFIRM;
        }
        if (inside(mouseX, mouseY, layout.cancelX(), layout.buttonY(), layout.cancelW(), BUTTON_H)
                || !inside(mouseX, mouseY, layout.x(), layout.y(), layout.w(), layout.h())) {
            return ClickResult.CANCEL;
        }
        return ClickResult.NONE;
    }

    enum ClickResult {
        NONE,
        CONFIRM,
        CANCEL
    }
}
