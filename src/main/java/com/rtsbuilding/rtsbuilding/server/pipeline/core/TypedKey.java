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
