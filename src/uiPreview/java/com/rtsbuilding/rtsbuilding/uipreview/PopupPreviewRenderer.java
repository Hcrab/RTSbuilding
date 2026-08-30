package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.FixedUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiFloatAnimation;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftFeedbackLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityDialogLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftFeedbackStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftQuantityStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.OverlayStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/** 对 screen 外两种活动 Popup 做确定性离屏回放。 */
final class PopupPreviewRenderer {
    void render(BufferedImageUiCanvas canvas, UiPreviewLayout layout,
                UiPreviewScenario scenario) {
        if (scenario.variant() == UiPreviewScenario.Variant.CONTAINER_CRAFT_DIALOG) {
            drawCraftDialog(canvas, layout);
        } else if (scenario.variant()
                == UiPreviewScenario.Variant.CRAFT_FEEDBACK_POPUP) {
            drawCraftFeedback(canvas, layout);
        } else if (scenario.variant()
                == UiPreviewScenario.Variant.ANIMATION_CARET_DAMAGE) {
            drawHalfDamageFlash(canvas, layout);
        }
    }

    private static void drawHalfDamageFlash(BufferedImageUiCanvas canvas,
                                            UiPreviewLayout layout) {
        FixedUiClock clock = new FixedUiClock(0L);
        UiFloatAnimation animation = new UiFloatAnimation(clock, 0.0D);
        animation.snapTo(1.0D);
        animation.animateTo(0.0D, 300L, UiEasing.LINEAR);
        clock.advanceMillis(150L);
        canvas.fill(layout.screen(), OverlayStyle.damageFlash(animation.value()));
    }

