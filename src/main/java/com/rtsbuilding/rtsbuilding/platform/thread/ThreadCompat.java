package com.rtsbuilding.rtsbuilding.platform.thread;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 为 1.7.10 补齐后续版本的客户端/服务端主线程调度语义。
 *
 * <p>1.7.10 的 SimpleNetworkWrapper 会在 Netty 线程调用处理器，而原版服务端没有
 * addScheduledTask。服务端任务因此先进入无锁队列，再由 ServerTickEvent 在主线程排空；
 * 客户端则使用原版的 func_152344_a。此类不执行任何游戏逻辑，只保证线程归属。</p>
 */
public final class ThreadCompat {
    private static final ConcurrentLinkedQueue<Runnable> SERVER_TASKS =
            new ConcurrentLinkedQueue<Runnable>();
    private static final int MAX_TASKS_PER_TICK = 16384;
    private static volatile Thread serverThread;

    private ThreadCompat() {}

    public static void scheduleServer(EntityPlayerMP player, Runnable task) {
        scheduleServer(task);
    }

    public static void scheduleServer(Runnable task) {
        if (task != null) SERVER_TASKS.offer(task);
    }

    public static void scheduleClient(Runnable task) {
        if (task == null) return;
        try {
            Class<?> bridge = Class.forName(
                    "com.rtsbuilding.rtsbuilding.platform.thread.client.ClientThreadCompat");
            bridge.getMethod("schedule", Runnable.class).invoke(null, task);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法把 RTS 客户端任务切回主线程", failure);
        }
    }

    public static void schedule(MessageContext context, Runnable task) {
        if (context != null && context.side == Side.CLIENT) scheduleClient(task);
        else scheduleServer(task);
    }

    /** 只能由服务端主线程的 tick 事件调用。 */
    public static void drainServerTasks() {
        serverThread = Thread.currentThread();
        int processed = 0;
        Runnable task;
        while (processed < MAX_TASKS_PER_TICK && (task = SERVER_TASKS.poll()) != null) {
            task.run();
            processed++;
        }
    }

    public static void clearServerTasks() {
        SERVER_TASKS.clear();
    }

    /**
     * 1.7.10 服务端没有 isCallingFromMinecraftThread；由服务端 tick 首次经过时记录真实线程。
     * 未记录前宁可返回 false，让调用方走安全调度路径。
     */
    public static boolean isServerThread() {
        return Thread.currentThread() == serverThread;
    }
}
