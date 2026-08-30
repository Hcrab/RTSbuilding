package com.rtsbuilding.rtsbuilding.client.screen.guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 游戏内 AI 求助窗口的短期会话。
 *
 * <p>这里只保存最近十轮问答，不读取 Minecraft 状态，也不负责网络请求。这样刷新、
 * 截断和上下文装配可以独立测试，不会把聊天状态继续堆进 {@code BuilderScreen}。
 */
public final class RtsAiConversation {
    public static final int MAX_EXCHANGES = 10;

    private final List<Exchange> exchanges = new ArrayList<>();

    public void add(String question, String answer) {
        String safeQuestion = question == null ? "" : question.strip();
        String safeAnswer = answer == null ? "" : answer.strip();
        if (safeQuestion.isEmpty() || safeAnswer.isEmpty()) {
            return;
        }
        this.exchanges.add(new Exchange(safeQuestion, safeAnswer));
        while (this.exchanges.size() > MAX_EXCHANGES) {
            this.exchanges.remove(0);
        }
    }

    public void clear() {
        this.exchanges.clear();
    }

    public boolean isEmpty() {
        return this.exchanges.isEmpty();
    }

    public int size() {
        return this.exchanges.size();
    }

    public List<Exchange> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(this.exchanges));
    }

    public record Exchange(String question, String answer) {
    }
}
