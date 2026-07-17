package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 工作流数据（{@code workflow.dat}）的所有 {@link DataComponent} 注册表。
 *
 * <p>每个组件对应 {@link com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager}
 * 的持久化数据。目前通过桥接组件 {@link #FULL_WORKFLOW} 将整个维度→槽位映射
 * 作为原始 NBT 存储。
 */
public final class WorkflowComponents {

    // ──────────────────────────────────────────────────────────────────
    //  全量工作流桥接
    // ──────────────────────────────────────────────────────────────────

    /**
 * 全量工作流桥接组件——将玩家所有维度的工作流槽位管理器序列化为一个 NBT 包。
 *
 * <p>NBT 结构：
 * <pre>
 * {
 *   "dimensions": {
 *     "minecraft:overworld": {slotManager NBT},
 *     "minecraft:the_nether": {slotManager NBT}
 *   }
 * }
 * </pre>
 *
 * <p>槽位管理器的编解码仍委托给
 * {@link com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager#saveToNbt()}
 * 和 {@link com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager#loadFromNbt(CompoundTag)}。
 */
    public static final DataComponent<CompoundTag> FULL_WORKFLOW = new DataComponent<>(
            "workflow",
            NbtCodec.of(
                    tag -> tag,                            // decode: 返回 slot 引用
                    (tag, v) -> {                           // encode: 复制所有键
                        for (String key : v.keySet()) {
                            tag.put(key, v.get(key));
                        }
                    }
            ),
            CompoundTag::new
    );

    private WorkflowComponents() {
    }
}
