package com.rtsbuilding.rtsbuilding.client.application.service;

import com.rtsbuilding.rtsbuilding.client.domain.event.EventBus;
import com.rtsbuilding.rtsbuilding.client.domain.module.ModuleState;
import com.rtsbuilding.rtsbuilding.client.domain.module.capability.EventReactive;
import com.rtsbuilding.rtsbuilding.client.domain.module.capability.NetworkPush;
import com.rtsbuilding.rtsbuilding.client.domain.module.capability.RenderFrameAware;
import com.rtsbuilding.rtsbuilding.client.domain.module.capability.Tickable;
import com.rtsbuilding.rtsbuilding.client.domain.time.Clock;

import java.util.*;
import java.util.function.Supplier;

public final class ModuleManager {
    private final Map<String, Object> modules = new LinkedHashMap<>();
    private final Map<String, ModuleState> states = new HashMap<>();

    private final List<Tickable> tickables = new ArrayList<>();
    private final List<EventReactive> eventReactives = new ArrayList<>();
    private final Map<Class<?>, List<NetworkPush<?>>> networkHandlers = new HashMap<>();
    private final List<RenderFrameAware> renderAware = new ArrayList<>();

    private final EventBus eventBus;
    private final Clock clock;

    public ModuleManager(EventBus eventBus, Clock clock) {
        this.eventBus = eventBus;
        this.clock = clock;
    }

    public void register(String id, Supplier<Object> factory) {
        registerInstance(id, factory.get());
    }

    public void registerInstance(String id, Object module) {
        modules.put(id, module);
        states.put(id, ModuleState.ON);

        if (module instanceof Tickable t) tickables.add(t);
        if (module instanceof EventReactive e) {
            eventReactives.add(e);
            eventBus.subscribe(e::onEvent);
        }
        if (module instanceof RenderFrameAware r) renderAware.add(r);
    }

    @SuppressWarnings("unchecked")
    public <T> T module(Class<T> type) {
        for (var m : modules.values()) {
            if (type.isInstance(m)) return (T) m;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T module(String id) {
        return (T) modules.get(id);
    }

    public void tick() {
        for (var t : tickables) {
            t.tick(clock);
        }
    }

    public void onRenderFrame(float partialTick) {
        for (var r : renderAware) {
            r.onRenderFrame(partialTick);
        }
    }

    public <T> void registerNetworkHandler(Class<T> type, NetworkPush<T> handler) {
        networkHandlers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void dispatchPacket(T packet) {
        var handlers = networkHandlers.get(packet.getClass());
        if (handlers != null) {
            for (var h : handlers) {
                ((NetworkPush<T>) h).onPacket(packet);
            }
        }
    }

    public int moduleCount() {
        return modules.size();
    }

    public int tickableCount() {
        return tickables.size();
    }

    public int networkHandlerCount() {
        return networkHandlers.size();
    }
}
