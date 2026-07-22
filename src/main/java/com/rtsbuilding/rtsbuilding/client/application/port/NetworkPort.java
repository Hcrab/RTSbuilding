package com.rtsbuilding.rtsbuilding.client.application.port;

import com.rtsbuilding.rtsbuilding.client.domain.module.capability.NetworkPush;

public interface NetworkPort {
    <T> void send(T packet);
    <T> void registerHandler(Class<T> type, NetworkPush<T> handler);
}
