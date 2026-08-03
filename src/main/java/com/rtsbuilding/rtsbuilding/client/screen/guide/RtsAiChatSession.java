package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.uikit.theme.AiChatStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * AI 聊天窗口的异步会话协调器。
 *
 * <p>它拥有请求代次、流式回答、十轮历史和错误状态，但不绘制窗口、不处理鼠标键盘。
 * 关闭或刷新会递增代次，已经排队回到客户端线程的旧回调因此无法污染新会话。
 */
public final class RtsAiChatSession {
    private final ClientRtsController controller;
    private final RtsAiConversation conversation = new RtsAiConversation();
    private final RtsAiChatClient client = new RtsAiChatClient();
    private final List<Notice> notices = new ArrayList<>();
    private final StringBuilder streamingAnswer = new StringBuilder();
    private CompletableFuture<Void> activeRequest;
    private String pendingQuestion = "";
    private boolean waiting;
    private int requestGeneration;

    public RtsAiChatSession(ClientRtsController controller) {
        this.controller = controller;
    }

    public boolean submit(String question) {
        String safeQuestion = question == null ? "" : question.strip();
        if (this.waiting || safeQuestion.isEmpty()) {
            return false;
        }
        this.pendingQuestion = safeQuestion;
        this.streamingAnswer.setLength(0);
        this.notices.clear();
        this.waiting = true;
        int generation = ++this.requestGeneration;

        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected().getCode();
        boolean chinese = language != null && language.toLowerCase(Locale.ROOT).startsWith("zh_");
        String prompt = RtsAiPrompt.compose(chinese, RtsAiKnowledgeBase.build(this.controller),
                this.conversation.snapshot(), safeQuestion);
        this.activeRequest = this.client.ask(prompt,
                chunk -> runOnClient(generation, () -> this.streamingAnswer.append(chunk)),
                error -> runOnClient(generation, () -> {
                    this.waiting = false;
                    this.pendingQuestion = "";
                    this.streamingAnswer.setLength(0);
                    this.notices.add(new Notice(Component.translatable(
                            "screen.rtsbuilding.ai_chat.error", error).getString(),
                            AiChatStyle.ERROR_TEXT.toArgb()));
                }),
                () -> runOnClient(generation, () -> {
                    String answer = RtsAiResponseSanitizer
                            .forInGameDisplay(this.streamingAnswer.toString()).strip();
                    if (!answer.isEmpty()) {
                        this.conversation.add(this.pendingQuestion, answer);
                    } else {
                        this.notices.add(new Notice(Component.translatable(
                                "screen.rtsbuilding.ai_chat.empty").getString(),
                                AiChatStyle.WARNING_TEXT.toArgb()));
                    }
                    this.pendingQuestion = "";
                    this.streamingAnswer.setLength(0);
                    this.waiting = false;
                }));
        return true;
    }

    public void reset() {
        cancel();
        this.conversation.clear();
        this.notices.clear();
        this.pendingQuestion = "";
        this.streamingAnswer.setLength(0);
        this.notices.add(new Notice(Component.translatable(
                "screen.rtsbuilding.ai_chat.cleared").getString(),
                AiChatStyle.SUCCESS_TEXT.toArgb()));
    }

    public void cancel() {
        this.requestGeneration++;
        this.waiting = false;
        if (this.activeRequest != null) {
            this.activeRequest.cancel(true);
            this.activeRequest = null;
        }
    }

    public boolean waiting() {
        return this.waiting;
    }

    public int exchangeCount() {
        return this.conversation.size();
    }

    public boolean conversationEmpty() {
        return this.conversation.isEmpty();
    }

    public List<RtsAiConversation.Exchange> exchanges() {
        return this.conversation.snapshot();
    }

    public String pendingQuestion() {
        return this.pendingQuestion;
    }

    public String streamingAnswer() {
        return RtsAiResponseSanitizer.forInGameDisplay(this.streamingAnswer.toString());
    }

    public List<Notice> notices() {
        return List.copyOf(this.notices);
    }

    private void runOnClient(int generation, Runnable action) {
        Minecraft.getInstance().execute(() -> {
            if (generation == this.requestGeneration) {
                action.run();
            }
        });
    }

    public record Notice(String text, int color) {
    }
}
