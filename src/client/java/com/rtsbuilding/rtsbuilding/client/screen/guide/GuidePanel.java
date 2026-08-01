package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.JadeOverlayLayout;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiAction;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiIcon;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiState;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiTopic;
import com.rtsbuilding.rtsbuilding.uikit.canvas.GuideWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowButtonChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.GuideWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.GuideWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowButtonStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.util.FormattedCharSequence;

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
    private static final int AI_HELP_DEFAULT_W = 440;
    private static final int AI_HELP_DEFAULT_H = 132;
    private static final int AI_HELP_MIN_W = 300;
    private static final int AI_HELP_MIN_H = 120;
    private static final int AI_HELP_SCREEN_HORIZONTAL_MARGIN = 28;
    private static final int AI_HELP_SCREEN_VERTICAL_MARGIN = 90;
    private static final int AI_HELP_BUTTON_TEXT_INSET = 10;
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
        if (this.context == GuideUiContext.TOP) {
            renderAiHelp(g, mouseX, mouseY);
            return;
        }
        GuideWindowLayout.Geometry geometry = contentGeometry();
        GuideUiTopic[] topics = topics();
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);

        int tabW = geometry.topicTabWidth;
        int visibleTopics = geometry.visibleTopicRows;
        syncFromCore(new GuideUiState(this.context, this.page, this.topicScroll, this.textScroll,
                visibleTopics, Integer.MAX_VALUE, geometry.visibleTextLines));
        int topicEnd = Math.min(topics.length, this.topicScroll + visibleTopics);
        for (int i = this.topicScroll; i < topicEnd; i++) {
            UiRect row = geometry.topicRow(i, this.topicScroll);
            int tabX = (int) row.getX();
            int ty = (int) row.getY();
            boolean active = i == this.page;
            GuideWindowChromeRenderer.renderTopic(canvas, row, active);
            if (this.context == GuideUiContext.BOTTOM) {
                String label = RtsClientUiUtil.trimToWidth(screen.font(),
                        Component.translatable(topics[i].titleKey).getString(),
                        tabW - GuideWindowLayout.TOPIC_LABEL_HORIZONTAL_PAD);
                g.drawString(screen.font(), label,
                        tabX + GuideWindowLayout.TOPIC_LABEL_INSET_X,
                        ty + GuideWindowLayout.TOPIC_LABEL_TEXT_Y,
                        GuideWindowStyle.topicContent(active).toArgb(), false);
            } else {
                drawTopicIcon(g, topics[i].icon,
                        tabX + GuideWindowLayout.TOPIC_ICON_CENTER_X,
                        ty + GuideWindowLayout.TOPIC_ICON_CENTER_Y,
                        GuideWindowStyle.topicContent(active));
            }
        }
        GuideWindowChromeRenderer.renderScrollbar(canvas, geometry.topicScrollbar,
                this.topicScroll, topics.length, visibleTopics);

        int textX = (int) geometry.title.getX();
        int lineY = (int) geometry.title.getY();
        int maxTextW = (int) geometry.title.getWidth();
        GuideUiTopic topic = topics[this.page];
        g.drawString(screen.font(),
                RtsClientUiUtil.trimToWidth(screen.font(),
                        Component.translatable(topic.titleKey).getString(), maxTextW),
                textX, lineY, GuideWindowStyle.TITLE_TEXT.toArgb(), false);

        int bodyTop = (int) geometry.body.getY();
        int bodyAreaH = (int) geometry.body.getHeight();
        int visibleTextLines = geometry.visibleTextLines;
        List<FormattedCharSequence> bodyLines = collectTextLines(topic, maxTextW);
        syncFromCore(new GuideUiState(this.context, this.page, this.topicScroll, this.textScroll,
                visibleTopics, bodyLines.size(), visibleTextLines));
        int lineEnd = Math.min(bodyLines.size(), this.textScroll + visibleTextLines);
        screen.enableRtsScissor(g, textX, bodyTop, textX + maxTextW, bodyTop + bodyAreaH);
        try {
            for (int i = this.textScroll; i < lineEnd; i++) {
                g.drawString(screen.font(), bodyLines.get(i), textX,
                        bodyTop + (i - this.textScroll) * GuideWindowLayout.BODY_LINE_H,
                        GuideWindowStyle.BODY_TEXT.toArgb(), false);
            }
        } finally {
            g.disableScissor();
        }
        GuideWindowChromeRenderer.renderScrollbar(canvas, geometry.bodyScrollbar,
                this.textScroll, bodyLines.size(), visibleTextLines);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (this.context == GuideUiContext.TOP) {
            handleAiHelpClick(mouseX, mouseY);
            return;
        }
        int topic = resolveTopicClick(mouseX, mouseY);
        if (topic >= 0) {
            GuideWindowLayout.Geometry geometry = contentGeometry();
            syncFromCore(GuideUiReducer.apply(new GuideUiState(this.context, this.page,
                            this.topicScroll, this.textScroll, geometry.visibleTopicRows,
                            Integer.MAX_VALUE, geometry.visibleTextLines),
                    new GuideUiAction(GuideUiAction.Type.SELECT_TOPIC, topic)));
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.context == GuideUiContext.TOP) {
            return true;
        }
        if (scrollY == 0.0D) {
            return true;
        }
        GuideWindowLayout.Geometry geometry = contentGeometry();
        GuideUiTopic[] topics = topics();
        GuideWindowLayout.Hit hit = GuideWindowLayout.hitAt(
                geometry, mouseX, mouseY, this.topicScroll, topics.length);
        if (hit.target == GuideWindowLayout.Target.NONE) {
            return true;
        }

        int delta = scrollY > 0.0D ? -1 : 1;
        if (hit.target == GuideWindowLayout.Target.TOPIC
                || hit.target == GuideWindowLayout.Target.TOPIC_SCROLL) {
            int visible = geometry.visibleTopicRows;
            syncFromCore(GuideUiReducer.apply(new GuideUiState(this.context, this.page,
                            this.topicScroll, this.textScroll, visible, Integer.MAX_VALUE,
                            geometry.visibleTextLines),
                    new GuideUiAction(GuideUiAction.Type.SCROLL_TOPICS, delta)));
            return true;
        }

        int maxTextW = (int) geometry.title.getWidth();
        GuideUiTopic topic = topics[this.page];
        int visible = geometry.visibleTextLines;
        int totalLines = collectTextLines(topic, maxTextW).size();
        syncFromCore(GuideUiReducer.apply(new GuideUiState(this.context, this.page,
                        this.topicScroll, this.textScroll, geometry.visibleTopicRows,
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
        return this.context == GuideUiContext.TOP
                ? AI_HELP_DEFAULT_W
                : GuideWindowLayout.DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return this.context == GuideUiContext.TOP
                ? AI_HELP_DEFAULT_H
                : GuideWindowLayout.DEFAULT_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return this.context == GuideUiContext.TOP
                ? AI_HELP_MIN_W
                : GuideWindowLayout.MIN_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return this.context == GuideUiContext.TOP
                ? AI_HELP_MIN_H
                : GuideWindowLayout.MIN_H;
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

        if (context == GuideUiContext.TOP || !hasUserBoundsPreference()) {
            int panelW = context == GuideUiContext.TOP
                    ? Math.min(AI_HELP_DEFAULT_W,
                            Math.max(AI_HELP_MIN_W,
                                    this.screen.width - AI_HELP_SCREEN_HORIZONTAL_MARGIN))
                    : GuideWindowLayout.openingWidth(this.screen.width);
            int panelH = context == GuideUiContext.TOP
                    ? Math.min(AI_HELP_DEFAULT_H,
                            Math.max(AI_HELP_MIN_H,
                                    this.screen.height - AI_HELP_SCREEN_VERTICAL_MARGIN))
                    : GuideWindowLayout.openingHeight(this.screen.height);
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
        int nextX = screen.width - GuideWindowLayout.EDGE_MARGIN;
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
        g.drawString(screen.font(), ">", hintX, y, GuideWindowStyle.HINT_TEXT.toArgb(), false);
        g.drawString(screen.font(), hint, hintX + 8, y,
                GuideWindowStyle.HINT_TEXT.toArgb(), false);
    }

    private Component title() {
        if (this.context == GuideUiContext.TOP) {
            return Component.translatable("screen.rtsbuilding.ai_help.title");
        }
        return Component.translatable(GuideUiCatalog.titleKey(this.context));
    }

    private void renderAiHelp(GuiGraphics g, int mouseX, int mouseY) {
        int x = contentX() + 10;
        int y = contentY() + 9;
        int w = Math.max(80, contentWidth() - 20);
        String description = RtsClientUiUtil.trimToWidth(
                screen.font(),
                Component.translatable("screen.rtsbuilding.ai_help.description").getString(),
                w);
        g.drawString(screen.font(), description, x, y,
                GuideWindowStyle.BODY_TEXT.toArgb(), false);

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);
        int buttonY = contentY() + 29;
        drawAiHelpButton(canvas, x, buttonY, w, 22, mouseX, mouseY,
                Component.translatable("screen.rtsbuilding.ai_help.chat"));
        drawAiHelpButton(canvas, x, buttonY + 26, w, 22, mouseX, mouseY,
                Component.translatable("screen.rtsbuilding.ai_help.copy"));
        drawAiHelpButton(canvas, x, buttonY + 52, w, 22, mouseX, mouseY,
                Component.translatable("screen.rtsbuilding.ai_help.website"));
    }

    private void handleAiHelpClick(double mouseX, double mouseY) {
        int x = contentX() + 10;
        int w = Math.max(80, contentWidth() - 20);
        int buttonY = contentY() + 29;
        if (UiRect.contains(x, buttonY, w, 22, mouseX, mouseY)) {
            close();
            screen.openAiChat();
        } else if (UiRect.contains(x, buttonY + 26, w, 22, mouseX, mouseY)) {
            boolean copied = RtsAiHelpClipboard.copy(this.controller);
            if (screen.getMinecraft().player != null) {
                screen.getMinecraft().player.displayClientMessage(Component.translatable(copied
                        ? "message.rtsbuilding.ai_help.copied"
                        : "message.rtsbuilding.ai_help.copy_failed"), true);
            }
        } else if (UiRect.contains(x, buttonY + 52, w, 22, mouseX, mouseY)) {
            Util.getPlatform().openUri(RtsCommunityLinks.WEBSITE);
        }
    }

    private void drawAiHelpButton(
            MinecraftUiCanvas canvas,
            int x,
            int y,
            int w,
            int h,
            int mouseX,
            int mouseY,
            Component label) {
        boolean hovered = UiRect.contains(x, y, w, h, mouseX, mouseY);
        WindowButtonChromeRenderer.renderSolid(canvas, new UiRect(x, y, w, h), hovered);
        String text = RtsClientUiUtil.trimToWidth(
                screen.font(), label.getString(), w - AI_HELP_BUTTON_TEXT_INSET);
        canvas.text(text,
                x + Math.max(5, (w - screen.font().width(text)) / 2),
                y + (h - screen.font().lineHeight) / 2,
                WindowButtonStyle.TEXT);
    }

    private GuideUiTopic[] topics() {
        return GuideUiCatalog.topics(this.context);
    }

    private GuideWindowLayout.Geometry contentGeometry() {
        return GuideWindowLayout.geometry(
                new UiRect(contentX(), contentY(), contentWidth(), contentHeight()),
                this.context == GuideUiContext.BOTTOM);
    }

    private GuideWindowLayout.Rect openingWindowRect(int panelW, int panelH) {
        return GuideWindowLayout.openingRect(this.context,
                screen.width, screen.height, panelW, panelH,
                this.anchorX, this.anchorY, TOP_H, getBottomY(), GEAR_MENU_H);
    }

    private int getBottomY() {
        return screen.height - BuilderScreenConstants.DEFAULT_BOTTOM_H;
    }

    private int resolveTopicClick(double mouseX, double mouseY) {
        GuideWindowLayout.Geometry geometry = contentGeometry();
        GuideUiTopic[] topics = topics();
        GuideWindowLayout.Hit hit = GuideWindowLayout.hitAt(
                geometry, mouseX, mouseY, this.topicScroll, topics.length);
        return hit.target == GuideWindowLayout.Target.TOPIC ? hit.topicIndex : -1;
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

    private void drawTopicIcon(GuiGraphics g, GuideUiIcon icon, int cx, int cy, UiColor color) {
        GuideIconTextures.Entry entry = GuideIconTextures.entry(icon);
        if (entry.tinted()) {
            RenderSystem.setShaderColor(
                    color.red() / 255.0F,
                    color.green() / 255.0F,
                    color.blue() / 255.0F,
                    color.alpha() / 255.0F);
            g.blit(entry.texture(), cx - 9, cy - 9, 0, 0, 18, 18, 18, 18);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            drawGuideTextureIcon(g, entry.texture(), cx, cy);
        }
    }

    private void drawGuideTextureIcon(GuiGraphics g, ResourceLocation texture, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(cx - 9, cy - 9, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.blit(texture, 0, 0, 0, 0, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H);
        g.pose().popPose();
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("guide", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
