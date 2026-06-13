package com.rtsbuilding.rtsbuilding.blueprint.client;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

/**
 * Windowed control surface for blueprint capture and blueprint preview placement.
 *
 * <p>This class owns only layout and input routing. Blueprint file scanning,
 * naming, material checks, and server placement stay in {@link BlueprintPanel}.
 * The important product rule is that capture and placement controls must behave
 * like small tools, not like loose debug widgets: stable rows, centered numeric
 * fields, a dedicated status area, and no overlapping text at high RTS UI scale.</p>
 */
public final class BlueprintWindowPanel extends RtsWindowPanel {
    private static final int LEGACY_DEFAULT_W = 300;
    private static final int LEGACY_DEFAULT_H = 286;
    private static final int PLACEMENT_PANEL_W = 248;
    private static final int PLACEMENT_PANEL_H = 292;
    private static final int CAPTURE_PANEL_W = 324;
    private static final int CAPTURE_PANEL_H = 160;
    private static final int PLACEMENT_MIN_W = PLACEMENT_PANEL_W;
    private static final int PLACEMENT_MIN_H = PLACEMENT_PANEL_H;
    private static final int CAPTURE_MIN_W = CAPTURE_PANEL_W;
    private static final int CAPTURE_MIN_H = CAPTURE_PANEL_H;
    private static final int PAD = 12;
    private static final int GAP = 8;
    private static final int CONTROL_GAP = 4;
    private static final int SECTION_PAD = 8;
    private static final int BUTTON_H = 20;
    private static final int SMALL_BUTTON_W = 18;
    private static final int TEXTBOX_H = BUTTON_H;
    private static final int AXIS_LABEL_W = 10;
    private static final int AXIS_ROW_GAP = 6;
    private static final int CAPTURE_AXIS_INPUT_W = 36;
    private static final int POSITION_AXIS_INPUT_W = 64;
    private static final int DETAILS_BUTTON_W = 58;
    private static final int STATUS_H = 22;
    private static final int FOOTER_GAP = 8;

    private WindowTextBox sizeXInput;
    private WindowTextBox sizeYInput;
    private WindowTextBox sizeZInput;
    private WindowTextBox posXInput;
    private WindowTextBox posYInput;
    private WindowTextBox posZInput;

