package com.rtsbuilding.rtsbuilding.server.workflow.event;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 轻量同步事件总线。监听器异常会被记录，不会阻断其他监听器。
 */
public final class RtsWorkflowEventBus {
    private final List<WorkflowEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(WorkflowEventListener listener) {
        if (listener != null && !this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(WorkflowEventListener listener) {
        this.listeners.remove(listener);
    }

    public void fire(WorkflowEvent event) {
        if (event == null) {
            return;
        }
        for (WorkflowEventListener listener : this.listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                RtsbuildingMod.LOGGER.error("[Workflow] listener {} failed on {}",
                        listener.getClass().getName(), event.type(), e);
            }
        }
    }
}
