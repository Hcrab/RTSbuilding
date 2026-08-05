package com.rtsbuilding.rtsbuilding.client.screen.craft;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityAction;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityOption;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityReducer;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityState;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityTransition;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiControlChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftQuantityWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftQuantityStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Window-layer version of the RTS craft quantity picker.
 *
 * <p>The panel owns only the recipe/count UI state and confirmed request. The
 * actual craft execution remains in {@link ClientRtsController}, so migrating
 * this popup into the RTS window layer does not change server-side crafting
 * semantics or linked-storage validation.
 */
public final class RtsCraftQuantityWindowPanel extends RtsWindowPanel {
    private static final UiControlState ENABLED_CONTROL = UiControlState.enabled();
    private ItemStack preview = null;
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
        List<CraftQuantityOption> options = new ArrayList<CraftQuantityOption>();
        if (entry.recipeOptions() != null) {
            for (com.rtsbuilding.rtsbuilding.client.record.CraftRecipeOption option : entry.recipeOptions()) {
                if (option != null) {
                    options.add(new CraftQuantityOption(option.recipeId(), option.summary(),
                            option.missingSummary(), option.resultCount(), option.craftable()));
                }
            }
        }
        CraftQuantityWindowLayout.Layout layout = resolveLayout();
        int selected = findDefaultRecipeIndex(options);
        this.state = new CraftQuantityState(true, entry.stack().getDisplayName(),
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
    protected void renderContent(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CraftQuantityWindowLayout.Layout layout = resolveLayout();
        FontRenderer font = screen.font();
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, font, screen);
        int visibleRows = CraftQuantityWindowLayout.visibleOptionRows(layout);
        CraftQuantityOption selected = this.state.selected();

        if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(this.preview)) {
            g.renderItem(this.preview, layout.x, layout.y);
            // Legacy RenderItem 会开启深度测试；窗口余下内容仍是二维 UI，立即恢复其预期状态。
            GlStateManager.disableDepth();
        }
        String label = font.trimStringToWidth(
                this.state.itemLabel,
                Math.max(24, layout.w - CraftQuantityWindowLayout.ITEM_TEXT_RIGHT_RESERVE));
        g.drawString(font, label,
                layout.x + CraftQuantityWindowLayout.ITEM_TEXT_X,
                layout.y + CraftQuantityWindowLayout.ITEM_LABEL_TOP,
                CraftQuantityStyle.ITEM_LABEL.toArgb(), false);
        int selectedCount = selected == null ? 1 : selected.resultCount;
        g.drawString(font, tr("screen.rtsbuilding.craft_quantity.each", "Each craft: x%s", selectedCount),
                layout.x + CraftQuantityWindowLayout.ITEM_TEXT_X,
                layout.y + CraftQuantityWindowLayout.ITEM_DETAIL_TOP,
                CraftQuantityStyle.MUTED_TEXT.toArgb(), false);

