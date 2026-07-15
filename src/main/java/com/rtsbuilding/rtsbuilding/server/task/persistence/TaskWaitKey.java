package com.rtsbuilding.rtsbuilding.server.task.persistence;

import java.util.Objects;

/**
 * 等待任务的事件索引键。
 *
 * <p>例如 {@code item/minecraft:stone}、{@code chunk/minecraft:overworld:12:8}。
 * 它只描述“什么变化能唤醒任务”，不保存玩家或世界对象。</p>
 */
public record TaskWaitKey(String kind, String value) implements Comparable<TaskWaitKey> {
    public TaskWaitKey {
        kind = requirePart(kind, "kind");
        value = requirePart(value, "value");
        if (kind.length() > 64) throw new IllegalArgumentException("kind 不能超过 64 个字符");
        if (value.length() > 512) throw new IllegalArgumentException("value 不能超过 512 个字符");
    }

    private static String requirePart(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }

    @Override
    public int compareTo(TaskWaitKey other) {
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0 ? kindOrder : value.compareTo(other.value);
    }
}
