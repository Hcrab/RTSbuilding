package com.rtsbuilding.rtsbuilding.client.infrastructure.network.adapter;

import com.rtsbuilding.rtsbuilding.client.application.port.NetworkPort;
import com.rtsbuilding.rtsbuilding.client.domain.module.capability.NetworkPush;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientNetworkAdapter implements NetworkPort {
    private final Map<Class<?>, List<NetworkPush<?>>> handlers = new HashMap<>();

    @Override
    public <T> void send(T packet) {
        // C2S sending — delegated to ServerNetworkAdapter
    }

    @Override
    public <T> void registerHandler(Class<T> type, NetworkPush<T> handler) {
        handlers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void dispatch(T packet) {
        var list = handlers.get(packet.getClass());
        if (list != null) {
            for (var h : list) {
                ((NetworkPush<T>) h).onPacket(packet);
            }
        }
    }
}
