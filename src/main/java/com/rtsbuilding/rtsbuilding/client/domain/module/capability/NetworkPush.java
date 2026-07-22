package com.rtsbuilding.rtsbuilding.client.domain.module.capability;

public interface NetworkPush<T> {
    void onPacket(T packet);
}
