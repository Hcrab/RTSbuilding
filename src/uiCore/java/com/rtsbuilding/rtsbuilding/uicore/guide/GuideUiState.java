package com.rtsbuilding.rtsbuilding.uicore.guide;

/** 指南选页与两套独立滚动位置。 */
public final class GuideUiState {
    public final GuideUiContext context;
    public final int page;
    public final int topicScroll;
    public final int textScroll;
    public final int visibleTopics;
    public final int totalTextLines;
    public final int visibleTextLines;

    public GuideUiState(GuideUiContext context, int page, int topicScroll, int textScroll,
                        int visibleTopics, int totalTextLines, int visibleTextLines) {
        this.context = context == null ? GuideUiContext.TOP : context;
        int topicCount = GuideUiCatalog.topics(this.context).length;
        this.visibleTopics = Math.max(1, visibleTopics);
        this.visibleTextLines = Math.max(1, visibleTextLines);
        this.totalTextLines = Math.max(0, totalTextLines);
        this.page = clamp(page, 0, Math.max(0, topicCount - 1));
        int adjustedTopicScroll = clamp(topicScroll, 0, Math.max(0, topicCount - this.visibleTopics));
        if (this.page < adjustedTopicScroll) adjustedTopicScroll = this.page;
        else if (this.page >= adjustedTopicScroll + this.visibleTopics) {
            adjustedTopicScroll = this.page - this.visibleTopics + 1;
        }
        this.topicScroll = clamp(adjustedTopicScroll, 0,
                Math.max(0, topicCount - this.visibleTopics));
        this.textScroll = clamp(textScroll, 0,
                Math.max(0, this.totalTextLines - this.visibleTextLines));
    }

    GuideUiState with(int nextPage, int nextTopicScroll, int nextTextScroll) {
        return new GuideUiState(context, nextPage, nextTopicScroll, nextTextScroll,
                visibleTopics, totalTextLines, visibleTextLines);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
