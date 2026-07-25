package com.rtsbuilding.rtsbuilding.server.data;

import java.util.function.Supplier;

/**
 * 一段类型安全持久化数据的元信息。
 *
 * <p>组件只描述稳定键名、NBT 编解码器和默认值工厂；加载时机、脏标记和刷盘
 * 都由 {@link DataCluster} 统一管理，业务代码不应自行读写文件。
 */
public final class DataComponent<T> {

    private final String key;
    private final NbtCodec<T> codec;
    private final Supplier<T> factory;

    public DataComponent(String key, NbtCodec<T> codec, Supplier<T> factory) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("DataComponent key 不能为空");
        }
        this.key = key;
        this.codec = codec;
        this.factory = factory;
    }

    public String key() {
        return key;
    }

    public NbtCodec<T> codec() {
        return codec;
    }

    public Supplier<T> factory() {
        return factory;
    }
}
