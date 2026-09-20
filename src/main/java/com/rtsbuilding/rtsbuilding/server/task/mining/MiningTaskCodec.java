package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.server.data.RtsDimensionKeys;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.HistoryRecordCodec;
import com.rtsbuilding.rtsbuilding.server.task.MiningTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** MiningTaskPayload 的版本化纯 NBT codec，并集中保存历史方块快照格式。 */
public final class MiningTaskCodec {
    public static final int SCHEMA_VERSION = 3;
    /** 保留 codec 对外常量名，实际边界与任务状态/选区共用。 */
    public static final int MAX_TARGETS = MiningTaskState.MAX_TARGETS;

    private MiningTaskCodec() {
    }

    public static CompoundTag encode(MiningTaskPayload payload) {
        MiningTaskState state = payload.state();
        if (state.totalUnits() > MAX_TARGETS) throw new IllegalArgumentException("mining target 数量越界");
        if (state.historyRecords().size()
                > (long) state.totalUnits() * MiningTaskState.MAX_HISTORY_RECORDS_PER_TARGET) {
            throw new IllegalArgumentException("mining history 越界");
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("owner", payload.ownerId());
        tag.putString("dimension", payload.dimension().location().toString());
        tag.putInt("workflow", payload.workflowEntryId());
        tag.putString("mode", state.mode().name());
        tag.putLongArray("remaining", state.remainingTargets().stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("total", state.totalUnits());
        tag.putInt("cursor", state.cursorUnits());
        tag.putInt("succeeded", state.succeededUnits());
        tag.putInt("failed", state.failedUnits());
        tag.putByte("face", (byte) state.face().get3DDataValue());
        tag.putInt("tool_slot", state.toolSlot());
        tag.putBoolean("selected_tool", state.selectedToolRequested());
        tag.putBoolean("protect_tool", state.toolProtectionEnabled());
        tag.putBoolean("creative_operation", state.creativeOperation());
        tag.putFloat("progress", state.blockProgress());
        tag.putInt("stage", state.visibleStage());
        HistoryRecordCodec.encode(tag, "history", "history_positions", state.historyRecords());
        return tag;
    }

    public static MiningTaskPayload decode(CompoundTag tag) {
        requireFields(tag);
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimensionId == null || !dimensionId.toString().equals(tag.getString("dimension"))) {
            throw new IllegalArgumentException("mining dimension 无效");
        }
        MiningTaskState.Mode mode;
        try {
            mode = MiningTaskState.Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException invalidMode) {
            throw new IllegalArgumentException("mining mode 无效", invalidMode);
        }
        long[] encodedTargets = tag.getLongArray("remaining");
        int total = tag.getInt("total");
        if (total < 0 || total > MAX_TARGETS || encodedTargets.length > total) {
            throw new IllegalArgumentException("mining target 数量越界");
        }
        List<BlockPos> targets = new ArrayList<>(encodedTargets.length);
        for (long encoded : encodedTargets) targets.add(BlockPos.of(encoded).immutable());
        List<CompoundTag> history = HistoryRecordCodec.decode(
                tag, "history", "history_positions",
                (int) Math.min(Integer.MAX_VALUE,
                        (long) total * MiningTaskState.MAX_HISTORY_RECORDS_PER_TARGET),
                tag.getInt("schema") >= 3);
        for (CompoundTag record : history) {
            validateCredential(record, "credential_before");
            validateCredential(record, "credential_after");
            validateCredential(record, "credentialBefore");
            validateCredential(record, "credentialAfter");
        }
        int workflow = tag.getInt("workflow");
        MiningTaskState state = new MiningTaskState(
                mode, workflow, targets, total,
                tag.getInt("cursor"), tag.getInt("succeeded"), tag.getInt("failed"),
                Direction.from3DDataValue(tag.getByte("face")), tag.getInt("tool_slot"),
                tag.getBoolean("selected_tool"), tag.getBoolean("protect_tool"),
                tag.getFloat("progress"), tag.getInt("stage"), history,
                tag.getInt("schema") >= 2 && tag.getBoolean("creative_operation"));
        ResourceKey<Level> dimension = RtsDimensionKeys.create(dimensionId);
        return new MiningTaskPayload(tag.getUUID("owner"), dimension, workflow, state);
    }

    public static CompoundTag encodeHistory(HistoryBlockRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", record.pos().asLong());
        tag.put("state", NbtUtils.writeBlockState(record.state()));
        if (record.blockEntityData() != null) tag.put("block_entity", record.blockEntityData().copy());
        if (record.credentialBefore() != null) {
            tag.put("credential_before", PlacedBlockTrackerData.encodeSnapshot(record.credentialBefore()));
        }
        if (record.credentialAfter() != null) {
            tag.put("credential_after", PlacedBlockTrackerData.encodeSnapshot(record.credentialAfter()));
        }
        return tag;
    }

    public static HistoryBlockRecord decodeHistory(RegistryAccess registryAccess, CompoundTag tag) {
        if (tag == null || !tag.contains("pos", Tag.TAG_LONG)
                || !tag.contains("state", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("mining history record 不完整");
        }
        BlockState state = NbtUtils.readBlockState(
                registryAccess.registryOrThrow(Registries.BLOCK).asLookup(), tag.getCompound("state"));
        if (state.isAir()) throw new IllegalArgumentException("mining history 不能记录空气");
        CompoundTag blockEntity = tag.contains("block_entity", Tag.TAG_COMPOUND)
                ? tag.getCompound("block_entity").copy() : null;
        PlacedBlockTrackerData.CredentialSnapshot credentialBefore = decodeCredential(
                tag, "credential_before");
        PlacedBlockTrackerData.CredentialSnapshot credentialAfter = decodeCredential(
                tag, "credential_after");
        return new HistoryBlockRecord(BlockPos.of(tag.getLong("pos")), state, blockEntity,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), null,
                credentialBefore, credentialAfter);
    }

    private static void validateCredential(CompoundTag record, String key) {
        if (!record.contains(key)) return;
        if (!record.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("mining history " + key + " 类型无效");
        }
        PlacedBlockTrackerData.decodeSnapshot(record.getCompound(key));
    }

    private static PlacedBlockTrackerData.CredentialSnapshot decodeCredential(
            CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("mining history " + key + " 类型无效");
        }
        return PlacedBlockTrackerData.decodeSnapshot(tag.getCompound(key));
    }

