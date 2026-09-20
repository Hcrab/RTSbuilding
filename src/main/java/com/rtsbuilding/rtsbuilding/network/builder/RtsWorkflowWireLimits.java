package com.rtsbuilding.rtsbuilding.network.builder;

/** 工作流同步协议的解码预算；这是协议安全上限，不是玩家可见槽位数量。 */
public final class RtsWorkflowWireLimits {
    /** 客户端状态容器可接纳的协议工作流数量上限。 */
    public static final int MAX_WORKFLOW_COUNT = 4096;
    static final int MAX_MISSING_ITEMS = 1024;
    static final int MAX_ITEM_ID_CHARS = 256;
    static final int MAX_DETAIL_CHARS = 1024;

    private RtsWorkflowWireLimits() {
    }
}
