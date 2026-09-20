package com.rtsbuilding.rtsbuilding.server.storage.state;

/**
 * 掉落缓存的纯容量策略，不依赖 Minecraft 注册表或 ItemStack 静态初始化。
 * 服务端状态和纯 JUnit 边界测试必须共用这里的判断，避免测试夹具绕过真实背压规则。
 */
public final class RtsMiningDropBufferPolicy {
    public static final int MAX_BUFFERED_ITEMS = 4096;
    public static final int MAX_STACKS = 4096;

    private RtsMiningDropBufferPolicy() {
    }

    public static int remainingCapacity(int bufferedItems) {
        return remainingCapacity(bufferedItems, configuredCapacity());
    }

    public static boolean isFull(int bufferedItems, int stackCount) {
        return isFull(bufferedItems, stackCount, configuredCapacity(), MAX_STACKS);
    }
    public static int remainingCapacity(int bufferedItems, int maxBufferedItems) {
        int capacity = Math.max(1, maxBufferedItems);
        if (bufferedItems >= capacity) return 0;
        return capacity - Math.max(0, bufferedItems);
    }
    public static boolean isFull(int bufferedItems, int stackCount, int maxBufferedItems, int maxStacks) {
        return bufferedItems >= Math.max(1, maxBufferedItems) || stackCount >= Math.max(1, maxStacks);
    }
    /** 持久化恢复使用物理硬上限，不因本次配置调低而丢弃已接纳的栈。 */
    public static boolean canRestorePersistedStack(int currentStackCount) {
        return currentStackCount >= 0 && currentStackCount < MAX_STACKS;
    }
    private static int configuredCapacity() {
        try { return com.rtsbuilding.rtsbuilding.Config.dropCacheSoftCapacity(); }
        catch (IllegalStateException ignored) { return MAX_BUFFERED_ITEMS; }
    }
}
