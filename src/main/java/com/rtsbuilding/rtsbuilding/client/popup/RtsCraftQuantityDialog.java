package com.rtsbuilding.rtsbuilding.client.popup;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.record.CraftRecipeOption;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityDialogLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftQuantityStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityDialogLayout.*;

/**
 * 容器覆盖层使用的合成数量对话框。
 * 该类只拥有对话框输入状态和待提交请求，不执行网络发送或配方逻辑。
 */
public final class RtsCraftQuantityDialog {
    private static final int MAX_CRAFT_COUNT = 999;

    private boolean open;
    private String itemLabel = "";
    private ItemStack preview = ItemStack.EMPTY;
    private final List<CraftRecipeOption> recipeOptions = new ArrayList<CraftRecipeOption>();
    private int selectedRecipeIndex;
    private int recipeScroll;
    private String quantityText = "1";
    /** true 表示下一次数字输入替换当前整段数字，同时绘制成全选状态。 */
    private boolean replaceOnNextDigit = true;
    private Request pendingRequest;

    public void open(String itemLabel, ItemStack preview,
                     List<CraftRecipeOption> recipeOptions, int initialCount) {
        this.open = true;
        this.itemLabel = itemLabel == null ? "" : itemLabel;
        this.preview = preview == null ? ItemStack.EMPTY : preview.copy();
        this.recipeOptions.clear();
        if (recipeOptions != null) this.recipeOptions.addAll(recipeOptions);
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

    public void render(LegacyGuiGraphics g, FontRenderer font, int screenWidth,
                       int screenHeight, int mouseX, int mouseY) {
        if (!this.open || g == null || font == null) return;
        CraftQuantityDialogLayout.Layout layout = resolveLayout(screenWidth, screenHeight);
        CraftRecipeOption selected = getSelectedOption();

        g.pushPose();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.translate(0.0F, 0.0F, 680.0F);
        GlStateManager.disableDepth();
        g.fill(0, 0, screenWidth, screenHeight, CraftQuantityStyle.MODAL_SCRIM.toArgb());
        drawPanelFrame(g, layout.panelX(), layout.panelY(), PANEL_W, PANEL_H,
                CraftQuantityStyle.DIALOG_BACKGROUND,
                RtsMainlineTheme.WINDOW_BORDER_LIGHT,
                RtsMainlineTheme.WINDOW_BORDER_DARK);
        g.fill(layout.panelX() + 1, layout.panelY() + 1,
                layout.panelX() + PANEL_W - 1, layout.panelY() + TITLE_H,
                RtsMainlineTheme.WINDOW_TITLE.toArgb());

        g.drawString(font, "Craft Recipe", layout.panelX() + 8, layout.panelY() + 6,
                RtsMainlineTheme.WINDOW_TITLE_TEXT.toArgb());
        drawSmallButton(g, font, layout.closeX(), layout.closeY(), CLOSE_SIZE, CLOSE_SIZE,
                "x", CraftQuantityStyle.CLOSE_BACKGROUND);
        if (!this.preview.isEmpty()) {
            g.renderItem(this.preview, layout.panelX() + 8, layout.panelY() + 21);
            GlStateManager.disableDepth();
        }
        String label = font.trimStringToWidth(this.itemLabel, PANEL_W - 42);
        g.drawString(font, label, layout.panelX() + 30, layout.panelY() + 22,
                CraftQuantityStyle.ITEM_LABEL.toArgb());
        int selectedCount = selected == null ? 1 : Math.max(1, selected.resultCount());
        g.drawString(font, "Each craft: x" + selectedCount,
                layout.panelX() + 30, layout.panelY() + 34,
                CraftQuantityStyle.MUTED_TEXT.toArgb());

        g.drawString(font, "Recipes", layout.panelX() + 8, layout.optionsY() - 10,
                CraftQuantityStyle.SECTION_LABEL.toArgb());
        drawPanelFrame(g, layout.optionsX(), layout.optionsY(), layout.optionsW(), layout.optionsH(),
                CraftQuantityStyle.OPTIONS_BACKGROUND,
                CraftQuantityStyle.OPTIONS_BORDER_LIGHT,
                CraftQuantityStyle.OPTIONS_BORDER_DARK);
        int visibleRows = Math.min(OPTION_VISIBLE_ROWS, Math.max(0, this.recipeOptions.size()));
        for (int row = 0; row < visibleRows; row++) {
            int optionIndex = this.recipeScroll + row;
            if (optionIndex >= this.recipeOptions.size()) break;
            CraftRecipeOption option = this.recipeOptions.get(optionIndex);
            int rowY = layout.optionsY() + 2 + row * OPTION_ROW_H;
            UiColor fill = CraftQuantityStyle.rowBackground(option.craftable(),
                    optionIndex == this.selectedRecipeIndex);
            g.fill(layout.optionsX() + 2, rowY, layout.optionsX() + layout.optionsW() - 2,
                    rowY + OPTION_ROW_H - 1, fill.toArgb());
            String summary = "x" + Math.max(1, option.resultCount()) + " "
                    + normalizeOptionSummary(option.summary());
            g.drawString(font, font.trimStringToWidth(summary, layout.optionsW() - 56),
                    layout.optionsX() + 6, rowY + 4, CraftQuantityStyle.ROW_TEXT.toArgb());
            g.drawString(font, option.craftable() ? "MAKE" : "MISS",
                    layout.optionsX() + layout.optionsW() - 30, rowY + 4,
                    CraftQuantityStyle.badge(option.craftable()).toArgb());
        }
        if (this.recipeOptions.size() > OPTION_VISIBLE_ROWS) {
            String pageText = (this.selectedRecipeIndex + 1) + "/" + this.recipeOptions.size();
            g.drawString(font, pageText,
                    layout.optionsX() + layout.optionsW() - font.getStringWidth(pageText) - 4,
                    layout.optionsY() - 10, CraftQuantityStyle.MUTED_TEXT.toArgb());
        }

        String detail = selected == null ? "No recipe"
                : selected.craftable() ? normalizeOptionSummary(selected.summary())
                : normalizeOptionMissingSummary(selected.missingSummary());
        g.drawString(font, font.trimStringToWidth(detail, PANEL_W - 16),
                layout.panelX() + 8, layout.detailY(),
                CraftQuantityStyle.detail(selected != null && !selected.craftable()).toArgb());

        drawSmallButton(g, font, layout.minusTenX(), layout.inputY(), STEP_W, STEP_H,
                "-10", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawSmallButton(g, font, layout.minusOneX(), layout.inputY(), STEP_W, STEP_H,
                "-1", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawPanelFrame(g, layout.inputX(), layout.inputY(), INPUT_W, INPUT_H,
                RtsMainlineTheme.INPUT_BACKGROUND,
                RtsMainlineTheme.INPUT_BORDER_LIGHT,
                RtsMainlineTheme.INPUT_BORDER_DARK);
        int textX = layout.inputX() + (INPUT_W - font.getStringWidth(this.quantityText)) / 2;
        if (this.replaceOnNextDigit) {
            g.fill(textX - 1, layout.inputY() + 2,
                    textX + font.getStringWidth(this.quantityText) + 1,
                    layout.inputY() + 12, CraftQuantityStyle.INPUT_SELECTION.toArgb());
        }
        g.drawString(font, this.quantityText, textX, layout.inputY() + 3,
                RtsMainlineTheme.BUTTON_TEXT.toArgb());
        if (!this.replaceOnNextDigit && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
            int caretX = textX + font.getStringWidth(this.quantityText) + 1;
            g.fill(caretX, layout.inputY() + 3, caretX + 1, layout.inputY() + 12,
                    RtsMainlineTheme.BUTTON_TEXT.toArgb());
        }
        drawSmallButton(g, font, layout.plusOneX(), layout.inputY(), STEP_W, STEP_H,
                "+1", RtsMainlineTheme.BUTTON_BACKGROUND);
        drawSmallButton(g, font, layout.plusTenX(), layout.inputY(), STEP_W, STEP_H,
                "+10", RtsMainlineTheme.BUTTON_BACKGROUND);

        g.drawString(font, "Click recipe, Enter confirm, Esc cancel",
                layout.panelX() + 8, layout.helpY(), CraftQuantityStyle.MUTED_TEXT.toArgb());
        drawSmallButton(g, font, layout.cancelX(), layout.actionY(), ACTION_W, ACTION_H,
                "Cancel", RtsMainlineTheme.BUTTON_DESTRUCTIVE_BACKGROUND);
        drawSmallButton(g, font, layout.confirmX(), layout.actionY(), ACTION_W, ACTION_H,
                "Craft", RtsMainlineTheme.BUTTON_PRIMARY_BACKGROUND);

        if (!this.preview.isEmpty() && UiRect.contains(
                layout.panelX() + 8, layout.panelY() + 21, 16, 16, mouseX, mouseY)) {
            g.renderTooltip(this.preview, mouseX, mouseY);
        }
        GL11.glPopAttrib();
        g.popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int screenWidth, int screenHeight) {
        if (!this.open) return false;
        CraftQuantityDialogLayout.Layout layout = resolveLayout(screenWidth, screenHeight);
        if (button != 0) return true;
        if (UiRect.contains(layout.inputX(), layout.inputY(), INPUT_W, INPUT_H, mouseX, mouseY)) {
            this.replaceOnNextDigit = true;
            return true;
        }
        CraftQuantityDialogLayout.Hit hit = CraftQuantityDialogLayout.hitAt(
                layout, this.recipeScroll, this.recipeOptions.size(), mouseX, mouseY);
        switch (hit.control()) {
            case OUTSIDE_PANEL:
            case CLOSE:
            case CANCEL:
                close();
                break;
            case OPTION:
                this.selectedRecipeIndex = hit.optionIndex();
                ensureSelectionVisible();
                break;
            case MINUS_TEN: adjustQuantity(-10); break;
            case MINUS_ONE: adjustQuantity(-1); break;
            case PLUS_ONE: adjustQuantity(1); break;
            case PLUS_TEN: adjustQuantity(10); break;
            case CONFIRM: confirm(); break;
            case NONE: break;
        }
        return true;
    }

    public boolean mouseScrolled(double scrollY) {
        if (!this.open || this.recipeOptions.size() <= 1 || scrollY == 0.0D) return this.open;
        moveRecipeSelection(scrollY > 0.0D ? -1 : 1);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.open) return false;
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        if (keyCode == Keyboard.KEY_ESCAPE) { close(); return true; }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { confirm(); return true; }
        if (keyCode == Keyboard.KEY_TAB) { moveRecipeSelection(GuiScreen.isShiftKeyDown() ? -1 : 1); return true; }
        if (keyCode == Keyboard.KEY_PRIOR) { moveRecipeSelection(-1); return true; }
        if (keyCode == Keyboard.KEY_NEXT) { moveRecipeSelection(1); return true; }
        if (keyCode == Keyboard.KEY_BACK) { backspace(); return true; }
        if (keyCode == Keyboard.KEY_DELETE) { this.replaceOnNextDigit = true; setQuantity(1); return true; }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_RIGHT) { adjustQuantity(ctrl ? 10 : 1); return true; }
        if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_LEFT) { adjustQuantity(ctrl ? -10 : -1); return true; }
        if (ctrl && keyCode == Keyboard.KEY_V) { appendDigits(GuiScreen.getClipboardString()); return true; }
        char typed = Keyboard.getEventCharacter();
        if (Character.isDigit(typed)) appendDigits(Character.toString(typed));
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.open) return false;
        if (Character.isDigit(codePoint)) appendDigits(Character.toString(codePoint));
        return true;
    }

    private void confirm() {
        CraftRecipeOption selected = getSelectedOption();
        int craftCount = getQuantity();
        if (selected == null || !selected.craftable() || selected.recipeId() == null
                || selected.recipeId().trim().isEmpty() || craftCount <= 0) return;
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
            if (this.recipeOptions.get(i).craftable()) return i;
        }
        return 0;
    }

    private void moveRecipeSelection(int delta) {
        if (this.recipeOptions.isEmpty()) return;
        this.selectedRecipeIndex = MathHelper.clamp(
                this.selectedRecipeIndex + delta, 0, this.recipeOptions.size() - 1);
        ensureSelectionVisible();
    }

    private void ensureSelectionVisible() {
        int maxScroll = Math.max(0, this.recipeOptions.size() - OPTION_VISIBLE_ROWS);
        if (this.selectedRecipeIndex < this.recipeScroll) this.recipeScroll = this.selectedRecipeIndex;
        else if (this.selectedRecipeIndex >= this.recipeScroll + OPTION_VISIBLE_ROWS) {
            this.recipeScroll = this.selectedRecipeIndex - OPTION_VISIBLE_ROWS + 1;
        }
        this.recipeScroll = MathHelper.clamp(this.recipeScroll, 0, maxScroll);
    }

    private void adjustQuantity(int delta) {
        this.replaceOnNextDigit = false;
        setQuantity(getQuantity() + delta);
    }

    private void backspace() {
        if (this.replaceOnNextDigit || this.quantityText.length() <= 1) {
            this.quantityText = "1";
            this.replaceOnNextDigit = true;
            return;
        }
        this.quantityText = this.quantityText.substring(0, this.quantityText.length() - 1);
        this.replaceOnNextDigit = false;
        setQuantity(parseQuantity(this.quantityText));
    }

    private void appendDigits(String text) {
        if (text == null || text.trim().isEmpty()) return;
        StringBuilder digits = new StringBuilder(this.replaceOnNextDigit ? "" : this.quantityText);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch) && digits.length() < 3) digits.append(ch);
        }
        if (digits.length() == 0) return;
        String next = digits.toString().replaceFirst("^0+(?!$)", "");
        this.replaceOnNextDigit = false;
        setQuantity(parseQuantity(next));
    }

    private void setQuantity(int value) {
        this.quantityText = Integer.toString(MathHelper.clamp(value, 1, MAX_CRAFT_COUNT));
    }

    private int getQuantity() {
        return parseQuantity(this.quantityText);
    }

    private static int parseQuantity(String text) {
        if (text == null || text.trim().isEmpty()) return 1;
        try {
            return MathHelper.clamp(Integer.parseInt(text), 1, MAX_CRAFT_COUNT);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String normalizeOptionSummary(String summary) {
        return summary == null || summary.trim().isEmpty() ? "Recipe" : summary;
    }

    private static String normalizeOptionMissingSummary(String summary) {
        return summary == null || summary.trim().isEmpty() ? "Missing ingredients." : summary;
    }

    private static void drawSmallButton(LegacyGuiGraphics g, FontRenderer font,
                                        int x, int y, int w, int h,
                                        String label, UiColor fill) {
        drawPanelFrame(g, x, y, w, h, fill,
                RtsMainlineTheme.BUTTON_BORDER_LIGHT,
                RtsMainlineTheme.BUTTON_BORDER_DARK);
        g.drawCenteredString(font, label, x + w / 2,
                y + Math.max(2, (h - font.FONT_HEIGHT) / 2),
                RtsMainlineTheme.BUTTON_TEXT.toArgb());
    }

    private static void drawPanelFrame(LegacyGuiGraphics g, int x, int y, int w, int h,
                                       UiColor fill, UiColor light, UiColor dark) {
        UiChromeRenderer.frame(
                new MinecraftUiCanvas(g, net.minecraft.client.Minecraft.getMinecraft().fontRenderer),
                new UiRect(x, y, w, h), 1.0D, fill, light, dark);
    }

    private static CraftQuantityDialogLayout.Layout resolveLayout(int screenWidth, int screenHeight) {
        return CraftQuantityDialogLayout.resolve(screenWidth, screenHeight);
    }

    /** 不可变请求值；显式实现值语义，避免依赖新版本语言生成的字节码。 */
    public static final class Request {
        private final String recipeId;
        private final int craftCount;

        public Request(String recipeId, int craftCount) {
            this.recipeId = recipeId;
            this.craftCount = craftCount;
        }

        public String recipeId() { return recipeId; }
        public int craftCount() { return craftCount; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Request)) return false;
            Request request = (Request) other;
            return craftCount == request.craftCount && Objects.equals(recipeId, request.recipeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recipeId, craftCount);
        }

        @Override
        public String toString() {
            return "Request{recipeId='" + recipeId + "', craftCount=" + craftCount + '}';
        }
    }
}
