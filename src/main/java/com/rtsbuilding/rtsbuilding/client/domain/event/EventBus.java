package com.rtsbuilding.rtsbuilding.client.domain.event;

import java.util.function.Consumer;

import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;

public interface EventBus {
    void publish(StateEvent event);
    void subscribe(Consumer<StateEvent> subscriber);
    void unsubscribe(Consumer<StateEvent> subscriber);
}
