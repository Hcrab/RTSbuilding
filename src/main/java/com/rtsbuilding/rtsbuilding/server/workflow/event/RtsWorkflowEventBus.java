package com.rtsbuilding.rtsbuilding.server.workflow.event;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工作流生命周期事件的简单线程安全事件总线。
 *
 * <p>使用 {@link CopyOnWriteArrayList}，因此可以在事件分发过程中安全地
 * 添加/移除监听器。监听器在调用者线程（通常为服务端 tick 线程）上同步调用。</p>
 *
 * <p>有意保持轻量——没有事件队列，没有异步分发，
 * 除 FIFO 插入顺序外不保证任何排序。</p>
 */
public final class RtsWorkflowEventBus {

    private final List<WorkflowEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 注册一个监听器。幂等操作（重复注册会被忽略）。
     */
    public void addListener(WorkflowEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除之前注册的监听器。
     */
    public void removeListener(WorkflowEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * 向所有已注册的监听器触发事件。
     * 单个监听器的异常会被捕获并记录日志，这样有问题的监听器
     * 不会阻止其他监听器收到事件。
     *
     * @param event 要触发的事件（不能为 null）
     */
    public void fire(WorkflowEvent event) {
        if (event == null) return;
        for (WorkflowEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.error(
                        "[WorkflowEventBus] Listener {} threw on event {}: {}",
                        listener.getClass().getSimpleName(), event.type(), e.getMessage(), e);
            }
        }
    }

    /** 返回已注册的监听器数量。 */
    public int listenerCount() {
        return listeners.size();
    }
}
