package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 移动模式注册表。管理所有 {@link MovementModeHandler} 的优先级排序和查询。
 * <p>
 * <h3>内置模式（按优先级从高到低）：</h3>
 * <ol>
 *   <li>{@link BuiltinMovementModes#ELYTRA} — 鞘翅飞行</li>
 *   <li>{@link BuiltinMovementModes#FLYING} — 创造飞行</li>
 *   <li>{@link BuiltinMovementModes#SWIMMING} — 游泳</li>
 *   <li>{@link BuiltinMovementModes#CRAWLING} — 爬行</li>
 *   <li>{@link BuiltinMovementModes#WALKING} — 步行（兜底）</li>
 * </ol>
 * <p>
 * <h3>第三方模组扩展：</h3>
 * 在客户端初始化阶段调用 {@code RtsMovementModeRegistry#register(handler, priority)}，
 * 或者监听 {@link RegisterMovementModeEvent} 事件。
 * <p>
 * 优先级数值越大越优先检测，内置模式优先级为 100 ~ 500。
 * 推荐第三方模组使用 50 ~ 90（低优先级兜底）或 600+（高优先级覆盖）。
 */
public final class RtsMovementModeRegistry {

    private static final List<PrioritizedHandler> HANDLERS = new CopyOnWriteArrayList<>();
    private static boolean initialized = false;

    private RtsMovementModeRegistry() {
    }

    /**
     * 初始化内置移动模式。仅首次调用有效。
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // 按优先级从高到低注册
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.ELYTRA, 500));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.FLYING, 400));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.SWIMMING, 300));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.CRAWLING, 200));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.WALKING, 100));

        // 按优先级降序排列（检测时从高到低）
        HANDLERS.sort(Comparator.comparingInt(PrioritizedHandler::priority).reversed());
    }

    /**
     * 注册自定义移动模式 handler。
     *
     * @param handler  移动模式实现
     * @param priority 优先级，越大越优先检测
     */
    public static void register(MovementModeHandler handler, int priority) {
        HANDLERS.add(new PrioritizedHandler(handler, priority));
        HANDLERS.sort(Comparator.comparingInt(PrioritizedHandler::priority).reversed());
    }

    /**
     * 注册自定义移动模式 handler（默认优先级 50）。
     */
    public static void register(MovementModeHandler handler) {
        register(handler, 50);
    }

    /**
     * 查找当前玩家激活的第一个（优先级最高）移动模式。
     *
     * @param player 本地玩家
     * @return 匹配的 handler，若无可用的则返回 null（此时应使用步行兜底）
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
     * 触发 {@link RegisterMovementModeEvent}，让其它模组通过事件注册自定义模式。
     * 应在 mod 构造期的客户端初始化完成后调用。
     */
    public static void fireRegistrationEvent() {
        NeoForge.EVENT_BUS.post(new RegisterMovementModeEvent());
    }

    private record PrioritizedHandler(MovementModeHandler handler, int priority) {
    }

    /**
     * 让其它模组通过事件监听来注册自定义移动模式的事件。
     * <p>
     * 用法：在客户端初始化时订阅此事件：
     * <pre>{@code
     * @SubscribeEvent
     * static void onRegisterMovementMode(RegisterMovementModeEvent event) {
     *     RtsMovementModeRegistry.register(new MyCustomMode(), 600);
     * }
     * }</pre>
     */
    public static final class RegisterMovementModeEvent extends net.neoforged.bus.api.Event {
        private RegisterMovementModeEvent() {
        }
    }
}
