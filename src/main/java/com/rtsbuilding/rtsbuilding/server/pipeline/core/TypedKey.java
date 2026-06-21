package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * 管线上下文使用的类型化键。
 *
 * <p>Forge 1.20.1 正在追 main 的 pipeline 架构，这个键同时保存字段名和
 * 运行时类型，避免后续 pipe 之间用裸字符串传值。</p>
 */
public record TypedKey<T>(String name, Class<T> type) {
    public TypedKey {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(type, "type");
    }

    @Override
    public String toString() {
        return name + "<" + type.getSimpleName() + ">";
    }
}
