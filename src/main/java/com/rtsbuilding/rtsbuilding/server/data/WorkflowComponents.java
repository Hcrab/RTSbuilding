package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 宸ヤ綔娴佹暟鎹紙{@code workflow.dat}锛夌殑鎵€鏈?{@link DataComponent} 娉ㄥ唽琛ㄣ€?
 *
 * <p>姣忎釜缁勪欢瀵瑰簲 {@link com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager}
 * 鐨勬寔涔呭寲鏁版嵁銆傜洰鍓嶉€氳繃妗ユ帴缁勪欢 {@link #FULL_WORKFLOW} 灏嗘暣涓淮搴︹啋妲戒綅鏄犲皠
 * 浣滀负鍘熷 NBT 瀛樺偍銆?
 */
public final class WorkflowComponents {

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    //  鍏ㄩ噺宸ヤ綔娴佹ˉ鎺?
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
 * 鍏ㄩ噺宸ヤ綔娴佹ˉ鎺ョ粍浠垛€斺€斿皢鐜╁鎵€鏈夌淮搴︾殑宸ヤ綔娴佹Ы浣嶇鐞嗗櫒搴忓垪鍖栦负涓€涓?NBT 鍖呫€?
 *
 * <p>NBT 缁撴瀯锛?
 * <pre>
 * {
 *   "dimensions": {
 *     "minecraft:overworld": {slotManager NBT},
 *     "minecraft:the_nether": {slotManager NBT}
 *   }
 * }
 * </pre>
 *
 * <p>妲戒綅绠＄悊鍣ㄧ殑缂栬В鐮佷粛濮旀墭缁?
 * {@link com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager#saveToNbt()}
 * 鍜?{@link com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager#loadFromNbt(CompoundTag)}銆?
 */
    public static final DataComponent<CompoundTag> FULL_WORKFLOW = new DataComponent<>(
            "workflow",
            NbtCodec.of(
                    tag -> tag,                            // decode: 杩斿洖 slot 寮曠敤
                    (tag, v) -> {                           // encode: 澶嶅埗鎵€鏈夐敭
                        for (String key : v.getAllKeys()) {
                            tag.put(key, v.get(key));
                        }
                    }
            ),
            CompoundTag::new
    );

    private WorkflowComponents() {
    }
}

