package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

/**
 * 已解析的链接物品处理器——将链接存储引用与其对应的物品处理器绑定。
 *
 * <p>封装了物品处理器的身份引用、显示名称、是否允许存入以及优先级。
 *
 * @param ref        链接存储引用
 * @param name       显示名称
 * @param handler    物品处理器
 * @param allowStore 是否允许存入物品（false = 仅提取模式）
 * @param priority   优先级（AE 风格，影响插入顺序）
 */
public final class LinkedHandler {
    private final LinkedStorageRef ref;
    private final String name;
    private final IItemHandler handler;
    private final boolean allowStore;
    private final int priority;

    public LinkedHandler(LinkedStorageRef ref, String name, IItemHandler handler, boolean allowStore, int priority) {
        this.ref = java.util.Objects.requireNonNull(ref, "ref");
        this.name = name == null ? "" : name;
        this.handler = java.util.Objects.requireNonNull(handler, "handler");
        this.allowStore = allowStore;
        this.priority = priority;
    }
    public LinkedStorageRef ref() { return ref; }
    public String name() { return name; }
    public IItemHandler handler() { return handler; }
    public boolean allowStore() { return allowStore; }
    public int priority() { return priority; }
    public BlockPos pos() {
        return this.ref.pos();
    }
}
