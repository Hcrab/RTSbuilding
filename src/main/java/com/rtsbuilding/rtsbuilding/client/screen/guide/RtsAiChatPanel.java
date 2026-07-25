package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

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
        this.input.setPlaceholder(Component.translatable("screen.rtsbuilding.ai_chat.placeholder").getString());
    }

    public void open() {
        setOpen(true);
        markBroughtToFront();
        if (this.input != null) {
            this.input.setFocused(true);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = contentX() + PAD;
        int y = contentY() + PAD;
        int w = Math.max(80, contentWidth() - PAD * 2);
        int footerY = contentY() + contentHeight() - PAD - INPUT_H;
        int refreshW = 70;

        drawButton(g, x + w - refreshW, y, refreshW, BUTTON_H, mouseX, mouseY,
                Component.translatable("screen.rtsbuilding.ai_chat.refresh"), false);
        String limit = Component.translatable("screen.rtsbuilding.ai_chat.turns",
                this.session.exchangeCount(), RtsAiConversation.MAX_EXCHANGES).getString();
        g.drawString(screen.font(), limit, x, y + 7, 0xFF91A4B8, false);

        int transcriptTop = y + BUTTON_H + 6;
        int transcriptBottom = footerY - 7;
        int transcriptH = Math.max(30, transcriptBottom - transcriptTop);
        List<RenderLine> lines = buildRenderLines(w - 8);
        int visible = Math.max(1, transcriptH / 12);
        this.scrollLines = Math.max(0, Math.min(this.scrollLines, Math.max(0, lines.size() - visible)));
        int first = Math.max(0, lines.size() - visible - this.scrollLines);
        int end = Math.min(lines.size(), first + visible);

        g.fill(x, transcriptTop, x + w, transcriptBottom, 0xB8141B23);
        screen.enableRtsScissor(g, x + 4, transcriptTop + 3, x + w - 4, transcriptBottom - 3);
        try {
            int lineY = transcriptTop + 5;
            for (int i = first; i < end; i++) {
                RenderLine line = lines.get(i);
                g.drawString(screen.font(), line.text(), x + 5, lineY, line.color(), false);
                lineY += 12;
            }
        } finally {
            g.disableScissor();
        }

        int sendW = 64;
        int inputW = Math.max(80, w - sendW - 6);
        this.input.setX(x);
        this.input.setY(footerY);
        this.input.setWidth(inputW);
        this.input.setEditable(!this.session.waiting());
        this.input.render(g, mouseX, mouseY, partialTick);
        drawButton(g, x + inputW + 6, footerY, sendW, INPUT_H, mouseX, mouseY,
                Component.translatable(this.session.waiting()
                        ? "screen.rtsbuilding.ai_chat.waiting"
                        : "screen.rtsbuilding.ai_chat.send"), this.session.waiting());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return this.input != null && this.input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return this.input != null && this.input.charTyped(codePoint, modifiers);
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.ai_chat.title");
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
        String question = this.input.getValue().strip();
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
            this.input.setEditable(true);
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
            appendWrapped(lines, Component.translatable("screen.rtsbuilding.ai_chat.welcome").getString(),
                    maxWidth, 0xFFB9C9D8);
        }
        for (RtsAiConversation.Exchange exchange : this.session.exchanges()) {
            appendWrapped(lines, Component.translatable("screen.rtsbuilding.ai_chat.you").getString()
                    + " " + exchange.question(), maxWidth, 0xFFE7C46A);
            appendWrapped(lines, Component.translatable("screen.rtsbuilding.ai_chat.ai").getString()
                    + " " + exchange.answer(), maxWidth, 0xFFE6EDF8);
            lines.add(new RenderLine(FormattedCharSequence.forward("", null), 0xFFE6EDF8));
        }
        if (!this.session.pendingQuestion().isEmpty()) {
            appendWrapped(lines, Component.translatable("screen.rtsbuilding.ai_chat.you").getString()
                    + " " + this.session.pendingQuestion(), maxWidth, 0xFFE7C46A);
            String answer = this.session.streamingAnswer();
            if (answer.isEmpty()) {
                answer = Component.translatable("screen.rtsbuilding.ai_chat.connecting").getString();
            }
            appendWrapped(lines, Component.translatable("screen.rtsbuilding.ai_chat.ai").getString()
                    + " " + answer, maxWidth, 0xFFE6EDF8);
        }
        for (RtsAiChatSession.Notice notice : this.session.notices()) {
            appendWrapped(lines, notice.text(), maxWidth, notice.color());
        }
        return lines;
    }

    private void appendWrapped(List<RenderLine> target, String text, int maxWidth, int color) {
        for (FormattedCharSequence line : screen.font().split(Component.literal(text), Math.max(40, maxWidth))) {
            target.add(new RenderLine(line, color));
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h,
                            int mouseX, int mouseY, Component label, boolean disabled) {
        boolean hovered = !disabled && inside(mouseX, mouseY, x, y, w, h);
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h,
                disabled ? 0xAA202832 : hovered ? 0xCC355A71 : 0xCC24313E,
                disabled ? 0xFF46515F : hovered ? 0xFFB7D9ED : 0xFF71879A,
                0xFF0D1218);
        String text = RtsClientUiUtil.trimToWidth(screen.font(), label.getString(), w - 8);
        g.drawString(screen.font(), text,
                x + Math.max(4, (w - screen.font().width(text)) / 2),
                y + (h - screen.font().lineHeight) / 2,
                disabled ? 0xFF778493 : 0xFFE6EDF8, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private record RenderLine(FormattedCharSequence text, int color) {
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("ai_chat", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return this.properties;
    }
}
