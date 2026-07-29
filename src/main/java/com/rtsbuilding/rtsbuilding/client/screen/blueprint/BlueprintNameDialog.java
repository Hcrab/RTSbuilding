package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintDialogStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.text;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.trim;

/**
 * 绘制正式命名/重命名窗口的内容区，并对确认、取消按钮做命中分类。
 *
 * <p>窗口框架、标题栏和关闭行为由 {@link BlueprintNameWindowPanel} 管理；
 * 本类不再保留旧的全屏模态包装层。</p>
 */
final class BlueprintNameDialog {
    private BlueprintNameDialog() {
    }

    /** 直接消费 Core 快照，避免生产窗与离屏窗各自拼装一套字段。 */
    static void renderCoreContent(GuiGraphics g, Font font, int x, int y, int w, int h,
            int mouseX, int mouseY, BlueprintUiState state) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, font);
        int textY = y + BlueprintWindowLayout.NAME_SUMMARY_TOP;
        if (state.captureNameMode) {
            g.drawString(font, trim(font,
                            text("screen.rtsbuilding.blueprints.capture_preview_title"),
                            w - BlueprintWindowLayout.NAME_SUMMARY_TEXT_INSET * 2),
                    x + BlueprintWindowLayout.NAME_SUMMARY_TEXT_INSET,
                    textY, BlueprintDialogStyle.CAPTURE_TEXT.toArgb(), false);
            textY += BlueprintWindowLayout.NAME_SUMMARY_LINE_STEP;
            String size = state.captureSize.x + "x" + state.captureSize.y + "x" + state.captureSize.z;
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.capture_preview_summary",
                            size, state.captureBlockCount),
                            w - BlueprintWindowLayout.NAME_SUMMARY_TEXT_INSET * 2),
                    x + BlueprintWindowLayout.NAME_SUMMARY_TEXT_INSET,
                    textY, BlueprintDialogStyle.READY.toArgb(), false);
        } else {
            g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.name_dialog_current",
                            state.blueprintName),
                            w - BlueprintWindowLayout.NAME_SUMMARY_TEXT_INSET * 2),
                    x + BlueprintWindowLayout.NAME_SUMMARY_TEXT_INSET,
                    textY, BlueprintDialogStyle.CURRENT_NAME_TEXT.toArgb(), false);
        }

        BlueprintWindowLayout.NameDialogGeometry layout = BlueprintWindowLayout.nameDialog(x, y, w, h);
        g.drawString(font, text("screen.rtsbuilding.blueprints.name_dialog_label"), layout.inputX,
                layout.inputY - BlueprintWindowLayout.NAME_INPUT_LABEL_GAP,
                BlueprintDialogStyle.LABEL_TEXT.toArgb(), false);
        UiChromeRenderer.frame(canvas, new UiRect(
                        layout.inputX, layout.inputY, layout.inputW,
                        BlueprintWindowLayout.NAME_INPUT_H), 1.0D,
                BlueprintDialogStyle.INPUT_BACKGROUND, BlueprintDialogStyle.INPUT_BORDER,
                BlueprintDialogStyle.DARK_BORDER);
        g.drawString(font, trim(font, state.nameDraft + "_",
                        layout.inputW - BlueprintWindowLayout.NAME_INPUT_TEXT_INSET * 2),
                layout.inputX + BlueprintWindowLayout.NAME_INPUT_TEXT_INSET,
                layout.inputY + BlueprintWindowLayout.NAME_INPUT_TEXT_TOP,
                BlueprintDialogStyle.PRIMARY_TEXT.toArgb(), false);
        drawCoreButton(g, font, canvas, layout.confirmX, layout.buttonY,
                BlueprintWindowLayout.NAME_CONFIRM_W, BlueprintWindowLayout.NAME_BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_confirm"),
                UiRect.contains(layout.confirmX, layout.buttonY,
                        BlueprintWindowLayout.NAME_CONFIRM_W,
                        BlueprintWindowLayout.NAME_BUTTON_H, mouseX, mouseY));
        drawCoreButton(g, font, canvas, layout.cancelX, layout.buttonY,
                BlueprintWindowLayout.NAME_CANCEL_W, BlueprintWindowLayout.NAME_BUTTON_H,
                text("screen.rtsbuilding.blueprints.name_dialog_cancel"),
                UiRect.contains(layout.cancelX, layout.buttonY,
                        BlueprintWindowLayout.NAME_CANCEL_W,
                        BlueprintWindowLayout.NAME_BUTTON_H, mouseX, mouseY));
    }

    static ClickResult clickContent(double mouseX, double mouseY, int x, int y, int w, int h) {
        BlueprintWindowLayout.NameDialogGeometry layout = BlueprintWindowLayout.nameDialog(x, y, w, h);
        if (UiRect.contains(layout.confirmX, layout.buttonY,
                BlueprintWindowLayout.NAME_CONFIRM_W, BlueprintWindowLayout.NAME_BUTTON_H,
                mouseX, mouseY)) {
            return ClickResult.CONFIRM;
        }
        if (UiRect.contains(layout.cancelX, layout.buttonY,
                BlueprintWindowLayout.NAME_CANCEL_W, BlueprintWindowLayout.NAME_BUTTON_H,
                mouseX, mouseY)) {
            return ClickResult.CANCEL;
        }
        return ClickResult.NONE;
    }

    enum ClickResult {
        NONE,
        CONFIRM,
        CANCEL
    }

    /** 当前窗口化命名流程使用共享九宫格。 */
    private static void drawCoreButton(GuiGraphics g, Font font, MinecraftUiCanvas canvas,
            int x, int y, int w, int h, String label, boolean hovered) {
        UiChromeRenderer.frame(canvas, new UiRect(x, y, w, h), 1.0D,
                hovered ? BlueprintDialogStyle.BUTTON_HOVER_BACKGROUND
                        : BlueprintDialogStyle.BUTTON_BACKGROUND,
                BlueprintDialogStyle.BUTTON_BORDER, BlueprintDialogStyle.BUTTON_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                trim(font, label, w - BlueprintWindowLayout.NAME_BUTTON_TEXT_INSET),
                x + w / 2, y + BlueprintWindowLayout.NAME_BUTTON_TEXT_TOP,
                BlueprintDialogStyle.PRIMARY_TEXT.toArgb());
    }
}
