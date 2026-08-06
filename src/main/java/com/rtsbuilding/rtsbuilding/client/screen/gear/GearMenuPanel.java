package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsSectionId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiSection;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiState;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiAction;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiRow;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiMotionSpec;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiValueAnimation;
import com.rtsbuilding.rtsbuilding.uikit.layout.SettingsWindowLayout;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Settings window for RTS Builder.
 *
 * <p>The window chrome, close button, drag/resize behavior, and z-order are
 * owned by {@link RtsWindowPanel}. This class owns only the settings rows and
 * their player-facing actions.
 */
public final class GearMenuPanel extends RtsWindowPanel {
    private static final int LEGACY_DEFAULT_WINDOW_W = 300;
    private static final int LEGACY_DEFAULT_WINDOW_H = 284;
    private static final int DEFAULT_WINDOW_W = 380;
    private static final int MIN_WINDOW_W = 280;

    private int scroll = 0;
    private boolean controlsExpanded = false;
    private boolean displayExpanded = false;
    private boolean helpersExpanded = false;
    private boolean soundExpanded = false;
    private boolean animationExpanded = false;
    private final Set<String> expandedHintKeys = new HashSet<>();
    private SettingsId draggingCoreSensitivity = null;
    private ThemeSettingsPanel themeSettingsPanel;
    private final EnumMap<SettingsId, UiControlAnimationState> toggleAnimations =
            controlAnimations(SettingsId.class);
    private final EnumMap<SettingsId, UiControlAnimationState> actionAnimations =
            controlAnimations(SettingsId.class);
    private final EnumMap<SettingsId, UiControlAnimationState> minusAnimations =
            controlAnimations(SettingsId.class);
    private final EnumMap<SettingsId, UiControlAnimationState> plusAnimations =
            controlAnimations(SettingsId.class);
    private final EnumMap<SettingsId, UiControlAnimationState> hintAnimations =
            controlAnimations(SettingsId.class);
    private final EnumMap<SettingsSectionId, UiControlAnimationState> sectionAnimations =
            controlAnimations(SettingsSectionId.class);
    private final EnumMap<SettingsId, UiValueAnimation> sensitivityAnimations =
            valueAnimations(SettingsId.class);

    /** 由 BuilderScreen 在创建浮窗层前绑定，设置目录本身不负责拥有子窗口。 */
    public void bindThemeSettingsPanel(ThemeSettingsPanel panel) {
        this.themeSettingsPanel = panel;
    }

    EnumSet<SettingsSectionId> coreExpandedSections() {
        EnumSet<SettingsSectionId> out = EnumSet.noneOf(SettingsSectionId.class);
        if (controlsExpanded) out.add(SettingsSectionId.CONTROLS);
        if (displayExpanded) out.add(SettingsSectionId.DISPLAY);
        if (helpersExpanded) out.add(SettingsSectionId.HELPERS);
        if (soundExpanded) out.add(SettingsSectionId.SOUND);
        if (animationExpanded) out.add(SettingsSectionId.ANIMATION);
        return out;
    }

    EnumSet<SettingsId> coreExpandedHints() {
        EnumSet<SettingsId> out = EnumSet.noneOf(SettingsId.class);
        for (SettingsId id : SettingsId.values()) {
            if (!id.hintKey.isEmpty() && expandedHintKeys.contains(id.hintKey)) out.add(id);
        }
        return out;
    }

    int coreScroll() {
        return scroll;
    }

    void applyCoreViewState(SettingsUiState state) {
        controlsExpanded = expanded(state, SettingsSectionId.CONTROLS);
        displayExpanded = expanded(state, SettingsSectionId.DISPLAY);
        helpersExpanded = expanded(state, SettingsSectionId.HELPERS);
        soundExpanded = expanded(state, SettingsSectionId.SOUND);
        animationExpanded = expanded(state, SettingsSectionId.ANIMATION);
        expandedHintKeys.clear();
        for (SettingsUiSection section : state.sections) {
            for (var row : section.rows) {
                if (row.hintExpanded && !row.id.hintKey.isEmpty()) expandedHintKeys.add(row.id.hintKey);
            }
        }
        scroll = Math.max(0, state.scroll);
        clampScroll();
    }

