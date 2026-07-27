package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class GhostRingBuffer {

    public static final int CAPACITY = 32;

    private final long[] keys = new long[CAPACITY];
    private final BlockState[] states = new BlockState[CAPACITY];
    private final long[] addedAtMs = new long[CAPACITY];
    private final boolean[] active = new boolean[CAPACITY];
    private int head;
    private int count;

    
    public void add(BlockPos pos, BlockState state, long nowMs) {
        long key = pos.asLong();
        
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

    
    public void forEach(SlotConsumer consumer) {
        int idx = (head - count) & (CAPACITY - 1);
        for (int i = 0; i < count; i++) {
            int slot = (idx + i) & (CAPACITY - 1);
            if (active[slot]) {
                consumer.accept(keys[slot], states[slot], addedAtMs[slot]);
            }
        }
    }

    
    public void prune(long nowMs, long maxAgeMs) {
        for (int i = 0; i < CAPACITY; i++) {
            if (active[i] && (nowMs - addedAtMs[i]) > maxAgeMs) {
                active[i] = false;
                count = Math.max(0, count - 1);
            }
        }
    }

    
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
