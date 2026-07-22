package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarIconRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.JadeOverlayLayout;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiAction;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiIcon;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiState;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiTopic;
import com.rtsbuilding.rtsbuilding.uikit.layout.GuideWindowLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 顶栏、底栏和设置入口共用的上下文指南窗口。
 *
 * <p>正式主题目录、选页和滚动约束由 Java 8 Core 持有；本类只负责 Minecraft 字体换行、
 * 图标绘制、{@link RtsWindowPanel} chrome 和持久化窗口边界，不再维护另一份主题清单。
 */
public final class GuidePanel extends RtsWindowPanel {
    private GuideUiContext context = GuideUiContext.TOP;
    private int page = 0;
    private int topicScroll = 0;
    private int textScroll = 0;
    private int anchorX = -1;
    private int anchorY = -1;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuideWindowLayout.Rect rect = contentRect();
        GuideUiTopic[] topics = topics();

        int tabX = rect.x + GuideWindowLayout.CONTENT_PAD;
        int tabY = rect.y + GuideWindowLayout.CONTENT_PAD;
        int tabW = topicTabWidth();
        int topicAreaH = topicAreaHeight(rect.h);
        int visibleTopics = visibleTopicRows(rect.h);
        syncFromCore(new GuideUiState(this.context, this.page, this.topicScroll, this.textScroll,
                visibleTopics, Integer.MAX_VALUE, visibleTextLines(rect.h)));
        int topicEnd = Math.min(topics.length, this.topicScroll + visibleTopics);
        for (int i = this.topicScroll; i < topicEnd; i++) {
            int ty = tabY + (i - this.topicScroll) * 22;
            boolean active = i == this.page;
            int bg = active ? 0xCC355A71 : 0x88303A45;
            RtsClientUiUtil.drawPanelFrame(g, tabX, ty, tabW, 18, bg,
                    active ? 0xFF8FB4D0 : 0xFF4A5665, 0xFF0D1218);
            if (this.context == GuideUiContext.BOTTOM) {
                String label = RtsClientUiUtil.trimToWidth(screen.font(),
                        Component.translatable(topics[i].titleKey).getString(), tabW - 8);
                g.drawString(screen.font(), label, tabX + 4, ty + 5,
                        active ? 0xFFF4FBFF : 0xFFB9C7D5, false);
            } else {
                drawTopicIcon(g, topics[i].icon, tabX + 10, ty + 9,
                        active ? 0xFFF4FBFF : 0xFFB9C7D5);
            }
        }
        drawVerticalScrollbar(g, tabX + tabW + 3, tabY, topicAreaH,
                this.topicScroll, topics.length, visibleTopics);

        int textX = rect.x + tabW + 18;
        int lineY = rect.y + 10;
        int maxTextW = textMaxWidth(rect.w, tabW);
        GuideUiTopic topic = topics[this.page];
        g.drawString(screen.font(),
                RtsClientUiUtil.trimToWidth(screen.font(),
                        Component.translatable(topic.titleKey).getString(), maxTextW),
                textX, lineY, 0xFFE7C46A, false);

        int bodyTop = lineY + 16;
        int bodyAreaH = textAreaHeight(rect.h);
        int visibleTextLines = visibleTextLines(rect.h);
        List<FormattedCharSequence> bodyLines = collectTextLines(topic, maxTextW);
        syncFromCore(new GuideUiState(this.context, this.page, this.topicScroll, this.textScroll,
                visibleTopics, bodyLines.size(), visibleTextLines));
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
        drawVerticalScrollbar(g, rect.x + rect.w - 8, bodyTop, bodyAreaH,
                this.textScroll, bodyLines.size(), visibleTextLines);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        int topic = resolveTopicClick(mouseX, mouseY);
        if (topic >= 0) {
            GuideWindowLayout.Rect rect = contentRect();
            syncFromCore(GuideUiReducer.apply(new GuideUiState(this.context, this.page,
                            this.topicScroll, this.textScroll, visibleTopicRows(rect.h),
                            Integer.MAX_VALUE, visibleTextLines(rect.h)),
                    new GuideUiAction(GuideUiAction.Type.SELECT_TOPIC, topic)));
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D) {
            return true;
        }
        GuideWindowLayout.Rect rect = contentRect();
        if (!inside(mouseX, mouseY, rect.x, rect.y, rect.w, rect.h)) {
            return true;
        }

