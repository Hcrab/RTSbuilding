package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.task.MiningTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.persistence.DimensionIdCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.init.Blocks;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/** MiningTaskPayload 的版本化纯 NBT codec，并集中保存历史方块快照格式。 */
public final class MiningTaskCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TARGETS = 32_768;

    private MiningTaskCodec() {
    }

    public static NBTTagCompound encode(MiningTaskPayload payload) {
        MiningTaskState state = payload.state();
        if (state.totalUnits() > MAX_TARGETS) throw new IllegalArgumentException("mining target 数量越界");
        if (state.historyRecords().size() > MAX_TARGETS * 7) {
            throw new IllegalArgumentException("mining history 越界");
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", SCHEMA_VERSION);
        NbtCompat.setUuid(tag, "owner", payload.ownerId());
        tag.setString("dimension", DimensionIdCodec.fromDimension(payload.dimension()));
        tag.setInteger("workflow", payload.workflowEntryId());
        tag.setString("mode", state.mode().name());
        long[] remaining = new long[state.remainingTargets().size()];
        for (int i = 0; i < remaining.length; i++) remaining[i] = state.remainingTargets().get(i).toLong();
        NbtCompat.setLongArray(tag, "remaining", remaining);
        tag.setInteger("total", state.totalUnits());
        tag.setInteger("cursor", state.cursorUnits());
        tag.setInteger("succeeded", state.succeededUnits());
        tag.setInteger("failed", state.failedUnits());
        tag.setByte("face", (byte) state.face().getIndex());
        tag.setInteger("tool_slot", state.toolSlot());
        tag.setBoolean("selected_tool", state.selectedToolRequested());
        tag.setBoolean("protect_tool", state.toolProtectionEnabled());
        tag.setFloat("progress", state.blockProgress());
        tag.setInteger("stage", state.visibleStage());
        NBTTagList history = new NBTTagList();
        for (NBTTagCompound entry : state.historyRecords()) history.appendTag(entry);
        tag.setTag("history", history);
        return tag;
    }

    public static MiningTaskPayload decode(NBTTagCompound tag) {
        requireFields(tag);
        String dimensionId = tag.getString("dimension");
        if (!DimensionIdCodec.isCanonical(dimensionId)) {
            throw new IllegalArgumentException("mining dimension 无效");
        }
        MiningTaskState.Mode mode;
        try {
            mode = MiningTaskState.Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException invalidMode) {
            throw new IllegalArgumentException("mining mode 无效", invalidMode);
        }
        long[] encodedTargets = NbtCompat.getLongArray(tag, "remaining");
        int total = tag.getInteger("total");
        if (total < 0 || total > MAX_TARGETS || encodedTargets.length > total) {
            throw new IllegalArgumentException("mining target 数量越界");
        }
        List<BlockPos> targets = new ArrayList<>(encodedTargets.length);
        for (long encoded : encodedTargets) targets.add(BlockPos.fromLong(encoded));
        NBTTagList encodedHistory = tag.getTagList("history", Constants.NBT.TAG_COMPOUND);
        if (encodedHistory.tagCount() > MAX_TARGETS * 7) throw new IllegalArgumentException("mining history 越界");
        List<NBTTagCompound> history = new ArrayList<NBTTagCompound>(encodedHistory.tagCount());
        for (int i = 0; i < encodedHistory.tagCount(); i++) {
            history.add(com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(
                    encodedHistory.getCompoundTagAt(i)));
        }
        int workflow = tag.getInteger("workflow");
        MiningTaskState state = new MiningTaskState(
                mode, workflow, targets, total,
                tag.getInteger("cursor"), tag.getInteger("succeeded"), tag.getInteger("failed"),
                EnumFacing.byIndex(tag.getByte("face")), tag.getInteger("tool_slot"),
                tag.getBoolean("selected_tool"), tag.getBoolean("protect_tool"),
                tag.getFloat("progress"), tag.getInteger("stage"), history);
        return new MiningTaskPayload(NbtCompat.getUuid(tag, "owner"),
                DimensionIdCodec.toDimension(dimensionId), workflow, state);
    }

    public static NBTTagCompound encodeHistory(HistoryBlockRecord record) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", record.pos().toLong());
        tag.setTag("state", com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat
                .writeBlockState(record.state()));
        if (record.blockEntityData() != null) tag.setTag("block_entity",
                com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(
                        record.blockEntityData()));
        return tag;
    }

    public static HistoryBlockRecord decodeHistory(NBTTagCompound tag) {
        if (tag == null || !NbtCompat.hasType(tag, "pos", Constants.NBT.TAG_LONG)
                || !NbtCompat.hasType(tag, "state", Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("mining history record 不完整");
        }
        BlockState state = com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat
                .readBlockState(tag.getCompoundTag("state"));
        if (state.getBlock() == Blocks.air) throw new IllegalArgumentException("mining history 不能记录空气");
        NBTTagCompound blockEntity = NbtCompat.hasType(tag, "block_entity", Constants.NBT.TAG_COMPOUND)
                ? com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(
                        tag.getCompoundTag("block_entity")) : null;
        return new HistoryBlockRecord(BlockPos.fromLong(tag.getLong("pos")), state, blockEntity);
    }

    private static void requireFields(NBTTagCompound tag) {
        if (tag == null || !NbtCompat.hasType(tag, "schema", Constants.NBT.TAG_INT)
                || tag.getInteger("schema") != SCHEMA_VERSION || !NbtCompat.hasUuid(tag, "owner")
                || !NbtCompat.hasType(tag, "dimension", Constants.NBT.TAG_STRING)
                || !NbtCompat.hasType(tag, "workflow", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "mode", Constants.NBT.TAG_STRING)
                || !NbtCompat.hasType(tag, "remaining", Constants.NBT.TAG_LIST)
                || !NbtCompat.hasType(tag, "total", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "cursor", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "succeeded", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "failed", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "face", Constants.NBT.TAG_BYTE)
                || !NbtCompat.hasType(tag, "tool_slot", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "selected_tool", Constants.NBT.TAG_BYTE)
                || !NbtCompat.hasType(tag, "protect_tool", Constants.NBT.TAG_BYTE)
                || !NbtCompat.hasType(tag, "progress", Constants.NBT.TAG_FLOAT)
                || !NbtCompat.hasType(tag, "stage", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "history", Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException("不支持或不完整的 mining task payload");
        }
    }
}
