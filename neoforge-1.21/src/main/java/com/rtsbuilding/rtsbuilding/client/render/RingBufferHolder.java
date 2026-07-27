package com.rtsbuilding.rtsbuilding.client.render;

public final class RingBufferHolder {
    public static final GhostRingBuffer INSTANCE = new GhostRingBuffer();
    private RingBufferHolder() {}
}
