package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 幽灵方块环形缓冲区——固定 32 槽的 ring buffer，零 GC 分配。
 *
 * <p>替代原 {@code HashMap<Long, PendingGhostEntry>} 和每帧的
 * {@code HashMap<BlockState, ArrayList<BlockPos>>} 分组分配。</p>
 *
 * <p>O(1) 插入/遍历，自然淘汰最老条目。</p>
 */
public final class GhostRingBuffer {

    public static final int CAPACITY = 32;

    private final long[] keys = new long[CAPACITY];
    private final BlockState[] states = new BlockState[CAPACITY];
    private final long[] addedAtMs = new long[CAPACITY];
    private final boolean[] active = new boolean[CAPACITY];
    private int head;
    private int count;

    /** 添加一个幽灵方块条目。如果已满，覆盖最旧的。 */
    public void add(BlockPos pos, BlockState state, long nowMs) {
        long key = pos.asLong();
        // 检查是否已存在
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i) & (CAPACITY - 1);
            if (active[idx] && keys[idx] == key) {
                states[idx] = state;
                addedAtMs[idx] = nowMs;
                return;
            }
        }
        keys[head] = key;
        states[head] = state;
        addedAtMs[head] = nowMs;
        active[head] = true;
        head = (head + 1) & (CAPACITY - 1);
        if (count < CAPACITY) count++;
    }

    /** 遍历所有活跃条目。每帧调用，零分配。 */
    public void forEach(SlotConsumer consumer) {
        int idx = (head - count) & (CAPACITY - 1);
        for (int i = 0; i < count; i++) {
            int slot = (idx + i) & (CAPACITY - 1);
            if (active[slot]) {
                consumer.accept(keys[slot], states[slot], addedAtMs[slot]);
            }
        }
    }

    /** 移除过期条目（超过 maxAgeMs 未更新）。 */
    public void prune(long nowMs, long maxAgeMs) {
        for (int i = 0; i < CAPACITY; i++) {
            if (active[i] && (nowMs - addedAtMs[i]) > maxAgeMs) {
                active[i] = false;
                count = Math.max(0, count - 1);
            }
        }
    }

    /** 清空所有条目。 */
    public void clear() {
        for (int i = 0; i < CAPACITY; i++) active[i] = false;
        head = 0;
        count = 0;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    @FunctionalInterface
    public interface SlotConsumer {
        void accept(long key, BlockState state, long addedAtMs);
    }
}
