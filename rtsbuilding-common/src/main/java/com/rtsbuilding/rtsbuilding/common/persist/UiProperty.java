package com.rtsbuilding.rtsbuilding.common.persist;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record UiProperty(Scope scope, String key, Serializer<?> serializer) {

    public enum Scope {GLOBAL, SESSION}

    public void applyToSnapshot(UiSnapshot snap) {
        serializer.accept(snap, true);
    }

    public void applyToRuntime(UiSnapshot snap) {
        serializer.accept(snap, false);
    }

    @FunctionalInterface
    public interface Serializer<T> {
        void accept(UiSnapshot snap, boolean toSnapshot);
    }

    public static <T> UiProperty of(Scope scope, String key,
                                    Function<UiSnapshot, T> snapGet,
                                    BiConsumer<UiSnapshot, T> snapSet,
                                    Supplier<T> liveGet,
                                    Consumer<T> liveSet) {
        return new UiProperty(scope, key, (snap, toSnapshot) -> {
            if (toSnapshot) {
                snapSet.accept(snap, liveGet.get());
            } else {
                liveSet.accept(snapGet.apply(snap));
            }
        });
    }
}
