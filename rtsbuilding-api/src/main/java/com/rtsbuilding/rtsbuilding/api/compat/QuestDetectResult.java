package com.rtsbuilding.rtsbuilding.api.compat;

public record QuestDetectResult(boolean available, boolean error, int scannedTasks, int newlyCompletedTasks) {
    public static QuestDetectResult unavailable() {
        return new QuestDetectResult(false, false, 0, 0);
    }

    public static QuestDetectResult failed() {
        return new QuestDetectResult(false, true, 0, 0);
    }

    public static QuestDetectResult complete(int scanned, int completed) {
        return new QuestDetectResult(true, false, scanned, completed);
    }
}