        g.drawString(font, tr("screen.rtsbuilding.craft_quantity.recipes", "Recipes"),
                layout.x, layout.optionsY - 10,
                CraftQuantityStyle.SECTION_LABEL.toArgb(), false);
        UiChromeRenderer.frame(canvas, rect(layout.x, layout.optionsY, layout.optionsW, layout.optionsH), 1.0D,
                CraftQuantityStyle.OPTIONS_BACKGROUND, CraftQuantityStyle.OPTIONS_BORDER_LIGHT,
                CraftQuantityStyle.OPTIONS_BORDER_DARK);
        for (int row = 0; row < visibleRows; row++) {
            int optionIndex = this.state.scroll + row;
            if (optionIndex >= this.state.options.size()) {
                break;
            }
            CraftQuantityOption option = this.state.options.get(optionIndex);
            int rowY = layout.optionsY + 2 + row * CraftQuantityWindowLayout.OPTION_ROW_H;
            UiColor fill = CraftQuantityStyle.rowBackground(option.craftable,
                    optionIndex == this.state.selectedIndex);
            g.fill(layout.x + CraftQuantityWindowLayout.OPTION_ROW_HORIZONTAL_INSET,
                    rowY,
                    layout.x + layout.optionsW
                            - CraftQuantityWindowLayout.OPTION_ROW_HORIZONTAL_INSET,
                    rowY + CraftQuantityWindowLayout.OPTION_ROW_H - 1, fill.toArgb());
            String summary = "x" + option.resultCount + " " + normalizeOptionSummary(option.summary);
            g.drawString(font, font.trimStringToWidth(summary, layout.optionsW - 56),
                    layout.x + CraftQuantityWindowLayout.OPTION_ROW_TEXT_X,
                    rowY + CraftQuantityWindowLayout.OPTION_ROW_TEXT_TOP,
                    CraftQuantityStyle.ROW_TEXT.toArgb(), false);
            g.drawString(font, option.craftable
                            ? tr("screen.rtsbuilding.craft_quantity.make", "MAKE")
                            : tr("screen.rtsbuilding.craft_quantity.miss", "MISS"),
                    layout.x + layout.optionsW - 30, rowY + 4,
                    CraftQuantityStyle.badge(option.craftable).toArgb(), false);
        }
        if (this.state.options.size() > visibleRows) {
            String pageText = (this.state.selectedIndex + 1) + "/" + this.state.options.size();
            g.drawString(font, pageText,
                    layout.x + layout.optionsW - font.getStringWidth(pageText) - 4,
                    layout.optionsY - 10, CraftQuantityStyle.MUTED_TEXT.toArgb(), false);
        }

        String detail = selected == null
                ? tr("screen.rtsbuilding.craft_quantity.no_recipe", "No recipe")
                : selected.craftable
                        ? normalizeOptionSummary(selected.summary)
                        : normalizeOptionMissingSummary(selected.missingSummary);
        UiColor detailColor = CraftQuantityStyle.detail(
                selected != null && !selected.craftable);
        g.drawString(font, font.trimStringToWidth(detail, layout.w),
                layout.x, layout.detailY, detailColor.toArgb(), false);

        drawSmallButton(g, canvas, layout.minusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H,
                "-10", UiControlRole.HOLD_REPEAT);
        drawSmallButton(g, canvas, layout.minusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H,
                "-1", UiControlRole.HOLD_REPEAT);
        UiChromeRenderer.frame(canvas, rect(layout.inputX, layout.inputY,
                        CraftQuantityWindowLayout.INPUT_W, CraftQuantityWindowLayout.INPUT_H), 1.0D,
                RtsMainlineTheme.INPUT_BACKGROUND, RtsMainlineTheme.INPUT_BORDER_LIGHT,
                RtsMainlineTheme.INPUT_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, Integer.toString(this.state.quantity),
                layout.inputX + CraftQuantityWindowLayout.INPUT_W / 2, layout.inputY + 3,
                RtsMainlineTheme.BUTTON_TEXT.toArgb());
        drawSmallButton(g, canvas, layout.plusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H,
                "+1", UiControlRole.HOLD_REPEAT);
        drawSmallButton(g, canvas, layout.plusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H,
                "+10", UiControlRole.HOLD_REPEAT);

