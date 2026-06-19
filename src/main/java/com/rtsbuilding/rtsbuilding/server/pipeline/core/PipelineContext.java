package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 可变上下文对象，在 {@link WorkflowPipeline} 执行的每个 {@link PipelinePipe} 中传递。
 *
 * <p>上下文携带：</p>
 * <ul>
 *   <li><b>不可变输入</b>（{@code args}）—— 在管道创建时一次性设置，永不修改。
 *       通过 {@link #getArg(TypedKey)} 访问。</li>
 *   <li><b>可变共享数据</b>（{@code data}）—— 管道可在此读写，用于向下游管道传递
 *       中间结果（如工作流条目 ID、工具租约、历史记录）。
 *       通过 {@link #getData(TypedKey)} / {@link #setData(TypedKey, Object)} 访问。</li>
 *   <li><b>玩家和会话</b> —— 基本的执行上下文。</li>
 * </ul>
 *
 * <p>所有键均定义为 {@link TypedKey} 常量，以便编译器（以及运行时通过
 * {@link Class#cast(Object)}）验证类型安全。
 * 优先使用类型化的 {@link #getArg(TypedKey)} / {@link #getData(TypedKey)} 重载
 * 而非原始的基于 {@code String} 的方法。</p>
 */
public class PipelineContext {

    /** 共享数据中工作流条目 ID 的键。 */
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID =
            new TypedKey<>("workflowEntryId", Integer.class);

    // ──────────────────────────────────────────────────────────────────
    //  不可变字段
    // ──────────────────────────────────────────────────────────────────

    private final ServerPlayer player;
    private final Map<String, Object> args;

    // ──────────────────────────────────────────────────────────────────
    //  可变共享数据
    // ──────────────────────────────────────────────────────────────────

    private final Map<String, Object> data = new HashMap<>();
    private PipelineResult result;

    // ──────────────────────────────────────────────────────────────────
    //  构造
    // ──────────────────────────────────────────────────────────────────

    /**
     * 创建管道上下文。
     *
     * @param player 执行操作的服务器端玩家
     * @param args   不可变输入参数（会创建防御性副本）
     */
    public PipelineContext(ServerPlayer player, Map<String, Object> args) {
        this.player = Objects.requireNonNull(player, "player");
        this.args = Collections.unmodifiableMap(new HashMap<>(args));
    }

    // ──────────────────────────────────────────────────────────────────
    //  获取器
    // ──────────────────────────────────────────────────────────────────

    /** 返回服务器端玩家。 */
    public ServerPlayer player() {
        return player;
    }

    /**
     * 从共享数据中返回玩家的存储会话，如果 {@link SessionValidatePipe}
     * 尚未运行则返回 {@code null}。
     */
    @Nullable
    public RtsStorageSession session() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    /** 返回输入参数的不可变视图。 */
    public Map<String, Object> args() {
        return args;
    }

    /**
     * 通过 {@link TypedKey} 获取类型化的输入参数。
     *
     * @throws ClassCastException 如果值不是期望的类型
     */
    @Nullable
    public <T> T getArg(TypedKey<T> key) {
        Object value = args.get(key.name());
        if (value == null) return null;
        return key.type().cast(value);
    }

    /** 如果 args 映射包含指定键则返回 {@code true}。 */
    public boolean hasArg(TypedKey<?> key) {
        return args.containsKey(key.name());
    }

    // ──────────────────────────────────────────────────────────────────
    //  共享数据（可变 — 管道间通过此通信）
    // ──────────────────────────────────────────────────────────────────

    /**
     * 使用 {@link TypedKey} 将值存入共享数据映射。
     * 编译器会根据键的类型参数检查值类型。
     */
    public <T> void setData(TypedKey<T> key, T value) {
        data.put(key.name(), value);
    }

    /**
     * 通过 {@link TypedKey} 从共享数据映射中获取类型化的值。
     *
     * @throws ClassCastException 如果值不是期望的类型
     */
    @Nullable
    public <T> T getData(TypedKey<T> key) {
        Object value = data.get(key.name());
        if (value == null) return null;
        return key.type().cast(value);
    }

    /**
     * 如果共享数据映射包含指定键则返回 {@code true}。
     */
    public boolean hasData(TypedKey<?> key) {
        return data.containsKey(key.name());
    }

    /**
     * 移除除指定键之外的所有共享数据。
     * 在同步阶段完成后调用，以在可 Tick 阶段开始前释放中间数据。
     *
     * <p>仅保留与给定键关联的值；共享数据映射中
     * 的所有其他条目均被丢弃。这防止了瞬态同步阶段数据
     *（队列模式标志、中间结果）在长时间的可 Tick 阶段中
     * 持续占用内存。</p>
     *
     * @param keys 应保留其值的键
     */
    /**
     * 使用预计算的键集合保留指定的共享数据键。
     * 比 varargs 版本少一次 HashSet 分配——由调用方在 hot path 上预计算。
     */
    public void retainOnly(Set<String> retainKeys) {
        data.keySet().removeIf(k -> !retainKeys.contains(k));
    }

    public void retainOnly(TypedKey<?>... keys) {
        Set<String> retain = new HashSet<>(keys.length);
        for (TypedKey<?> key : keys) {
            retain.add(key.name());
        }
        data.keySet().removeIf(k -> !retain.contains(k));
    }

    // ──────────────────────────────────────────────────────────────────
    //  管道结果
    // ──────────────────────────────────────────────────────────────────

    /**
     * 返回管道结果，如果管道尚未完成则返回 {@code null}。
     */
    @Nullable
    public PipelineResult result() {
        return result;
    }

    /**
     * 设置管道结果。由 {@link WorkflowPipeline#execute(PipelineContext)}
     * 内部调用。
     */
    public void setResult(PipelineResult result) {
        this.result = result;
    }
}
