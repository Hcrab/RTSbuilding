package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 玩家级别的非会话 {@link DataComponent} 注册表。
 *
 * <p>与 {@link SessionComponents} 不同，此处的组件不归属于存储会话，
 * 而是每个玩家独立的次要数据（如插件、装备栏、进度等）。
 * 所有组件写入同一份 {@code session.dat}，统一由 {@link SaveScheduler} 管理。
 */
public final class PlayerComponents {

    /** 已安装插件列表——CompoundTag 桥接，兼容 {@code RtsPluginPersistence} 格式 */
    public static final DataComponent<CompoundTag> PLUGINS = bridge("plugins");

    /** 挖掘装备栏绑定——CompoundTag 桥接，兼容 {@code MiningLoadoutState} 格式 */
    public static final DataComponent<CompoundTag> MINING_LOADOUT = bridge("mining_loadout");

    /** 玩家进度数据——CompoundTag 桥接，兼容 {@code RtsProgressionPersistence} 格式 */
    public static final DataComponent<CompoundTag> PROGRESSION = bridge("progression");

    /** 创建直通桥接组件 */
    private static DataComponent<CompoundTag> bridge(String key) {
        return new DataComponent<>(
                key,
                NbtCodec.of(
                        tag -> tag,
                        (tag, v) -> {
                            for (String k : v.keySet()) {
                                tag.put(k, v.get(k));
                            }
                        }
                ),
                CompoundTag::new
        );
    }

    private PlayerComponents() {
    }
}
