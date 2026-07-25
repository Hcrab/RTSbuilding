package com.rtsbuilding.rtsbuilding.server.workflow.event;

import com.rtsbuilding.rtsbuilding.server.workflow.core.IWorkflowEngine;

/**
 * 工作流生命周期事件监听器的函数式接口。
 *
 * <p>通过 {@link IWorkflowEngine#addListener(WorkflowEventListener)}
 * 注册实例，以响应工作流状态变更。实现必须是线程安全的，且不应阻塞。</p>
 *
 * <pre>{@code
 * engine.addListener(event -> {
 *     if (event.type() == WorkflowEventType.COMPLETED) {
 *         // 刷新存储页面
 *     }
 * });
 * }</pre>
 */
@FunctionalInterface
public interface WorkflowEventListener {
    void onEvent(WorkflowEvent event);
}
