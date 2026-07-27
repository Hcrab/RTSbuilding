package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowButtonChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.AiChatStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowButtonStyle;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 可拖动、可缩放的游戏内 AI 求助窗口。
 *
 * <p>本类只协调窗口输入和可见消息；教程装配、十轮会话截断与 HTTPS/SSE 请求分别交给
 * 独立组件。刷新会清空上下文并让仍在返回的旧请求失效。
 */
public final class RtsAiChatPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = 520;
    private static final int DEFAULT_H = 330;
    private static final int MIN_W = 360;
    private static final int MIN_H = 220;
    private static final int PAD = 9;
    private static final int BUTTON_H = 22;
    private static final int INPUT_H = 22;
    private static final int LIMIT_TEXT_Y_OFFSET = 7;
    private static final int TEXT_HORIZONTAL_INSET = 8;
    private static final int TRANSCRIPT_CLIP_X_INSET = 4;
    private static final int TRANSCRIPT_CLIP_Y_INSET = 3;
    private static final int TRANSCRIPT_TEXT_X_INSET = 5;

    private RtsAiChatSession session;
    private WindowTextBox input;
    private int scrollLines;

    public RtsAiChatPanel() {
        this.resizable = true;
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.session = new RtsAiChatSession(controller);
        this.input = new WindowTextBox(screen.font(), 0, 0, 100, INPUT_H);
        this.input.setMaxLength(500);
        this.input.setPlaceholder(I18n.format("screen.rtsbuilding.ai_chat.placeholder"));
    }

    public void open() {
        setOpen(true);
        markBroughtToFront();
        if (this.input != null) {
            this.input.setFocused(true);
        }
    }

    @Override
    protected void renderContent(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = contentX() + PAD;
        int y = contentY() + PAD;
        int w = Math.max(80, contentWidth() - PAD * 2);
        int footerY = contentY() + contentHeight() - PAD - INPUT_H;
        int refreshW = 70;

        drawButton(g, x + w - refreshW, y, refreshW, BUTTON_H, mouseX, mouseY,
                I18n.format("screen.rtsbuilding.ai_chat.refresh"), false);
        String limit = I18n.format("screen.rtsbuilding.ai_chat.turns",
                this.session.exchangeCount(), RtsAiConversation.MAX_EXCHANGES);
        g.drawString(screen.font(), limit, x, y + LIMIT_TEXT_Y_OFFSET,
                AiChatStyle.LIMIT_TEXT.toArgb(), false);

        int transcriptTop = y + BUTTON_H + 6;
        int transcriptBottom = footerY - 7;
        int transcriptH = Math.max(30, transcriptBottom - transcriptTop);
        List<RenderLine> lines = buildRenderLines(w - TEXT_HORIZONTAL_INSET);
        int visible = Math.max(1, transcriptH / 12);
        this.scrollLines = Math.max(0, Math.min(this.scrollLines, Math.max(0, lines.size() - visible)));
        int first = Math.max(0, lines.size() - visible - this.scrollLines);
        int end = Math.min(lines.size(), first + visible);

        g.fill(x, transcriptTop, x + w, transcriptBottom,
                AiChatStyle.TRANSCRIPT_BACKGROUND.toArgb());
        screen.enableRtsScissor(
                g,
                x + TRANSCRIPT_CLIP_X_INSET,
                transcriptTop + TRANSCRIPT_CLIP_Y_INSET,
                x + w - TRANSCRIPT_CLIP_X_INSET,
                transcriptBottom - TRANSCRIPT_CLIP_Y_INSET);
        try {
            int lineY = transcriptTop + 5;
            for (int i = first; i < end; i++) {
                RenderLine line = lines.get(i);
                g.drawString(screen.font(), line.text(), x + TRANSCRIPT_TEXT_X_INSET,
                        lineY, line.color(), false);
                lineY += 12;
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        int sendW = 64;
        int inputW = Math.max(80, w - sendW - 6);
        this.input.setX(x);
        this.input.setY(footerY);
        this.input.width = inputW;
        this.input.setEnabled(!this.session.waiting());
        this.input.render(g, mouseX, mouseY, partialTick);
        drawButton(g, x + inputW + 6, footerY, sendW, INPUT_H, mouseX, mouseY,
                I18n.format(this.session.waiting()
                        ? "screen.rtsbuilding.ai_chat.waiting"
                        : "screen.rtsbuilding.ai_chat.send"), this.session.waiting());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        int x = contentX() + PAD;
        int y = contentY() + PAD;
        int w = Math.max(80, contentWidth() - PAD * 2);
        int footerY = contentY() + contentHeight() - PAD - INPUT_H;
        int refreshW = 70;
        int sendW = 64;
        int inputW = Math.max(80, w - sendW - 6);

        if (inside(mouseX, mouseY, x + w - refreshW, y, refreshW, BUTTON_H)) {
            resetConversation();
            return;
        }
        if (this.input.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        if (inside(mouseX, mouseY, x + inputW + 6, footerY, sendW, INPUT_H)) {
            submit();
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0.0D) {
            this.scrollLines += 3;
        } else if (scrollY < 0.0D) {
            this.scrollLines = Math.max(0, this.scrollLines - 3);
        }
        return true;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.input != null && this.input.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                submit();
            } else {
                this.input.textboxKeyTyped((char) 0, keyCode);
            }
            // 文本框拥有焦点时吞掉完整按键事件，防止 WASD、快捷键和镜头动作穿透。
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return this.input != null && this.input.textboxKeyTyped(codePoint, 0);
    }

    /** 文本框聚焦期间，BuilderScreen 与相机服务必须把键盘所有权交给本窗口。 */
    public boolean isInputFocused() {
        return isOpen() && this.input != null && this.input.isFocused();
    }

    @Override
    protected ITextComponent getTitle() {
        return new TextComponentTranslation("screen.rtsbuilding.ai_chat.title");
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
        this.windowX = Math.max(8, (this.screen.width - DEFAULT_W) / 2);
        this.windowY = Math.max(8, (this.screen.height - DEFAULT_H) / 2);
    }

    @Override
    protected void onClose() {
        cancelActiveRequest();
        super.onClose();
    }

    private void submit() {
        if (this.session == null || this.session.waiting() || this.input == null) {
            return;
        }
        String question = this.input.getValue().trim();
        if (question.isEmpty()) {
            return;
        }
        if (this.session.submit(question)) {
            this.input.setValue("");
            this.scrollLines = 0;
        }
    }

    private void resetConversation() {
        if (this.session != null) {
            this.session.reset();
        }
        this.scrollLines = 0;
        if (this.input != null) {
            this.input.setValue("");
            this.input.setEnabled(true);
            this.input.setFocused(true);
        }
    }

    private void cancelActiveRequest() {
        if (this.session != null) {
            this.session.cancel();
        }
    }

    private List<RenderLine> buildRenderLines(int maxWidth) {
        List<RenderLine> lines = new ArrayList<>();
        if (this.session == null) {
            return lines;
        }
        if (this.session.conversationEmpty()
                && this.session.pendingQuestion().isEmpty()
                && this.session.notices().isEmpty()) {
            appendWrapped(lines, I18n.format("screen.rtsbuilding.ai_chat.welcome"),
                    maxWidth, AiChatStyle.WELCOME_TEXT.toArgb());
        }
        for (RtsAiConversation.Exchange exchange : this.session.exchanges()) {
            appendWrapped(lines, I18n.format("screen.rtsbuilding.ai_chat.you")
                    + " " + exchange.question(), maxWidth, AiChatStyle.PLAYER_TEXT.toArgb());
            appendWrapped(lines, I18n.format("screen.rtsbuilding.ai_chat.ai")
                    + " " + exchange.answer(), maxWidth, AiChatStyle.AI_TEXT.toArgb());
            lines.add(new RenderLine(
                    "",
                    AiChatStyle.AI_TEXT.toArgb()));
        }
        if (!this.session.pendingQuestion().isEmpty()) {
            appendWrapped(lines, I18n.format("screen.rtsbuilding.ai_chat.you")
                    + " " + this.session.pendingQuestion(), maxWidth, AiChatStyle.PLAYER_TEXT.toArgb());
            String answer = this.session.streamingAnswer();
            if (answer.isEmpty()) {
                answer = I18n.format("screen.rtsbuilding.ai_chat.connecting");
            }
            appendWrapped(lines, I18n.format("screen.rtsbuilding.ai_chat.ai")
                    + " " + answer, maxWidth, AiChatStyle.AI_TEXT.toArgb());
        }
        for (RtsAiChatSession.Notice notice : this.session.notices()) {
            appendWrapped(lines, notice.text(), maxWidth, notice.color());
        }
        return lines;
    }

    private void appendWrapped(List<RenderLine> target, String text, int maxWidth, int color) {
        for (String line : screen.font().listFormattedStringToWidth(text, Math.max(40, maxWidth))) {
            target.add(new RenderLine(line, color));
        }
    }

    private void drawButton(LegacyGuiGraphics g, int x, int y, int w, int h,
                            int mouseX, int mouseY, String label, boolean disabled) {
        boolean hovered = !disabled && inside(mouseX, mouseY, x, y, w, h);
        WindowButtonChromeRenderer.renderSolid(
                new MinecraftUiCanvas(g, screen.font(), screen),
                new UiRect(x, y, w, h),
                hovered);
        String text = trimToWidth(label, w - TEXT_HORIZONTAL_INSET);
        g.drawString(screen.font(), text,
                x + Math.max(4, (w - screen.font().getStringWidth(text)) / 2),
                y + (h - screen.font().FONT_HEIGHT) / 2,
                WindowButtonStyle.text(!disabled).toArgb(), false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return UiRect.contains(x, y, w, h, mouseX, mouseY);
    }

    private static final class RenderLine {
        private final String text;
        private final int color;
        private RenderLine(String text, int color) { this.text = text; this.color = color; }
        private String text() { return this.text; }
        private int color() { return this.color; }
    }

    private final List<PersistableProperty> properties = java.util.Collections.singletonList(
            PersistableProperty.bounds("ai_chat", this));

    private String trimToWidth(String value, int maxWidth) {
        String safe = value == null ? "" : value;
        if (this.screen.font().getStringWidth(safe) <= maxWidth) return safe;
        String suffix = "...";
        int width = Math.max(0, maxWidth - this.screen.font().getStringWidth(suffix));
        return this.screen.font().trimStringToWidth(safe, width) + suffix;
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        return this.properties;
    }
}