    private static void drawCraftDialog(BufferedImageUiCanvas canvas,
                                        UiPreviewLayout previewLayout) {
        CraftQuantityDialogLayout.Layout layout = CraftQuantityDialogLayout.resolve(
                (int) previewLayout.screen().getWidth(),
                (int) previewLayout.screen().getHeight());
        canvas.fill(previewLayout.screen(), CraftQuantityStyle.MODAL_SCRIM);
        UiRect panel = new UiRect(layout.panelX, layout.panelY,
                CraftQuantityDialogLayout.PANEL_W,
                CraftQuantityDialogLayout.PANEL_H);
        UiChromeRenderer.frame(canvas, panel, 1.0D,
                CraftQuantityStyle.DIALOG_BACKGROUND,
                RtsMainlineTheme.WINDOW_BORDER_LIGHT,
                RtsMainlineTheme.WINDOW_BORDER_DARK);
        canvas.fill(layout.panelX + 1, layout.panelY + 1,
                CraftQuantityDialogLayout.PANEL_W - 2,
                CraftQuantityDialogLayout.TITLE_H - 1,
                RtsMainlineTheme.WINDOW_TITLE);
        canvas.text("Craft Recipe", layout.panelX + 8, layout.panelY + 6,
                RtsMainlineTheme.WINDOW_TITLE_TEXT);
        drawButton(canvas, layout.closeX, layout.closeY,
                CraftQuantityDialogLayout.CLOSE_SIZE,
                CraftQuantityDialogLayout.CLOSE_SIZE,
                "x", CraftQuantityStyle.CLOSE_BACKGROUND);

        canvas.fill(layout.panelX + 8, layout.panelY + 21, 16, 16,
                RtsMainlineTheme.INPUT_BACKGROUND);
        canvas.text("64x", layout.panelX + 10, layout.panelY + 23,
                CraftQuantityStyle.ITEM_LABEL);
        canvas.text("Polished Andesite", layout.panelX + 30, layout.panelY + 22,
                CraftQuantityStyle.ITEM_LABEL);
        canvas.text("Each craft: x4", layout.panelX + 30, layout.panelY + 34,
                CraftQuantityStyle.MUTED_TEXT);
        canvas.text("Recipes", layout.panelX + 8, layout.optionsY - 10,
                CraftQuantityStyle.SECTION_LABEL);
        UiChromeRenderer.frame(canvas,
                new UiRect(layout.optionsX, layout.optionsY,
                        layout.optionsW, layout.optionsH),
                1.0D, CraftQuantityStyle.OPTIONS_BACKGROUND,
                CraftQuantityStyle.OPTIONS_BORDER_LIGHT,
                CraftQuantityStyle.OPTIONS_BORDER_DARK);
        for (int row = 0; row < CraftQuantityDialogLayout.OPTION_VISIBLE_ROWS; row++) {
            boolean craftable = row != 2;
            canvas.fill(layout.optionsX + 2,
                    layout.optionsY + 2
                            + row * CraftQuantityDialogLayout.OPTION_ROW_H,
                    layout.optionsW - 4,
                    CraftQuantityDialogLayout.OPTION_ROW_H - 1,
                    CraftQuantityStyle.rowBackground(craftable, row == 0));
            canvas.text("x4 recipe " + (row + 1), layout.optionsX + 6,
                    layout.optionsY + 6
                            + row * CraftQuantityDialogLayout.OPTION_ROW_H,
                    CraftQuantityStyle.ROW_TEXT);
            canvas.text(craftable ? "MAKE" : "MISS",
                    layout.optionsX + layout.optionsW - 30,
                    layout.optionsY + 6
                            + row * CraftQuantityDialogLayout.OPTION_ROW_H,
                    CraftQuantityStyle.badge(craftable));
        }
        canvas.text("Uses 2 andesite + 2 quartz", layout.panelX + 8,
                layout.detailY, CraftQuantityStyle.DETAIL);
        drawButton(canvas, layout.minusTenX, layout.inputY,
                CraftQuantityDialogLayout.STEP_W,
                CraftQuantityDialogLayout.STEP_H,
                "-10", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawButton(canvas, layout.minusOneX, layout.inputY,
                CraftQuantityDialogLayout.STEP_W,
                CraftQuantityDialogLayout.STEP_H,
                "-1", RtsMainlineTheme.BUTTON_BACKGROUND);
        UiChromeRenderer.frame(canvas,
                new UiRect(layout.inputX, layout.inputY,
                        CraftQuantityDialogLayout.INPUT_W,
                        CraftQuantityDialogLayout.INPUT_H),
                1.0D, RtsMainlineTheme.INPUT_BACKGROUND,
                RtsMainlineTheme.INPUT_BORDER_LIGHT,
                RtsMainlineTheme.INPUT_BORDER_DARK);
        centered(canvas, "16", layout.inputX,
                CraftQuantityDialogLayout.INPUT_W, layout.inputY + 3,
                RtsMainlineTheme.BUTTON_TEXT);
        drawButton(canvas, layout.plusOneX, layout.inputY,
                CraftQuantityDialogLayout.STEP_W,
                CraftQuantityDialogLayout.STEP_H,
                "+1", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawButton(canvas, layout.plusTenX, layout.inputY,
                CraftQuantityDialogLayout.STEP_W,
                CraftQuantityDialogLayout.STEP_H,
                "+10", RtsMainlineTheme.BUTTON_BACKGROUND);
        canvas.text("Click recipe, Enter confirm, Esc cancel",
                layout.panelX + 8, layout.helpY, CraftQuantityStyle.MUTED_TEXT);
        drawButton(canvas, layout.cancelX, layout.actionY,
                CraftQuantityDialogLayout.ACTION_W,
                CraftQuantityDialogLayout.ACTION_H,
                "Cancel", RtsMainlineTheme.BUTTON_DESTRUCTIVE_BACKGROUND);
        drawButton(canvas, layout.confirmX, layout.actionY,
                CraftQuantityDialogLayout.ACTION_W,
                CraftQuantityDialogLayout.ACTION_H,
                "Craft", RtsMainlineTheme.BUTTON_PRIMARY_BACKGROUND);
    }

    private static void drawCraftFeedback(BufferedImageUiCanvas canvas,
                                          UiPreviewLayout layout) {
        int ingredientCount = 6;
        int rows = CraftFeedbackLayout.visibleRows(ingredientCount);
        int x = CraftFeedbackLayout.panelX((int) layout.screen().getWidth());
        int y = CraftFeedbackLayout.panelY(
                RtsMainlineLayout.TOP_H + 6);
        int alpha = CraftFeedbackStyle.alpha(0.62D);
        UiColor text = CraftFeedbackStyle.faded(CraftFeedbackStyle.TEXT, alpha);
        UiColor secondary = CraftFeedbackStyle.faded(
                CraftFeedbackStyle.SECONDARY_TEXT, alpha);
        UiChromeRenderer.frame(canvas,
                new UiRect(x, y, CraftFeedbackLayout.PANEL_W,
                        CraftFeedbackLayout.panelHeight(ingredientCount)),
                1.0D,
                CraftFeedbackStyle.faded(CraftFeedbackStyle.PANEL, alpha),
                CraftFeedbackStyle.faded(CraftFeedbackStyle.BORDER_LIGHT, alpha),
                CraftFeedbackStyle.faded(CraftFeedbackStyle.BORDER_DARK, alpha));
        canvas.fill(x + 8, y + 8, 16, 16,
                CraftFeedbackStyle.faded(RtsMainlineTheme.INPUT_BACKGROUND, alpha));
        canvas.text("Crafted x16", x + 30, y + 9, text);
        canvas.text("抛光安山岩", x + 30, y + 21, secondary);
        canvas.text("Consumed", x + 8, y + 40, secondary);
        int rowY = y + CraftFeedbackLayout.BASE_H;
        for (int row = 0; row < rows; row++) {
            canvas.fill(x + 8, rowY - 2,
                    CraftFeedbackLayout.PANEL_W - 16, 16,
                    CraftFeedbackStyle.faded(CraftFeedbackStyle.ROW, alpha));
            canvas.fill(x + 10, rowY - 1, 16, 16,
                    CraftFeedbackStyle.faded(RtsMainlineTheme.INPUT_BACKGROUND, alpha));
            canvas.text("ingredient " + (row + 1), x + 30, rowY + 1, text);
            canvas.text("x" + (row + 2),
                    x + CraftFeedbackLayout.PANEL_W - 30, rowY + 1, secondary);
            rowY += CraftFeedbackLayout.ROW_H;
        }
        canvas.text("+2 more", x + 10, rowY + 1, secondary);
    }

    private static void drawButton(BufferedImageUiCanvas canvas, int x, int y,
                                   int width, int height, String label,
                                   UiColor background) {
        UiChromeRenderer.frame(canvas, new UiRect(x, y, width, height), 1.0D,
                background, RtsMainlineTheme.BUTTON_BORDER_LIGHT,
                RtsMainlineTheme.BUTTON_BORDER_DARK);
        centered(canvas, label, x, width, y + 2, RtsMainlineTheme.BUTTON_TEXT);
    }

    private static void centered(BufferedImageUiCanvas canvas, String text,
                                 int x, int width, int y, UiColor color) {
        canvas.text(text, x + (width - canvas.textWidth(text)) / 2.0D, y, color);
    }
}
