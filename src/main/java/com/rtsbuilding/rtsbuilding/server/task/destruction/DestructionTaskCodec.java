package com.rtsbuilding.rtsbuilding.server.task.destruction;

import com.rtsbuilding.rtsbuilding.server.task.DestructionTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** DestructionTaskPayload 的有界、版本化 NBT 编解码器。 */
public final class DestructionTaskCodec {
    public static final int SCHEMA_VERSION = 1;

    private DestructionTaskCodec() {
    }

    public static CompoundTag encode(DestructionTaskPayload payload) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "owner", payload.ownerId());
        tag.putString("dimension", payload.dimension().identifier().toString());
        tag.putInt("workflow", payload.workflowEntryId());

        DestructionTaskState state = payload.state();
        tag.putLongArray("targets", state.targets().stream().mapToLong(BlockPos::asLong).toArray());
        tag.putByte("toolSlot", state.toolSlot());
        tag.putBoolean("toolProtection", state.toolProtectionEnabled());
        tag.putBoolean("selectedTool", state.selectedToolRequested());
        tag.putInt("cursor", state.cursorUnits());
        tag.putInt("succeeded", state.succeededUnits());
        tag.putInt("failed", state.failedUnits());
        tag.putLongArray("destroyed",
                state.destroyedPositions().stream().mapToLong(BlockPos::asLong).toArray());
        ListTag history = new ListTag();
        state.historyRecords().forEach(history::add);
        tag.put("history", history);
        return tag;
    }

    public static DestructionTaskPayload decode(CompoundTag tag) {
        if (tag == null || tag.getIntOr("schema", 0) != SCHEMA_VERSION || !com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.hasUuid(tag, "owner")) {
            throw new IllegalArgumentException("不支持或不完整的 destruction task payload");
        }
        requireType(tag, "dimension", Tag.TAG_STRING);
        requireType(tag, "workflow", Tag.TAG_INT);
        requireType(tag, "targets", Tag.TAG_LONG_ARRAY);
        requireType(tag, "toolSlot", Tag.TAG_BYTE);
        requireType(tag, "toolProtection", Tag.TAG_BYTE);
        requireType(tag, "selectedTool", Tag.TAG_BYTE);
        requireType(tag, "cursor", Tag.TAG_INT);
        requireType(tag, "succeeded", Tag.TAG_INT);
        requireType(tag, "failed", Tag.TAG_INT);
        requireType(tag, "destroyed", Tag.TAG_LONG_ARRAY);
        requireType(tag, "history", Tag.TAG_LIST);

        Identifier dimensionId = Identifier.tryParse(tag.getStringOr("dimension", ""));
        if (dimensionId == null || !dimensionId.toString().equals(tag.getStringOr("dimension", ""))) {
            throw new IllegalArgumentException("destruction task 维度无效");
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

        List<BlockPos> targets = decodePositions(tag.getLongArray("targets").orElseGet(() -> new long[0]),
                DestructionTaskState.MAX_TARGETS, "targets");
        if (targets.isEmpty()) throw new IllegalArgumentException("destruction targets 不能为空");
        List<BlockPos> destroyed = decodePositions(tag.getLongArray("destroyed").orElseGet(() -> new long[0]),
                targets.size(), "destroyed");
        ListTag encodedHistory = (ListTag) tag.get("history");
        if (encodedHistory.stream().anyMatch(element -> element.getId() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("destruction history 元素类型无效");
        }
        long maxHistory = (long) targets.size() * DestructionTaskState.MAX_HISTORY_RECORDS_PER_TARGET;
        if (encodedHistory.size() > maxHistory) {
            throw new IllegalArgumentException("destruction history 超过有界上限");
        }
        List<CompoundTag> history = new ArrayList<>(encodedHistory.size());
        for (int i = 0; i < encodedHistory.size(); i++) {
            CompoundTag record = encodedHistory.getCompoundOrEmpty(i);
            requireType(record, "pos", Tag.TAG_LONG);
            requireType(record, "state", Tag.TAG_COMPOUND);
            if (record.contains("blockEntity") && !record.contains("blockEntity")) {
                throw new IllegalArgumentException("destruction history blockEntity 类型无效");
            }
            history.add(record.copy());
        }

        int workflow = tag.getIntOr("workflow", 0);
        DestructionTaskState state = new DestructionTaskState(
                targets,
                tag.getByteOr("toolSlot", (byte) 0),
                tag.getBooleanOr("toolProtection", false),
                tag.getBooleanOr("selectedTool", false),
                workflow,
                tag.getIntOr("cursor", 0),
                tag.getIntOr("succeeded", 0),
                tag.getIntOr("failed", 0),
                destroyed,
                history);
        return new DestructionTaskPayload(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "owner"), dimension, workflow, state);
    }

    private static List<BlockPos> decodePositions(long[] encoded, int max, String field) {
        if (encoded.length > max) throw new IllegalArgumentException("destruction " + field + " 越界");
        List<BlockPos> positions = new ArrayList<>(encoded.length);
        for (long value : encoded) positions.add(BlockPos.of(value).immutable());
        return positions;
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key)) {
            throw new IllegalArgumentException("destruction payload 字段类型无效: " + key);
        }
    }
}
