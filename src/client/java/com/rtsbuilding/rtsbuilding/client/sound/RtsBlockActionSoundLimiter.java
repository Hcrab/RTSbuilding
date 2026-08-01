package com.rtsbuilding.rtsbuilding.client.sound;

/**
 * 客户端方块操作音效的逐 tick 计数器。
 *
 * <p>超过上限的声音立即丢弃，不保存任何待播状态。</p>
 */
final class RtsBlockActionSoundLimiter {
    private long gameTick = Long.MIN_VALUE;
    private int used;

    boolean tryAcquire(long currentGameTick, int limit) {
        if (limit <= 0) {
            return false;
        }
        if (currentGameTick != this.gameTick) {
            this.gameTick = currentGameTick;
            this.used = 0;
        }
        if (this.used >= limit) {
            return false;
        }
        this.used++;
        return true;
    }
}
