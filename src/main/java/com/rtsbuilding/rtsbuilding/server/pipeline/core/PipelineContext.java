package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * pipeline 执行上下文。
 *
 * <p>它分离不可变输入参数和可变共享数据，后续 Forge 1.20.1 的放置、
 * 挖掘、蓝图 pipe 都应通过类型化 key 传递中间状态，而不是继续在服务类
 * 之间散落临时字段。</p>
 */
public class PipelineContext {
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID =
            new TypedKey<>("workflowEntryId", Integer.class);
    public static final TypedKey<RtsStorageSession> KEY_SESSION =
            new TypedKey<>("session", RtsStorageSession.class);

    private final ServerPlayer player;
    private final Map<String, Object> args;
    private final Map<String, Object> data = new HashMap<>();
    private PipelineResult result;

    public PipelineContext(ServerPlayer player, Map<String, Object> args) {
        this.player = Objects.requireNonNull(player, "player");
        this.args = Collections.unmodifiableMap(new HashMap<>(args == null ? Map.of() : args));
    }

    public ServerPlayer player() {
        return this.player;
    }

    @Nullable
    public RtsStorageSession session() {
        return getData(KEY_SESSION);
    }

    public Map<String, Object> args() {
        return this.args;
    }

    @Nullable
    public <T> T getArg(TypedKey<T> key) {
        Object value = this.args.get(key.name());
        return value == null ? null : key.type().cast(value);
    }

    public boolean hasArg(TypedKey<?> key) {
        return this.args.containsKey(key.name());
    }

    public <T> void setData(TypedKey<T> key, T value) {
        this.data.put(key.name(), value);
    }

    @Nullable
    public <T> T getData(TypedKey<T> key) {
        Object value = this.data.get(key.name());
        return value == null ? null : key.type().cast(value);
    }

    public boolean hasData(TypedKey<?> key) {
        return this.data.containsKey(key.name());
    }

    public void retainOnly(Set<String> retainKeys) {
        this.data.keySet().removeIf(k -> !retainKeys.contains(k));
    }

    public void retainOnly(TypedKey<?>... keys) {
        Set<String> retain = new HashSet<>(keys.length);
        for (TypedKey<?> key : keys) {
            retain.add(key.name());
        }
        retainOnly(retain);
    }

    @Nullable
    public PipelineResult result() {
        return this.result;
    }

    public void setResult(PipelineResult result) {
        this.result = result;
    }
}
