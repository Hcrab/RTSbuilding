package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.JadeOverlayLayout;
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
import net.minecraft.client.gui.Gui;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentTranslation;
import org.lwjgl.opengl.GL11;

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
    protected void renderContent(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
                String label = trimToWidth(
                        I18n.format(topics[i].titleKey),
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
                trimToWidth(I18n.format(topic.titleKey), maxTextW),
                textX, lineY, GuideWindowStyle.TITLE_TEXT.toArgb(), false);

        int bodyTop = (int) geometry.body.getY();
        int bodyAreaH = (int) geometry.body.getHeight();
        int visibleTextLines = geometry.visibleTextLines;
        List<String> bodyLines = collectTextLines(topic, maxTextW);
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
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
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
    protected IChatComponent getTitle() {
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

    public void renderTopHint(LegacyGuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) {
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
        String hint = trimToWidth(I18n.format("screen.rtsbuilding.top_hint.guide"), maxW - 8);
        if (hint.trim().isEmpty()) {
            return;
        }
        int y = 12;
        g.drawString(screen.font(), ">", hintX, y, GuideWindowStyle.HINT_TEXT.toArgb(), false);
        g.drawString(screen.font(), hint, hintX + 8, y,
                GuideWindowStyle.HINT_TEXT.toArgb(), false);
    }

    private IChatComponent title() {
        if (this.context == GuideUiContext.TOP) {
            return new ChatComponentTranslation("screen.rtsbuilding.ai_help.title");
        }
        return new ChatComponentTranslation(GuideUiCatalog.titleKey(this.context));
    }

    private void renderAiHelp(LegacyGuiGraphics g, int mouseX, int mouseY) {
        int x = contentX() + 10;
        int y = contentY() + 9;
        int w = Math.max(80, contentWidth() - 20);
        String description = trimToWidth(I18n.format("screen.rtsbuilding.ai_help.description"), w);
        g.drawString(screen.font(), description, x, y,
                GuideWindowStyle.BODY_TEXT.toArgb(), false);

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);
        int buttonY = contentY() + 29;
        drawAiHelpButton(canvas, x, buttonY, w, 22, mouseX, mouseY,
                I18n.format("screen.rtsbuilding.ai_help.chat"));
        drawAiHelpButton(canvas, x, buttonY + 26, w, 22, mouseX, mouseY,
                I18n.format("screen.rtsbuilding.ai_help.copy"));
        drawAiHelpButton(canvas, x, buttonY + 52, w, 22, mouseX, mouseY,
                I18n.format("screen.rtsbuilding.ai_help.website"));
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
            if (screen.getMinecraft().thePlayer != null) {
                com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(screen.getMinecraft().thePlayer, new ChatComponentTranslation(copied
                        ? "message.rtsbuilding.ai_help.copied"
                        : "message.rtsbuilding.ai_help.copy_failed"), true);
            }
        } else if (UiRect.contains(x, buttonY + 52, w, 22, mouseX, mouseY)) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(RtsCommunityLinks.WEBSITE));
            } catch (Exception ignored) {
                // 旧版启动器或无桌面环境可能不支持打开浏览器；按钮仍保持离线求助入口可用。
            }
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
            String label) {
        boolean hovered = UiRect.contains(x, y, w, h, mouseX, mouseY);
        WindowButtonChromeRenderer.renderSolid(canvas, new UiRect(x, y, w, h), hovered);
        String text = trimToWidth(label, w - AI_HELP_BUTTON_TEXT_INSET);
        canvas.text(text,
                x + Math.max(5, (w - screen.font().getStringWidth(text)) / 2),
                y + (h - screen.font().FONT_HEIGHT) / 2,
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

    private List<String> collectTextLines(GuideUiTopic topic, int maxTextW) {
        List<String> lines = new ArrayList<>();
        for (String key : topic.lineKeys) {
            lines.addAll(screen.font().listFormattedStringToWidth(I18n.format(key), maxTextW));
        }
        return lines;
    }

    private void syncFromCore(GuideUiState state) {
        this.context = state.context;
        this.page = state.page;
        this.topicScroll = state.topicScroll;
        this.textScroll = state.textScroll;
    }

    private void drawTopicIcon(LegacyGuiGraphics g, GuideUiIcon icon, int cx, int cy, UiColor color) {
        GuideIconTextures.Entry entry = GuideIconTextures.entry(icon);
        if (entry.tinted()) {
            GlStateManager.color(
                    color.red() / 255.0F,
                    color.green() / 255.0F,
                    color.blue() / 255.0F,
                    color.alpha() / 255.0F);
            blit(entry.texture(), cx - 9, cy - 9, 18, 18);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            drawGuideTextureIcon(g, entry.texture(), cx, cy);
        }
    }

    private void drawGuideTextureIcon(LegacyGuiGraphics g, ResourceLocation texture, int cx, int cy) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx - 9, cy - 9, 0.0F);
        GlStateManager.scale(0.75F, 0.75F, 1.0F);
        blit(texture, 0, 0, TOP_BUTTON_H, TOP_BUTTON_H);
        GlStateManager.popMatrix();
    }

    private final List<PersistableProperty> properties = java.util.Collections.singletonList(
            PersistableProperty.bounds("guide", this));

    private void blit(ResourceLocation texture, int x, int y, int width, int height) {
        this.screen.getMinecraft().getTextureManager().bindTexture(texture);
        com.rtsbuilding.rtsbuilding.platform.client.GuiCompat.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F,
                width, height, width, height);
    }

    private String trimToWidth(String text, int maxWidth) {
        String safe = text == null ? "" : text;
        if (this.screen.font().getStringWidth(safe) <= maxWidth) return safe;
        String suffix = "...";
        int width = Math.max(0, maxWidth - this.screen.font().getStringWidth(suffix));
        return this.screen.font().trimStringToWidth(safe, width) + suffix;
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
