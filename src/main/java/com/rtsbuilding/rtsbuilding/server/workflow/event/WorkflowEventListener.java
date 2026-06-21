package com.rtsbuilding.rtsbuilding.server.workflow.event;

@FunctionalInterface
public interface WorkflowEventListener {
    void onEvent(WorkflowEvent event);
}
