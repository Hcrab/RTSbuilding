package com.rtsbuilding.rtsbuilding.server.task.destruction;

import com.rtsbuilding.rtsbuilding.server.task.DestructionTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
        tag.putUUID("owner", payload.ownerId());
        tag.putString("dimension", payload.dimension().location().toString());
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
        if (tag == null || tag.getInt("schema") != SCHEMA_VERSION || !tag.hasUUID("owner")) {
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

        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimensionId == null || !dimensionId.toString().equals(tag.getString("dimension"))) {
            throw new IllegalArgumentException("destruction task 维度无效");
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

        List<BlockPos> targets = decodePositions(tag.getLongArray("targets"),
                DestructionTaskState.MAX_TARGETS, "targets");
        if (targets.isEmpty()) throw new IllegalArgumentException("destruction targets 不能为空");
        List<BlockPos> destroyed = decodePositions(tag.getLongArray("destroyed"),
                targets.size(), "destroyed");
        ListTag encodedHistory = (ListTag) tag.get("history");
        if (!encodedHistory.isEmpty() && encodedHistory.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("destruction history 元素类型无效");
        }
        long maxHistory = (long) targets.size() * DestructionTaskState.MAX_HISTORY_RECORDS_PER_TARGET;
        if (encodedHistory.size() > maxHistory) {
            throw new IllegalArgumentException("destruction history 超过有界上限");
        }
        List<CompoundTag> history = new ArrayList<>(encodedHistory.size());
        for (int i = 0; i < encodedHistory.size(); i++) {
            CompoundTag record = encodedHistory.getCompound(i);
            requireType(record, "pos", Tag.TAG_LONG);
            requireType(record, "state", Tag.TAG_COMPOUND);
            if (record.contains("blockEntity") && !record.contains("blockEntity", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("destruction history blockEntity 类型无效");
            }
            history.add(record.copy());
        }

        int workflow = tag.getInt("workflow");
        DestructionTaskState state = new DestructionTaskState(
                targets,
                tag.getByte("toolSlot"),
                tag.getBoolean("toolProtection"),
                tag.getBoolean("selectedTool"),
                workflow,
                tag.getInt("cursor"),
                tag.getInt("succeeded"),
                tag.getInt("failed"),
                destroyed,
                history);
        return new DestructionTaskPayload(tag.getUUID("owner"), dimension, workflow, state);
    }

    private static List<BlockPos> decodePositions(long[] encoded, int max, String field) {
        if (encoded.length > max) throw new IllegalArgumentException("destruction " + field + " 越界");
        List<BlockPos> positions = new ArrayList<>(encoded.length);
        for (long value : encoded) positions.add(BlockPos.of(value).immutable());
        return positions;
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException("destruction payload 字段类型无效: " + key);
        }
    }
}
