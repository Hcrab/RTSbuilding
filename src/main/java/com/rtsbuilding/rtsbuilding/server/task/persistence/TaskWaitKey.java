package com.rtsbuilding.rtsbuilding.server.task.persistence;

import java.util.Objects;

/**
 * 等待任务的事件索引键。
 *
 * <p>例如 {@code item/minecraft:stone}、{@code chunk/minecraft:overworld:12:8}。
 * 它只描述“什么变化能唤醒任务”，不保存玩家或世界对象。</p>
 */
public final class TaskWaitKey implements Comparable<TaskWaitKey> {
    private final String kind;
    private final String value;

    public TaskWaitKey(String kind, String value) {
        this.kind = requirePart(kind, "kind");
        this.value = requirePart(value, "value");
        if (this.kind.length() > 64) throw new IllegalArgumentException("kind 不能超过 64 个字符");
        if (this.value.length() > 512) throw new IllegalArgumentException("value 不能超过 512 个字符");
        NbtStringLimits.requireWritable(this.kind, "wait kind");
        NbtStringLimits.requireWritable(this.value, "wait value");
    }

    public String kind() { return kind; }
    public String value() { return value; }

    private static String requirePart(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }

    @Override
    public int compareTo(TaskWaitKey other) {
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0 ? kindOrder : value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TaskWaitKey)) return false;
        TaskWaitKey key = (TaskWaitKey) other;
        return kind.equals(key.kind) && value.equals(key.value);
    }

    @Override
    public int hashCode() { return 31 * kind.hashCode() + value.hashCode(); }

    @Override
    public String toString() { return kind + '/' + value; }
}
