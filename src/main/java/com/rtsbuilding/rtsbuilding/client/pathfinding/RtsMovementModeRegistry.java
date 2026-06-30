package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 移动模式注册表 — 管理所有 {@link MovementModeHandler} 的优先级排序和动态查询。
 * <p>
 * 采用「按优先级降序排列 → 顺序遍历 → 返回首个匹配」的策略，
 * 确保玩家当前最匹配的移动模式被优先选中。
 * 使用 {@link CopyOnWriteArrayList} 保证并发安全，支持运行时动态注册。
 * </p>
 *
 * <h3>内置模式优先级一览</h3>
 * <table border="1">
 *   <tr><th>优先级</th><th>模式</th><th>检测依据</th></tr>
 *   <tr><td>500</td><td>鞘翅飞行</td><td>{@code player.isFallFlying()}</td></tr>
 *   <tr><td>400</td><td>创造飞行</td><td>{@code player.getAbilities().flying}</td></tr>
 *   <tr><td>300</td><td>游泳</td><td>{@code Pose.SWIMMING} + 水下 + 水中</td></tr>
 *   <tr><td>200</td><td>爬行</td><td>{@code Pose.SWIMMING} + 地面 + 非液体</td></tr>
 *   <tr><td>100</td><td>步行（兜底）</td><td>始终 {@code true}</td></tr>
 * </table>
 *
 * <h3>第三方模组扩展</h3>
 * <p>
 * 在客户端初始化阶段调用 {@link #register(MovementModeHandler, int)}，
 * 或监听 {@link RegisterMovementModeEvent} 事件。
 * 建议优先级范围：
 * </p>
 * <ul>
 *   <li>50 ~ 90：低优先级兜底模式</li>
 *   <li>600+：高优先级覆盖模式（覆盖内置检测）</li>
 * </ul>
 *
 * @see MovementModeHandler
 * @see BuiltinMovementModes
 */
public final class RtsMovementModeRegistry {

    /** 按优先级降序排列的 handler 列表。使用 CopyOnWriteArrayList 保证并发安全。 */
    private static final List<PrioritizedHandler> HANDLERS = new CopyOnWriteArrayList<>();

    /** 是否已完成内置模式的初始化。 */
    private static boolean initialized = false;

    private RtsMovementModeRegistry() {
    }

    // ======================================================================
    //  初始化
    // ======================================================================

    /**
     * 初始化并注册所有内置移动模式。
     * <p>
     * 仅在首次调用时执行，后续重复调用将被忽略（幂等）。
     * 按优先级从低到高依次添加，然后在末尾一次排序，减少不必要的排序开销。
     * 注册顺序（高→低）：鞘翅(500) → 飞行(400) → 游泳(300) → 爬行(200) → 步行(100)。
     * </p>
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.ELYTRA, 500));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.FLYING, 400));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.SWIMMING, 300));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.CRAWLING, 200));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.WALKING, 100));

        // 按优先级降序排列，使检测时优先级最高的 handler 最先被遍历到
        HANDLERS.sort(Comparator.comparingInt(PrioritizedHandler::priority).reversed());
    }

    // ======================================================================
    //  注册 API
    // ======================================================================

    /**
     * 注册自定义移动模式 handler，并指定检测优先级。
     * <p>
     * 注册后自动按优先级降序重新排序。后续的 {@link #findActive(LocalPlayer)}
     * 会按优先级顺序检测，返回首个匹配的 handler。
     * </p>
     *
     * @param handler  移动模式实现
     * @param priority 检测优先级，数值越大越优先被检测
     */
    public static void register(MovementModeHandler handler, int priority) {
        HANDLERS.add(new PrioritizedHandler(handler, priority));
        HANDLERS.sort(Comparator.comparingInt(PrioritizedHandler::priority).reversed());
    }

    /**
     * 使用默认优先级（50）注册自定义移动模式 handler。
     *
     * @param handler 移动模式实现
     */
    public static void register(MovementModeHandler handler) {
        register(handler, 50);
    }

    // ======================================================================
    //  查询 API
    // ======================================================================

    /**
     * 查找当前玩家激活的优先级最高的移动模式。
     * <p>
     * 按注册优先级从高到低遍历所有 handler，返回第一个 {@link MovementModeHandler#isActive(LocalPlayer)}
     * 返回 {@code true} 的 handler。如果没有任何 handler 匹配（理论上不会发生，因为步行模式始终返回 true），
     * 则返回 {@code null}。
     * </p>
     *
     * @param player 本地玩家
     * @return 优先级最高的匹配 handler；如果没有匹配则返回 {@code null}
     */
    public static MovementModeHandler findActive(LocalPlayer player) {
        for (PrioritizedHandler ph : HANDLERS) {
            if (ph.handler().isActive(player)) {
                return ph.handler();
            }
        }
        return null;
    }

    /**
     * 触发 {@link RegisterMovementModeEvent} 事件，让其它模组通过 NeoForge 事件系统注册自定义移动模式。
     * 应在 Mod 构造期的客户端初始化完成后调用一次。
     */
    public static void fireRegistrationEvent() {
        NeoForge.EVENT_BUS.post(new RegisterMovementModeEvent());
    }

    // ======================================================================
    //  内部数据结构
    // ======================================================================

    /**
     * handler 与优先级的配对记录，用于在列表中同时存储实现和其优先级。
     *
     * @param handler  移动模式实现
     * @param priority 检测优先级
     */
    private record PrioritizedHandler(MovementModeHandler handler, int priority) {
    }

    /**
     * 移动模式注册事件 — 让其它模组通过事件监听注册自定义模式。
     * <p>
     * 使用方式：
     * </p>
     * <pre>{@code
     * @SubscribeEvent
     * static void onRegisterMovementMode(RegisterMovementModeEvent event) {
     *     RtsMovementModeRegistry.register(new MyCustomMode(), 600);
     * }
     * }</pre>
     * <p>
     * 此事件在 {@link #fireRegistrationEvent()} 被调用时触发。
     * </p>
     */
    public static final class RegisterMovementModeEvent extends net.neoforged.bus.api.Event {
        private RegisterMovementModeEvent() {
        }
    }
}
