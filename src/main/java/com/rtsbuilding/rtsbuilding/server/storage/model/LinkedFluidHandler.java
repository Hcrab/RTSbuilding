package com.rtsbuilding.rtsbuilding.server.storage.model;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.storage.IFluidHandler;

/**
 * 已解析的链接流体处理器——将链接存储引用与其对应的流体处理器绑定。
 *
 * <p>封装了流体处理器的身份引用、显示名称、是否允许存入以及优先级。
 *
 * @param ref        链接存储引用
 * @param name       显示名称
 * @param handler    流体处理器
 * @param allowStore 是否允许存入流体（false = 仅提取模式）
 * @param priority   优先级（AE 风格，影响插入顺序）
 */
public final class LinkedFluidHandler {
    private final LinkedStorageRef ref;
    private final String name;
    private final IFluidHandler handler;
    private final boolean allowStore;
    private final int priority;

    public LinkedFluidHandler(LinkedStorageRef ref, String name, IFluidHandler handler, boolean allowStore, int priority) {
        this.ref = java.util.Objects.requireNonNull(ref, "ref");
        this.name = name == null ? "" : name;
        this.handler = java.util.Objects.requireNonNull(handler, "handler");
        this.allowStore = allowStore;
        this.priority = priority;
    }
    public LinkedStorageRef ref() { return ref; }
    public String name() { return name; }
    public IFluidHandler handler() { return handler; }
    public boolean allowStore() { return allowStore; }
    public int priority() { return priority; }
    public BlockPos pos() {
        return this.ref.pos();
    }
}
