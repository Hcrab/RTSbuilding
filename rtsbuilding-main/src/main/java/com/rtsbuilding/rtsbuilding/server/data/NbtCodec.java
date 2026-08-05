package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 类型安全的 NBT 编解码器——将 {@link CompoundTag} 与 Java 对象互相转换。
 *
 * <p>函数式设计，可通过 {@link #of(Function, BiConsumer)} 快捷创建：
 * <pre>{@code
 * NbtCodec<RtsBrowserState> CODEC = NbtCodec.of(
 *     NbtCodecExample::decode,
 *     NbtCodecExample::encode
 * );
 * }</pre>
 *
 * @param <T> 编解码的目标类型
 */
@FunctionalInterface
public interface NbtCodec<T> {

    /**
     * 从 NBT 标签解码出目标对象。
     *
     * @param tag NBT 数据源，永不为 null
     * @return 解码后的对象，解析失败可返回 null
     */
    @Nullable
    T decode(CompoundTag tag);

    /**
     * 将目标对象编码到 NBT 标签中。
     * <p>默认实现抛出 {@link UnsupportedOperationException}，只读 codec 不需要实现此方法。
     *
     * @param tag   目标 NBT 标签
     * @param value 要编码的对象
     */
    default void encode(CompoundTag tag, T value) {
        throw new UnsupportedOperationException("此 NbtCodec 是只读的");
    }

    /**
     * 通过解码和编码函数快速创建双向编解码器。
     *
     * @param decoder 解码函数 {@code (CompoundTag) -> T}
     * @param encoder 编码函数 {@code (CompoundTag, T) -> void}
     * @param <T>     目标类型
     * @return 双向编解码器
     */
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
