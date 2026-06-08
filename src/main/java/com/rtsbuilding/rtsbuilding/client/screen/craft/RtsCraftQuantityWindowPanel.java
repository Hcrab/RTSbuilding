package com.rtsbuilding.rtsbuilding.client.screen.craft;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Window-layer version of the RTS craft quantity picker.
 *
 * <p>The panel owns only the recipe/count UI state and confirmed request. The
 * actual craft execution remains in {@link ClientRtsController}, so migrating
 * this popup into the RTS window layer does not change server-side crafting
 * semantics or linked-storage validation.
 */
public final class RtsCraftQuantityWindowPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = 238;
    private static final int DEFAULT_H = 196;
    private static final int MIN_W = 220;
    private static final int MIN_H = 176;
    private static final int OPTION_ROW_H = 16;
    private static final int INPUT_W = 42;
    private static final int INPUT_H = 14;
    private static final int STEP_W = 24;
    private static final int STEP_H = 14;
    private static final int ACTION_W = 52;
    private static final int ACTION_H = 16;
    private static final int MAX_CRAFT_COUNT = 999;

    private String itemLabel = "";
    private ItemStack preview = ItemStack.EMPTY;
    private final List<ClientRtsController.CraftRecipeOption> recipeOptions = new ArrayList<>();
    private int selectedRecipeIndex;
    private int recipeScroll;
    private String quantityText = "1";
    private boolean replaceOnNextDigit = true;
    private Request pendingRequest;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    public void open(ClientRtsController.CraftableEntry entry) {
        if (entry == null || !entry.craftable()) {
            return;
        }
        this.itemLabel = entry.stack().getHoverName().getString();
        this.preview = entry.stack().copy();
        this.recipeOptions.clear();
        this.recipeOptions.addAll(entry.recipeOptions());
        this.selectedRecipeIndex = findDefaultRecipeIndex();
        this.recipeScroll = 0;
        ensureSelectionVisible(visibleOptionRows(resolveLayout()));
        this.pendingRequest = null;
        this.replaceOnNextDigit = true;
        setQuantity(1);
        setOpen(true);
        markBroughtToFront();
    }

    public Request consumePendingRequest() {
        Request request = this.pendingRequest;
        this.pendingRequest = null;
        return request;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Layout layout = resolveLayout();
        int visibleRows = visibleOptionRows(layout);
        ensureSelectionVisible(visibleRows);
        ClientRtsController.CraftRecipeOption selected = getSelectedOption();

        if (!this.preview.isEmpty()) {
            g.renderItem(this.preview, layout.x(), layout.y());
        }
        String label = screen.font().plainSubstrByWidth(this.itemLabel, Math.max(24, layout.w() - 28));
        g.drawString(screen.font(), label, layout.x() + 22, layout.y() + 1, 0xE4ECF6, false);
        int selectedCount = selected == null ? 1 : Math.max(1, selected.resultCount());
        g.drawString(screen.font(), "Each craft: x" + selectedCount,
                layout.x() + 22, layout.y() + 13, 0xAFC0D3, false);

        g.drawString(screen.font(), "Recipes", layout.optionsX(), layout.optionsY() - 10, 0xD8E3EE, false);
        drawPanelFrame(g, layout.optionsX(), layout.optionsY(), layout.optionsW(), layout.optionsH(),
                0xAA202833, 0xFF61758A, 0xFF11161C);
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
            g.fill(layout.optionsX() + 2, rowY, layout.optionsX() + layout.optionsW() - 2,
                    rowY + OPTION_ROW_H - 1, fill);
            String summary = "x" + Math.max(1, option.resultCount()) + " " + normalizeOptionSummary(option.summary());
            g.drawString(screen.font(), screen.font().plainSubstrByWidth(summary, layout.optionsW() - 56),
                    layout.optionsX() + 6, rowY + 4, 0xF2F7FF, false);
            g.drawString(screen.font(), option.craftable() ? "MAKE" : "MISS",
                    layout.optionsX() + layout.optionsW() - 30, rowY + 4,
                    option.craftable() ? 0xC9F0C7 : 0xF0C4C4, false);
        }
        if (this.recipeOptions.size() > visibleRows) {
            String pageText = (this.selectedRecipeIndex + 1) + "/" + this.recipeOptions.size();
            g.drawString(screen.font(), pageText,
                    layout.optionsX() + layout.optionsW() - screen.font().width(pageText) - 4,
                    layout.optionsY() - 10, 0xAFC0D3, false);
        }

        String detail = selected == null
                ? "No recipe"
                : selected.craftable()
                        ? normalizeOptionSummary(selected.summary())
                        : normalizeOptionMissingSummary(selected.missingSummary());
        int detailColor = selected != null && !selected.craftable() ? 0xFFD6AAAA : 0xFFBCD0E2;
        g.drawString(screen.font(), screen.font().plainSubstrByWidth(detail, layout.w()),
                layout.x(), layout.detailY(), detailColor, false);

        drawSmallButton(g, layout.minusTenX(), layout.inputY(), STEP_W, STEP_H, "-10", 0xAA2A3340);
        drawSmallButton(g, layout.minusOneX(), layout.inputY(), STEP_W, STEP_H, "-1", 0xAA2A3340);
        drawPanelFrame(g, layout.inputX(), layout.inputY(), INPUT_W, INPUT_H,
                0xFF202833, 0xFF61758A, 0xFF11161C);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), this.quantityText,
                layout.inputX() + (INPUT_W / 2), layout.inputY() + 3, 0xFFFFFF);
        drawSmallButton(g, layout.plusOneX(), layout.inputY(), STEP_W, STEP_H, "+1", 0xAA2A3340);
        drawSmallButton(g, layout.plusTenX(), layout.inputY(), STEP_W, STEP_H, "+10", 0xAA2A3340);

        g.drawString(screen.font(), screen.font().plainSubstrByWidth("Enter confirm, Esc cancel", layout.w()),
                layout.x(), layout.helpY(), 0xAFC0D3, false);
        drawSmallButton(g, layout.cancelX(), layout.actionY(), ACTION_W, ACTION_H, "Cancel", 0xAA473030);
        drawSmallButton(g, layout.confirmX(), layout.actionY(), ACTION_W, ACTION_H, "Craft", 0xAA345A38);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        Layout layout = resolveLayout();
        int optionIndex = resolveClickedOption(mouseX, mouseY, layout, visibleOptionRows(layout));
        if (optionIndex >= 0) {
            this.selectedRecipeIndex = optionIndex;
            ensureSelectionVisible(visibleOptionRows(layout));
            return;
        }
        if (inside(mouseX, mouseY, layout.minusTenX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(-10);
        } else if (inside(mouseX, mouseY, layout.minusOneX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(-1);
        } else if (inside(mouseX, mouseY, layout.plusOneX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(1);
        } else if (inside(mouseX, mouseY, layout.plusTenX(), layout.inputY(), STEP_W, STEP_H)) {
            adjustQuantity(10);
        } else if (inside(mouseX, mouseY, layout.cancelX(), layout.actionY(), ACTION_W, ACTION_H)) {
            setOpen(false);
        } else if (inside(mouseX, mouseY, layout.confirmX(), layout.actionY(), ACTION_W, ACTION_H)) {
            confirm();
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.recipeOptions.size() > 1 && scrollY != 0.0D) {
            moveRecipeSelection(scrollY > 0.0D ? -1 : 1);
        }
        return true;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
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

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (Character.isDigit(codePoint)) {
            appendDigits(Character.toString(codePoint));
        }
        return true;
    }

    @Override
    protected void onClose() {
        this.itemLabel = "";
        this.preview = ItemStack.EMPTY;
        this.recipeOptions.clear();
        this.selectedRecipeIndex = 0;
        this.recipeScroll = 0;
        this.quantityText = "1";
        this.replaceOnNextDigit = true;
    }

    @Override
    protected Component getTitle() {
        return Component.literal("Craft Recipe");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return DEFAULT_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return MIN_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Math.max(24, (this.screen.height - this.windowHeight) / 2);
    }

    private void confirm() {
        ClientRtsController.CraftRecipeOption selected = getSelectedOption();
        int craftCount = getQuantity();
        if (selected == null || !selected.craftable()
                || selected.recipeId() == null || selected.recipeId().isBlank()
                || craftCount <= 0) {
            return;
        }
        this.pendingRequest = new Request(selected.recipeId(), craftCount);
        setOpen(false);
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
        return 0;
    }

    private void moveRecipeSelection(int delta) {
        if (this.recipeOptions.isEmpty()) {
            return;
        }
        this.selectedRecipeIndex = Mth.clamp(this.selectedRecipeIndex + delta, 0, this.recipeOptions.size() - 1);
        ensureSelectionVisible(visibleOptionRows(resolveLayout()));
    }

    private void ensureSelectionVisible(int visibleRows) {
        int maxScroll = Math.max(0, this.recipeOptions.size() - Math.max(1, visibleRows));
        if (this.selectedRecipeIndex < this.recipeScroll) {
            this.recipeScroll = this.selectedRecipeIndex;
        } else if (this.selectedRecipeIndex >= this.recipeScroll + visibleRows) {
            this.recipeScroll = this.selectedRecipeIndex - visibleRows + 1;
        }
        this.recipeScroll = Mth.clamp(this.recipeScroll, 0, maxScroll);
    }

    private int resolveClickedOption(double mouseX, double mouseY, Layout layout, int visibleRows) {
        if (!inside(mouseX, mouseY, layout.optionsX(), layout.optionsY(), layout.optionsW(), layout.optionsH())) {
            return -1;
        }
        int localY = (int) (mouseY - layout.optionsY()) - 2;
        if (localY < 0) {
            return -1;
        }
        int row = localY / OPTION_ROW_H;
        if (row < 0 || row >= visibleRows) {
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

    private Layout resolveLayout() {
        int x = contentX() + 8;
        int y = contentY() + 7;
        int w = Math.max(1, contentWidth() - 16);
        int actionY = contentY() + contentHeight() - ACTION_H - 8;
        int helpY = actionY - 14;
        int inputY = helpY - 18;
        int detailY = inputY - 14;
        int optionsY = y + 40;
        int optionsH = Math.max(OPTION_ROW_H + 4, detailY - optionsY - 8);
        int controlsW = STEP_W * 4 + INPUT_W + 24;
        int controlsX = x + Math.max(0, (w - controlsW) / 2);
        int minusTenX = controlsX;
        int minusOneX = minusTenX + STEP_W + 4;
        int inputX = minusOneX + STEP_W + 6;
        int plusOneX = inputX + INPUT_W + 6;
        int plusTenX = plusOneX + STEP_W + 4;
        int cancelX = x + w - (ACTION_W * 2) - 4;
        int confirmX = x + w - ACTION_W;
        return new Layout(x, y, w, optionsY, w, optionsH, detailY, inputY,
                minusTenX, minusOneX, inputX, plusOneX, plusTenX, helpY, actionY, cancelX, confirmX);
    }

    private int visibleOptionRows(Layout layout) {
        return Math.max(1, (layout.optionsH() - 4) / OPTION_ROW_H);
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

    private void drawSmallButton(GuiGraphics g, int x, int y, int w, int h, String label, int fill) {
        drawPanelFrame(g, x, y, w, h, fill, 0xFF667D95, 0xFF111821);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label,
                x + (w / 2), y + Math.max(2, (h - screen.font().lineHeight) / 2), 0xFFFFFF);
    }

    private static void drawPanelFrame(GuiGraphics g, int x, int y, int w, int h, int fillColor, int light, int dark) {
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, fillColor, light, dark);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public record Request(String recipeId, int craftCount) {
    }

    private record Layout(
            int x,
            int y,
            int w,
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

        int optionsX() {
            return x;
        }
    }
}
