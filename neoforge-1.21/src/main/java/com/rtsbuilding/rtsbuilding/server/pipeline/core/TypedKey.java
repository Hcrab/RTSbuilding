package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * A key that carries both compile-time and runtime expected types.
 *
 * <p>Used with {@link PipelineContext#getArg(TypedKey)} /
 * {@link PipelineContext#getData(TypedKey)},
 * providing type-safe access to pipeline context arguments and shared data.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID =
 *         new TypedKey<>("workflowEntryId", Integer.class);
 *
 * int id = ctx.getData(KEY_WORKFLOW_ENTRY_ID);  // no unchecked cast needed
 * }</pre>
 *
 * @param <T> the expected value type
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