        GuideUiTopic[] topics = topics();
        int delta = scrollY > 0.0D ? -1 : 1;
        int tabX = rect.x + GuideWindowLayout.CONTENT_PAD;
        int tabY = rect.y + GuideWindowLayout.CONTENT_PAD;
        int tabW = topicTabWidth();
        if (inside(mouseX, mouseY, tabX, tabY, tabW + 8, topicAreaHeight(rect.h))) {
            int visible = visibleTopicRows(rect.h);
            syncFromCore(GuideUiReducer.apply(new GuideUiState(this.context, this.page,
                            this.topicScroll, this.textScroll, visible, Integer.MAX_VALUE,
                            visibleTextLines(rect.h)),
                    new GuideUiAction(GuideUiAction.Type.SCROLL_TOPICS, delta)));
            return true;
        }

        int maxTextW = textMaxWidth(rect.w, tabW);
        GuideUiTopic topic = topics[this.page];
        int visible = visibleTextLines(rect.h);
        int totalLines = collectTextLines(topic, maxTextW).size();
        syncFromCore(GuideUiReducer.apply(new GuideUiState(this.context, this.page,
                        this.topicScroll, this.textScroll, visibleTopicRows(rect.h),
                        totalLines, visible),
                new GuideUiAction(GuideUiAction.Type.SCROLL_TEXT, delta)));
        return true;
    }

    @Override
    protected Component getTitle() {
        return title();
    }

    @Override
    protected int getDefaultWidth() {
        return GuideWindowLayout.DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return GuideWindowLayout.DEFAULT_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return GuideWindowLayout.MIN_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return GuideWindowLayout.MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = 8;
        this.windowY = TOP_H + 6;
    }

    public GuideUiContext getContext() {
        return this.context;
    }

    public void open(GuideUiContext context) {
        open(context, -1, -1);
    }

    public void open(GuideUiContext context, int anchorX, int anchorY) {
        this.context = context;
        this.page = 0;
        this.topicScroll = 0;
        this.textScroll = 0;
        this.anchorX = anchorX;
        this.anchorY = anchorY;

        if (!hasUserBoundsPreference()) {
            int panelW = Math.min(GuideWindowLayout.DEFAULT_W,
                    Math.max(GuideWindowLayout.MIN_W, this.screen.width - 28));
            int panelH = Math.min(GuideWindowLayout.DEFAULT_H,
                    Math.max(GuideWindowLayout.MIN_H, this.screen.height - 90));
            GuideWindowLayout.Rect rect = openingWindowRect(panelW, panelH);
            setTransientBounds(rect.x, rect.y, rect.w, rect.h);
        }
        setOpen(true);
        markBroughtToFront();
    }

    public void renderTopHint(GuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) {
        if (this.open && this.context == GuideUiContext.TOP) {
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
        int jadeLeftX = JadeOverlayLayout.currentReservedLeftVirtualX();
        if (jadeLeftX >= 0) {
            nextX = Math.min(nextX, jadeLeftX);
        }
        if (guide == null) {
            return;
        }
        int hintX = guide.x() + guide.width() + 4;
        int maxW = nextX - hintX - 4;
        if (maxW < 42) {
            return;
        }
        String hint = RtsClientUiUtil.trimToWidth(screen.font(),
                Component.translatable("screen.rtsbuilding.top_hint.guide").getString(), maxW - 8);
        if (hint.isBlank()) {
            return;
        }
        int y = 12;
        g.drawString(screen.font(), ">", hintX, y, 0xFFE7C46A, false);
        g.drawString(screen.font(), hint, hintX + 8, y, 0xFFE7C46A, false);
    }

    private Component title() {
        return Component.translatable(GuideUiCatalog.titleKey(this.context));
    }

    private GuideUiTopic[] topics() {
        return GuideUiCatalog.topics(this.context);
    }

    private GuideWindowLayout.Rect contentRect() {
        return new GuideWindowLayout.Rect(contentX(), contentY(), contentWidth(), contentHeight());
    }

    private GuideWindowLayout.Rect openingWindowRect(int panelW, int panelH) {
        int x;
        int y;
        if (this.context == GuideUiContext.BOTTOM) {
            if (hasAnchor()) {
                x = clampPanelX(this.anchorX - panelW + 20, panelW);
                y = clampPanelY(this.anchorY - panelH - 8, panelH);
            } else {
                x = Math.max(8, screen.width - panelW - 8);
                y = Math.max(TOP_H + 6, getBottomY() - panelH - 6);
            }
        } else if (this.context == GuideUiContext.SETTINGS) {
            int settingsW = Math.min(300, screen.width - 24);
            int settingsX = (screen.width - settingsW) / 2;
            int settingsY = (screen.height - GEAR_MENU_H) / 2;
            int gap = 6;
            int leftSpace = Math.max(0, settingsX - 8 - gap);
            int rightSpace = Math.max(0, screen.width - (settingsX + settingsW) - 8 - gap);
            if (leftSpace >= 230 || rightSpace >= 230) {
                boolean useLeft = leftSpace >= rightSpace;
                panelW = Math.min(GuideWindowLayout.DEFAULT_W, useLeft ? leftSpace : rightSpace);
                x = useLeft ? settingsX - gap - panelW : settingsX + settingsW + gap;
                y = Mth.clamp(settingsY, 8, Math.max(8, screen.height - panelH - 8));
            } else {
                panelW = Math.min(GuideWindowLayout.DEFAULT_W, Math.max(220, screen.width - 16));
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
        return new GuideWindowLayout.Rect(x, y, panelW, panelH);
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
        GuideWindowLayout.Rect rect = contentRect();
        GuideUiTopic[] topics = topics();
        int tabX = rect.x + GuideWindowLayout.CONTENT_PAD;
        int tabY = rect.y + GuideWindowLayout.CONTENT_PAD;
        int tabW = topicTabWidth();
        int visible = visibleTopicRows(rect.h);
        int end = Math.min(topics.length, this.topicScroll + visible);
        for (int i = this.topicScroll; i < end; i++) {
            if (inside(mouseX, mouseY, tabX, tabY + (i - this.topicScroll) * 22, tabW, 18)) {
                return i;
            }
        }
        return -1;
    }

    private int topicTabWidth() {
        return GuideWindowLayout.topicTabWidth(this.context == GuideUiContext.BOTTOM);
    }

    private int topicAreaHeight(int panelH) {
        return GuideWindowLayout.topicAreaHeight(panelH);
    }

    private int visibleTopicRows(int panelH) {
        return GuideWindowLayout.visibleTopicRows(panelH);
    }

    private int textAreaHeight(int panelH) {
        return GuideWindowLayout.textAreaHeight(panelH);
    }

    private int textMaxWidth(int panelW, int tabW) {
        return GuideWindowLayout.textMaxWidth(panelW, tabW);
    }

    private int visibleTextLines(int panelH) {
        return GuideWindowLayout.visibleTextLines(panelH);
    }

    private List<FormattedCharSequence> collectTextLines(GuideUiTopic topic, int maxTextW) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String key : topic.lineKeys) {
            lines.addAll(screen.font().split(Component.translatable(key), maxTextW));
        }
        return lines;
    }

    private void syncFromCore(GuideUiState state) {
        this.context = state.context;
        this.page = state.page;
        this.topicScroll = state.topicScroll;
        this.textScroll = state.textScroll;
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

    private void drawTopicIcon(GuiGraphics g, GuideUiIcon icon, int cx, int cy, int color) {
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
            case GEAR -> TopBarIconRenderer.renderIcon(
                    TopBarTypes.TopBarButtonId.GEAR, g, cx, cy, color, false, null);
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

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("guide", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
