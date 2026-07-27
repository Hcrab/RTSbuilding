package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * 一个同时携带编译期和运行时期望类型的键。
 *
 * <p>配合 {@link PipelineContext#getArg(TypedKey)} /
 * {@link PipelineContext#getData(TypedKey)} 使用，
 * 实现对管道上下文参数和共享数据的类型安全访问。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID =
 *         new TypedKey<>("workflowEntryId", Integer.class);
 *
 * int id = ctx.getData(KEY_WORKFLOW_ENTRY_ID);  // 无需 unchecked 强制转换
 * }</pre>
 *
 * @param <T> 期望的值类型
 */
public final class TypedKey<T> {
    private final String name;
    private final Class<T> type;

    public TypedKey(String name, Class<T> type) {
        this.name = java.util.Objects.requireNonNull(name, "name");
        this.type = java.util.Objects.requireNonNull(type, "type");
    }

    public String name() { return name; }
    public Class<T> type() { return type; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TypedKey)) return false;
        TypedKey<?> key = (TypedKey<?>) other;
        return name.equals(key.name) && type.equals(key.type);
    }

    @Override
    public int hashCode() { return 31 * name.hashCode() + type.hashCode(); }

    @Override
    public String toString() {
        return name + "<" + type.getSimpleName() + ">";
    }
}