    private static boolean expanded(SettingsUiState state, SettingsSectionId id) {
        SettingsUiSection section = state.section(id);
        return section != null && section.expanded;
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = true;
    }

    public void open() {
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(RtsGuiContext g, int mouseX, int mouseY, float partialTick) {
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);
        renderCoreControls(g, canvas, mouseX, mouseY);
        renderScrollbar(g, canvas, contentX(), contentY(), contentWidth(), contentHeight());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            handleCoreClick(mouseX, mouseY);
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return true;
        }
        int delta = scrollY > 0.0D ? -18 : 18;
        return GearMenuUiAdapter.dispatch(this, screen, controller,
                SettingsUiAction.scroll(this.scroll + delta, maxScroll),
                contentX(), contentWidth());
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.settings.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_WINDOW_W;
    }

    @Override
    protected int getDefaultHeight() {
        return GEAR_MENU_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return MIN_WINDOW_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return GEAR_MENU_MIN_H;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean legacyDefaultBounds = width == LEGACY_DEFAULT_WINDOW_W && height == LEGACY_DEFAULT_WINDOW_H;
        int restoredWidth = legacyDefaultBounds ? DEFAULT_WINDOW_W : width;
        int restoredHeight = legacyDefaultBounds ? GEAR_MENU_H : height;
        super.setBounds(x, y, restoredWidth, restoredHeight);
    }

    @Override
    protected int getMaxWindowWidth() {
        if (this.screen == null) {
            return super.getMaxWindowWidth();
        }
        int viewportLimit = Math.max(getMinWindowWidth(), (this.screen.width * 2) / 3);
        return Math.min(super.getMaxWindowWidth(), viewportLimit);
    }

    @Override
    protected int getMaxWindowHeight() {
        if (this.screen == null) {
            return super.getMaxWindowHeight();
        }
        int viewportLimit = Math.max(getMinWindowHeight(), (this.screen.height * 2) / 3);
        return Math.min(super.getMaxWindowHeight(), viewportLimit);
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Mth.clamp((this.screen.height - this.windowHeight) / 2,
                TOP_H + 6,
                Math.max(TOP_H + 6, this.screen.height - this.windowHeight - 8));
    }

    /** 生产绘制直接遍历 Core 正式目录，避免设置项在预览与运行时漂移。 */
    private void renderCoreControls(RtsGuiContext g, MinecraftUiCanvas canvas, int mouseX, int mouseY) {
        SettingsUiState state = coreSnapshot();
        SettingsWindowLayout.Layout layout = coreLayout(state);
        for (SettingsWindowLayout.Node node : layout.nodes) {
            int drawY = node.y - state.scroll;
            if (node.isSection()) {
                drawCoreSectionHeader(g, canvas, mouseX, mouseY, node.x, node.width, drawY,
                        node.section.id, node.section.id.titleKey, node.section.expanded);
            } else {
                drawCoreRow(g, canvas, mouseX, mouseY, node.row, node.x, drawY, node.width);
            }
        }
    }

    private void drawCoreRow(RtsGuiContext g, MinecraftUiCanvas canvas, int mouseX, int mouseY,
                             SettingsUiRow row, int x, int y, int w) {
        switch (row.id.kind) {
            case SENSITIVITY -> drawCoreSensitivityRow(g, row, y, x, w);
            case STEP_VALUE -> drawCoreStepRow(g, canvas, mouseX, mouseY, row, y, x, w);
            case SIMPLE_TOGGLE -> drawCoreSimpleToggleRow(g, canvas, mouseX, mouseY, row, x, w, y);
            case HINT_TOGGLE -> drawCoreHintToggleRow(g, canvas, mouseX, mouseY, row, x, w, y);
            case ACTION -> drawCoreActionRow(g, canvas, mouseX, mouseY, row, x, w, y);
        }
    }

    private void drawCoreActionRow(RtsGuiContext g, MinecraftUiCanvas canvas, int mouseX, int mouseY,
                                   SettingsUiRow row, int x, int w, int rowY) {
        g.drawString(screen.font(), trimToWidth(text(row.id.labelKey),
                        w - SettingsWindowLayout.ACTION_LABEL_RIGHT_RESERVE),
                x + SettingsWindowLayout.ROW_TEXT_INSET, rowY + 4,
                SettingsWindowStyle.LABEL.toArgb(), false);
        String current = Component.translatable(row.valueLabel).getString();
        g.drawString(screen.font(), trimToWidth(current,
                        w - SettingsWindowLayout.ACTION_LABEL_RIGHT_RESERVE),
                x + SettingsWindowLayout.ROW_TEXT_INSET, rowY + 17,
                SettingsWindowStyle.HINT.toArgb(), false);
        int buttonX = x + w - SettingsWindowLayout.ACTION_BUTTON_RIGHT_INSET;
        drawCoreActionButton(g, canvas, mouseX, mouseY, row.id, buttonX,
                rowY + SettingsWindowLayout.ACTION_BUTTON_TOP,
                SettingsWindowLayout.ACTION_BUTTON_W, SettingsWindowLayout.ACTION_BUTTON_H,
                text("screen.rtsbuilding.theme.open"));
    }

    private void drawCoreActionButton(RtsGuiContext g, MinecraftUiCanvas canvas,
                                      int mouseX, int mouseY, SettingsId id,
                                      int x, int y, int w, int h,
                                      String label) {
        boolean hover = UiRect.contains(x, y, w, h, mouseX, mouseY);
        UiControlAnimationState.Snapshot animation = updateControl(
                this.actionAnimations, id, true, hover, false);
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, w, h),
                UiColor.interpolate(SettingsWindowStyle.STEP_BACKGROUND,
                        SettingsWindowStyle.STEP_HOVER_BACKGROUND, animation.hover()),
                SettingsWindowStyle.STEP_BORDER, SettingsWindowStyle.STEP_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label,
                x + w / 2, y + SettingsWindowLayout.ACTION_BUTTON_TEXT_TOP,
                SettingsWindowStyle.VALUE.toArgb());
    }

    private void drawCoreSensitivityRow(RtsGuiContext g, SettingsUiRow row, int rowY, int x, int w) {
        g.drawString(screen.font(), Component.translatable(row.id.labelKey),
                x + SettingsWindowLayout.ROW_TEXT_INSET, rowY + 5, (row.enabled ? SettingsWindowStyle.LABEL
                        : SettingsWindowStyle.DISABLED_TEXT).toArgb(), false);
        g.drawString(screen.font(), row.valueLabel,
                x + w - SettingsWindowLayout.SENSITIVITY_VALUE_RIGHT_INSET,
                rowY + 5, (row.enabled ? SettingsWindowStyle.VALUE
                        : SettingsWindowStyle.DISABLED_TEXT).toArgb(), false);
        int trackX = x + SettingsWindowLayout.SENSITIVITY_TRACK_INSET;
        int trackY = rowY + 24;
        int trackW = w - SettingsWindowLayout.SENSITIVITY_TRACK_INSET * 2;
        g.fill(trackX, trackY, trackX + trackW, trackY + 4,
                SettingsWindowStyle.TRACK_BACKGROUND.toArgb());
        g.fill(trackX + 1, trackY + 1, trackX + trackW - 1, trackY + 3,
                SettingsWindowStyle.TRACK_FILL.toArgb());
        double targetFraction = row.valueIndex
                / (double) Math.max(1, row.valueCount - 1);
        double displayFraction = this.sensitivityAnimations.get(row.id).update(
                targetFraction, Config.isUiAnimationsEnabled(), UiMotionSpec.SLIDER_MS);
        int knobX = trackX + (int) Math.round(displayFraction * trackW);
        g.fill(knobX - 3, trackY - 5, knobX + 4, trackY + 8,
                (row.enabled ? SettingsWindowStyle.KNOB : SettingsWindowStyle.KNOB_DISABLED).toArgb());
    }

    private void drawCoreStepRow(RtsGuiContext g, MinecraftUiCanvas canvas, int mouseX, int mouseY,
                                 SettingsUiRow row, int rowY, int x, int w) {
        int minusX = x + w - SettingsWindowLayout.STEP_CONTROLS_RIGHT_INSET;
        int valueX = minusX + 26;
        int plusX = valueX + 60;
        boolean soundLimit = row.id == SettingsId.BLOCK_SOUNDS_PER_TICK;
        int labelY = soundLimit ? rowY + 3 : rowY + 8;
        int buttonY = soundLimit ? rowY + 8 : rowY + 6;
        g.drawString(screen.font(), trimToWidth(text(row.id.labelKey),
                        w - SettingsWindowLayout.STEP_LABEL_RIGHT_RESERVE),
                x + SettingsWindowLayout.ROW_TEXT_INSET, labelY, (row.enabled ? SettingsWindowStyle.LABEL
                        : SettingsWindowStyle.DISABLED_TEXT).toArgb(), false);
        if (soundLimit) {
            g.drawString(screen.font(), trimToWidth(text(row.id.hintKey),
                            w - SettingsWindowLayout.STEP_LABEL_RIGHT_RESERVE),
                    x + SettingsWindowLayout.ROW_TEXT_INSET, rowY + 18,
                    SettingsWindowStyle.HINT.toArgb(), false);
        }
        drawCoreStepButton(g, canvas, mouseX, mouseY,
                row.id, false, minusX, buttonY, "-");
        UiCompactFrameRenderer.frame(canvas, new UiRect(valueX, buttonY, 56, 22),
                SettingsWindowStyle.VALUE_BACKGROUND, SettingsWindowStyle.VALUE_BORDER,
                SettingsWindowStyle.VALUE_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), row.valueLabel,
                valueX + 28, buttonY + 7, SettingsWindowStyle.VALUE.toArgb());
        drawCoreStepButton(g, canvas, mouseX, mouseY,
                row.id, true, plusX, buttonY, "+");
    }

    private void drawCoreSimpleToggleRow(RtsGuiContext g, MinecraftUiCanvas canvas, int mouseX, int mouseY,
                                         SettingsUiRow row, int x, int w, int rowY) {
        g.drawString(screen.font(), trimToWidth(text(row.id.labelKey),
                        w - SettingsWindowLayout.SIMPLE_LABEL_RIGHT_RESERVE),
                x + SettingsWindowLayout.ROW_TEXT_INSET, rowY + 9, (row.enabled ? SettingsWindowStyle.LABEL
                        : SettingsWindowStyle.DISABLED_TEXT).toArgb(), false);
        drawCoreToggleButton(g, canvas, mouseX, mouseY, row.id,
                x + w - SettingsWindowLayout.TOGGLE_RIGHT_INSET, rowY + 1,
                SettingsWindowLayout.TOGGLE_WIDTH,
                SettingsWindowLayout.TOGGLE_HEIGHT, row.enabled, row.active);
    }

    private void drawCoreHintToggleRow(RtsGuiContext g, MinecraftUiCanvas canvas, int mouseX, int mouseY,
                                       SettingsUiRow row, int x, int w, int rowY) {
        int hintX = hintTextX(x, row.hintExpandable);
        int hintW = hintTextMaxWidth(x, w, row.hintExpandable);
        g.drawString(screen.font(), trimToWidth(text(row.id.labelKey),
                        w - SettingsWindowLayout.HINT_LABEL_RIGHT_RESERVE),
                x + SettingsWindowLayout.ROW_TEXT_INSET, rowY + 2, (row.enabled ? SettingsWindowStyle.LABEL
                        : SettingsWindowStyle.DISABLED_TEXT).toArgb(), false);
        if (row.hintExpandable) drawCoreHintExpandButton(
                g, canvas, mouseX, mouseY, row.id, x, rowY, row.hintExpanded);
        if (row.hintExpanded) {
            List<FormattedCharSequence> lines = wrappedHintLines(x, w, row.id.hintKey);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(screen.font(), lines.get(i), hintX,
                        rowY + 13 + i * SettingsWindowLayout.HINT_LINE_H,
                        SettingsWindowStyle.HINT.toArgb(), false);
            }
        } else {
            String hint = row.enabled || row.disabledReasonKey.isEmpty()
                    ? text(row.id.hintKey) : text(row.disabledReasonKey);
            g.drawString(screen.font(), trimToWidth(hint, hintW), hintX, rowY + 13,
                    (row.enabled ? SettingsWindowStyle.HINT
                            : SettingsWindowStyle.DISABLED_REASON).toArgb(), false);
        }
        drawCoreToggleButton(g, canvas, mouseX, mouseY, row.id,
                x + w - SettingsWindowLayout.TOGGLE_RIGHT_INSET, rowY + 2,
                SettingsWindowLayout.TOGGLE_WIDTH,
                SettingsWindowLayout.TOGGLE_HEIGHT, row.enabled, row.active);
    }

    private SettingsUiState coreSnapshot() {
        return GearMenuUiAdapter.snapshot(this, screen, controller, contentX(), contentWidth());
    }

    private SettingsWindowLayout.Layout coreLayout(SettingsUiState state) {
        return SettingsWindowLayout.layout(state, contentX(), contentY(), contentWidth(),
                row -> row.hintExpanded
                        ? wrappedHintLines(contentX(), contentWidth(), row.id.hintKey).size() : 1);
    }

    private void handleCoreClick(double mouseX, double mouseY) {
        SettingsUiState state = coreSnapshot();
        SettingsWindowLayout.Layout layout = coreLayout(state);
        double contentMouseY = mouseY + state.scroll;
        for (SettingsWindowLayout.Node node : layout.nodes) {
            if (node.isSection()) {
                if (UiRect.contains(
                        node.x + SettingsWindowLayout.SECTION_HORIZONTAL_INSET, node.y,
                        node.width - SettingsWindowLayout.SECTION_HORIZONTAL_INSET * 2, node.height,
                        mouseX, contentMouseY)) {
                    dispatchCore(SettingsUiAction.section(node.section.id));
                    return;
                }
                continue;
            }
            SettingsUiRow row = node.row;
            if (row.id.kind == com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind.ACTION
                    && UiRect.contains(node.x + node.width - SettingsWindowLayout.ACTION_BUTTON_RIGHT_INSET,
                    node.y + SettingsWindowLayout.ACTION_BUTTON_TOP,
                    SettingsWindowLayout.ACTION_BUTTON_W, SettingsWindowLayout.ACTION_BUTTON_H,
                    mouseX, contentMouseY)) {
                if (row.id == SettingsId.UI_THEME && this.themeSettingsPanel != null) {
                    close();
                    this.themeSettingsPanel.open();
                }
                return;
            }
            if (row.id.kind == com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind.SENSITIVITY
                    && UiRect.contains(
                    node.x + SettingsWindowLayout.SENSITIVITY_TRACK_INSET,
                    node.y + SettingsWindowLayout.SENSITIVITY_TRACK_INSET,
                    node.width - SettingsWindowLayout.SENSITIVITY_TRACK_INSET * 2, 22,
                    mouseX, contentMouseY)) {
                double fraction = calcSensitivityFraction(mouseX, node.x, node.width);
                if (dispatchCore(SettingsUiAction.sensitivity(row.id, fraction))) {
                    draggingCoreSensitivity = row.id;
                }
                return;
            }
            if (row.hintExpandable && UiRect.contains(
                    hintExpandButtonX(node.x),
                    node.y + SettingsWindowLayout.HINT_BUTTON_TOP,
                    SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE,
                    SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE, mouseX, contentMouseY)) {
                dispatchCore(SettingsUiAction.setting(SettingsUiAction.Type.TOGGLE_HINT, row.id));
                return;
            }
            if (row.id.kind == com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind.STEP_VALUE) {
                int minusX = node.x + node.width - SettingsWindowLayout.STEP_CONTROLS_RIGHT_INSET;
                int buttonY = node.y + (row.id == SettingsId.BLOCK_SOUNDS_PER_TICK ? 8 : 6);
                if (UiRect.contains(minusX, buttonY, 22, 22, mouseX, contentMouseY)) {
                    dispatchCore(SettingsUiAction.adjust(row.id, -1));
                    return;
                }
                if (UiRect.contains(minusX + 86, buttonY, 22, 22, mouseX, contentMouseY)) {
                    dispatchCore(SettingsUiAction.adjust(row.id, 1));
                    return;
                }
                continue;
            }
            if ((row.id.kind == com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind.SIMPLE_TOGGLE
                    || row.id.kind == com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind.HINT_TOGGLE)
                    && UiRect.contains(
                    node.x + SettingsWindowLayout.TOGGLE_ROW_HORIZONTAL_INSET, node.y,
                    node.width - SettingsWindowLayout.TOGGLE_ROW_HORIZONTAL_INSET * 2, node.height,
                    mouseX, contentMouseY)) {
                dispatchCore(SettingsUiAction.setting(SettingsUiAction.Type.TOGGLE_VALUE, row.id));
                return;
            }
        }
    }

    private boolean dispatchCore(SettingsUiAction action) {
        return GearMenuUiAdapter.dispatch(this, screen, controller, action,
                contentX(), contentWidth());
    }

    /** Core 正式目录统一走共享紧凑框体，设置页不再保留私有旧绘制分支。 */
    private void drawCoreSectionHeader(RtsGuiContext g, MinecraftUiCanvas canvas,
                                       int mouseX, int mouseY, int x, int w, int y,
                                       SettingsSectionId id, String titleKey, boolean expanded) {
        boolean hover = UiRect.contains(
                x + SettingsWindowLayout.SECTION_HORIZONTAL_INSET, y,
                w - SettingsWindowLayout.SECTION_HORIZONTAL_INSET * 2,
                SettingsWindowLayout.SECTION_HEADER_H, mouseX, mouseY);
        UiControlAnimationState.Snapshot animation = updateControl(
                this.sectionAnimations, id, true, hover, expanded);
        UiCompactFrameRenderer.frame(canvas,
                new UiRect(x + SettingsWindowLayout.SECTION_HORIZONTAL_INSET, y,
                        w - SettingsWindowLayout.SECTION_HORIZONTAL_INSET * 2,
                        SettingsWindowLayout.SECTION_HEADER_H),
                UiColor.interpolate(SettingsWindowStyle.SECTION_BACKGROUND,
                        SettingsWindowStyle.SECTION_HOVER_BACKGROUND, animation.hover()),
                SettingsWindowStyle.SECTION_BORDER, SettingsWindowStyle.SECTION_DARK_BORDER);
        g.drawString(screen.font(), expanded ? "v" : ">",
                x + SettingsWindowLayout.ROW_TEXT_INSET,
                y + SettingsWindowLayout.SECTION_TEXT_TOP,
                SettingsWindowStyle.VALUE.toArgb(), false);
        g.drawString(screen.font(),
                trimToWidth(text(titleKey),
                        w - SettingsWindowLayout.SECTION_TITLE_RIGHT_RESERVE),
                x + SettingsWindowLayout.SECTION_TITLE_X,
                y + SettingsWindowLayout.SECTION_TEXT_TOP,
                SettingsWindowStyle.VALUE.toArgb(), false);
    }

    private void drawCoreStepButton(RtsGuiContext g, MinecraftUiCanvas canvas,
                                    int mouseX, int mouseY, SettingsId id, boolean plus,
                                    int x, int y, String label) {
        boolean hover = UiRect.contains(x, y, 22, 22, mouseX, mouseY);
        UiControlAnimationState.Snapshot animation = updateControl(
                plus ? this.plusAnimations : this.minusAnimations,
                id, true, hover, false);
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, 22, 22),
                UiColor.interpolate(SettingsWindowStyle.STEP_BACKGROUND,
                        SettingsWindowStyle.STEP_HOVER_BACKGROUND, animation.hover()),
                SettingsWindowStyle.STEP_BORDER, SettingsWindowStyle.STEP_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label,
                x + SettingsWindowLayout.STEP_BUTTON_TEXT_X,
                y + SettingsWindowLayout.STEP_BUTTON_TEXT_TOP,
                SettingsWindowStyle.VALUE.toArgb());
    }

    private void drawCoreToggleButton(RtsGuiContext g, MinecraftUiCanvas canvas,
                                      int mouseX, int mouseY, SettingsId id,
                                      int x, int y, int w, int h,
                                      boolean enabled, boolean active) {
        boolean hover = UiRect.contains(x, y, w, h, mouseX, mouseY);
        UiControlAnimationState.Snapshot animation = updateControl(
                this.toggleAnimations, id, enabled, enabled && hover, active);
        SettingsSwitchTextureRenderer.render(
                g, x, y, animation.selection(), animation.hover());
    }

    private void drawCoreHintExpandButton(RtsGuiContext g, MinecraftUiCanvas canvas,
                                          int mouseX, int mouseY, SettingsId id,
                                          int x, int rowY,
                                          boolean expanded) {
        int buttonX = hintExpandButtonX(x);
        int buttonY = rowY + 12;
        boolean hover = UiRect.contains(buttonX, buttonY,
                SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE,
                SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE, mouseX, mouseY);
        UiControlAnimationState.Snapshot animation = updateControl(
                this.hintAnimations, id, true, hover, expanded);
        UiCompactFrameRenderer.frame(canvas, new UiRect(buttonX, buttonY,
                        SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE,
                        SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE),
                UiColor.interpolate(SettingsWindowStyle.STEP_BACKGROUND,
                        SettingsWindowStyle.STEP_HOVER_BACKGROUND, animation.hover()),
                SettingsWindowStyle.STEP_BORDER, SettingsWindowStyle.STEP_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), expanded ? "v" : ">",
                buttonX + SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE / 2,
                buttonY + 2, SettingsWindowStyle.VALUE.toArgb());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingCoreSensitivity != null && button == 0) {
            dispatchCore(SettingsUiAction.sensitivity(this.draggingCoreSensitivity,
                    calcSensitivityFraction(mouseX, contentX(), contentWidth())));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingCoreSensitivity != null) {
            this.draggingCoreSensitivity = null;
            screen.persistUiState();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private double calcSensitivityFraction(double mouseX, int menuX, int menuW) {
        int trackX = menuX + SettingsWindowLayout.SENSITIVITY_TRACK_INSET;
        int trackW = menuW - SettingsWindowLayout.SENSITIVITY_TRACK_INSET * 2;
        return (mouseX - trackX) / Math.max(1.0D, trackW);
    }

    private String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String trimToWidth(String text, int maxWidth) {
        return RtsClientUiUtil.trimToWidth(screen.font(), text, maxWidth);
    }

    private static <K extends Enum<K>> EnumMap<K, UiControlAnimationState> controlAnimations(
            Class<K> type) {
        EnumMap<K, UiControlAnimationState> animations = new EnumMap<>(type);
        for (K id : type.getEnumConstants()) {
            animations.put(id, new UiControlAnimationState(SystemUiClock.INSTANCE));
        }
        return animations;
    }

    private static <K extends Enum<K>> EnumMap<K, UiValueAnimation> valueAnimations(
            Class<K> type) {
        EnumMap<K, UiValueAnimation> animations = new EnumMap<>(type);
        for (K id : type.getEnumConstants()) {
            animations.put(id, new UiValueAnimation(SystemUiClock.INSTANCE));
        }
        return animations;
    }

    private static <K> UiControlAnimationState.Snapshot updateControl(
            Map<K, UiControlAnimationState> animations,
            K id, boolean enabled, boolean hovered, boolean selected) {
        UiControlAnimationState animation = animations.get(id);
        if (animation == null) {
            throw new IllegalArgumentException("unknown settings control: " + id);
        }
        return animation.update(new UiControlState(
                        true, enabled, hovered, false, false,
                        selected, false, false, enabled ? "" : "disabled"),
                Config.isUiAnimationsEnabled());
    }

    private int maxScroll() {
        SettingsWindowLayout.Layout layout = coreLayout(coreSnapshot());
        return SettingsWindowLayout.maxScroll(layout, contentHeight());
    }

    private void clampScroll() {
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
    }

    private int settingsContentHeight() {
        return coreLayout(coreSnapshot()).contentHeight;
    }

    private void renderScrollbar(RtsGuiContext g, MinecraftUiCanvas canvas, int x, int y, int w, int h) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackX = x + w - SettingsWindowLayout.SCROLLBAR_RIGHT_INSET;
        int trackH = Math.max(1, h);
        canvas.fill(new UiRect(
                        trackX, y + SettingsWindowLayout.SCROLL_TRACK_TOP, 2,
                        Math.max(0, h - SettingsWindowLayout.SCROLL_TRACK_VERTICAL_INSET)),
                SettingsWindowStyle.SCROLL_TRACK);
        int totalH = settingsContentHeight() + SettingsWindowLayout.CONTENT_TOP_PADDING;
        int thumbH = Math.max(18, (int) Math.round(trackH * (trackH / (double) Math.max(trackH, totalH))));
        int thumbY = y + (int) Math.round((trackH - thumbH) * (this.scroll / (double) maxScroll));
        canvas.fill(new UiRect(trackX - 1, thumbY, 4, thumbH),
                SettingsWindowStyle.SCROLL_THUMB);
    }

    private boolean hintCanExpand(int x, int w, String hintKey) {
        return screen.font().width(text(hintKey)) > hintTextMaxWidth(x, w, true);
    }

    private List<FormattedCharSequence> wrappedHintLines(int x, int w, String hintKey) {
        return screen.font().split(Component.translatable(hintKey), hintTextMaxWidth(x, w, true));
    }

    private int hintTextX(int x, boolean hasExpandButton) {
        return x + SettingsWindowLayout.ROW_TEXT_INSET
                + (hasExpandButton ? SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE + 4 : 0);
    }

    private int hintTextMaxWidth(int x, int w, boolean hasExpandButton) {
        int toggleX = x + w - SettingsWindowLayout.TOGGLE_RIGHT_INSET;
        return Math.max(24, toggleX - hintTextX(x, hasExpandButton) - 8);
    }

    private int hintExpandButtonX(int x) {
        return x + SettingsWindowLayout.ROW_TEXT_INSET;
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("settings", this),
            PersistableProperty.boolField(
                    "settings_controls_expanded",
                    state -> state.settings.controlsExpanded,
                    (state, v) -> state.settings.controlsExpanded = v,
                    () -> this.controlsExpanded,
                    v -> this.controlsExpanded = v),
            PersistableProperty.boolField(
                    "settings_display_expanded",
                    state -> state.settings.displayExpanded,
                    (state, v) -> state.settings.displayExpanded = v,
                    () -> this.displayExpanded,
                    v -> this.displayExpanded = v),
            PersistableProperty.boolField(
                    "settings_helpers_expanded",
                    state -> state.settings.helpersExpanded,
                    (state, v) -> state.settings.helpersExpanded = v,
                    () -> this.helpersExpanded,
                    v -> this.helpersExpanded = v),
            PersistableProperty.boolField(
                    "settings_sound_expanded",
                    state -> state.settings.soundExpanded,
                    (state, v) -> state.settings.soundExpanded = v,
                    () -> this.soundExpanded,
                    v -> this.soundExpanded = v),
            PersistableProperty.boolField(
                    "settings_animation_expanded",
                    state -> state.settings.animationExpanded,
                    (state, v) -> state.settings.animationExpanded = v,
                    () -> this.animationExpanded,
                    v -> this.animationExpanded = v)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
