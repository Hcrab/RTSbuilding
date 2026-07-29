package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 单次历史的统一容量门禁；任何超限都拒绝整条记录。 */
public final class HistoryCapacityPolicy {
    private HistoryCapacityPolicy() {
    }

    public static boolean accepts(List<HistoryBlockRecord> records) {
        return accepts(records, RtsHistoryConstants.MAX_BLOCKS_PER_ENTRY,
                RtsHistoryConstants.MAX_COMPRESSED_NBT_BYTES_PER_ENTRY);
    }

    /** 可注入阈值的纯策略入口；生产调用统一使用无阈值重载，测试可用小阈值覆盖边界。 */
    public static boolean accepts(
            List<HistoryBlockRecord> records, int maxBlocks, int maxCompressedNbtBytes) {
        if (records == null || records.isEmpty()
                || records.size() > maxBlocks) {
            return false;
        }
        ListTag blockEntities = new ListTag();
        for (HistoryBlockRecord record : records) {
            if (record.blockEntityData() != null) blockEntities.add(record.blockEntityData().copy());
            if (record.afterBlockEntityData() != null) {
                blockEntities.add(record.afterBlockEntityData().copy());
            }
        }
        if (blockEntities.isEmpty()) return true;
        CompoundTag root = new CompoundTag();
        root.put("blockEntities", blockEntities);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, output);
            return output.size() <= maxCompressedNbtBytes;
        } catch (IOException exception) {
            return false;
        }
    }
}
