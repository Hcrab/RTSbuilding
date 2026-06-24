package com.rtsbuilding.rtsbuilding.client.render;

/**
 * 全局 GhostRingBuffer 持有者。
 * 供 {@link com.rtsbuilding.rtsbuilding.client.render.pass.GhostBlockPass} 和网络回调共享。
 */
public final class RingBufferHolder {
    public static final GhostRingBuffer INSTANCE = new GhostRingBuffer();
    private RingBufferHolder() {}
}
