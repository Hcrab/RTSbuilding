package com.rtsbuilding.rtsbuilding.server.storage.state;

/**
 * 掉落缓存的纯容量策略，不依赖 Minecraft 注册表或 ItemStack 静态初始化。
 * 服务端状态和纯 JUnit 边界测试必须共用这里的判断，避免测试夹具绕过真实背压规则。
 */
public final class RtsMiningDropBufferPolicy {
    public static final int MAX_BUFFERED_ITEMS = 4096;
    public static final int MAX_STACKS = 128;

    private RtsMiningDropBufferPolicy() {
    }

    public static int remainingCapacity(int bufferedItems) {
        return Math.max(0, MAX_BUFFERED_ITEMS - bufferedItems);
    }

    public static boolean isFull(int bufferedItems, int stackCount) {
        return bufferedItems >= MAX_BUFFERED_ITEMS || stackCount >= MAX_STACKS;
    }
}
