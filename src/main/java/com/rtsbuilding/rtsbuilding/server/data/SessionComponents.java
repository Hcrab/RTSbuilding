package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.nbt.CompoundTag;

/**
 * 存储会话（{@code session.dat}）的所有 {@link DataComponent} 注册表。
 *
 * <p>每个组件对应 {@link com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession}
 * 中一个独立可持久化的子模块。组件之间互相独立、独立脏标记。
 */
public final class SessionComponents {

    // ==================================================================
    //  复合桥接组件（CompoundTag 桥接）
    // ==================================================================

    /** 浏览状态——翻页、搜索、分类、排序 */
    public static final DataComponent<CompoundTag> BROWSER = bridge("browser");

    /** 会话标志——autoStore、useBdNetwork、内部流体 */
    public static final DataComponent<CompoundTag> FLAGS = bridge("flags");

    /** 建造模式 */
    public static final DataComponent<BuilderMode> MODE = new DataComponent<>(
            "mode",
            NbtCodec.of(
                    tag -> {
                        String name = tag.getString("mode");
                        try {
                            return name.isEmpty() ? BuilderMode.INTERACT : BuilderMode.valueOf(name);
                        } catch (IllegalArgumentException e) {
                            return BuilderMode.INTERACT;
                        }
                    },
                    (tag, v) -> tag.putString("mode", v.name())
            ),
            () -> BuilderMode.INTERACT
    );

    /** 链接存储——引用、模式、优先级、背包 */
    public static final DataComponent<CompoundTag> LINKED_STORAGE = bridge("linked");

    /** UI 记忆——近期条目、快捷槽、GUI 绑定 */
    public static final DataComponent<CompoundTag> UI_MEMORY = bridge("ui_memory");

    /** 放置任务——待处理 + 进行中 */
    public static final DataComponent<CompoundTag> PLACEMENT = bridge("placement");

    /** 破坏任务——活跃 + 挂起 */
    public static final DataComponent<CompoundTag> DESTROY = bridge("destroy");

    /** 自动存入挖掘掉落的持久缓存。 */
    public static final DataComponent<CompoundTag> DROP_BUFFER = bridge("drop_buffer");

    // ==================================================================
    //  工具
    // ==================================================================

    /** 创建一个 {@link CompoundTag} 直通桥接组件 */
    private static DataComponent<CompoundTag> bridge(String key) {
        return new DataComponent<>(
                key,
                NbtCodec.of(
                        tag -> tag,
                        (tag, v) -> {
                            for (String k : v.getAllKeys()) {
                                tag.put(k, v.get(k));
                            }
                        }
                ),
                CompoundTag::new
        );
    }

    private SessionComponents() {
    }
}
