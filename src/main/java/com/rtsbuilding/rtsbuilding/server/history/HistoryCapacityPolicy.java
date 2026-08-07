package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 单次历史的统一容量门槛；任何维度超限都拒绝整条记录。 */
public final class HistoryCapacityPolicy {
    private HistoryCapacityPolicy() {}

    public static boolean accepts(List<HistoryBlockRecord> records) {
        return accepts(records, RtsHistoryConstants.MAX_BLOCKS_PER_ENTRY,
                RtsHistoryConstants.MAX_COMPRESSED_NBT_BYTES_PER_ENTRY);
    }

    static boolean accepts(List<HistoryBlockRecord> records, int maxBlocks, int maxCompressedNbtBytes) {
        if (records == null || records.isEmpty() || records.size() > maxBlocks) return false;
        NBTTagList blockEntities = new NBTTagList();
        for (HistoryBlockRecord record : records) {
            NBTTagCompound before = record.blockEntityData();
            NBTTagCompound after = record.afterBlockEntityData();
            if (before != null) blockEntities.appendTag(before);
            if (after != null) blockEntities.appendTag(after);
        }
        if (blockEntities.isEmpty()) return true;
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("blockEntities", blockEntities);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(root, output);
            return output.size() <= maxCompressedNbtBytes;
        } catch (IOException exception) {
            return false;
        }
    }
}
