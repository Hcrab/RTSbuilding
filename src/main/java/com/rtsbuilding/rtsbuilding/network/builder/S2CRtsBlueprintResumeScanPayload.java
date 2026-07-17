package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：蓝图材料扫描结果。
 * <p>
 * 覆盖蓝图剩余方块所需的每种材料及其可用性统计。
 *
 * @param itemIds          材料物品 ID 列表
 * @param itemLabels       材料物品显示名称列表
 * @param required         每种材料剩余需求数列表
 * @param available        每种材料当前存储可用数列表
 * @param workflowEntryId  目标工作流条目 ID
 * @param completedCount   已放置方块数
 * @param totalCount       蓝图总方块数
 */
public record S2CRtsBlueprintResumeScanPayload(
        List<String> itemIds,
        List<String> itemLabels,
        List<Integer> required,
        List<Long> available,
        int workflowEntryId,
        int completedCount,
        int totalCount
) implements CustomPacketPayload {
    public static final Type<S2CRtsBlueprintResumeScanPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_blueprint_resume_scan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsBlueprintResumeScanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.itemIds().size());
                for (int i = 0; i < payload.itemIds().size(); i++) {
                    buf.writeUtf(payload.itemIds().get(i));
                    buf.writeUtf(payload.itemLabels().get(i));
                    buf.writeInt(payload.required().get(i));
                    buf.writeLong(payload.available().get(i));
                }
                buf.writeInt(payload.workflowEntryId());
                buf.writeInt(payload.completedCount());
                buf.writeInt(payload.totalCount());
            },
            (buf) -> {
                int size = buf.readInt();
                List<String> itemIds = new ArrayList<>(size);
                List<String> itemLabels = new ArrayList<>(size);
                List<Integer> required = new ArrayList<>(size);
                List<Long> available = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    itemIds.add(buf.readUtf());
                    itemLabels.add(buf.readUtf());
                    required.add(buf.readInt());
                    available.add(buf.readLong());
                }
                return new S2CRtsBlueprintResumeScanPayload(
                        itemIds, itemLabels, required, available,
                        buf.readInt(), buf.readInt(), buf.readInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
