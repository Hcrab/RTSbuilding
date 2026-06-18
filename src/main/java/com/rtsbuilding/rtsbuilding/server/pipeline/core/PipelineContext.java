package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable context object passed through every {@link PipelinePipe} in a
 * {@link WorkflowPipeline} execution.
 *
 * <p>The context carries:</p>
 * <ul>
 *   <li><b>Immutable inputs</b> ({@code args}) — set once at pipeline creation,
 *       never modified.  Access via {@link #getArg(String)}.</li>
 *   <li><b>Mutable shared data</b> ({@code data}) — pipes can read/write here to
 *       pass intermediate results (e.g. workflow entry ID, tool lease, history
 *       records) to downstream pipes.  Access via
 *       {@link #getData(String)} / {@link #setData(String, Object)}.</li>
 *   <li><b>Player and session</b> — the fundamental execution context.</li>
 * </ul>
 *
 * <p>All keys are defined as {@link TypedKey} constants so the compiler (and
 * runtime via {@link Class#cast(Object)}) can verify type safety.  Prefer
 * the typed {@link #getArg(TypedKey)} / {@link #getData(TypedKey)} overloads
 * over the raw {@code String}-based methods.</p>
 */
public class PipelineContext {

    /** Key for the workflow entry ID stored in shared data. */
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID =
            new TypedKey<>("workflowEntryId", Integer.class);

    // ──────────────────────────────────────────────────────────────────
    //  Immutable fields
    // ──────────────────────────────────────────────────────────────────

    private final ServerPlayer player;
    private final Map<String, Object> args;

    // ──────────────────────────────────────────────────────────────────
    //  Mutable shared data
    // ──────────────────────────────────────────────────────────────────

    private final Map<String, Object> data = new HashMap<>();
    private PipelineResult result;

    // ──────────────────────────────────────────────────────────────────
    //  Construction
    // ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new pipeline context.
     *
     * @param player the server-side player executing the operation
     * @param args   immutable input arguments (a defensive copy is taken)
     */
    public PipelineContext(ServerPlayer player, Map<String, Object> args) {
        this.player = Objects.requireNonNull(player, "player");
        this.args = Collections.unmodifiableMap(new HashMap<>(args));
    }

    // ──────────────────────────────────────────────────────────────────
    //  Getters
    // ──────────────────────────────────────────────────────────────────

    /** Returns the server-side player. */
    public ServerPlayer player() {
        return player;
    }

    /**
     * Returns the player's storage session from shared data, or {@code null}
     * if {@link SessionValidatePipe} has not run yet.
     */
    @Nullable
    public RtsStorageSession session() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    /** Returns an immutable view of the input arguments. */
    public Map<String, Object> args() {
        return args;
    }

    /**
     * Retrieves a typed input argument by {@link TypedKey}.
     *
     * @throws ClassCastException if the value is not of the expected type
     */
    @Nullable
    public <T> T getArg(TypedKey<T> key) {
        Object value = args.get(key.name());
        if (value == null) return null;
        return key.type().cast(value);
    }

    /** Returns {@code true} if the args map contains the given key. */
    public boolean hasArg(TypedKey<?> key) {
        return args.containsKey(key.name());
    }

    // ──────────────────────────────────────────────────────────────────
    //  Shared data (mutable — pipes communicate through this)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Stores a value in the shared data map using a {@link TypedKey}.
     * The value type is checked by the compiler against the key's type parameter.
     */
    public <T> void setData(TypedKey<T> key, T value) {
        data.put(key.name(), value);
    }

    /**
     * Retrieves a typed value from the shared data map by {@link TypedKey}.
     *
     * @throws ClassCastException if the value is not of the expected type
     */
    @Nullable
    public <T> T getData(TypedKey<T> key) {
        Object value = data.get(key.name());
        if (value == null) return null;
        return key.type().cast(value);
    }

    /**
     * Returns {@code true} if the shared data map contains the given key.
     */
    public boolean hasData(TypedKey<?> key) {
        return data.containsKey(key.name());
    }

    /**
     * Removes all shared data except the specified keys.
     * Called after the sync phase completes to free intermediate data
     * before the tickable phase begins.
     *
     * <p>Only values associated with the given keys are preserved; all
     * other entries in the shared data map are discarded.  This prevents
     * transient sync-phase data (queue mode flags, intermediate results)
     * from lingering in memory for the duration of a long tickable phase.</p>
     *
     * @param keys the keys whose values should be retained
     */
    public void retainOnly(TypedKey<?>... keys) {
        Map<String, Object> retained = new HashMap<>();
        for (TypedKey<?> key : keys) {
            Object value = data.get(key.name());
            if (value != null) {
                retained.put(key.name(), value);
            }
        }
        data.clear();
        data.putAll(retained);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Pipeline result
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns the pipeline result, or {@code null} if the pipeline has not
     * completed yet.
     */
    @Nullable
    public PipelineResult result() {
        return result;
    }

    /**
     * Sets the pipeline result.  Called internally by
     * {@link WorkflowPipeline#execute(PipelineContext)}.
     */
    public void setResult(PipelineResult result) {
        this.result = result;
    }
}
