package com.rtsbuilding.rtsbuilding.client.screen.craft;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityAction;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityOption;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityReducer;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityState;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityTransition;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityWindowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
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
    private ItemStack preview = ItemStack.EMPTY;
    private CraftQuantityState state = new CraftQuantityState(false, "", "",
            Collections.<CraftQuantityOption>emptyList(), 0, 0, 1, 1, true);
    private Request pendingRequest;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    public void open(CraftableEntry entry) {
        if (entry == null || !entry.craftable()) {
            return;
        }
        this.preview = entry.stack().copy();
        List<CraftQuantityOption> options = entry.recipeOptions().stream()
                .map(option -> new CraftQuantityOption(option.recipeId(), option.summary(),
                        option.missingSummary(), option.resultCount(), option.craftable()))
                .toList();
        CraftQuantityWindowLayout.Layout layout = resolveLayout();
        int selected = findDefaultRecipeIndex(options);
        this.state = new CraftQuantityState(true, entry.stack().getHoverName().getString(),
                entry.itemId(), options, selected, 0,
                CraftQuantityWindowLayout.visibleOptionRows(layout), 1, true);
        this.pendingRequest = null;
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
        CraftQuantityWindowLayout.Layout layout = resolveLayout();
        int visibleRows = CraftQuantityWindowLayout.visibleOptionRows(layout);
        CraftQuantityOption selected = this.state.selected();

        if (!this.preview.isEmpty()) {
            g.renderItem(this.preview, layout.x, layout.y);
        }
        String label = screen.font().plainSubstrByWidth(this.state.itemLabel, Math.max(24, layout.w - 28));
        g.drawString(screen.font(), label, layout.x + 22, layout.y + 1, 0xE4ECF6, false);
        int selectedCount = selected == null ? 1 : selected.resultCount;
        g.drawString(screen.font(), "Each craft: x" + selectedCount,
                layout.x + 22, layout.y + 13, 0xAFC0D3, false);

        g.drawString(screen.font(), "Recipes", layout.x, layout.optionsY - 10, 0xD8E3EE, false);
        drawPanelFrame(g, layout.x, layout.optionsY, layout.optionsW, layout.optionsH,
                0xAA202833, 0xFF61758A, 0xFF11161C);
        for (int row = 0; row < visibleRows; row++) {
            int optionIndex = this.state.scroll + row;
            if (optionIndex >= this.state.options.size()) {
                break;
            }
            CraftQuantityOption option = this.state.options.get(optionIndex);
            int rowY = layout.optionsY + 2 + row * CraftQuantityWindowLayout.OPTION_ROW_H;
            int fill = option.craftable ? 0xAA223B2E : 0xAA402626;
            if (optionIndex == this.state.selectedIndex) {
                fill = option.craftable ? 0xCC2E5B43 : 0xCC684040;
            }
            g.fill(layout.x + 2, rowY, layout.x + layout.optionsW - 2,
                    rowY + CraftQuantityWindowLayout.OPTION_ROW_H - 1, fill);
            String summary = "x" + option.resultCount + " " + normalizeOptionSummary(option.summary);
            g.drawString(screen.font(), screen.font().plainSubstrByWidth(summary, layout.optionsW - 56),
                    layout.x + 6, rowY + 4, 0xF2F7FF, false);
            g.drawString(screen.font(), option.craftable ? "MAKE" : "MISS",
                    layout.x + layout.optionsW - 30, rowY + 4,
                    option.craftable ? 0xC9F0C7 : 0xF0C4C4, false);
        }
        if (this.state.options.size() > visibleRows) {
            String pageText = (this.state.selectedIndex + 1) + "/" + this.state.options.size();
            g.drawString(screen.font(), pageText,
                    layout.x + layout.optionsW - screen.font().width(pageText) - 4,
                    layout.optionsY - 10, 0xAFC0D3, false);
        }

        String detail = selected == null
                ? "No recipe"
                : selected.craftable
                        ? normalizeOptionSummary(selected.summary)
                        : normalizeOptionMissingSummary(selected.missingSummary);
        int detailColor = selected != null && !selected.craftable ? 0xFFD6AAAA : 0xFFBCD0E2;
        g.drawString(screen.font(), screen.font().plainSubstrByWidth(detail, layout.w),
                layout.x, layout.detailY, detailColor, false);

        drawSmallButton(g, layout.minusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, "-10", 0xAA2A3340);
        drawSmallButton(g, layout.minusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, "-1", 0xAA2A3340);
        drawPanelFrame(g, layout.inputX, layout.inputY,
                CraftQuantityWindowLayout.INPUT_W, CraftQuantityWindowLayout.INPUT_H,
                0xFF202833, 0xFF61758A, 0xFF11161C);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), Integer.toString(this.state.quantity),
                layout.inputX + CraftQuantityWindowLayout.INPUT_W / 2, layout.inputY + 3, 0xFFFFFF);
        drawSmallButton(g, layout.plusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, "+1", 0xAA2A3340);
        drawSmallButton(g, layout.plusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, "+10", 0xAA2A3340);

        g.drawString(screen.font(), screen.font().plainSubstrByWidth("Enter confirm, Esc cancel", layout.w),
                layout.x, layout.helpY, 0xAFC0D3, false);
        drawSmallButton(g, layout.cancelX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H,
                "Cancel", 0xAA473030);
        drawSmallButton(g, layout.confirmX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H,
                "Craft", 0xAA345A38);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        CraftQuantityWindowLayout.Layout layout = resolveLayout();
        int optionIndex = resolveClickedOption(mouseX, mouseY, layout,
                CraftQuantityWindowLayout.visibleOptionRows(layout));
        if (optionIndex >= 0) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.SELECT, optionIndex));
            return;
        }
        if (inside(mouseX, mouseY, layout.minusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, -10));
        } else if (inside(mouseX, mouseY, layout.minusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, -1));
        } else if (inside(mouseX, mouseY, layout.plusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, 1));
        } else if (inside(mouseX, mouseY, layout.plusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, 10));
        } else if (inside(mouseX, mouseY, layout.cancelX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H)) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.CANCEL));
        } else if (inside(mouseX, mouseY, layout.confirmX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H)) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.CONFIRM));
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.state.options.size() > 1 && scrollY != 0.0D) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.MOVE,
                    scrollY > 0.0D ? -1 : 1));
        }
        return true;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.CONFIRM));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.MOVE,
                    (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.MOVE, -1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.MOVE, 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.BACKSPACE));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.CLEAR));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_RIGHT) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, ctrl ? 10 : 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_LEFT) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, ctrl ? -10 : -1));
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                dispatch(CraftQuantityAction.text(minecraft.keyboardHandler.getClipboard()));
            }
            return true;
        }
        return true;
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (Character.isDigit(codePoint)) {
            dispatch(CraftQuantityAction.text(Character.toString(codePoint)));
        }
        return true;
    }

    @Override
    protected void onClose() {
        this.preview = ItemStack.EMPTY;
        this.state = new CraftQuantityState(false, "", "",
                Collections.<CraftQuantityOption>emptyList(), 0, 0, 1, 1, true);
    }

    @Override
    protected Component getTitle() {
        return Component.literal("Craft Recipe");
    }

    @Override
    protected int getDefaultWidth() {
        return CraftQuantityWindowLayout.DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return CraftQuantityWindowLayout.DEFAULT_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return CraftQuantityWindowLayout.MIN_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return CraftQuantityWindowLayout.MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Math.max(24, (this.screen.height - this.windowHeight) / 2);
    }

    private static int findDefaultRecipeIndex(List<CraftQuantityOption> options) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).craftable) {
                return i;
            }
        }
        return 0;
    }

    private int resolveClickedOption(double mouseX, double mouseY,
                                     CraftQuantityWindowLayout.Layout layout, int visibleRows) {
        if (!inside(mouseX, mouseY, layout.x, layout.optionsY, layout.optionsW, layout.optionsH)) {
            return -1;
        }
        int localY = (int) (mouseY - layout.optionsY) - 2;
        if (localY < 0) {
            return -1;
        }
        int row = localY / CraftQuantityWindowLayout.OPTION_ROW_H;
        if (row < 0 || row >= visibleRows) {
            return -1;
        }
        int index = this.state.scroll + row;
        return index < this.state.options.size() ? index : -1;
    }

    private CraftQuantityWindowLayout.Layout resolveLayout() {
        return CraftQuantityWindowLayout.resolve(
                contentX(), contentY(), contentWidth(), contentHeight());
    }

    private void dispatch(CraftQuantityAction action) {
        CraftQuantityTransition transition = CraftQuantityReducer.apply(this.state, action);
        this.state = transition.state;
        if (transition.command == CraftQuantityTransition.Command.CONFIRM) {
            this.pendingRequest = new Request(transition.recipeId, transition.craftCount);
            setOpen(false);
        } else if (transition.command == CraftQuantityTransition.Command.CANCEL) {
            setOpen(false);
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

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("craft_quantity", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
