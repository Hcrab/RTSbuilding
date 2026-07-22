package com.rtsbuilding.rtsbuilding.client.application.service;

import com.rtsbuilding.rtsbuilding.client.domain.event.EventBus;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBusImpl implements EventBus {
    private final List<Consumer<StateEvent>> subscribers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(StateEvent event) {
        for (var sub : subscribers) {
            sub.accept(event);
        }
    }

    @Override
    public void subscribe(Consumer<StateEvent> subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void unsubscribe(Consumer<StateEvent> subscriber) {
        subscribers.remove(subscriber);
    }
}
