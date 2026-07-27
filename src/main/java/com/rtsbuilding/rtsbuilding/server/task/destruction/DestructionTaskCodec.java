package com.rtsbuilding.rtsbuilding.server.task.destruction;

import com.rtsbuilding.rtsbuilding.server.task.DestructionTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.persistence.DimensionIdCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/** DestructionTaskPayload 的有界、版本化 NBT 编解码器。 */
public final class DestructionTaskCodec {
    public static final int SCHEMA_VERSION = 1;

    private DestructionTaskCodec() {
    }

    public static NBTTagCompound encode(DestructionTaskPayload payload) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", SCHEMA_VERSION);
        NbtCompat.setUuid(tag, "owner", payload.ownerId());
        tag.setString("dimension", DimensionIdCodec.fromDimension(payload.dimension()));
        tag.setInteger("workflow", payload.workflowEntryId());

        DestructionTaskState state = payload.state();
        NbtCompat.setLongArray(tag, "targets", encodePositions(state.targets()));
        tag.setByte("toolSlot", state.toolSlot());
        tag.setBoolean("toolProtection", state.toolProtectionEnabled());
        tag.setBoolean("selectedTool", state.selectedToolRequested());
        tag.setInteger("cursor", state.cursorUnits());
        tag.setInteger("succeeded", state.succeededUnits());
        tag.setInteger("failed", state.failedUnits());
        NbtCompat.setLongArray(tag, "destroyed", encodePositions(state.destroyedPositions()));
        NBTTagList history = new NBTTagList();
        for (NBTTagCompound entry : state.historyRecords()) history.appendTag(entry);
        tag.setTag("history", history);
        return tag;
    }

    public static DestructionTaskPayload decode(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("schema") != SCHEMA_VERSION || !NbtCompat.hasUuid(tag, "owner")) {
            throw new IllegalArgumentException("不支持或不完整的 destruction task payload");
        }
        requireType(tag, "dimension", Constants.NBT.TAG_STRING);
        requireType(tag, "workflow", Constants.NBT.TAG_INT);
        requireType(tag, "targets", Constants.NBT.TAG_LIST);
        requireType(tag, "toolSlot", Constants.NBT.TAG_BYTE);
        requireType(tag, "toolProtection", Constants.NBT.TAG_BYTE);
        requireType(tag, "selectedTool", Constants.NBT.TAG_BYTE);
        requireType(tag, "cursor", Constants.NBT.TAG_INT);
        requireType(tag, "succeeded", Constants.NBT.TAG_INT);
        requireType(tag, "failed", Constants.NBT.TAG_INT);
        requireType(tag, "destroyed", Constants.NBT.TAG_LIST);
        requireType(tag, "history", Constants.NBT.TAG_LIST);

        String dimensionId = tag.getString("dimension");
        if (!DimensionIdCodec.isCanonical(dimensionId)) {
            throw new IllegalArgumentException("destruction task 维度无效");
        }
        int dimension = DimensionIdCodec.toDimension(dimensionId);

        List<BlockPos> targets = decodePositions(NbtCompat.getLongArray(tag, "targets"),
                DestructionTaskState.MAX_TARGETS, "targets");
        if (targets.isEmpty()) throw new IllegalArgumentException("destruction targets 不能为空");
        List<BlockPos> destroyed = decodePositions(NbtCompat.getLongArray(tag, "destroyed"),
                targets.size(), "destroyed");
        NBTTagList encodedHistory = tag.getTagList("history", Constants.NBT.TAG_COMPOUND);
        long maxHistory = (long) targets.size() * DestructionTaskState.MAX_HISTORY_RECORDS_PER_TARGET;
        if (encodedHistory.tagCount() > maxHistory) {
            throw new IllegalArgumentException("destruction history 超过有界上限");
        }
        List<NBTTagCompound> history = new ArrayList<NBTTagCompound>(encodedHistory.tagCount());
        for (int i = 0; i < encodedHistory.tagCount(); i++) {
            NBTTagCompound record = encodedHistory.getCompoundTagAt(i);
            requireType(record, "pos", Constants.NBT.TAG_LONG);
            requireType(record, "state", Constants.NBT.TAG_COMPOUND);
            if (record.hasKey("blockEntity") && !record.hasKey("blockEntity", Constants.NBT.TAG_COMPOUND)) {
                throw new IllegalArgumentException("destruction history blockEntity 类型无效");
            }
            history.add(record.copy());
        }

        int workflow = tag.getInteger("workflow");
        DestructionTaskState state = new DestructionTaskState(
                targets,
                tag.getByte("toolSlot"),
                tag.getBoolean("toolProtection"),
                tag.getBoolean("selectedTool"),
                workflow,
                tag.getInteger("cursor"),
                tag.getInteger("succeeded"),
                tag.getInteger("failed"),
                destroyed,
                history);
        return new DestructionTaskPayload(NbtCompat.getUuid(tag, "owner"), dimension, workflow, state);
    }

    private static List<BlockPos> decodePositions(long[] encoded, int max, String field) {
        if (encoded.length > max) throw new IllegalArgumentException("destruction " + field + " 越界");
        List<BlockPos> positions = new ArrayList<>(encoded.length);
        for (long value : encoded) positions.add(BlockPos.fromLong(value));
        return positions;
    }

    private static long[] encodePositions(List<BlockPos> positions) {
        long[] encoded = new long[positions.size()];
        for (int i = 0; i < encoded.length; i++) encoded[i] = positions.get(i).toLong();
        return encoded;
    }

    private static void requireType(NBTTagCompound tag, String key, int type) {
        if (!tag.hasKey(key, type)) {
            throw new IllegalArgumentException("destruction payload 字段类型无效: " + key);
        }
    }
}
