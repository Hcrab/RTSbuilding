package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRequestToken;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView;
import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.StandaloneScreenStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模组设置入口：个人偏好和当前世界规则分域展示。
 *
 * <p>个人域只在保存时写入已有 CLIENT/偏好来源；世界域只通过 typed 网络门面提交。
 * 页面生命周期拥有草稿，但不拥有服务器权限、revision 或落盘逻辑。</p>
 */
public final class RtsModConfigScreen extends Screen {
    private static final int CONTENT_MAX_W = 720;
    private static final int HEADER_H = 56;
    private static final int FOOTER_H = 42;
    private static final int OPTION_ROW_H = RtsModConfigLayout.OPTION_ROW_H;
    private static final int GROUP_H = RtsModConfigLayout.GROUP_H;
    private static final int GROUP_GAP = RtsModConfigLayout.GROUP_GAP;
    private static final int SCROLL_STEP = 24;

    private final Screen parent;
    private Page page = Page.PERSONAL;
    private final EnumMap<RtsClientPersonalSettings.Key, Boolean> personalDraft =
            RtsClientPersonalSettings.snapshot();
    private final EnumMap<RtsClientPersonalSettings.Key, Boolean> personalBaseline =
            new EnumMap<>(personalDraft);
    private RtsServerConfigView latestServerView = RtsServerConfigView.defaults().readOnly();
    private RtsServerConfigDraft worldDraft = RtsServerConfigDraft.from(latestServerView);
    private long observedSessionId;
    private RtsServerConfigRequestToken pendingSave;
    private Button saveButton;
    private boolean initialized;
    private int scroll;
    private String statusKey = "screen.rtsbuilding.settings.status.personal_ready";
    private final EnumMap<RtsClientPersonalSettings.Key, Button> personalButtons =
            new EnumMap<>(RtsClientPersonalSettings.Key.class);
    private final EnumMap<RtsServerConfigField, EditBox> worldBoxes =
            new EnumMap<>(RtsServerConfigField.class);

    private enum Page { PERSONAL, WORLD }

