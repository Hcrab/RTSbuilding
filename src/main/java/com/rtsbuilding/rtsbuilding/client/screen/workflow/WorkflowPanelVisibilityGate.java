package com.rtsbuilding.rtsbuilding.client.screen.workflow;

final class WorkflowPanelVisibilityGate {
    private final long delayMs;
    private long candidateSinceMs = -1L;

    WorkflowPanelVisibilityGate(long delayMs) {
        this.delayMs = Math.max(0L, delayMs);
    }

    boolean canShow(boolean candidateVisible, long nowMs) {
        if (!candidateVisible) {
            reset();
            return false;
        }
        if (this.candidateSinceMs < 0L) {
            this.candidateSinceMs = nowMs;
            return this.delayMs == 0L;
        }
        return nowMs - this.candidateSinceMs >= this.delayMs;
    }

    void reset() {
        this.candidateSinceMs = -1L;
    }
}
