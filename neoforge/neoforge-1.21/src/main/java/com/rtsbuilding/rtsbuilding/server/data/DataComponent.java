package com.rtsbuilding.rtsbuilding.server.data;

import java.util.function.Supplier;

/**
 * 数据组件——描述「一段可持久化的数据」的元信息。
 *
 * <p>每个 {@code DataComponent} 代表一个独立可持久化的数据片段，
 * 包含它的唯一键名、编解码方式和默认值工厂。
 *
 * <p>与 {@link DataCluster} 配合使用：
 * <pre>{@code
 * public static final DataComponent<RtsBrowserState> BROWSER = new DataComponent<>(
 *     "browser",
 *     NbtCodec.of(...),
 *     RtsBrowserState::new
 * );
 *
 * DataCluster cluster = ...;
 * RtsBrowserState state = cluster.get(BROWSER);    // 类型安全！
 * cluster.set(BROWSER, newState);                    // 标记脏
 * }</pre>
 *
 * @param <T> 该组件的数据类型
 */
public final class DataComponent<T> {

    private final String key;
    private final NbtCodec<T> codec;
    private final Supplier<T> factory;

    /**
     * @param key     组件的唯一键名，作为 NBT 中的顶层键，如 {@code "browser"}
     * @param codec   编解码器，负责 NBT ↔ T 的转换
     * @param factory 默认值工厂，在文件不存在或解码失败时使用
     */
    public DataComponent(String key, NbtCodec<T> codec, Supplier<T> factory) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("DataComponent key 不能为空");
        }
        this.key = key;
        this.codec = codec;
        this.factory = factory;
    }

    /** 组件的唯一键名 */
    public String key() {
        return key;
    }

    /** 编解码器 */
    public NbtCodec<T> codec() {
        return codec;
    }

    /** 默认值工厂 */
    public Supplier<T> factory() {
        return factory;
    }
}