    public RtsModConfigScreen(Screen parent) {
        super(Component.translatable("screen.rtsbuilding.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!this.initialized) {
            this.initialized = true;
            observeServerBeforeBuild();
        } else {
            // resize/reinit 只重建几何；同一连接的草稿和在途保存请求必须继续存在。
            captureVisibleDrafts();
        }
        rebuildConfigWidgets(false);
    }

    @Override
    public void tick() {
        super.tick();
        observeServerState();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPageBackground(g);
        drawCentered(g, this.title, this.width / 2, 8, StandaloneScreenStyle.TITLE_TEXT.toArgb());
        drawStatus(g);
        drawContent(g);
        drawScrollbar(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (insideViewport(mouseX, mouseY)) {
            int next = Mth.clamp(this.scroll - (int) Math.signum(scrollY) * SCROLL_STEP, 0, maxScroll());
            if (next != this.scroll) {
                captureVisibleDrafts();
                setFocused(null);
                this.scroll = next;
                rebuildConfigWidgets(false);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeWithoutSaving();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeWithoutSaving();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void observeServerBeforeBuild() {
        if (this.minecraft == null || this.minecraft.level == null) {
            this.latestServerView = RtsServerConfigView.defaults().readOnly();
            this.worldDraft.accept(this.latestServerView);
            this.statusKey = "screen.rtsbuilding.settings.status.no_world";
            return;
        }
        this.observedSessionId = RtsClientPacketGateway.serverConfigSessionId();
        RtsServerConfigRequestToken query = RtsClientPacketGateway.requestServerConfig();
        this.observedSessionId = query.sessionId();
        this.latestServerView = RtsClientPacketGateway.currentServerConfig();
        this.worldDraft.accept(this.latestServerView);
        this.statusKey = this.latestServerView.worldLoaded()
                ? statusForPermission(this.latestServerView)
                : "screen.rtsbuilding.settings.status.loading";
    }

    /** 只同步服务端最新展示值；有草稿时不得改写输入框。 */
    private void observeServerState() {
        long currentSession = RtsClientPacketGateway.serverConfigSessionId();
        if (this.minecraft == null || this.minecraft.level == null) {
            if (this.observedSessionId != 0L || this.latestServerView.worldLoaded()) {
                this.observedSessionId = 0L;
                this.pendingSave = null;
                this.latestServerView = RtsServerConfigView.defaults().readOnly();
                this.worldDraft.accept(this.latestServerView);
                this.statusKey = "screen.rtsbuilding.settings.status.no_world";
                rebuildConfigWidgets(false);
            }
            return;
        }
        if (currentSession == 0L) {
            RtsServerConfigRequestToken query = RtsClientPacketGateway.requestServerConfig();
            currentSession = query.sessionId();
        }
        if (currentSession != this.observedSessionId) {
            this.observedSessionId = currentSession;
            this.pendingSave = null;
            this.latestServerView = RtsClientPacketGateway.currentServerConfig();
            this.worldDraft.accept(this.latestServerView);
            this.statusKey = this.latestServerView.worldLoaded()
                    ? statusForPermission(this.latestServerView)
                    : "screen.rtsbuilding.settings.status.loading";
            rebuildConfigWidgets(false);
            return;
        }
        RtsServerConfigView incoming = RtsClientPacketGateway.currentServerConfig();
        if (!incoming.equals(this.latestServerView)) {
            captureVisibleDrafts();
            this.latestServerView = incoming;
            if (this.pendingSave == null && !this.worldDraft.isDirty()) {
                this.worldDraft.accept(incoming);
            }
            if (this.pendingSave == null && !incoming.worldLoaded()) {
                this.statusKey = "screen.rtsbuilding.settings.status.loading";
            } else if (this.pendingSave == null && !incoming.editable()) {
                this.statusKey = "screen.rtsbuilding.settings.status.read_only";
            }
            rebuildConfigWidgets(false);
        }
        acceptPendingResultIfPresent();
    }

    private void acceptPendingResultIfPresent() {
        if (this.pendingSave == null) return;
        if (RtsClientPacketGateway.lastServerConfigResultSessionId() != this.pendingSave.sessionId()
                || RtsClientPacketGateway.lastServerConfigResultRequestId() != this.pendingSave.requestId()) {
            return;
        }
        RtsServerConfigUpdateResult result = RtsClientPacketGateway.lastServerConfigResult();
        if (result == null) return;
        this.pendingSave = null;
        // 广播可能先于 ACK 到达；精确 ACK 只确认本次请求，不回退已经看到的高 revision。
        RtsServerConfigView currentView = RtsClientPacketGateway.currentServerConfig();
        this.latestServerView = currentView.revision() >= result.view().revision() ? currentView : result.view();
        if (result.succeeded()) {
            this.worldDraft.accept(this.latestServerView);
            this.statusKey = result.status() == RtsServerConfigUpdateResult.Status.NO_CHANGE
                    ? "screen.rtsbuilding.settings.status.no_change"
                    : "screen.rtsbuilding.settings.status.world_saved";
        } else {
            // 失败结果只更新服务端最新展示与权限；草稿和 baseline 保持原样。
            this.statusKey = statusForResult(result.status());
        }
        rebuildConfigWidgets(false);
    }

    private void rebuildConfigWidgets(boolean captureDrafts) {
        if (captureDrafts) captureVisibleDrafts();
        clearWidgets();
        this.personalButtons.clear();
        this.worldBoxes.clear();
        this.saveButton = null;
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        addTabButtons();
        if (this.page == Page.PERSONAL) addPersonalWidgets();
        else addWorldWidgets();
        addFooterButtons();
    }

    private void addTabButtons() {
        int tabWidth = Math.min(156, Math.max(112, (this.width - 28) / 2));
        int gap = 8;
        int startX = (this.width - tabWidth * 2 - gap) / 2;
        Button personal = addRenderableWidget(Button.builder(
                Component.translatable("screen.rtsbuilding.settings.personal"), b -> switchPage(Page.PERSONAL))
                .bounds(startX, 30, tabWidth, 20).build());
        Button world = addRenderableWidget(Button.builder(
                Component.translatable("screen.rtsbuilding.settings.world"), b -> switchPage(Page.WORLD))
                .bounds(startX + tabWidth + gap, 30, tabWidth, 20).build());
        personal.active = this.page != Page.PERSONAL;
        world.active = this.page != Page.WORLD;
    }

    private void switchPage(Page next) {
        captureVisibleDrafts();
        this.page = next;
        this.scroll = 0;
        this.statusKey = next == Page.PERSONAL
                ? "screen.rtsbuilding.settings.status.personal_ready"
                : (this.minecraft == null || this.minecraft.level == null
                ? "screen.rtsbuilding.settings.status.no_world"
                : statusForServer(this.latestServerView));
        rebuildConfigWidgets(false);
    }

    private void addPersonalWidgets() {
        int y = viewportTop() - this.scroll + GROUP_H;
        int x = contentX();
        int width = contentWidth();
        int controlX = x + width - controlWidth(width) - 10;
        for (RtsClientPersonalSettings.Key key : RtsClientPersonalSettings.Key.values()) {
            if (fullyVisible(y, OPTION_ROW_H)) {
                boolean value = Boolean.TRUE.equals(this.personalDraft.get(key));
                Button button = addRenderableWidget(Button.builder(
                        Component.translatable(value ? "screen.rtsbuilding.settings.enabled" : "screen.rtsbuilding.settings.disabled"),
                        b -> {
                            captureVisibleDrafts();
                            this.personalDraft.put(key, !Boolean.TRUE.equals(this.personalDraft.get(key)));
                            rebuildConfigWidgets(false);
                        }).bounds(controlX, y + 10, controlWidth(width), 20).build());
                this.personalButtons.put(key, button);
            }
            y += OPTION_ROW_H;
        }
    }

    private void addWorldWidgets() {
        if (this.minecraft == null || this.minecraft.level == null) return;
        int x = contentX();
        int width = contentWidth();
        int controlW = controlWidth(width);
        int controlX = x + width - controlW - 10;
        RtsModConfigLayout.WorldLayout layout = worldLayout();
        for (RtsModConfigLayout.GroupLayout group : layout.groups) {
            for (RtsModConfigLayout.RowLayout row : group.rows) {
                if (fullyVisible(row.y, OPTION_ROW_H)) {
                    addWorldControl(row.field, controlX, row.y, controlW);
                }
            }
        }
    }

    private void addWorldControl(RtsServerConfigField field, int x, int y, int width) {
        boolean editable = canEditWorld() && this.pendingSave == null;
        Component tooltip = Component.translatable(field.hintKey());
        if (field.inputKind == RtsServerConfigField.InputKind.BOOLEAN) {
            boolean value = Boolean.parseBoolean(this.worldDraft.text(field.key));
            Button button = addRenderableWidget(Button.builder(
                    Component.translatable(value ? "screen.rtsbuilding.settings.enabled" : "screen.rtsbuilding.settings.disabled"),
                    b -> {
                        captureVisibleDrafts();
                        this.worldDraft.setText(field.key, Boolean.toString(!Boolean.parseBoolean(this.worldDraft.text(field.key))));
                        rebuildConfigWidgets(false);
                    }).bounds(x, y + 10, width, 20).build());
            button.active = editable;
            button.setTooltip(Tooltip.create(editable ? tooltip : Component.translatable(
                    this.pendingSave == null ? "screen.rtsbuilding.settings.status.read_only" : "screen.rtsbuilding.settings.status.pending")));
            return;
        }
        if (field.inputKind == RtsServerConfigField.InputKind.HARVEST_TIER) {
            String raw = this.worldDraft.text(field.key);
            String tier = raw == null ? RangeMiningHarvestTier.UNLIMITED.name() : raw.toUpperCase(Locale.ROOT);
            Button button = addRenderableWidget(Button.builder(
                    Component.translatable("screen.rtsbuilding.world.tier." + tier.toLowerCase(Locale.ROOT)),
                    b -> {
                        captureVisibleDrafts();
                        RangeMiningHarvestTier current = RangeMiningHarvestTier.valueOf(this.worldDraft.text(field.key));
                        this.worldDraft.setText(field.key, current.next().name());
                        rebuildConfigWidgets(false);
                    }).bounds(x, y + 10, width, 20).build());
            button.active = editable;
            button.setTooltip(Tooltip.create(editable ? tooltip : Component.translatable(
                    this.pendingSave == null ? "screen.rtsbuilding.settings.status.read_only" : "screen.rtsbuilding.settings.status.pending")));
            return;
        }
        EditBox box = new EditBox(this.font, x, y + 10, width, 20,
                Component.translatable(field.labelKey()));
        box.setMaxLength(field.inputKind == RtsServerConfigField.InputKind.DECIMAL ? 24 : 11);
        box.setValue(this.worldDraft.text(field.key));
        box.setResponder(value -> {
            this.worldDraft.setText(field.key, value);
            updateSaveButtonState();
        });
        box.setEditable(editable);
        box.setTextColor(StandaloneScreenStyle.TITLE_TEXT.toArgb());
        box.setTextColorUneditable(StandaloneScreenStyle.INFO_EMPTY.toArgb());
        box.setTooltip(Tooltip.create(editable ? tooltip : Component.translatable(
                this.pendingSave == null ? "screen.rtsbuilding.settings.status.read_only" : "screen.rtsbuilding.settings.status.pending")));
        addRenderableWidget(box);
        this.worldBoxes.put(field, box);
    }

    private void addFooterButtons() {
        int buttonW = Math.min(132, Math.max(76, (this.width - 28) / 3));
        int gap = 8;
        int total = buttonW * 2 + gap;
        int startX = (this.width - total) / 2;
        Button save = addRenderableWidget(Button.builder(
                Component.translatable(this.page == Page.PERSONAL
                        ? "screen.rtsbuilding.settings.save_personal"
                        : "screen.rtsbuilding.settings.save_world"),
                b -> {
                    if (this.page == Page.PERSONAL) {
                        savePersonal();
                    } else {
                        saveWorld();
                    }
                })
                .bounds(startX, this.height - 30, buttonW, 20).build());
        this.saveButton = save;
        updateSaveButtonState();
        addRenderableWidget(Button.builder(Component.translatable("screen.rtsbuilding.settings.cancel"),
                b -> closeWithoutSaving()).bounds(startX + buttonW + gap, this.height - 30, buttonW, 20).build());
    }

    private void savePersonal() {
        captureVisibleDrafts();
        if (!hasPersonalChanges()) {
            this.statusKey = "screen.rtsbuilding.settings.status.no_change";
            rebuildConfigWidgets(false);
            return;
        }
        try {
            RtsClientPersonalSettings.writeChanged(this.personalDraft, this.personalBaseline);
            this.personalBaseline.clear();
            this.personalBaseline.putAll(this.personalDraft);
            this.statusKey = "screen.rtsbuilding.settings.status.personal_saved";
        } catch (RuntimeException failure) {
            // 已写入的个人字段仍来自既有 CLIENT 来源；未完成字段继续保留草稿。
            this.statusKey = "screen.rtsbuilding.settings.status.personal_failed";
        }
        rebuildConfigWidgets(false);
    }

    private void saveWorld() {
        captureVisibleDrafts();
        if (this.pendingSave != null) return;
        if (this.minecraft == null || this.minecraft.level == null) {
            this.statusKey = "screen.rtsbuilding.settings.status.no_world";
            rebuildConfigWidgets(false);
            return;
        }
        if (!this.latestServerView.worldLoaded()) {
            this.statusKey = "screen.rtsbuilding.settings.status.loading";
            rebuildConfigWidgets(false);
            return;
        }
        if (!this.latestServerView.editable()) {
            this.statusKey = "screen.rtsbuilding.settings.status.read_only";
            rebuildConfigWidgets(false);
            return;
        }
        final List<RtsServerConfigChange> changes;
        try {
            changes = this.worldDraft.changes();
            String validationError = this.worldDraft.validationError();
            if (validationError != null) {
                this.statusKey = "screen.rtsbuilding.settings.status.invalid";
                rebuildConfigWidgets(false);
                return;
            }
        } catch (IllegalArgumentException invalidInput) {
            this.statusKey = "screen.rtsbuilding.settings.status.invalid";
            rebuildConfigWidgets(false);
            return;
        }
        if (changes.isEmpty()) {
            this.statusKey = "screen.rtsbuilding.settings.status.no_change";
            rebuildConfigWidgets(false);
            return;
        }
        try {
            this.pendingSave = RtsClientPacketGateway.saveServerConfig(
                    this.worldDraft.baselineRevision(), changes);
            this.statusKey = "screen.rtsbuilding.settings.status.pending";
        } catch (RuntimeException sendFailure) {
            this.statusKey = "screen.rtsbuilding.settings.status.save_failed";
        }
        rebuildConfigWidgets(false);
    }

    private void closeWithoutSaving() {
        captureVisibleDrafts();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    private void captureVisibleDrafts() {
        for (Map.Entry<RtsServerConfigField, EditBox> entry : this.worldBoxes.entrySet()) {
            this.worldDraft.setText(entry.getKey().key, entry.getValue().getValue());
        }
        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        if (this.saveButton == null) return;
        this.saveButton.active = this.page == Page.PERSONAL
                ? hasPersonalChanges()
                : worldSaveEnabled(this.latestServerView,
                this.minecraft != null && this.minecraft.level != null,
                this.pendingSave != null, this.worldDraft.isDirty());
    }

    /** 供屏幕按钮和纯行为测试共用的保存资格判断。 */
    static boolean worldSaveEnabled(
            RtsServerConfigView view, boolean hasWorld, boolean pending, boolean dirty) {
        return worldSaveEnabled(hasWorld,
                view != null && view.worldLoaded(),
                view != null && view.editable(), pending, dirty);
    }

    static boolean worldSaveEnabled(
            boolean hasWorld, boolean worldLoaded, boolean editable,
            boolean pending, boolean dirty) {
        return hasWorld && worldLoaded && editable && !pending && dirty;
    }

    private RtsModConfigLayout.WorldLayout worldLayout() {
        return RtsModConfigLayout.world(viewportTop() - this.scroll);
    }

    private boolean hasPersonalChanges() {
        for (RtsClientPersonalSettings.Key key : RtsClientPersonalSettings.Key.values()) {
            if (Boolean.TRUE.equals(this.personalDraft.get(key))
                    != Boolean.TRUE.equals(this.personalBaseline.get(key))) return true;
        }
        return false;
    }

    private boolean canEditWorld() {
        return this.latestServerView.worldLoaded() && this.latestServerView.editable();
    }

    private String statusForPermission(RtsServerConfigView view) {
        return view.editable() ? "screen.rtsbuilding.settings.status.ready"
                : "screen.rtsbuilding.settings.status.read_only";
    }

    private String statusForServer(RtsServerConfigView view) {
        return view.worldLoaded() ? statusForPermission(view)
                : "screen.rtsbuilding.settings.status.loading";
    }

    private String statusForResult(RtsServerConfigUpdateResult.Status status) {
        return switch (status) {
            case NOT_EDITABLE -> "screen.rtsbuilding.settings.status.read_only";
            case REVISION_CONFLICT -> "screen.rtsbuilding.settings.status.conflict";
            case INVALID -> "screen.rtsbuilding.settings.status.invalid";
            case SAVE_FAILED -> "screen.rtsbuilding.settings.status.save_failed";
            case APPLIED -> "screen.rtsbuilding.settings.status.world_saved";
            case NO_CHANGE -> "screen.rtsbuilding.settings.status.no_change";
        };
    }

    private void drawContent(GuiGraphics g) {
        int x = contentX();
        int y = viewportTop() - this.scroll;
        int width = contentWidth();
        g.enableScissor(x, viewportTop(), x + width, viewportBottom());
        if (this.page == Page.PERSONAL) {
            drawGroup(g, x, y, width, Component.translatable("screen.rtsbuilding.settings.personal_group"),
                    Component.translatable("screen.rtsbuilding.settings.personal_group.hint"));
            y += GROUP_H;
            for (RtsClientPersonalSettings.Key key : RtsClientPersonalSettings.Key.values()) {
                drawRow(g, x, y, width, Component.translatable(key.labelKey()),
                        Component.translatable(key.hintKey()));
                y += OPTION_ROW_H;
            }
        } else {
            if (this.minecraft == null || this.minecraft.level == null) {
                drawNoShadow(g, Component.translatable("screen.rtsbuilding.settings.status.no_world"),
                        x + 10, y + 8, StandaloneScreenStyle.INFO_VALUE.toArgb());
                g.disableScissor();
                return;
            }
            for (RtsModConfigLayout.GroupLayout group : worldLayout().groups) {
                drawGroup(g, x, group.headerY, width,
                        Component.translatable(group.group.titleKey()),
                        Component.translatable(group.group.hintKey()));
                for (RtsModConfigLayout.RowLayout row : group.rows) {
                    drawRow(g, x, row.y, width,
                            Component.translatable(row.field.labelKey()),
                            Component.translatable(row.field.hintKey()));
                }
            }
        }
        g.disableScissor();
    }

    private void drawStatus(GuiGraphics g) {
        Component status = Component.translatable(this.statusKey);
        String text = this.font.plainSubstrByWidth(status.getString(), Math.max(20, this.width - 20));
        drawNoShadow(g, Component.literal(text), 10, HEADER_H + 4,
                StandaloneScreenStyle.INFO_LABEL.toArgb());
    }

    private void drawGroup(GuiGraphics g, int x, int y, int width, Component title, Component hint) {
        drawNoShadow(g, title, x + 2, y + 3, StandaloneScreenStyle.SECTION_TEXT.toArgb());
        String text = this.font.plainSubstrByWidth(hint.getString(), Math.max(20, width - 180));
        drawNoShadow(g, Component.literal(text), x + Math.max(120, width - this.font.width(text) - 8),
                y + 3, StandaloneScreenStyle.INFO_LABEL.toArgb());
        g.hLine(x, x + width, y + GROUP_H - 1, StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
    }

    private void drawRow(GuiGraphics g, int x, int y, int width, Component label, Component hint) {
        int controlW = controlWidth(width);
        int textW = Math.max(24, width - controlW - 34);
        g.fill(x, y, x + width, y + OPTION_ROW_H - 2,
                StandaloneScreenStyle.INFO_ROW_BACKGROUND.toArgb());
        g.hLine(x, x + width, y, StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
        String labelText = this.font.plainSubstrByWidth(label.getString(), textW);
        String hintText = this.font.plainSubstrByWidth(hint.getString(), textW);
        drawNoShadow(g, Component.literal(labelText), x + 10, y + 7,
                StandaloneScreenStyle.INFO_VALUE.toArgb());
        drawNoShadow(g, Component.literal(hintText), x + 10, y + 22,
                StandaloneScreenStyle.INFO_LABEL.toArgb());
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        int viewportH = viewportHeight();
        int contentH = contentHeight();
        if (max <= 0 || viewportH <= 0 || contentH <= 0) return;
        int x = contentX() + contentWidth() - 4;
        int y = viewportTop();
        int thumbH = Math.max(18, viewportH * viewportH / contentH);
        int thumbY = y + (viewportH - thumbH) * this.scroll / max;
        g.fill(x, y, x + 3, y + viewportH, StandaloneScreenStyle.SCROLLBAR_TRACK.toArgb());
        g.fill(x, thumbY, x + 3, thumbY + thumbH, StandaloneScreenStyle.INFO_LABEL.toArgb());
    }

    private int contentHeight() {
        if (this.page == Page.PERSONAL) {
            return RtsModConfigLayout.personalContentHeight(
                    RtsClientPersonalSettings.Key.values().length);
        }
        if (this.minecraft == null || this.minecraft.level == null) return GROUP_H + OPTION_ROW_H;
        return RtsModConfigLayout.world(0).contentHeight;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewportHeight());
    }

    private int contentWidth() {
        return Math.max(140, Math.min(CONTENT_MAX_W, this.width - 24));
    }

    private int contentX() {
        return (this.width - contentWidth()) / 2;
    }

    private int viewportTop() {
        return HEADER_H + 16;
    }

    private int viewportBottom() {
        return Math.max(viewportTop(), this.height - FOOTER_H - 8);
    }

    private int viewportHeight() {
        return Math.max(0, viewportBottom() - viewportTop());
    }

    private int controlWidth(int width) {
        return Math.min(180, Math.max(100, width / 3));
    }

    private boolean fullyVisible(int y, int height) {
        return y >= viewportTop() && y + height <= viewportBottom();
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return UiRect.contains(contentX(), viewportTop(), contentWidth(), viewportHeight(), mouseX, mouseY);
    }

    private void renderPageBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, StandaloneScreenStyle.PAGE_BACKGROUND.toArgb());
        g.fill(0, 0, this.width, HEADER_H, StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        g.fill(0, this.height - FOOTER_H, this.width, this.height,
                StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        g.hLine(0, this.width, HEADER_H, StandaloneScreenStyle.BAR_DIVIDER.toArgb());
        g.hLine(0, this.width, this.height - FOOTER_H, StandaloneScreenStyle.BAR_DIVIDER.toArgb());
    }

    private void drawCentered(GuiGraphics g, Component text, int centerX, int y, int color) {
        drawNoShadow(g, text, centerX - this.font.width(text) / 2, y, color);
    }

    private void drawNoShadow(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(this.font, text, x, y, color, false);
    }
}
