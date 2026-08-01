package com.rtsbuilding.rtsbuilding.client.popup;

import com.rtsbuilding.rtsbuilding.client.record.CraftRecipeOption;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityDialogLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftQuantityStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityDialogLayout.*;

/**
 * 原版容器 Overlay 使用的合成数量对话框。
 *
 * <p>它与 RTS 主屏浮窗入口并存：这里只拥有容器 Overlay 的键鼠生命周期和待发送请求，
 * 不删除或替代 {@code RtsCraftQuantityWindowPanel}。几何、主题和半开命中已下沉到 Core/Kit。</p>
 */
public final class RtsCraftQuantityDialog {
    private static final int MAX_CRAFT_COUNT = 999;

    private boolean open;
    private String itemLabel = "";
    private ItemStack preview = ItemStack.EMPTY;
    private final List<CraftRecipeOption> recipeOptions = new ArrayList<>();
    private int selectedRecipeIndex;
    private int recipeScroll;
    private String quantityText = "1";
    private boolean replaceOnNextDigit = true;
    private Request pendingRequest;

    public void open(
            String itemLabel,
            ItemStack preview,
            List<CraftRecipeOption> recipeOptions,
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
        CraftQuantityDialogLayout.Layout layout = resolveLayout(screenWidth, screenHeight);
        CraftRecipeOption selected = getSelectedOption();
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, font);

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 680.0F);
        g.fill(0, 0, screenWidth, screenHeight, CraftQuantityStyle.MODAL_SCRIM.toArgb());
        drawPanelFrame(canvas, layout.panelX(), layout.panelY(), PANEL_W, PANEL_H,
                CraftQuantityStyle.DIALOG_BACKGROUND,
                RtsMainlineTheme.WINDOW_BORDER_LIGHT,
                RtsMainlineTheme.WINDOW_BORDER_DARK);
        g.fill(layout.panelX() + 1, layout.panelY() + 1,
                layout.panelX() + PANEL_W - 1, layout.panelY() + TITLE_H,
                RtsMainlineTheme.WINDOW_TITLE.toArgb());

        g.drawString(font, "Craft Recipe", layout.panelX() + 8, layout.panelY() + 6,
                RtsMainlineTheme.WINDOW_TITLE_TEXT.toArgb(), false);
        drawSmallButton(g, canvas, font, layout.closeX(), layout.closeY(),
                CLOSE_SIZE, CLOSE_SIZE, "x", CraftQuantityStyle.CLOSE_BACKGROUND);
        if (!this.preview.isEmpty()) {
            g.renderItem(this.preview, layout.panelX() + 8, layout.panelY() + 21);
        }
        String label = font.plainSubstrByWidth(this.itemLabel, PANEL_W - 42);
        g.drawString(font, label, layout.panelX() + 30, layout.panelY() + 22,
                CraftQuantityStyle.ITEM_LABEL.toArgb(), false);
        int selectedCount = selected == null ? 1 : Math.max(1, selected.resultCount());
        g.drawString(font, "Each craft: x" + selectedCount,
                layout.panelX() + 30, layout.panelY() + 34,
                CraftQuantityStyle.MUTED_TEXT.toArgb(), false);

        g.drawString(font, "Recipes", layout.panelX() + 8, layout.optionsY() - 10,
                CraftQuantityStyle.SECTION_LABEL.toArgb(), false);
        drawPanelFrame(canvas, layout.optionsX(), layout.optionsY(),
                layout.optionsW(), layout.optionsH(),
                CraftQuantityStyle.OPTIONS_BACKGROUND,
                CraftQuantityStyle.OPTIONS_BORDER_LIGHT,
                CraftQuantityStyle.OPTIONS_BORDER_DARK);
        int visibleRows = Math.min(OPTION_VISIBLE_ROWS, Math.max(0, this.recipeOptions.size()));
        for (int row = 0; row < visibleRows; row++) {
            int optionIndex = this.recipeScroll + row;
            if (optionIndex >= this.recipeOptions.size()) {
                break;
            }
            CraftRecipeOption option = this.recipeOptions.get(optionIndex);
            int rowY = layout.optionsY() + 2 + row * OPTION_ROW_H;
            UiColor fill = CraftQuantityStyle.rowBackground(option.craftable(),
                    optionIndex == this.selectedRecipeIndex);
            g.fill(layout.optionsX() + 2, rowY,
                    layout.optionsX() + layout.optionsW() - 2,
                    rowY + OPTION_ROW_H - 1, fill.toArgb());
            String summary = "x" + Math.max(1, option.resultCount()) + " " + normalizeOptionSummary(option.summary());
            g.drawString(font, font.plainSubstrByWidth(summary, layout.optionsW() - 56),
                    layout.optionsX() + 6, rowY + 4,
                    CraftQuantityStyle.ROW_TEXT.toArgb(), false);
            g.drawString(font, option.craftable() ? "MAKE" : "MISS", layout.optionsX() + layout.optionsW() - 30, rowY + 4,
                    CraftQuantityStyle.badge(option.craftable()).toArgb(), false);
        }
        if (this.recipeOptions.size() > OPTION_VISIBLE_ROWS) {
            String pageText = (this.selectedRecipeIndex + 1) + "/" + this.recipeOptions.size();
            g.drawString(font, pageText, layout.optionsX() + layout.optionsW() - font.width(pageText) - 4,
                    layout.optionsY() - 10,
                    CraftQuantityStyle.MUTED_TEXT.toArgb(), false);
        }

        String detail = selected == null
                ? "No recipe"
                : selected.craftable()
                        ? normalizeOptionSummary(selected.summary())
                        : normalizeOptionMissingSummary(selected.missingSummary());
        g.drawString(font, font.plainSubstrByWidth(detail, PANEL_W - 16),
                layout.panelX() + 8, layout.detailY(),
                CraftQuantityStyle.detail(
                        selected != null && !selected.craftable()).toArgb(), false);

        drawSmallButton(g, canvas, font, layout.minusTenX(), layout.inputY(),
                STEP_W, STEP_H, "-10", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawSmallButton(g, canvas, font, layout.minusOneX(), layout.inputY(),
                STEP_W, STEP_H, "-1", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawPanelFrame(canvas, layout.inputX(), layout.inputY(), INPUT_W, INPUT_H,
                RtsMainlineTheme.INPUT_BACKGROUND,
                RtsMainlineTheme.INPUT_BORDER_LIGHT,
                RtsMainlineTheme.INPUT_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, this.quantityText,
                layout.inputX() + (INPUT_W / 2), layout.inputY() + 3,
                RtsMainlineTheme.BUTTON_TEXT.toArgb());
        drawSmallButton(g, canvas, font, layout.plusOneX(), layout.inputY(),
                STEP_W, STEP_H, "+1", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawSmallButton(g, canvas, font, layout.plusTenX(), layout.inputY(),
                STEP_W, STEP_H, "+10", RtsMainlineTheme.BUTTON_BACKGROUND);

        g.drawString(font, "Click recipe, Enter confirm, Esc cancel",
                layout.panelX() + 8, layout.helpY(),
                CraftQuantityStyle.MUTED_TEXT.toArgb(), false);
        drawSmallButton(g, canvas, font, layout.cancelX(), layout.actionY(),
                ACTION_W, ACTION_H, "Cancel",
                RtsMainlineTheme.BUTTON_DESTRUCTIVE_BACKGROUND);
        drawSmallButton(g, canvas, font, layout.confirmX(), layout.actionY(),
                ACTION_W, ACTION_H, "Craft",
                RtsMainlineTheme.BUTTON_PRIMARY_BACKGROUND);
        g.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!this.open) {
            return false;
        }
        CraftQuantityDialogLayout.Layout layout = resolveLayout(screenWidth, screenHeight);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        CraftQuantityDialogLayout.Hit hit = CraftQuantityDialogLayout.hitAt(
                layout, this.recipeScroll, this.recipeOptions.size(), mouseX, mouseY);
        switch (hit.control()) {
            case OUTSIDE_PANEL, CLOSE, CANCEL -> close();
            case OPTION -> {
                this.selectedRecipeIndex = hit.optionIndex();
                ensureSelectionVisible();
            }
            case MINUS_TEN -> adjustQuantity(-10);
            case MINUS_ONE -> adjustQuantity(-1);
            case PLUS_ONE -> adjustQuantity(1);
            case PLUS_TEN -> adjustQuantity(10);
            case CONFIRM -> confirm();
            case NONE -> {
            }
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
        CraftRecipeOption selected = getSelectedOption();
        int craftCount = getQuantity();
        if (selected == null || !selected.craftable() || selected.recipeId() == null || selected.recipeId().isBlank() || craftCount <= 0) {
            return;
        }
        this.pendingRequest = new Request(selected.recipeId(), craftCount);
        close();
    }

    private CraftRecipeOption getSelectedOption() {
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

    private static void drawSmallButton(GuiGraphics g, MinecraftUiCanvas canvas,
                                        Font font, int x, int y, int w, int h,
                                        String label, UiColor fill) {
        drawPanelFrame(canvas, x, y, w, h, fill,
                RtsMainlineTheme.BUTTON_BORDER_LIGHT,
                RtsMainlineTheme.BUTTON_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, label, x + (w / 2),
                y + Math.max(2, (h - font.lineHeight) / 2),
                RtsMainlineTheme.BUTTON_TEXT.toArgb());
    }

    private static void drawPanelFrame(MinecraftUiCanvas canvas, int x, int y,
                                       int w, int h, UiColor fillColor,
                                       UiColor light, UiColor dark) {
        UiChromeRenderer.frame(canvas, new UiRect(x, y, w, h), 1.0D,
                fillColor, light, dark);
    }

    private static CraftQuantityDialogLayout.Layout resolveLayout(
            int screenWidth, int screenHeight) {
        return CraftQuantityDialogLayout.resolve(screenWidth, screenHeight);
    }

    public record Request(String recipeId, int craftCount) {
    }

}
