package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.task.MiningTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** MiningTaskPayload 的版本化纯 NBT codec，并集中保存历史方块快照格式。 */
public final class MiningTaskCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TARGETS = 32_768;

    private MiningTaskCodec() {
    }

    public static CompoundTag encode(MiningTaskPayload payload) {
        MiningTaskState state = payload.state();
        if (state.totalUnits() > MAX_TARGETS) throw new IllegalArgumentException("mining target 数量越界");
        if (state.historyRecords().size() > MAX_TARGETS * 7) {
            throw new IllegalArgumentException("mining history 越界");
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "owner", payload.ownerId());
        tag.putString("dimension", payload.dimension().identifier().toString());
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
        tag.putFloat("progress", state.blockProgress());
        tag.putInt("stage", state.visibleStage());
        ListTag history = new ListTag();
        state.historyRecords().forEach(history::add);
        tag.put("history", history);
        return tag;
    }

    public static MiningTaskPayload decode(CompoundTag tag) {
        requireFields(tag);
        Identifier dimensionId = Identifier.tryParse(tag.getStringOr("dimension", ""));
        if (dimensionId == null || !dimensionId.toString().equals(tag.getStringOr("dimension", ""))) {
            throw new IllegalArgumentException("mining dimension 无效");
        }
        MiningTaskState.Mode mode;
        try {
            mode = MiningTaskState.Mode.valueOf(tag.getStringOr("mode", ""));
        } catch (IllegalArgumentException invalidMode) {
            throw new IllegalArgumentException("mining mode 无效", invalidMode);
        }
        long[] encodedTargets = tag.getLongArray("remaining").orElseGet(() -> new long[0]);
        int total = tag.getIntOr("total", 0);
        if (total < 0 || total > MAX_TARGETS || encodedTargets.length > total) {
            throw new IllegalArgumentException("mining target 数量越界");
        }
        List<BlockPos> targets = new ArrayList<>(encodedTargets.length);
        for (long encoded : encodedTargets) targets.add(BlockPos.of(encoded).immutable());
        ListTag encodedHistory = tag.getListOrEmpty("history");
        if (encodedHistory.size() > MAX_TARGETS * 7) throw new IllegalArgumentException("mining history 越界");
        List<CompoundTag> history = new ArrayList<>(encodedHistory.size());
        for (int i = 0; i < encodedHistory.size(); i++) history.add(encodedHistory.getCompoundOrEmpty(i).copy());
        int workflow = tag.getIntOr("workflow", 0);
        MiningTaskState state = new MiningTaskState(
                mode, workflow, targets, total,
                tag.getIntOr("cursor", 0), tag.getIntOr("succeeded", 0), tag.getIntOr("failed", 0),
                Direction.from3DDataValue(tag.getByteOr("face", (byte) 0)), tag.getIntOr("tool_slot", 0),
                tag.getBooleanOr("selected_tool", false), tag.getBooleanOr("protect_tool", false),
                tag.getFloatOr("progress", 0.0F), tag.getIntOr("stage", 0), history);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new MiningTaskPayload(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "owner"), dimension, workflow, state);
    }

    public static CompoundTag encodeHistory(HistoryBlockRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", record.pos().asLong());
        tag.put("state", NbtUtils.writeBlockState(record.state()));
        if (record.blockEntityData() != null) tag.put("block_entity", record.blockEntityData().copy());
        return tag;
    }

    public static HistoryBlockRecord decodeHistory(RegistryAccess registryAccess, CompoundTag tag) {
        if (tag == null || !tag.contains("pos")
                || !tag.contains("state")) {
            throw new IllegalArgumentException("mining history record 不完整");
        }
        BlockState state = NbtUtils.readBlockState(
                registryAccess.lookupOrThrow(Registries.BLOCK), tag.getCompoundOrEmpty("state"));
        if (state.isAir()) throw new IllegalArgumentException("mining history 不能记录空气");
        CompoundTag blockEntity = tag.contains("block_entity")
                ? tag.getCompoundOrEmpty("block_entity").copy() : null;
        return new HistoryBlockRecord(BlockPos.of(tag.getLongOr("pos", 0L)), state, blockEntity);
    }

    private static void requireFields(CompoundTag tag) {
        if (tag == null || !tag.contains("schema")
                || tag.getIntOr("schema", 0) != SCHEMA_VERSION || !com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.hasUuid(tag, "owner")
                || !tag.contains("dimension") || !tag.contains("workflow")
                || !tag.contains("mode") || !tag.contains("remaining")
                || !tag.contains("total") || !tag.contains("cursor")
                || !tag.contains("succeeded") || !tag.contains("failed")
                || !tag.contains("face") || !tag.contains("tool_slot")
                || !tag.contains("selected_tool") || !tag.contains("protect_tool")
                || !tag.contains("progress") || !tag.contains("stage")
                || !tag.contains("history")) {
            throw new IllegalArgumentException("不支持或不完整的 mining task payload");
        }
    }
}