    private static void requireFields(CompoundTag tag) {
        if (tag == null || !tag.contains("schema", Tag.TAG_INT)
                || tag.getInt("schema") < 1 || tag.getInt("schema") > SCHEMA_VERSION
                || !tag.hasUUID("owner")
                || !tag.contains("dimension", Tag.TAG_STRING) || !tag.contains("workflow", Tag.TAG_INT)
                || !tag.contains("mode", Tag.TAG_STRING) || !tag.contains("remaining", Tag.TAG_LONG_ARRAY)
                || !tag.contains("total", Tag.TAG_INT) || !tag.contains("cursor", Tag.TAG_INT)
                || !tag.contains("succeeded", Tag.TAG_INT) || !tag.contains("failed", Tag.TAG_INT)
                || !tag.contains("face", Tag.TAG_BYTE) || !tag.contains("tool_slot", Tag.TAG_INT)
                || !tag.contains("selected_tool", Tag.TAG_BYTE) || !tag.contains("protect_tool", Tag.TAG_BYTE)
                || !tag.contains("progress", Tag.TAG_FLOAT) || !tag.contains("stage", Tag.TAG_INT)
                || !tag.contains("history", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("不支持或不完整的 mining task payload");
        }
        if (tag.getInt("schema") >= 2 && !tag.contains("creative_operation", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("mining task 缺少 creative_operation");
        }
        if (tag.getInt("schema") >= 3
                && (!tag.contains("history_positions", Tag.TAG_LONG_ARRAY)
                || !tag.contains(HistoryRecordCodec.STATES_KEY, Tag.TAG_LIST)
                || !tag.contains(HistoryRecordCodec.STATE_INDICES_KEY, Tag.TAG_INT_ARRAY))) {
            throw new IllegalArgumentException("schema3 mining task 缺少紧凑 history");
        }
    }
}
