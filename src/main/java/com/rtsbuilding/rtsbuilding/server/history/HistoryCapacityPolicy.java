package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 单次历史的统一容量门禁；超限时拒绝整条记录，避免截断后产生误导性撤回。 */
public final class HistoryCapacityPolicy {
    private HistoryCapacityPolicy() {
    }

    public static boolean accepts(List<HistoryBlockRecord> records) {
        if (records == null || records.isEmpty()
                || records.size() > RtsHistoryConstants.MAX_BLOCKS_PER_ENTRY) {
            return false;
        }
        ListTag blockEntities = new ListTag();
        for (HistoryBlockRecord record : records) {
            if (record.blockEntityData() != null) {
                blockEntities.add(record.blockEntityData().copy());
            }
        }
        if (blockEntities.isEmpty()) {
            return true;
        }
        CompoundTag root = new CompoundTag();
        root.put("blockEntities", blockEntities);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, output);
            return output.size() <= RtsHistoryConstants.MAX_COMPRESSED_NBT_BYTES_PER_ENTRY;
        } catch (IOException exception) {
            return false;
        }
    }
}