        g.drawString(font, font.trimStringToWidth(
                        tr("screen.rtsbuilding.craft_quantity.help", "Enter confirm, Esc cancel"), layout.w),
                layout.x, layout.helpY, CraftQuantityStyle.MUTED_TEXT.toArgb(), false);
        drawSmallButton(g, canvas, layout.cancelX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H,
                tr("gui.cancel", "Cancel"), UiControlRole.DESTRUCTIVE_CONFIRM);
        drawSmallButton(g, canvas, layout.confirmX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H,
                tr("screen.rtsbuilding.craft_quantity.confirm", "Craft"), UiControlRole.PRIMARY_ACTION);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        CraftQuantityWindowLayout.Layout layout = resolveLayout();
        int optionIndex = resolveClickedOption(mouseX, mouseY, layout,
                CraftQuantityWindowLayout.visibleOptionRows(layout));
        if (optionIndex >= 0) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.SELECT, optionIndex));
            return;
        }
        if (UiRect.contains(layout.minusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, mouseX, mouseY)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, -10));
        } else if (UiRect.contains(layout.minusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, mouseX, mouseY)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, -1));
        } else if (UiRect.contains(layout.plusOneX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, mouseX, mouseY)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, 1));
        } else if (UiRect.contains(layout.plusTenX, layout.inputY,
                CraftQuantityWindowLayout.STEP_W, CraftQuantityWindowLayout.STEP_H, mouseX, mouseY)) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, 10));
        } else if (UiRect.contains(layout.cancelX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H, mouseX, mouseY)) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.CANCEL));
        } else if (UiRect.contains(layout.confirmX, layout.actionY,
                CraftQuantityWindowLayout.ACTION_W, CraftQuantityWindowLayout.ACTION_H, mouseX, mouseY)) {
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
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.CONFIRM));
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.MOVE,
                    isShiftDown() ? -1 : 1));
            return true;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.MOVE, -1));
            return true;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.MOVE, 1));
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.BACKSPACE));
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            dispatch(CraftQuantityAction.simple(CraftQuantityAction.Type.CLEAR));
            return true;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_RIGHT) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, ctrl ? 10 : 1));
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_LEFT) {
            dispatch(CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, ctrl ? -10 : -1));
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_V) {
            dispatch(CraftQuantityAction.text(GuiScreen.getClipboardString()));
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
        this.preview = null;
        this.state = new CraftQuantityState(false, "", "",
                Collections.<CraftQuantityOption>emptyList(), 0, 0, 1, 1, true);
    }

    @Override
    protected IChatComponent getTitle() {
        return new ChatComponentText(tr(
                "screen.rtsbuilding.craft_quantity.title", "Craft Recipe"));
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
        if (!UiRect.contains(layout.x, layout.optionsY, layout.optionsW, layout.optionsH, mouseX, mouseY)) {
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
        return summary == null || summary.trim().isEmpty()
                ? tr("screen.rtsbuilding.craft_quantity.recipe", "Recipe") : summary;
    }

    private static String normalizeOptionMissingSummary(String summary) {
        return summary == null || summary.trim().isEmpty()
                ? tr("screen.rtsbuilding.craft_quantity.missing", "Missing ingredients.") : summary;
    }

    private void drawSmallButton(LegacyGuiGraphics g, MinecraftUiCanvas canvas,
                                 int x, int y, int w, int h, String label,
                                 UiControlRole role) {
        UiControlChromeRenderer.compactFrame(canvas, rect(x, y, w, h), role, ENABLED_CONTROL);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label,
                x + (w / 2), y + Math.max(2, (h - screen.font().FONT_HEIGHT) / 2),
                RtsMainlineTheme.BUTTON_TEXT.toArgb());
    }

    private static UiRect rect(int x, int y, int w, int h) {
        return new UiRect(x, y, w, h);
    }

    /** Java 8 可加载的不可变确认请求；网络发送仍由 BuilderScreen 所有者执行。 */
    public static final class Request {
        private final String recipeId;
        private final int craftCount;

        public Request(String recipeId, int craftCount) {
            this.recipeId = recipeId == null ? "" : recipeId;
            this.craftCount = craftCount;
        }

        public String recipeId() {
            return recipeId;
        }

        public int craftCount() {
            return craftCount;
        }

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
    }

    private final List<PersistableProperty> properties = Collections.singletonList(
            PersistableProperty.bounds("craft_quantity", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    /**
     * 1.12 使用客户端 I18n；旧资源包缺少新键时保留 main 的英文文案，避免界面直接显示键名。
     */
    private static String tr(String key, String fallback, Object... arguments) {
        return net.minecraft.util.StatCollector.canTranslate(key) ? I18n.format(key, arguments)
                : String.format(java.util.Locale.ROOT, fallback, arguments);
    }
}
