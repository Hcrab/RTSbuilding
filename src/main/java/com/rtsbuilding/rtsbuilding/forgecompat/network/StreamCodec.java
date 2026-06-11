package com.rtsbuilding.rtsbuilding.forgecompat.network;


import java.util.function.BiConsumer;
import java.util.function.Function;

public final class StreamCodec<B, T> {
    private final BiConsumer<B, T> encoder;
    private final Function<B, T> decoder;

    private StreamCodec(final BiConsumer<B, T> encoder, final Function<B, T> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public static <B, T> StreamCodec<B, T> of(final BiConsumer<B, T> encoder, final Function<B, T> decoder) {
        return new StreamCodec<>(encoder, decoder);
    }

    public static <B, T> StreamCodec<B, T> unit(final T value) {
        return new StreamCodec<>((buf, payload) -> {
        }, buf -> value);
    }

    public void encode(final B buffer, final T value) {
        this.encoder.accept(buffer, value);
    }

    public T decode(final B buffer) {
        return this.decoder.apply(buffer);
    }
}
