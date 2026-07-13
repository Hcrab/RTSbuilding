package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 自动存入的有界中间缓存。
 *
 * <p>它接住真实掉落并保留完整 ItemStack 组件；不负责解析储存或发包。缓存故意不写入普通
 * Session NBT，退出服务器前必须由服务层回退到背包或世界，避免复制物品。</p>
 */
public final class RtsMiningDropBufferState {
    public static final int MAX_BUFFERED_ITEMS = RtsMiningDropBufferPolicy.MAX_BUFFERED_ITEMS;
    public static final int MAX_STACKS = RtsMiningDropBufferPolicy.MAX_STACKS;

    public final Deque<ItemStack> stacks = new ArrayDeque<>();
    public int bufferedItems;
    public long firstQueuedGameTime = -1L;
    public boolean fullNoticeSent;

    public int remainingCapacity() {
        return RtsMiningDropBufferPolicy.remainingCapacity(bufferedItems);
    }

    public boolean isFull() {
        return RtsMiningDropBufferPolicy.isFull(bufferedItems, stacks.size());
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public void clearTimingWhenEmpty() {
        if (stacks.isEmpty()) {
            bufferedItems = 0;
            firstQueuedGameTime = -1L;
            fullNoticeSent = false;
        }
    }
}
