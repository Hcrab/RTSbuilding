package com.rtsbuilding.rtsbuilding.client.popup;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class RtsCraftQuantityDialog {
    private static final int PANEL_W = 238;
    private static final int PANEL_H = 186;
    private static final int OPTION_VISIBLE_ROWS = 4;
    private static final int OPTION_ROW_H = 16;
    private static final int INPUT_W = 42;
    private static final int INPUT_H = 14;
    private static final int STEP_W = 24;
    private static final int STEP_H = 14;
    private static final int ACTION_W = 52;
    private static final int ACTION_H = 16;
    private static final int MAX_CRAFT_COUNT = 999;

    private boolean open;
    private String itemLabel = "";
    private ItemStack preview = ItemStack.EMPTY;
    private final List<ClientRtsController.CraftRecipeOption> recipeOptions = new ArrayList<>();
    private int selectedRecipeIndex;
    private int recipeScroll;
    private String quantityText = "1";
    private boolean replaceOnNextDigit = true;
    private Request pendingRequest;

    public void open(
            String itemLabel,
            ItemStack preview,
            List<ClientRtsController.CraftRecipeOption> recipeOptions,
            int initialCount) {
        this.open = true;
        this.itemLabel = itemLabel == null ? "" : itemLabel;
        this.preview = preview == null ? ItemStack.EMPTY : preview.copy();
        this.recipeOptions.clear();
        if (recipeOptions != null) {
            this.recipeOptions.addAll(recipeOptions);
        }
        this.selectedRecipeIndex = findDefaultRecipeIndex();
        this.recipeScroll = 0;
        ensureSelectionVisible();
        this.pendingRequest = null;
        this.replaceOnNextDigit = true;
        setQuantity(initialCount);
    }

    public boolean isOpen() {
        return this.open;
    }

    public void close() {
        this.open = false;
        this.itemLabel = "";
        this.preview = ItemStack.EMPTY;
        this.recipeOptions.clear();
        this.selectedRecipeIndex = 0;
        this.recipeScroll = 0;
        this.quantityText = "1";
        this.replaceOnNextDigit = true;
    }

    public Request consumePendingRequest() {
        Request request = this.pendingRequest;
        this.pendingRequest = null;
        return request;
    }

    public void render(GuiGraphics g, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!this.open) {
            return;
        }
        Layout layout = resolveLayout(screenWidth, screenHeight);
        ClientRtsController.CraftRecipeOption selected = getSelectedOption();

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 680.0F);
        g.fill(0, 0, screenWidth, screenHeight, 0x78000000);
        drawPanelFrame(g, layout.panelX(), layout.panelY(), PANEL_W, PANEL_H, 0xEE171C24, 0xFF6C839A, 0xFF0D1117);

        g.drawString(font, "Craft Recipe", layout.panelX() + 8, layout.panelY() + 7, 0xF2F7FF);
        if (!this.preview.isEmpty()) {
            g.renderItem(this.preview, layout.panelX() + 8, layout.panelY() + 21);
        }
        String label = font.plainSubstrByWidth(this.itemLabel, PANEL_W - 42);
        g.drawString(font, label, layout.panelX() + 30, layout.panelY() + 22, 0xE4ECF6);
        int selectedCount = selected == null ? 1 : Math.max(1, selected.resultCount());
        g.drawString(font, "Each craft: x" + selectedCount, layout.panelX() + 30, layout.panelY() + 34, 0xAFC0D3);

        g.drawString(font, "Recipes", layout.panelX() + 8, layout.optionsY() - 10, 0xD8E3EE);
        drawPanelFrame(g, layout.optionsX(), layout.optionsY(), layout.optionsW(), layout.optionsH(), 0xAA202833, 0xFF61758A, 0xFF11161C);
        int visibleRows = Math.min(OPTION_VISIBLE_ROWS, Math.max(0, this.recipeOptions.size()));
        for (int row = 0; row < visibleRows; row++) {
            int optionIndex = this.recipeScroll + row;
            if (optionIndex >= this.recipeOptions.size()) {
                break;
            }
            ClientRtsController.CraftRecipeOption option = this.recipeOptions.get(optionIndex);
            int rowY = layout.optionsY() + 2 + row * OPTION_ROW_H;
            int fill = option.craftable() ? 0xAA223B2E : 0xAA402626;
            if (optionIndex == this.selectedRecipeIndex) {
                fill = option.craftable() ? 0xCC2E5B43 : 0xCC684040;
            }
            g.fill(layout.optionsX() + 2, rowY, layout.optionsX() + layout.optionsW() - 2, rowY + OPTION_ROW_H - 1, fill);
            String summary = "x" + Math.max(1, option.resultCount()) + " " + normalizeOptionSummary(option.summary());
            g.drawString(font, font.plainSubstrByWidth(summary, layout.optionsW() - 56), layout.optionsX() + 6, rowY + 4, 0xF2F7FF);
            g.drawString(font, option.craftable() ? "MAKE" : "MISS", layout.optionsX() + layout.optionsW() - 30, rowY + 4,
                    option.craftable() ? 0xC9F0C7 : 0xF0C4C4);
        }
        if (this.recipeOptions.size() > OPTION_VISIBLE_ROWS) {
            String pageText = (this.selectedRecipeIndex + 1) + "/" + this.recipeOptions.size();
            g.drawString(font, pageText, layout.optionsX() + layout.optionsW() - font.width(pageText) - 4, layout.optionsY() - 10, 0xAFC0D3);
        }

        String detail = selected == null
                ? "No recipe"
                : selected.craftable()
                        ? normalizeOptionSummary(selected.summary())
                        : normalizeOptionMissingSummary(selected.missingSummary());
        int detailColor = selected != null && !selected.craftable() ? 0xFFD6AAAA : 0xFFBCD0E2;
        g.drawString(font, font.plainSubstrByWidth(detail, PANEL_W - 16), layout.panelX() + 8, layout.detailY(), detailColor);

        drawSmallButton(g, font, layout.minusTenX(), layout.inputY(), STEP_W, STEP_H, "-10", 0xAA2A3340);
        drawSmallButton(g, font, layout.minusOneX(), layout.inputY(), STEP_W, STEP_H, "-1", 0xAA2A3340);
        drawPanelFrame(g, layout.inputX(), layout.inputY(), INPUT_W, INPUT_H, 0xFF202833, 0xFF61758A, 0xFF11161C);
        g.drawCenteredString(font, this.quantityText, layout.inputX() + (INPUT_W / 2), layout.inputY() + 3, 0xFFFFFF);
        drawSmallButton(g, font, layout.plusOneX(), layout.inputY(), STEP_W, STEP_H, "+1", 0xAA2A3340);
        drawSmallButton(g, font, layout.plusTenX(), layout.inputY(), STEP_W, STEP_H, "+10", 0xAA2A3340);

        g.drawString(font, "Click recipe, Enter confirm, Esc cancel", layout.panelX() + 8, layout.helpY(), 0xAFC0D3);
        drawSmallButton(g, font, layout.cancelX(), layout.actionY(), ACTION_W, ACTION_H, "Cancel", 0xAA473030);
        drawSmallButton(g, font, layout.confirmX(), layout.actionY(), ACTION_W, ACTION_H, "Craft", 0xAA345A38);
        g.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!this.open) {
            return false;
        }
        Layout layout = resolveLayout(screenWidth, screenHeight);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (!inside(mouseX, mouseY, layout.panelX(), layout.panelY(), PANEL_W, PANEL_H)) {
            close();
            return true;
        }
        int optionIndex = resolveClickedOption(mouseX, mouseY, layout);
        if (optionIndex >= 0) {
            this.selectedRecipeIndex = optionIndex;
            ensureSelectionVisible();
            return true;
        }
        if (inside(mouseX, mouseY, layout.minusTenX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(-10);
            return true;
        }
        if (inside(mouseX, mouseY, layout.minusOneX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(-1);
            return true;
        }
        if (inside(mouseX, mouseY, layout.plusOneX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(1);
            return true;
        }
        if (inside(mouseX, mouseY, layout.plusTenX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(10);
            return true;
        }
        if (inside(mouseX, mouseY, layout.cancelX(), layout.actionY(), ACTION_W, ACTION_H)) {
            close();
            return true;
        }
        if (inside(mouseX, mouseY, layout.confirmX(), layout.actionY(), ACTION_W, ACTION_H)) {
            confirm();
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(double scrollY) {
        if (!this.open || this.recipeOptions.size() <= 1 || scrollY == 0.0D) {
            return this.open;
        }
        moveRecipeSelection(scrollY > 0.0D ? -1 : 1);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.open) {
            return false;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            moveRecipeSelection((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            moveRecipeSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            moveRecipeSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            this.replaceOnNextDigit = true;
            setQuantity(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_RIGHT) {
            adjustQuantity(ctrl ? 10 : 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_LEFT) {
            adjustQuantity(ctrl ? -10 : -1);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                appendDigits(minecraft.keyboardHandler.getClipboard());
            }
            return true;
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.open) {
            return false;
        }
        if (Character.isDigit(codePoint)) {
            appendDigits(Character.toString(codePoint));
        }
        return true;
    }

    private void confirm() {
        ClientRtsController.CraftRecipeOption selected = getSelectedOption();
        int craftCount = getQuantity();
        if (selected == null || !selected.craftable() || selected.recipeId() == null || selected.recipeId().isBlank() || craftCount <= 0) {
            return;
        }
        this.pendingRequest = new Request(selected.recipeId(), craftCount);
        close();
    }

    private ClientRtsController.CraftRecipeOption getSelectedOption() {
        if (this.selectedRecipeIndex < 0 || this.selectedRecipeIndex >= this.recipeOptions.size()) {
            return this.recipeOptions.isEmpty() ? null : this.recipeOptions.get(0);
        }
        return this.recipeOptions.get(this.selectedRecipeIndex);
    }

    private int findDefaultRecipeIndex() {
        for (int i = 0; i < this.recipeOptions.size(); i++) {
            if (this.recipeOptions.get(i).craftable()) {
                return i;
            }
        }
        return this.recipeOptions.isEmpty() ? 0 : 0;
    }

    private void moveRecipeSelection(int delta) {
        if (this.recipeOptions.isEmpty()) {
            return;
        }
        this.selectedRecipeIndex = Mth.clamp(this.selectedRecipeIndex + delta, 0, this.recipeOptions.size() - 1);
        ensureSelectionVisible();
    }

    private void ensureSelectionVisible() {
        int maxScroll = Math.max(0, this.recipeOptions.size() - OPTION_VISIBLE_ROWS);
        if (this.selectedRecipeIndex < this.recipeScroll) {
            this.recipeScroll = this.selectedRecipeIndex;
        } else if (this.selectedRecipeIndex >= this.recipeScroll + OPTION_VISIBLE_ROWS) {
            this.recipeScroll = this.selectedRecipeIndex - OPTION_VISIBLE_ROWS + 1;
        }
        this.recipeScroll = Mth.clamp(this.recipeScroll, 0, maxScroll);
    }

    private int resolveClickedOption(double mouseX, double mouseY, Layout layout) {
        if (!inside(mouseX, mouseY, layout.optionsX(), layout.optionsY(), layout.optionsW(), layout.optionsH())) {
            return -1;
        }
        int localY = (int) (mouseY - layout.optionsY()) - 2;
        if (localY < 0) {
            return -1;
        }
        int row = localY / OPTION_ROW_H;
        if (row < 0 || row >= OPTION_VISIBLE_ROWS) {
            return -1;
        }
        int index = this.recipeScroll + row;
        return index < this.recipeOptions.size() ? index : -1;
    }

    private void adjustQuantity(int delta) {
        this.replaceOnNextDigit = false;
        setQuantity(getQuantity() + delta);
    }

    private void backspace() {
        this.replaceOnNextDigit = false;
        if (this.quantityText.length() <= 1) {
            this.quantityText = "1";
            return;
        }
        this.quantityText = this.quantityText.substring(0, this.quantityText.length() - 1);
        if (this.quantityText.isBlank()) {
            this.quantityText = "1";
            return;
        }
        setQuantity(parseQuantity(this.quantityText));
    }

    private void appendDigits(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        StringBuilder digits = new StringBuilder(this.replaceOnNextDigit ? "" : this.quantityText);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch) && digits.length() < 3) {
                digits.append(ch);
            }
        }
        if (digits.length() <= 0) {
            return;
        }
        String next = digits.toString().replaceFirst("^0+(?!$)", "");
        this.replaceOnNextDigit = false;
        setQuantity(parseQuantity(next));
    }

    private void setQuantity(int value) {
        this.quantityText = Integer.toString(Mth.clamp(value, 1, MAX_CRAFT_COUNT));
    }

    private int getQuantity() {
        return parseQuantity(this.quantityText);
    }

    private static int parseQuantity(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        try {
            return Mth.clamp(Integer.parseInt(text), 1, MAX_CRAFT_COUNT);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String normalizeOptionSummary(String summary) {
        return summary == null || summary.isBlank() ? "Recipe" : summary;
    }

    private static String normalizeOptionMissingSummary(String summary) {
        return summary == null || summary.isBlank() ? "Missing ingredients." : summary;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void drawSmallButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int fill) {
        drawPanelFrame(g, x, y, w, h, fill, 0xFF667D95, 0xFF111821);
        g.drawCenteredString(font, label, x + (w / 2), y + 4, 0xFFFFFF);
    }

    private static void drawPanelFrame(GuiGraphics g, int x, int y, int w, int h, int fillColor, int light, int dark) {
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, fillColor, light, dark);
    }

    private static Layout resolveLayout(int screenWidth, int screenHeight) {
        int panelX = (screenWidth - PANEL_W) / 2;
        int panelY = (screenHeight - PANEL_H) / 2;
        int optionsX = panelX + 8;
        int optionsY = panelY + 50;
        int optionsW = PANEL_W - 16;
        int optionsH = OPTION_VISIBLE_ROWS * OPTION_ROW_H + 4;
        int detailY = optionsY + optionsH + 8;
        int inputY = detailY + 14;
        int minusTenX = panelX + 8;
        int minusOneX = minusTenX + STEP_W + 4;
        int inputX = minusOneX + STEP_W + 6;
        int plusOneX = inputX + INPUT_W + 6;
        int plusTenX = plusOneX + STEP_W + 4;
        int helpY = inputY + 20;
        int actionY = panelY + PANEL_H - ACTION_H - 8;
        int cancelX = panelX + PANEL_W - (ACTION_W * 2) - 12;
        int confirmX = panelX + PANEL_W - ACTION_W - 8;
        return new Layout(
                panelX,
                panelY,
                optionsX,
                optionsY,
                optionsW,
                optionsH,
                detailY,
                inputY,
                minusTenX,
                minusOneX,
                inputX,
                plusOneX,
                plusTenX,
                helpY,
                actionY,
                cancelX,
                confirmX);
    }

    public record Request(String recipeId, int craftCount) {
    }

    private record Layout(
            int panelX,
            int panelY,
            int optionsX,
            int optionsY,
            int optionsW,
            int optionsH,
            int detailY,
            int inputY,
            int minusTenX,
            int minusOneX,
            int inputX,
            int plusOneX,
            int plusTenX,
            int helpY,
            int actionY,
            int cancelX,
            int confirmX) {
    }
}
