package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * 指南面板。
 * <p>
 * 独立的指南面板组件，处理指南面板的渲染、输入和状态管理。
 * 由 {@link BuilderScreen} 统一调度生命周期。
 */
public final class GuidePanel extends RtsWindowPanel {

    // ── 状态 ──
    private GuideTypes.GuideContext context = GuideTypes.GuideContext.TOP;
    private int page = 0;
    private int topicScroll = 0;
    private int textScroll = 0;
    private int anchorX = -1;
    private int anchorY = -1;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    // ── 输入方法 ──

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, 0.0D, scrollY);
    }

    public boolean keyPressed(int keyCode) {
        return super.keyPressed(keyCode, 0, 0);
    }

    // ── 渲染 ──

    public void render(GuiGraphics g) {
        if (!this.open) {
            return;
        }
        GuideTypes.GuidePanelRect rect = panelRect();
        int panelW = rect.w();
        int panelH = rect.h();
        int x = rect.x();
        int y = rect.y();
        int closeX = x + panelW - 20;

        g.fill(x, y, x + panelW, y + panelH, 0xEE151A21);
        g.hLine(x, x + panelW, y, 0xFF6E7C90);
        g.hLine(x, x + panelW, y + panelH, 0xFF0E1014);
        g.vLine(x, y, y + panelH, 0xFF6E7C90);
        g.vLine(x + panelW, y, y + panelH, 0xFF0E1014);

        g.drawString(screen.font(), title(), x + 12, y + 10, 0xFFFFFF);
        g.fill(closeX, y + 8, closeX + 12, y + 20, 0xAA2C3442);
        g.drawCenteredString(screen.font(), "X", closeX + 6, y + 11, 0xFFFFFF);

        GuideTypes.GuideTopic[] topics = topics();
        this.page = Mth.clamp(this.page, 0, Math.max(0, topics.length - 1));
        int tabX = x + 8;
        int tabY = y + 30;
        int tabW = topicTabWidth();
        int topicAreaH = topicAreaHeight(panelH);
        int visibleTopics = visibleTopicRows(panelH);
        this.topicScroll = Mth.clamp(this.topicScroll, 0, Math.max(0, topics.length - visibleTopics));
        if (this.page < this.topicScroll) {
            this.topicScroll = this.page;
        } else if (this.page >= this.topicScroll + visibleTopics) {
            this.topicScroll = Math.max(0, this.page - visibleTopics + 1);
        }
        int topicEnd = Math.min(topics.length, this.topicScroll + visibleTopics);
        for (int i = this.topicScroll; i < topicEnd; i++) {
            int ty = tabY + (i - this.topicScroll) * 22;
            boolean active = i == this.page;
            int bg = active ? 0xCC355A71 : 0x88303A45;
            RtsClientUiUtil.drawPanelFrame(g, tabX, ty, tabW, 18, bg, active ? 0xFF8FB4D0 : 0xFF4A5665, 0xFF0D1218);
            if (this.context == GuideTypes.GuideContext.BOTTOM) {
                String label = RtsClientUiUtil.trimToWidth(screen.font(), Component.translatable(topics[i].titleKey()).getString(), tabW - 8);
                g.drawString(screen.font(), label, tabX + 4, ty + 5, active ? 0xFFF4FBFF : 0xFFB9C7D5, false);
            } else {
                drawTopicIcon(g, topics[i].icon(), tabX + 10, ty + 9, active ? 0xFFF4FBFF : 0xFFB9C7D5);
            }
        }
        drawVerticalScrollbar(g, tabX + tabW + 3, tabY, topicAreaH, this.topicScroll, topics.length, visibleTopics);

        int textX = x + tabW + 18;
        int lineY = y + 32;
        int maxTextW = textMaxWidth(panelW, tabW);
        GuideTypes.GuideTopic topic = topics[this.page];
        g.drawString(screen.font(), RtsClientUiUtil.trimToWidth(screen.font(), Component.translatable(topic.titleKey()).getString(), maxTextW), textX, lineY, 0xFFE7C46A);
        int bodyTop = lineY + 16;
        int bodyAreaH = textAreaHeight(panelH);
        int visibleTextLines = visibleTextLines(panelH);
        List<FormattedCharSequence> bodyLines = collectTextLines(topic, maxTextW);
        this.textScroll = Mth.clamp(this.textScroll, 0, Math.max(0, bodyLines.size() - visibleTextLines));
        int lineEnd = Math.min(bodyLines.size(), this.textScroll + visibleTextLines);
        screen.enableRtsScissor(g, textX, bodyTop, textX + maxTextW, bodyTop + bodyAreaH);
        try {
            for (int i = this.textScroll; i < lineEnd; i++) {
                g.drawString(screen.font(), bodyLines.get(i), textX, bodyTop + (i - this.textScroll) * 12, 0xE6EDF8);
            }
        } finally {
            g.disableScissor();
        }
        drawVerticalScrollbar(g, x + panelW - 8, bodyTop, bodyAreaH, this.textScroll, bodyLines.size(), visibleTextLines);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuideTypes.GuidePanelRect rect = contentPanelRect();
        GuideTypes.GuideTopic[] topics = topics();
        this.page = Mth.clamp(this.page, 0, Math.max(0, topics.length - 1));

        int tabX = rect.x() + 8;
        int tabY = rect.y() + 8;
        int tabW = topicTabWidth();
        int topicAreaH = topicAreaHeight(rect.h());
        int visibleTopics = visibleTopicRows(rect.h());
        this.topicScroll = Mth.clamp(this.topicScroll, 0, Math.max(0, topics.length - visibleTopics));
        if (this.page < this.topicScroll) {
            this.topicScroll = this.page;
        } else if (this.page >= this.topicScroll + visibleTopics) {
            this.topicScroll = Math.max(0, this.page - visibleTopics + 1);
        }
        int topicEnd = Math.min(topics.length, this.topicScroll + visibleTopics);
        for (int i = this.topicScroll; i < topicEnd; i++) {
            int ty = tabY + (i - this.topicScroll) * 22;
            boolean active = i == this.page;
            int bg = active ? 0xCC355A71 : 0x88303A45;
            RtsClientUiUtil.drawPanelFrame(g, tabX, ty, tabW, 18, bg,
                    active ? 0xFF8FB4D0 : 0xFF4A5665, 0xFF0D1218);
            if (this.context == GuideTypes.GuideContext.BOTTOM) {
                String label = RtsClientUiUtil.trimToWidth(screen.font(),
                        Component.translatable(topics[i].titleKey()).getString(), tabW - 8);
                g.drawString(screen.font(), label, tabX + 4, ty + 5,
                        active ? 0xFFF4FBFF : 0xFFB9C7D5, false);
            } else {
                drawTopicIcon(g, topics[i].icon(), tabX + 10, ty + 9,
                        active ? 0xFFF4FBFF : 0xFFB9C7D5);
            }
        }
        drawVerticalScrollbar(g, tabX + tabW + 3, tabY, topicAreaH,
                this.topicScroll, topics.length, visibleTopics);

        int textX = rect.x() + tabW + 18;
        int lineY = rect.y() + 10;
        int maxTextW = textMaxWidth(rect.w(), tabW);
        GuideTypes.GuideTopic topic = topics[this.page];
        g.drawString(screen.font(),
                RtsClientUiUtil.trimToWidth(screen.font(),
                        Component.translatable(topic.titleKey()).getString(), maxTextW),
                textX, lineY, 0xFFE7C46A, false);

        int bodyTop = lineY + 16;
        int bodyAreaH = textAreaHeight(rect.h());
        int visibleTextLines = visibleTextLines(rect.h());
        List<FormattedCharSequence> bodyLines = collectTextLines(topic, maxTextW);
        this.textScroll = Mth.clamp(this.textScroll, 0, Math.max(0, bodyLines.size() - visibleTextLines));
        int lineEnd = Math.min(bodyLines.size(), this.textScroll + visibleTextLines);
        screen.enableRtsScissor(g, textX, bodyTop, textX + maxTextW, bodyTop + bodyAreaH);
        try {
            for (int i = this.textScroll; i < lineEnd; i++) {
                g.drawString(screen.font(), bodyLines.get(i), textX,
                        bodyTop + (i - this.textScroll) * 12, 0xE6EDF8, false);
            }
        } finally {
            g.disableScissor();
        }
        drawVerticalScrollbar(g, rect.x() + rect.w() - 8, bodyTop, bodyAreaH,
                this.textScroll, bodyLines.size(), visibleTextLines);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        int topic = resolveTopicClickInRect(contentPanelRect(), mouseX, mouseY);
        if (topic >= 0) {
            this.page = topic;
            this.textScroll = 0;
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D) {
            return true;
        }
        GuideTypes.GuidePanelRect rect = contentPanelRect();
        GuideTypes.GuideTopic[] topics = topics();
        int delta = scrollY > 0.0D ? -1 : 1;
        int tabX = rect.x() + 8;
        int tabY = rect.y() + 8;
        int tabW = topicTabWidth();
        if (inside(mouseX, mouseY, tabX, tabY, tabW + 8, topicAreaHeight(rect.h()))) {
            int visible = visibleTopicRows(rect.h());
            this.topicScroll = Mth.clamp(this.topicScroll + delta, 0, Math.max(0, topics.length - visible));
            return true;
        }

        int maxTextW = textMaxWidth(rect.w(), tabW);
        this.page = Mth.clamp(this.page, 0, Math.max(0, topics.length - 1));
        GuideTypes.GuideTopic topic = topics[this.page];
        int visible = visibleTextLines(rect.h());
        int maxScroll = Math.max(0, collectTextLines(topic, maxTextW).size() - visible);
        this.textScroll = Mth.clamp(this.textScroll + delta, 0, maxScroll);
        return true;
    }

    @Override
    protected Component getTitle() {
        return title();
    }

    @Override
    protected int getDefaultWidth() {
        return 330;
    }

    @Override
    protected int getDefaultHeight() {
        return 198;
    }

    @Override
    protected int getMinWindowWidth() {
        return 250;
    }

    @Override
    protected int getMinWindowHeight() {
        return 158;
    }

    @Override
    protected void computeDefaultPosition() {
        GuideTypes.GuidePanelRect rect = openingWindowRect();
        this.windowX = rect.x();
        this.windowY = rect.y();
        this.windowWidth = rect.w();
        this.windowHeight = rect.h();
    }

    // ── 公开查询方法 ──

    public GuideTypes.GuideContext getContext() {
        return this.context;
    }

    public void open(GuideTypes.GuideContext context) {
        open(context, -1, -1);
    }

    public void open(GuideTypes.GuideContext context, int anchorX, int anchorY) {
        this.context = context;
        this.page = 0;
        this.topicScroll = 0;
        this.textScroll = 0;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        GuideTypes.GuidePanelRect rect = openingWindowRect();
        setBounds(rect.x(), rect.y(), rect.w(), rect.h());
        setOpen(true);
    }

    // ── 顶部栏指南提示（由 BuilderScreen 调用） ──

    public void renderTopHint(GuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) {
        if (this.open && this.context == GuideTypes.GuideContext.TOP) {
            return;
        }
        TopBarTypes.TopBarButtonLayout guide = null;
        int nextX = screen.width - 8;
        for (TopBarTypes.TopBarButtonLayout button : topButtons) {
            if (button.id() == TopBarTypes.TopBarButtonId.GUIDE) {
                guide = button;
                continue;
            }
            if (guide != null && button.x() > guide.x()) {
                nextX = Math.min(nextX, button.x());
            }
        }
        if (guide == null) {
            return;
        }
        int hintX = guide.x() + guide.width() + 4;
        int maxW = nextX - hintX - 4;
        if (maxW < 42) {
            return;
        }
        String hint = RtsClientUiUtil.trimToWidth(screen.font(), Component.translatable("screen.rtsbuilding.top_hint.guide").getString(), maxW - 8);
        if (hint.isBlank()) {
            return;
        }
        int y = 12;
        g.drawString(screen.font(), ">", hintX, y, 0xFFE7C46A);
        g.drawString(screen.font(), hint, hintX + 8, y, 0xFFE7C46A);
    }

    // ── 私有方法与辅助 ──

    private Component title() {
        return switch (this.context) {
            case BOTTOM -> Component.translatable("screen.rtsbuilding.guide.bottom.title");
            case SETTINGS -> Component.translatable("screen.rtsbuilding.guide.settings.title");
            default -> Component.translatable("screen.rtsbuilding.guide.top.title");
        };
    }

    private GuideTypes.GuideTopic[] topics() {
        return switch (this.context) {
            case BOTTOM -> new GuideTypes.GuideTopic[]{
                    topic(GuideTypes.GuideIcon.SORT, "screen.rtsbuilding.guide.bottom.sort.title", "screen.rtsbuilding.guide.bottom.sort.1", "screen.rtsbuilding.guide.bottom.sort.2", "screen.rtsbuilding.guide.bottom.sort.3", "screen.rtsbuilding.guide.bottom.sort.4"),
                    topic(GuideTypes.GuideIcon.CRAFT, "screen.rtsbuilding.guide.bottom.remote.title", "screen.rtsbuilding.guide.bottom.remote.1", "screen.rtsbuilding.guide.bottom.remote.2", "screen.rtsbuilding.guide.bottom.remote.3"),
                    topic(GuideTypes.GuideIcon.GRID, "screen.rtsbuilding.guide.bottom.main.title", "screen.rtsbuilding.guide.bottom.main.1", "screen.rtsbuilding.guide.bottom.main.2", "screen.rtsbuilding.guide.bottom.main.3", "screen.rtsbuilding.guide.bottom.main.4"),
                    topic(GuideTypes.GuideIcon.PIN, "screen.rtsbuilding.guide.bottom.recent_pins.title", "screen.rtsbuilding.guide.bottom.recent_pins.1", "screen.rtsbuilding.guide.bottom.recent_pins.2", "screen.rtsbuilding.guide.bottom.recent_pins.3"),
                    topic(GuideTypes.GuideIcon.SEARCH, "screen.rtsbuilding.guide.bottom.craft_panel.title", "screen.rtsbuilding.guide.bottom.craft_panel.1", "screen.rtsbuilding.guide.bottom.craft_panel.2")
            };
            case SETTINGS -> new GuideTypes.GuideTopic[]{
                    topic(GuideTypes.GuideIcon.SLIDER, "screen.rtsbuilding.guide.settings.sensitivity.title", "screen.rtsbuilding.guide.settings.sensitivity.1", "screen.rtsbuilding.guide.settings.sensitivity.2"),
                    topic(GuideTypes.GuideIcon.GRID, "screen.rtsbuilding.guide.settings.ui_scale.title", "screen.rtsbuilding.guide.settings.ui_scale.1", "screen.rtsbuilding.guide.settings.ui_scale.2"),
                    topic(GuideTypes.GuideIcon.TOGGLE, "screen.rtsbuilding.guide.settings.autostore.title", "screen.rtsbuilding.guide.settings.autostore.1", "screen.rtsbuilding.guide.settings.autostore.2"),
                    topic(GuideTypes.GuideIcon.TOGGLE, "screen.rtsbuilding.guide.settings.placed_recovery.title", "screen.rtsbuilding.guide.settings.placed_recovery.1", "screen.rtsbuilding.guide.settings.placed_recovery.2"),
                    topic(GuideTypes.GuideIcon.GEAR, "screen.rtsbuilding.guide.settings.config.title", "screen.rtsbuilding.guide.settings.config.1", "screen.rtsbuilding.guide.settings.config.2")
            };
            default -> new GuideTypes.GuideTopic[]{
                    topic(GuideTypes.GuideIcon.HAND, "screen.rtsbuilding.guide.top.interact.title", "screen.rtsbuilding.guide.top.interact.1", "screen.rtsbuilding.guide.top.interact.2", "screen.rtsbuilding.guide.top.interact.3", "screen.rtsbuilding.guide.top.interact.4"),
                    topic(GuideTypes.GuideIcon.GRID, "screen.rtsbuilding.guide.top.camera.title", "screen.rtsbuilding.guide.top.camera.1", "screen.rtsbuilding.guide.top.camera.2", "screen.rtsbuilding.guide.top.camera.3", "screen.rtsbuilding.guide.top.camera.4"),
                    topic(GuideTypes.GuideIcon.LINK, "screen.rtsbuilding.guide.top.link.title", "screen.rtsbuilding.guide.top.link.1", "screen.rtsbuilding.guide.top.link.2"),
                    topic(GuideTypes.GuideIcon.FUNNEL, "screen.rtsbuilding.guide.top.funnel.title", "screen.rtsbuilding.guide.top.funnel.1", "screen.rtsbuilding.guide.top.funnel.2"),
                    topic(GuideTypes.GuideIcon.ROTATE, "screen.rtsbuilding.guide.top.rotate.title", "screen.rtsbuilding.guide.top.rotate.1"),
                    topic(GuideTypes.GuideIcon.BUILD, "screen.rtsbuilding.guide.top.build.title", "screen.rtsbuilding.guide.top.build.1", "screen.rtsbuilding.guide.top.build.2", "screen.rtsbuilding.guide.top.build.3"),
                    topic(GuideTypes.GuideIcon.PICKAXE, "screen.rtsbuilding.guide.top.ultimine.title", "screen.rtsbuilding.guide.top.ultimine.1", "screen.rtsbuilding.guide.top.ultimine.2"),
                    topic(GuideTypes.GuideIcon.GRID, "screen.rtsbuilding.guide.top.chunk.title", "screen.rtsbuilding.guide.top.chunk.1")
            };
        };
    }

    private static GuideTypes.GuideTopic topic(GuideTypes.GuideIcon icon, String titleKey, String... lineKeys) {
        return new GuideTypes.GuideTopic(icon, titleKey, lineKeys);
    }

    private int getPageCount() {
        return topics().length;
    }

    private GuideTypes.GuidePanelRect contentPanelRect() {
        return new GuideTypes.GuidePanelRect(contentX(), contentY(), contentWidth(), contentHeight());
    }

    private GuideTypes.GuidePanelRect openingWindowRect() {
        int panelW = Math.min(getDefaultWidth(), Math.max(getMinWindowWidth(), screen.width - 28));
        int panelH = Math.min(getDefaultHeight(), Math.max(getMinWindowHeight(), screen.height - 90));
        return panelRect(panelW, panelH);
    }

    private GuideTypes.GuidePanelRect panelRect() {
        int panelW = Math.min(330, Math.max(250, screen.width - 28));
        int panelH = Math.min(178, Math.max(138, screen.height - 90));
        return panelRect(panelW, panelH);
    }

    private GuideTypes.GuidePanelRect panelRect(int panelW, int panelH) {
        int x;
        int y;
        if (this.context == GuideTypes.GuideContext.BOTTOM) {
            if (hasAnchor()) {
                x = clampPanelX(this.anchorX - panelW + 20, panelW);
                y = clampPanelY(this.anchorY - panelH - 8, panelH);
            } else {
                x = Math.max(8, screen.width - panelW - 8);
                y = Math.max(TOP_H + 6, getBottomY() - panelH - 6);
            }
        } else if (this.context == GuideTypes.GuideContext.SETTINGS) {
            int settingsW = Math.min(300, screen.width - 24);
            int settingsX = (screen.width - settingsW) / 2;
            int settingsY = (screen.height - GEAR_MENU_H) / 2;
            int gap = 6;
            int leftSpace = Math.max(0, settingsX - 8 - gap);
            int rightSpace = Math.max(0, screen.width - (settingsX + settingsW) - 8 - gap);
            if (leftSpace >= 230 || rightSpace >= 230) {
                boolean useLeft = leftSpace >= rightSpace;
                panelW = Math.min(330, useLeft ? leftSpace : rightSpace);
                x = useLeft ? settingsX - gap - panelW : settingsX + settingsW + gap;
                y = Mth.clamp(settingsY, 8, Math.max(8, screen.height - panelH - 8));
            } else {
                panelW = Math.min(330, Math.max(220, screen.width - 16));
                x = Math.max(8, (screen.width - panelW) / 2);
                int belowY = settingsY + GEAR_MENU_H + gap;
                y = belowY + panelH <= screen.height - 8
                        ? belowY
                        : Math.max(8, settingsY - panelH - gap);
            }
        } else {
            if (hasAnchor()) {
                x = clampPanelX(this.anchorX - panelW / 2, panelW);
                y = clampPanelY(this.anchorY + 8, panelH);
            } else {
                x = 8;
                y = TOP_H + 6;
            }
        }
        return new GuideTypes.GuidePanelRect(x, y, panelW, panelH);
    }

    private int getBottomY() {
        return screen.height - BuilderScreenConstants.DEFAULT_BOTTOM_H;
    }

    private boolean hasAnchor() {
        return this.anchorX >= 0 && this.anchorY >= 0;
    }

    private int clampPanelX(int x, int panelW) {
        return Mth.clamp(x, 8, Math.max(8, screen.width - panelW - 8));
    }

    private int clampPanelY(int y, int panelH) {
        int minY = TOP_H + 6;
        return Mth.clamp(y, minY, Math.max(minY, screen.height - panelH - 8));
    }

    private int resolveTopicClick(double mouseX, double mouseY) {
        GuideTypes.GuidePanelRect rect = panelRect();
        return resolveTopicClickInRect(rect, mouseX, mouseY);
    }

    private int resolveTopicClickInRect(GuideTypes.GuidePanelRect rect, double mouseX, double mouseY) {
        GuideTypes.GuideTopic[] topics = topics();
        int tabX = rect.x() + 8;
        int tabY = rect.y() + 8;
        int tabW = topicTabWidth();
        int visible = visibleTopicRows(rect.h());
        int end = Math.min(topics.length, this.topicScroll + visible);
        for (int i = this.topicScroll; i < end; i++) {
            if (inside(mouseX, mouseY, tabX, tabY + (i - this.topicScroll) * 22, tabW, 18)) {
                return i;
            }
        }
        return -1;
    }

    private int topicTabWidth() {
        return this.context == GuideTypes.GuideContext.BOTTOM ? 92 : 20;
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        GuideTypes.GuidePanelRect rect = panelRect();
        return inside(mouseX, mouseY, rect.x(), rect.y(), rect.w(), rect.h());
    }

    private boolean isInsideClose(double mouseX, double mouseY) {
        GuideTypes.GuidePanelRect rect = panelRect();
        int closeX = rect.x() + rect.w() - 20;
        return inside(mouseX, mouseY, closeX, rect.y() + 8, 12, 12);
    }

    private boolean isInsidePrev(double mouseX, double mouseY) {
        return false;
    }

    private boolean isInsideNext(double mouseX, double mouseY) {
        return false;
    }

    private int topicAreaHeight(int panelH) {
        return Math.max(18, panelH - 42);
    }

    private int visibleTopicRows(int panelH) {
        return Math.max(1, topicAreaHeight(panelH) / 22);
    }

    private int textAreaHeight(int panelH) {
        return Math.max(24, panelH - 62);
    }

    private int textMaxWidth(int panelW, int tabW) {
        return Math.max(48, panelW - tabW - 42);
    }

    private int visibleTextLines(int panelH) {
        return Math.max(1, textAreaHeight(panelH) / 12);
    }

    private List<FormattedCharSequence> collectTextLines(GuideTypes.GuideTopic topic, int maxTextW) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String key : topic.lineKeys()) {
            lines.addAll(screen.font().split(Component.translatable(key), maxTextW));
        }
        return lines;
    }

    private void drawVerticalScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int total, int visible) {
        if (total <= visible || h <= 0) {
            return;
        }
        int trackW = 3;
        int knobH = Math.max(10, h * visible / Math.max(visible + 1, total));
        int maxScroll = Math.max(1, total - visible);
        int knobY = y + (h - knobH) * Mth.clamp(scroll, 0, maxScroll) / maxScroll;
        g.fill(x, y, x + trackW, y + h, 0x55303A45);
        g.fill(x, knobY, x + trackW, knobY + knobH, 0xCC8FB4D0);
    }

    private void drawTopicIcon(GuiGraphics g, GuideTypes.GuideIcon icon, int cx, int cy, int color) {
        switch (icon) {
            case HAND -> drawGuideTextureIcon(g, TOPBAR_INTERACT_ACTIVE, cx, cy);
            case LINK -> drawGuideTextureIcon(g, TOPBAR_LINK_ACTIVE, cx, cy);
            case FUNNEL -> drawGuideTextureIcon(g, TOPBAR_FUNNEL_ACTIVE, cx, cy);
            case ROTATE -> drawGuideTextureIcon(g, TOPBAR_ROTATE_ACTIVE, cx, cy);
            case BUILD -> drawGuideTextureIcon(g, TOPBAR_QUICK_BUILD_ACTIVE, cx, cy);
            case PICKAXE -> drawGuideTextureIcon(g, TOPBAR_ULTIMINE_ACTIVE, cx, cy);
            case GRID -> drawGuideTextureIcon(g, TOPBAR_CHUNK_VIEW_ACTIVE, cx, cy);
            case SEARCH -> {
                g.fill(cx - 6, cy - 6, cx + 2, cy - 4, color);
                g.fill(cx - 6, cy + 1, cx + 2, cy + 3, color);
                g.fill(cx - 6, cy - 6, cx - 4, cy + 3, color);
                g.fill(cx + 1, cy - 6, cx + 3, cy + 3, color);
                g.fill(cx + 3, cy + 3, cx + 7, cy + 7, color);
            }
            case SORT -> {
                g.fill(cx - 7, cy - 7, cx - 2, cy - 5, color);
                g.fill(cx - 7, cy - 1, cx + 2, cy + 1, color);
                g.fill(cx - 7, cy + 5, cx + 7, cy + 7, color);
                g.fill(cx + 5, cy - 7, cx + 7, cy - 3, color);
                g.fill(cx + 3, cy - 4, cx + 9, cy - 2, color);
                g.fill(cx + 5, cy + 2, cx + 7, cy + 7, color);
                g.fill(cx + 3, cy + 1, cx + 9, cy + 3, color);
            }
            case CLOCK -> {
                g.fill(cx - 6, cy - 6, cx + 6, cy + 6, 0x331B222C);
                g.hLine(cx - 4, cx + 4, cy - 6, color);
                g.hLine(cx - 4, cx + 4, cy + 6, color);
                g.vLine(cx - 6, cy - 4, cy + 4, color);
                g.vLine(cx + 6, cy - 4, cy + 4, color);
                g.fill(cx, cy - 4, cx + 2, cy + 1, color);
                g.fill(cx, cy, cx + 5, cy + 2, color);
            }
            case DROPLET -> {
                g.fill(cx - 2, cy - 7, cx + 2, cy - 4, color);
                g.fill(cx - 5, cy - 3, cx + 5, cy + 5, color);
                g.fill(cx - 3, cy + 5, cx + 3, cy + 8, color);
            }
            case PIN -> {
                g.fill(cx - 4, cy - 7, cx + 4, cy - 5, color);
                g.fill(cx - 2, cy - 5, cx + 2, cy + 2, color);
                g.fill(cx - 5, cy + 1, cx + 5, cy + 3, color);
                g.fill(cx, cy + 3, cx + 1, cy + 8, color);
            }
            case CRAFT -> {
                g.fill(cx - 7, cy - 7, cx + 7, cy + 7, color);
                g.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFF1B222C);
                g.fill(cx - 1, cy - 7, cx + 1, cy + 7, 0xFF1B222C);
                g.fill(cx - 7, cy - 1, cx + 7, cy + 1, 0xFF1B222C);
            }
            case SLIDER -> {
                g.fill(cx - 7, cy - 4, cx + 7, cy - 2, color);
                g.fill(cx - 7, cy + 4, cx + 7, cy + 6, color);
                g.fill(cx - 2, cy - 7, cx + 2, cy + 1, color);
                g.fill(cx + 3, cy + 1, cx + 7, cy + 8, color);
            }
            case TOGGLE -> {
                g.fill(cx - 8, cy - 4, cx + 8, cy + 4, color);
                g.fill(cx + 1, cy - 7, cx + 7, cy + 7, 0xFF1B222C);
            }
            case GEAR -> TopBarIconRenderer.renderIcon(TopBarTypes.TopBarButtonId.GEAR, g, cx, cy, color, false, null);
        }
    }

    private void drawGuideTextureIcon(GuiGraphics g, ResourceLocation texture, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(cx - 9, cy - 9, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.blit(texture, 0, 0, 0, 0, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H);
        g.pose().popPose();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
