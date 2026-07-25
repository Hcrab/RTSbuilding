package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * {@link CompoundTag} 与业务值对象之间的类型安全编解码端口。
 *
 * <p>它不负责文件位置、原子替换或调度；这些职责留给数据簇和存储实现。
 */
@FunctionalInterface
public interface NbtCodec<T> {

    @Nullable
    T decode(CompoundTag tag);

    default void encode(CompoundTag tag, T value) {
        throw new UnsupportedOperationException("此 NbtCodec 是只读的");
    }

    static <T> NbtCodec<T> of(Function<CompoundTag, T> decoder, BiConsumer<CompoundTag, T> encoder) {
        return new NbtCodec<>() {
            @Override
            public T decode(CompoundTag tag) {
                return decoder.apply(tag);
            }

            @Override
            public void encode(CompoundTag tag, T value) {
                encoder.accept(tag, value);
            }
        };
    }
}
