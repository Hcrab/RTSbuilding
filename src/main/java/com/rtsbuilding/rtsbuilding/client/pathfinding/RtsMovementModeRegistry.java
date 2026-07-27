package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端移动模式注册表。
 *
 * <p>内置顺序是鞘翅、自由飞行、游泳、爬行、步行。第三方仍可直接调用
 * {@link #register(MovementModeHandler, int)}，或监听
 * {@link RegisterMovementModeEvent} 在客户端初始化期间注册。</p>
 */
@SideOnly(Side.CLIENT)
public final class RtsMovementModeRegistry {
    private static final List<PrioritizedHandler> HANDLERS = new CopyOnWriteArrayList<>();
    private static boolean initialized;

    private RtsMovementModeRegistry() {
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.ELYTRA, 500));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.FLYING, 400));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.SWIMMING, 300));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.CRAWLING, 200));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.WALKING, 100));
        sortHandlers();
    }

    public static synchronized void register(MovementModeHandler handler, int priority) {
        HANDLERS.add(new PrioritizedHandler(Objects.requireNonNull(handler, "handler"), priority));
        sortHandlers();
    }

    public static void register(MovementModeHandler handler) {
        register(handler, 50);
    }

    public static MovementModeHandler findActive(EntityPlayerSP player) {
        for (PrioritizedHandler prioritized : HANDLERS) {
            if (prioritized.handler().isActive(player)) {
                return prioritized.handler();
            }
        }
        return null;
    }

    /** 在 Forge 客户端事件总线上暴露第三方注册时机。 */
    public static void fireRegistrationEvent() {
        MinecraftForge.EVENT_BUS.post(new RegisterMovementModeEvent());
    }

    private static void sortHandlers() {
        HANDLERS.sort(Comparator.comparingInt(PrioritizedHandler::priority).reversed());
    }

    /** Java 8 下替代 record 的不可变优先级值。 */
    static final class PrioritizedHandler {
        private final MovementModeHandler handler;
        private final int priority;

        PrioritizedHandler(MovementModeHandler handler, int priority) {
            this.handler = handler;
            this.priority = priority;
        }

        MovementModeHandler handler() { return handler; }
        int priority() { return priority; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PrioritizedHandler)) return false;
            PrioritizedHandler that = (PrioritizedHandler) other;
            return priority == that.priority && handler.equals(that.handler);
        }

        @Override
        public int hashCode() {
            return 31 * handler.hashCode() + priority;
        }

        @Override
        public String toString() {
            return "PrioritizedHandler[handler=" + handler + ", priority=" + priority + ']';
        }
    }

    public static final class RegisterMovementModeEvent extends Event {
        private RegisterMovementModeEvent() {
        }
    }
}