    private WindowButton saveCaptureButton;
    private WindowButton cancelButton;
    private WindowButton previousButton;
    private WindowButton nextButton;
    private WindowButton detailsButton;
    private WindowButton buildButton;
    private WindowButton clearButton;
    private WindowButton[] sizePlusButtons;
    private WindowButton[] sizeMinusButtons;
    private WindowButton[] posPlusButtons;
    private WindowButton[] posMinusButtons;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = false;
        this.draggable = true;
        createTextBoxes();
        createButtons();
    }

    public void syncWithBlueprintState() {
        if (!shouldRepresentBlueprintState()) {
            return;
        }
        boolean wasOpen = isOpen();
        if (!wasOpen) {
            setOpen(true);
        }
        fitWindowToBlueprintMode();
        if (!wasOpen) {
            markBroughtToFront();
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean legacyDefaultBounds = width <= LEGACY_DEFAULT_W && height <= LEGACY_DEFAULT_H;
        super.setBounds(x, y,
                legacyDefaultBounds ? preferredWindowWidth() : width,
                legacyDefaultBounds ? preferredWindowHeight() : height);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (BlueprintPanel.isCaptureModeActive()) {
            renderCaptureContent(g, mouseX, mouseY, partialTick);
        } else {
            renderPlacementContent(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderCaptureContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        syncCaptureInputs();
        int x = contentX() + PAD;
        int y = contentY() + 8;
        int w = contentWidth() - PAD * 2;
        int footerY = footerY();
        int statusY = footerY - STATUS_H - FOOTER_GAP;
        boolean complete = BlueprintPanel.isCaptureSelectionComplete();
        boolean saving = BlueprintPanel.isCaptureSaving();

        drawSectionTitle(g, Component.translatable("screen.rtsbuilding.blueprints.capture_tool_title"), x, y);
        drawLabel(g, Component.translatable("screen.rtsbuilding.blueprints.capture_window_hint"),
                x, y + 14, complete ? 0xFF8EEA9B : 0xFFFFC06C, w);
        drawLabel(g, Component.translatable("screen.rtsbuilding.blueprints.capture_window_scroll_hint"),
                x, y + 26, 0xFF9FB3C8, w);
        drawPointSummary(g, x, y + 42, w, complete);

        if (complete) {
            renderCaptureXYZControls(g, mouseX, mouseY, partialTick, x, y + 54, w, !saving);
        }

        Component status = saving
                ? Component.literal(BlueprintPanel.captureSaveProgressLine())
                : complete
                        ? Component.translatable("screen.rtsbuilding.blueprints.capture_blocks",
                                Long.toString(BlueprintPanel.countCaptureBlocks()))
                        : BlueprintPanel.statusText();
        int statusColor = saving || complete ? 0xFFB7CDE2 : BlueprintPanel.statusColor();
        renderStatusLine(g, x, statusY, w, status, statusColor);

        if (complete) {
            renderFooterButtons(g, mouseX, mouseY, partialTick, x, footerY, w,
                    new FooterButton(this.saveCaptureButton, true, true),
                    new FooterButton(this.cancelButton, !saving, false));
        } else {
            renderFooterButtons(g, mouseX, mouseY, partialTick, x, footerY, w,
                    new FooterButton(this.saveCaptureButton, false, true),
                    new FooterButton(this.cancelButton, !saving, false));
        }
    }

    private void renderPlacementContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        syncPlacementInputs();
        int x = contentX() + PAD;
        int y = contentY() + 8;
        int w = contentWidth() - PAD * 2;
        int actionY = placementActionY();
        int statusY = actionY - STATUS_H - FOOTER_GAP;
        boolean pinned = BlueprintPanel.hasPinnedPreview();

        int selectorH = 56;
        drawSectionFrame(g, x, y, w, selectorH);
        renderBlueprintSelector(g, mouseX, mouseY, partialTick, x + SECTION_PAD, y + 8, w - SECTION_PAD * 2);
        y += selectorH + GAP;

        int positionH = 106;
        drawSectionFrame(g, x, y, w, positionH);
        drawSectionTitle(g, Component.translatable("screen.rtsbuilding.blueprints.window_position"),
                x + SECTION_PAD, y + 6);
        renderAxisRows(g, mouseX, mouseY, partialTick,
                x + SECTION_PAD, y + 22, w - SECTION_PAD * 2,
                this.posXInput, this.posYInput, this.posZInput,
                this.posPlusButtons, this.posMinusButtons, pinned, false);

        Component status;
        int color;
        if (pinned) {
            status = Component.translatable("screen.rtsbuilding.blueprints.status.ready_to_build");
            color = 0xFF8EEA9B;
        } else {
            status = Component.translatable("screen.rtsbuilding.blueprints.placement_window_hint");
            color = 0xFFFFE66D;
        }
        renderStatusLine(g, x, statusY, w, status, color);
        renderStackedActionButtons(g, mouseX, mouseY, partialTick, x, actionY, w,
                new FooterButton(this.buildButton, pinned, true),
                new FooterButton(this.clearButton, true, false));
    }

    private void renderBlueprintSelector(GuiGraphics g, int mouseX, int mouseY, float partialTick, int x, int y, int w) {
        int buttonW = SMALL_BUTTON_W;
        int nameX = x + buttonW + GAP;
        int nameW = Math.max(56, w - buttonW * 2 - GAP * 2);
        nameW = Math.min(150, nameW);
        int nameGroupW = buttonW * 2 + CONTROL_GAP * 2 + nameW;
        int nameGroupX = x + Math.max(0, (w - nameGroupW) / 2);
        nameX = nameGroupX + buttonW + CONTROL_GAP;
        renderButtonAt(g, this.previousButton, nameGroupX, y, buttonW, true, mouseX, mouseY, partialTick);
        renderButtonAt(g, this.nextButton, nameX + nameW + CONTROL_GAP, y, buttonW, true, mouseX, mouseY, partialTick);
        String name = RtsClientUiUtil.trimToWidth(this.screen.font(), BlueprintPanel.selectedBlueprintName(), nameW);
        int nameDrawX = nameX + Math.max(0, (nameW - this.screen.font().width(name)) / 2);
        g.drawString(this.screen.font(), name, nameDrawX, y + 7, 0xFFEAF2FF, false);

        String rawSize = BlueprintPanel.selectedBlueprintSizeText();
        int sizeW = Math.min(74, Math.max(42, this.screen.font().width(rawSize) + 6));
        int detailGroupW = sizeW + CONTROL_GAP + DETAILS_BUTTON_W;
        int sizeBoxX = x + Math.max(0, (w - detailGroupW) / 2);
        int detailsX = sizeBoxX + sizeW + CONTROL_GAP;
        String size = RtsClientUiUtil.trimToWidth(this.screen.font(), rawSize, sizeW);
        int sizeX = sizeBoxX + Math.max(0, (sizeW - this.screen.font().width(size)) / 2);
        g.drawString(this.screen.font(), size, sizeX, y + 32, 0xFF9FB3C8, false);
        renderButtonAt(g, this.detailsButton, detailsX, y + 27, DETAILS_BUTTON_W, true, mouseX, mouseY, partialTick);
    }

    private void drawPointSummary(GuiGraphics g, int x, int y, int w, boolean complete) {
        String a = Component.translatable("screen.rtsbuilding.blueprints.capture_point_a",
                BlueprintPanel.capturePointAText()).getString();
        String b = Component.translatable("screen.rtsbuilding.blueprints.capture_point_b",
                BlueprintPanel.capturePointBText()).getString();
        int half = Math.max(60, (w - GAP) / 2);
        g.drawString(this.screen.font(), RtsClientUiUtil.trimToWidth(this.screen.font(), a, half),
                x, y, 0xFFB7CDE2, false);
        g.drawString(this.screen.font(), RtsClientUiUtil.trimToWidth(this.screen.font(), b, half),
                x + half + GAP, y, 0xFFB7CDE2, false);
    }

    private void renderCaptureXYZControls(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            int x, int y, int w, boolean enabled) {
        int groupW = Math.max(1, (w - GAP * 2) / 3);
        renderCompactAxisControl(g, mouseX, mouseY, partialTick, "X", this.sizeXInput,
                this.sizePlusButtons[0], this.sizeMinusButtons[0], x, y, groupW, enabled);
        renderCompactAxisControl(g, mouseX, mouseY, partialTick, "Y", this.sizeYInput,
                this.sizePlusButtons[1], this.sizeMinusButtons[1], x + groupW + GAP, y, groupW, enabled);
        renderCompactAxisControl(g, mouseX, mouseY, partialTick, "Z", this.sizeZInput,
                this.sizePlusButtons[2], this.sizeMinusButtons[2], x + (groupW + GAP) * 2, y, groupW, enabled);
    }

    private void renderCompactAxisControl(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            String label, WindowTextBox box, WindowButton plusButton, WindowButton minusButton,
            int x, int y, int w, boolean enabled) {
        int labelColor = enabled ? 0xFF9FB3C8 : 0xFF4F5B68;
        int labelY = y + Math.max(0, (TEXTBOX_H - this.screen.font().lineHeight) / 2);
        g.drawString(this.screen.font(), label, x, labelY, labelColor, false);
        int minusX = x + AXIS_LABEL_W + CONTROL_GAP;
        int inputW = Math.min(CAPTURE_AXIS_INPUT_W,
                Math.max(34, w - AXIS_LABEL_W - SMALL_BUTTON_W * 2 - CONTROL_GAP * 3));
        renderButtonAt(g, minusButton, minusX, y, SMALL_BUTTON_W, enabled, mouseX, mouseY, partialTick);
        int boxX = minusX + SMALL_BUTTON_W + CONTROL_GAP;
        box.setX(boxX);
        box.setY(y);
        box.setWidth(inputW);
        box.setEditable(enabled);
        box.setCenteredText(true);
        box.renderWidget(g, mouseX, mouseY, partialTick);
        if (!enabled) {
            g.fill(boxX, y, boxX + inputW, y + TEXTBOX_H, 0x55101620);
        }
        renderButtonAt(g, plusButton, boxX + inputW + CONTROL_GAP, y, SMALL_BUTTON_W, enabled, mouseX, mouseY, partialTick);
    }

    private void renderAxisRows(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            int x, int y, int w, WindowTextBox xBox, WindowTextBox yBox, WindowTextBox zBox,
            WindowButton[] plusButtons, WindowButton[] minusButtons, boolean enabled, boolean sizeInputs) {
        WindowTextBox[] boxes = {xBox, yBox, zBox};
        String[] labels = {"X", "Y", "Z"};
        int labelColor = enabled ? 0xFF9FB3C8 : 0xFF4F5B68;
        int labelYInset = Math.max(0, (TEXTBOX_H - this.screen.font().lineHeight) / 2);
        int targetInputW = sizeInputs ? CAPTURE_AXIS_INPUT_W : POSITION_AXIS_INPUT_W;
        int inputW = Math.max(34, Math.min(targetInputW,
                w - AXIS_LABEL_W - SMALL_BUTTON_W * 2 - CONTROL_GAP * 3));
        int rowW = AXIS_LABEL_W + CONTROL_GAP + SMALL_BUTTON_W + CONTROL_GAP
                + inputW + CONTROL_GAP + SMALL_BUTTON_W;
        int rowX = x + Math.max(0, (w - rowW) / 2);
        for (int i = 0; i < boxes.length; i++) {
            int rowY = y + i * (BUTTON_H + AXIS_ROW_GAP);
            g.drawString(this.screen.font(), labels[i], rowX, rowY + labelYInset, labelColor, false);
            int minusX = rowX + AXIS_LABEL_W + CONTROL_GAP;
            renderButtonAt(g, minusButtons[i], minusX, rowY, SMALL_BUTTON_W, enabled, mouseX, mouseY, partialTick);
            int boxX = minusX + SMALL_BUTTON_W + CONTROL_GAP;
            boxes[i].setX(boxX);
            boxes[i].setY(rowY);
            boxes[i].setWidth(inputW);
            boxes[i].setEditable(enabled);
            boxes[i].setCenteredText(true);
            boxes[i].renderWidget(g, mouseX, mouseY, partialTick);
            if (!enabled) {
                g.fill(boxX, rowY, boxX + inputW, rowY + TEXTBOX_H, 0x55101620);
            }
            renderButtonAt(g, plusButtons[i], boxX + inputW + CONTROL_GAP, rowY, SMALL_BUTTON_W,
                    enabled, mouseX, mouseY, partialTick);
        }
    }

    private void drawSectionFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x33111821);
        g.fill(x, y, x + w, y + 1, 0x55344555);
        g.fill(x, y + h - 1, x + w, y + h, 0x550D1117);
    }

    private void drawSectionTitle(GuiGraphics g, Component text, int x, int y) {
        g.drawString(this.screen.font(), text, x, y, 0xFFD8E3EE, false);
    }

    private void drawLabel(GuiGraphics g, Component text, int x, int y, int color, int maxWidth) {
        g.drawString(this.screen.font(), RtsClientUiUtil.trimToWidth(this.screen.font(), text.getString(), maxWidth),
                x, y, color, false);
    }

    private void renderStatusLine(GuiGraphics g, int x, int y, int w, Component status, int color) {
        if (status == null) {
            return;
        }
        g.fill(x, y, x + w, y + STATUS_H, 0x66111821);
        g.fill(x, y, x + w, y + 1, 0x44344555);
        String line = RtsClientUiUtil.trimToWidth(this.screen.font(), status.getString(), w - 12);
        int textX = x + Math.max(6, (w - this.screen.font().width(line)) / 2);
        int textY = y + Math.max(1, (STATUS_H - this.screen.font().lineHeight) / 2);
        g.drawString(this.screen.font(), line, textX, textY, color, false);
    }

    private void renderFooterButtons(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            int x, int y, int w, FooterButton... buttons) {
        renderButtonGrid(g, mouseX, mouseY, partialTick, x, y, w, 108, buttons);
    }

    private void renderStackedActionButtons(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            int x, int y, int w, FooterButton... buttons) {
        int buttonW = Math.min(180, Math.max(120, w));
        int bx = x + Math.max(0, (w - buttonW) / 2);
        for (int i = 0; i < buttons.length; i++) {
            int by = y + i * (BUTTON_H + CONTROL_GAP);
            if (buttons[i].primary()) {
                renderPrimaryButtonAt(g, buttons[i].button(), bx, by, buttonW,
                        buttons[i].enabled(), mouseX, mouseY, partialTick);
            } else {
                renderButtonAt(g, buttons[i].button(), bx, by, buttonW,
                        buttons[i].enabled(), mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderButtonGrid(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            int x, int y, int w, int preferredW, FooterButton... buttons) {
        int count = buttons.length;
        if (count <= 0) {
            return;
        }
        int columns = Math.min(count, Math.max(1, (w + GAP) / (preferredW + GAP)));
        int buttonW = Math.max(48, (w - GAP * (columns - 1)) / columns);
        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = i / columns;
            int bx = x + col * (buttonW + GAP);
            int by = y + row * (BUTTON_H + GAP);
            if (buttons[i].primary()) {
                renderPrimaryButtonAt(g, buttons[i].button(), bx, by, buttonW,
                        buttons[i].enabled(), mouseX, mouseY, partialTick);
            } else {
                renderButtonAt(g, buttons[i].button(), bx, by, buttonW,
                        buttons[i].enabled(), mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderButtonAt(GuiGraphics g, WindowButton button, int x, int y, int width, boolean active,
            int mouseX, int mouseY, float partialTick) {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.active = active;
        button.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPrimaryButtonAt(GuiGraphics g, WindowButton button, int x, int y, int width, boolean active,
            int mouseX, int mouseY, float partialTick) {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.active = active;
        if (!active) {
            button.render(g, mouseX, mouseY, partialTick);
            return;
        }
        g.fill(x, y, x + width, y + BUTTON_H, 0xCC244E35);
        drawButtonHighlight(g, x, y, width, BUTTON_H, 0xFF7FCEA0);
        String label = RtsClientUiUtil.trimToWidth(this.screen.font(), button.getMessage().getString(),
                Math.max(8, width - 10));
        int textX = x + (width - this.screen.font().width(label)) / 2;
        int textY = y + (BUTTON_H - this.screen.font().lineHeight) / 2;
        g.drawString(this.screen.font(), label, textX, textY, 0xFFEAF2FF, false);
    }

    private void drawButtonHighlight(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y, color);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        g.fill(x - 1, y - 1, x, y + h + 1, color);
        g.fill(x + w, y - 1, x + w + 1, y + h + 1, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (BlueprintPanel.isCaptureModeActive()
                && (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (BlueprintPanel.isCaptureModeActive()
                && (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
            return false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (BlueprintPanel.isCaptureModeActive()) {
            handleCaptureClick(mouseX, mouseY, button);
        } else {
            handlePlacementClick(mouseX, mouseY, button);
        }
    }

    private void handleCaptureClick(double mouseX, double mouseY, int button) {
        if (BlueprintPanel.isCaptureSelectionComplete()
                && (clickTextBox(this.sizeXInput, mouseX, mouseY, button)
                || clickTextBox(this.sizeYInput, mouseX, mouseY, button)
                || clickTextBox(this.sizeZInput, mouseX, mouseY, button))) {
            return;
        }
        clearFocus();
        clickButtons(mouseX, mouseY, button, this.saveCaptureButton, this.cancelButton);
        if (BlueprintPanel.isCaptureSelectionComplete()) {
            clickButtons(mouseX, mouseY, button,
                    this.sizePlusButtons[0], this.sizeMinusButtons[0],
                    this.sizePlusButtons[1], this.sizeMinusButtons[1],
                    this.sizePlusButtons[2], this.sizeMinusButtons[2]);
        }
    }

    private void handlePlacementClick(double mouseX, double mouseY, int button) {
        WindowTextBox focusedBefore = focusedTextBox();
        if (clickTextBox(this.posXInput, mouseX, mouseY, button)
                || clickTextBox(this.posYInput, mouseX, mouseY, button)
                || clickTextBox(this.posZInput, mouseX, mouseY, button)) {
            commitPositionIfFocusChanged(focusedBefore);
            return;
        }
        commitFocusedPositionBeforeBlur();
        clearFocus();
        clickButtons(mouseX, mouseY, button,
                this.previousButton, this.nextButton, this.detailsButton, this.buildButton, this.clearButton);
        clickButtons(mouseX, mouseY, button, this.posPlusButtons);
        clickButtons(mouseX, mouseY, button, this.posMinusButtons);
    }

    private boolean clickTextBox(WindowTextBox box, double mouseX, double mouseY, int button) {
        if (box == null) {
            return false;
        }
        boolean clicked = box.mouseClicked(mouseX, mouseY, button);
        if (clicked) {
            if (box != this.sizeXInput) this.sizeXInput.setFocused(false);
            if (box != this.sizeYInput) this.sizeYInput.setFocused(false);
            if (box != this.sizeZInput) this.sizeZInput.setFocused(false);
            if (box != this.posXInput) this.posXInput.setFocused(false);
            if (box != this.posYInput) this.posYInput.setFocused(false);
            if (box != this.posZInput) this.posZInput.setFocused(false);
        }
        return clicked;
    }

    private void clickButtons(double mouseX, double mouseY, int button, WindowButton... buttons) {
        for (WindowButton windowButton : buttons) {
            if (windowButton != null && windowButton.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        WindowTextBox focused = focusedTextBox();
        if (focused != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitFocusedTextBox(focused);
                focused.setFocused(false);
                return true;
            }
            return focused.keyPressed(keyCode, scanCode, modifiers);
        }
        if (BlueprintPanel.isCaptureModeActive()) {
            return handleCaptureKey(keyCode);
        }
        return handlePlacementKey(keyCode);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        WindowTextBox focused = focusedTextBox();
        return focused != null && focused.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (BlueprintPanel.isCaptureModeActive()) {
            if (!isOpen() || !isInsideWindow(mouseX, mouseY)) {
                return false;
            }
            if (!BlueprintPanel.isCaptureSelectionComplete()) {
                return true;
            }
            int step = scrollY > 0.0D ? 1 : -1;
            if (isMouseOver(this.sizeXInput, mouseX, mouseY)) {
                BlueprintPanel.adjustCaptureSize(step, 0, 0);
                syncCaptureSizeInputs(true);
                return true;
            }
            if (isMouseOver(this.sizeYInput, mouseX, mouseY)) {
                BlueprintPanel.adjustCaptureSize(0, step, 0);
                syncCaptureSizeInputs(true);
                return true;
            }
            if (isMouseOver(this.sizeZInput, mouseX, mouseY)) {
                BlueprintPanel.adjustCaptureSize(0, 0, step);
                syncCaptureSizeInputs(true);
                return true;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0.0D ? 1 : -1;
        if (BlueprintPanel.hasPinnedPreview()) {
            if (isMouseOver(this.posXInput, mouseX, mouseY)) {
                commitPinnedPositionDraft();
                BlueprintPanel.nudgePinnedAnchor(step, 0, 0, this.controller);
                syncPinnedPositionInputs(true);
                return true;
            }
            if (isMouseOver(this.posYInput, mouseX, mouseY)) {
                commitPinnedPositionDraft();
                BlueprintPanel.nudgePinnedAnchor(0, step, 0, this.controller);
                syncPinnedPositionInputs(true);
                return true;
            }
            if (isMouseOver(this.posZInput, mouseX, mouseY)) {
                commitPinnedPositionDraft();
                BlueprintPanel.nudgePinnedAnchor(0, 0, step, this.controller);
                syncPinnedPositionInputs(true);
                return true;
            }
        }
        return true;
    }

    private boolean handleCaptureKey(int keyCode) {
        int step = isAltDown() ? 4 : 1;
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            BlueprintPanel.moveCaptureSelection(0, step, 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            BlueprintPanel.moveCaptureSelection(0, -step, 0);
            return true;
        }
        return false;
    }

    private boolean handlePlacementKey(int keyCode) {
        if (!BlueprintPanel.hasPinnedPreview()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_KP_4) {
            return BlueprintPanel.nudgePinnedAnchorRelative(-1, 0, 0, this.controller);
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_KP_6) {
            return BlueprintPanel.nudgePinnedAnchorRelative(1, 0, 0, this.controller);
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_KP_8) {
            return BlueprintPanel.nudgePinnedAnchorRelative(0, 1, 0, this.controller);
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_KP_2) {
            return BlueprintPanel.nudgePinnedAnchorRelative(0, -1, 0, this.controller);
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            return BlueprintPanel.nudgePinnedAnchor(0, 1, 0, this.controller);
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            return BlueprintPanel.nudgePinnedAnchor(0, -1, 0, this.controller);
        }
        return false;
    }

    private void commitFocusedTextBox(WindowTextBox focused) {
        if (focused == this.sizeXInput || focused == this.sizeYInput || focused == this.sizeZInput) {
            commitCaptureSizeDraft();
        } else if (focused == this.posXInput || focused == this.posYInput || focused == this.posZInput) {
            commitPinnedPositionDraft();
        }
    }

    private void commitCaptureSizeDraft() {
        int x = parsePositive(this.sizeXInput.getValue(), BlueprintPanel.captureSizeX());
        int y = parseNonNegative(this.sizeYInput.getValue(), BlueprintPanel.captureSizeY());
        int z = parsePositive(this.sizeZInput.getValue(), BlueprintPanel.captureSizeZ());
        BlueprintPanel.setCaptureSize(x, y, z);
        syncCaptureSizeInputs(true);
    }

    private void commitPinnedPositionDraft() {
        BlockPos anchor = BlueprintPanel.getPinnedAnchor();
        if (anchor == null) {
            return;
        }
        int x = parseAnyInt(this.posXInput.getValue(), anchor.getX());
        int y = parseAnyInt(this.posYInput.getValue(), anchor.getY());
        int z = parseAnyInt(this.posZInput.getValue(), anchor.getZ());
        BlueprintPanel.setPinnedAnchor(new BlockPos(x, y, z), this.controller);
        syncPinnedPositionInputs(true);
    }

    private WindowTextBox focusedTextBox() {
        WindowTextBox[] boxes = {
                this.sizeXInput, this.sizeYInput, this.sizeZInput,
                this.posXInput, this.posYInput, this.posZInput
        };
        for (WindowTextBox box : boxes) {
            if (box != null && box.isFocused()) {
                return box;
            }
        }
        return null;
    }

    private void clearFocus() {
        this.sizeXInput.setFocused(false);
        this.sizeYInput.setFocused(false);
        this.sizeZInput.setFocused(false);
        this.posXInput.setFocused(false);
        this.posYInput.setFocused(false);
        this.posZInput.setFocused(false);
    }

    private void commitFocusedPositionBeforeBlur() {
        WindowTextBox focused = focusedTextBox();
        if (focused == this.posXInput || focused == this.posYInput || focused == this.posZInput) {
            commitPinnedPositionDraft();
        }
    }

    private void commitPositionIfFocusChanged(WindowTextBox focusedBefore) {
        if (focusedBefore != null && focusedBefore != focusedTextBox()
                && (focusedBefore == this.posXInput || focusedBefore == this.posYInput || focusedBefore == this.posZInput)) {
            commitPinnedPositionDraft();
        }
    }

    @Override
    protected Component getTitle() {
        return Component.translatable(BlueprintPanel.isCaptureModeActive()
                ? "screen.rtsbuilding.blueprints.window_title_capture"
                : "screen.rtsbuilding.blueprints.window_title_placement");
    }

    @Override
    protected int getDefaultWidth() {
        return preferredWindowWidth();
    }

    @Override
    protected int getDefaultHeight() {
        return preferredWindowHeight();
    }

    @Override
    protected int getMinWindowWidth() {
        return BlueprintPanel.isCaptureModeActive() ? CAPTURE_MIN_W : PLACEMENT_MIN_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return BlueprintPanel.isCaptureModeActive() ? CAPTURE_MIN_H : PLACEMENT_MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(4, this.screen.width - this.windowWidth - 8);
        this.windowY = TOP_H + 8;
    }

    private int preferredWindowWidth() {
        return BlueprintPanel.isCaptureModeActive() ? CAPTURE_PANEL_W : PLACEMENT_PANEL_W;
    }

    private int preferredWindowHeight() {
        return BlueprintPanel.isCaptureModeActive() ? CAPTURE_PANEL_H : PLACEMENT_PANEL_H;
    }

    private void fitWindowToBlueprintMode() {
        int targetW = preferredWindowWidth();
        int targetH = preferredWindowHeight();
        if (this.windowWidth == targetW && this.windowHeight == targetH) {
            return;
        }
        this.windowWidth = Math.max(getMinWindowWidth(), targetW);
        this.windowHeight = Math.max(getMinWindowHeight(), targetH);
        if (this.screen != null) {
            int maxX = Math.max(4, this.screen.width - this.windowWidth - 4);
            int maxY = Math.max(TOP_H + 4, this.screen.height - this.windowHeight - 4);
            this.windowX = Mth.clamp(this.windowX, 4, maxX);
            this.windowY = Mth.clamp(this.windowY, TOP_H + 4, maxY);
        }
    }

    @Override
    protected boolean canShowWindow() {
        return shouldRepresentBlueprintState();
    }

    @Override
    protected void onClose() {
        if (BlueprintPanel.isCaptureModeActive()) {
            BlueprintPanel.cancelCaptureMode();
        } else if (BlueprintPanel.hasSelectedBlueprint()) {
            BlueprintPanel.clearSelectedBlueprint();
        }
        clearFocus();
    }

    private boolean shouldRepresentBlueprintState() {
        return Config.areBlueprintsEnabled()
                && (BlueprintPanel.isCaptureModeActive() || BlueprintPanel.hasSelectedBlueprint());
    }

    private void createTextBoxes() {
        this.sizeXInput = createSizeInput();
        this.sizeYInput = createSizeInput();
        this.sizeZInput = createSizeInput();
        this.posXInput = createPositionInput();
        this.posYInput = createPositionInput();
        this.posZInput = createPositionInput();
    }

    private WindowTextBox createSizeInput() {
        WindowTextBox box = new WindowTextBox(this.screen.font(), 0, 0, CAPTURE_AXIS_INPUT_W, TEXTBOX_H);
        box.setMaxLength(4);
        box.setInputFilter(value -> value != null && value.matches("\\d*"));
        box.setCenteredText(true);
        return box;
    }

    private WindowTextBox createPositionInput() {
        WindowTextBox box = new WindowTextBox(this.screen.font(), 0, 0, POSITION_AXIS_INPUT_W, TEXTBOX_H);
        box.setMaxLength(8);
        box.setPlaceholder("-");
        box.setInputFilter(value -> value != null && value.matches("-?\\d*"));
        box.setCenteredText(true);
        return box;
    }

    private void createButtons() {
        this.saveCaptureButton = actionButton("screen.rtsbuilding.blueprints.save_area", 108,
                button -> BlueprintPanel.saveCapturedArea());
        this.cancelButton = actionButton("screen.rtsbuilding.blueprints.capture_cancel", 108,
                button -> {
                    if (BlueprintPanel.isCaptureModeActive()) {
                        BlueprintPanel.cancelCaptureMode();
                    } else {
                        BlueprintPanel.clearSelectedBlueprint();
                    }
                });
        this.previousButton = new WindowButton(0, 0, SMALL_BUTTON_W, BUTTON_H, Component.literal("<"),
                button -> BlueprintPanel.selectRelativeBlueprint(-1));
        this.nextButton = new WindowButton(0, 0, SMALL_BUTTON_W, BUTTON_H, Component.literal(">"),
                button -> BlueprintPanel.selectRelativeBlueprint(1));
        this.detailsButton = actionButton("screen.rtsbuilding.blueprints.details", DETAILS_BUTTON_W,
                button -> BlueprintPanel.openMaterialDialog());
        this.buildButton = actionButton("screen.rtsbuilding.blueprints.build_preview", 140,
                button -> BlueprintPanel.confirmPinnedPreview());
        this.clearButton = actionButton("screen.rtsbuilding.blueprints.capture_cancel", 140,
                button -> BlueprintPanel.clearSelectedBlueprint());
        this.sizePlusButtons = axisButtons(true, true);
        this.sizeMinusButtons = axisButtons(false, true);
        this.posPlusButtons = axisButtons(true, false);
        this.posMinusButtons = axisButtons(false, false);
    }

    private WindowButton actionButton(String key, int width, WindowButton.OnPress onPress) {
        return new WindowButton(0, 0, width, BUTTON_H, Component.translatable(key), onPress);
    }

    private WindowButton[] axisButtons(boolean plus, boolean sizeButtons) {
        WindowButton[] buttons = new WindowButton[3];
        for (int i = 0; i < buttons.length; i++) {
            int axis = i;
            buttons[i] = new WindowButton(0, 0, SMALL_BUTTON_W, BUTTON_H,
                    Component.literal(plus ? "+" : "-"),
                    button -> {
                        int delta = plus ? 1 : -1;
                        if (sizeButtons) {
                            if (axis == 0) BlueprintPanel.adjustCaptureSize(delta, 0, 0);
                            if (axis == 1) BlueprintPanel.adjustCaptureSize(0, delta, 0);
                            if (axis == 2) BlueprintPanel.adjustCaptureSize(0, 0, delta);
                            syncCaptureSizeInputs(true);
                        } else {
                            commitPinnedPositionDraft();
                            if (axis == 0) BlueprintPanel.nudgePinnedAnchor(delta, 0, 0, this.controller);
                            if (axis == 1) BlueprintPanel.nudgePinnedAnchor(0, delta, 0, this.controller);
                            if (axis == 2) BlueprintPanel.nudgePinnedAnchor(0, 0, delta, this.controller);
                            syncPinnedPositionInputs(true);
                        }
                    });
        }
        return buttons;
    }

    private void syncCaptureInputs() {
        syncCaptureSizeInputs(false);
    }

    private void syncCaptureSizeInputs(boolean force) {
        if (!BlueprintPanel.isCaptureSelectionComplete()) {
            if (force || !this.sizeXInput.isFocused()) this.sizeXInput.setValue("");
            if (force || !this.sizeYInput.isFocused()) this.sizeYInput.setValue("");
            if (force || !this.sizeZInput.isFocused()) this.sizeZInput.setValue("");
            return;
        }
        if (force || !this.sizeXInput.isFocused()) this.sizeXInput.setValue(Integer.toString(BlueprintPanel.captureSizeX()));
        if (force || !this.sizeYInput.isFocused()) this.sizeYInput.setValue(Integer.toString(BlueprintPanel.captureSizeY()));
        if (force || !this.sizeZInput.isFocused()) this.sizeZInput.setValue(Integer.toString(BlueprintPanel.captureSizeZ()));
    }

    private void syncPlacementInputs() {
        syncPinnedPositionInputs(false);
    }

    private void syncPinnedPositionInputs(boolean force) {
        BlockPos anchor = BlueprintPanel.getPinnedAnchor();
        if (anchor == null) {
            if (force || !this.posXInput.isFocused()) this.posXInput.setValue("");
            if (force || !this.posYInput.isFocused()) this.posYInput.setValue("");
            if (force || !this.posZInput.isFocused()) this.posZInput.setValue("");
            return;
        }
        if (force || !this.posXInput.isFocused()) this.posXInput.setValue(Integer.toString(anchor.getX()));
        if (force || !this.posYInput.isFocused()) this.posYInput.setValue(Integer.toString(anchor.getY()));
        if (force || !this.posZInput.isFocused()) this.posZInput.setValue(Integer.toString(anchor.getZ()));
    }

    private int footerY() {
        return contentY() + contentHeight() - BUTTON_H - 8;
    }

    private int placementActionY() {
        return contentY() + contentHeight() - BUTTON_H * 2 - CONTROL_GAP - 8;
    }

    private boolean isMouseOver(WindowTextBox box, double mouseX, double mouseY) {
        return box != null
                && mouseX >= box.getX()
                && mouseX <= box.getX() + box.getWidth()
                && mouseY >= box.getY()
                && mouseY <= box.getY() + box.getHeight();
    }

    private int parsePositive(String value, int fallback) {
        return Math.max(1, parseAnyInt(value, Math.max(1, fallback)));
    }

    private int parseNonNegative(String value, int fallback) {
        return Math.max(0, parseAnyInt(value, Math.max(0, fallback)));
    }

    private int parseAnyInt(String value, int fallback) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return fallback;
        }
        try {
            return Mth.clamp(Integer.parseInt(value), -30000000, 30000000);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean isAltDown() {
        long window = this.screen.getMinecraft().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private record FooterButton(WindowButton button, boolean enabled, boolean primary) {
    }
}
