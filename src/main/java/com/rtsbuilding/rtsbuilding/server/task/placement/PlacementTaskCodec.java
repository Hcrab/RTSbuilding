package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.server.task.PlacementTaskPayload;
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

/** PlacementTaskPayload 的有界、版本化 NBT 编解码器。 */
public final class PlacementTaskCodec {
    public static final int SCHEMA_VERSION = 3;
    public static final int MAX_TARGETS = 32_768;

    private PlacementTaskCodec() {
    }

    public static CompoundTag encode(PlacementTaskPayload payload) {
        PlacementTaskState state = payload.state();
        validateDefinition(state.definition(), state.totalUnits());
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("owner", payload.ownerId());
        tag.putString("dimension", payload.dimension().location().toString());
        tag.putInt("workflow", payload.workflowEntryId());
        tag.put("definition", state.definition());
        tag.putInt("total", state.totalUnits());
        tag.putInt("cursor", state.cursorUnits());
        tag.putInt("succeeded", state.succeededUnits());
        tag.putInt("failed", state.failedUnits());
        tag.putString("resumePolicy", state.resumePolicy().name());
        tag.putBoolean("creativeOperation", state.creativeOperation());
        tag.putLongArray("placed", state.placedPositions().stream().mapToLong(BlockPos::asLong).toArray());
        ListTag history = new ListTag();
        state.historyRecords().forEach(history::add);
        tag.put("history", history);
        return tag;
    }

    public static PlacementTaskPayload decode(CompoundTag tag) {
        if (tag == null
                || !tag.contains("schema", Tag.TAG_INT)
                || (tag.getInt("schema") < 1 || tag.getInt("schema") > SCHEMA_VERSION)
                || !tag.hasUUID("owner")
                || !tag.contains("dimension", Tag.TAG_STRING)
                || !tag.contains("workflow", Tag.TAG_INT)
                || !tag.contains("total", Tag.TAG_INT)
                || !tag.contains("cursor", Tag.TAG_INT)
                || !tag.contains("succeeded", Tag.TAG_INT)
                || !tag.contains("failed", Tag.TAG_INT)
                || !tag.contains("placed", Tag.TAG_LONG_ARRAY)) {
            throw new IllegalArgumentException("不支持或不完整的 placement task payload");
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimensionId == null || !dimensionId.toString().equals(tag.getString("dimension"))) {
            throw new IllegalArgumentException("placement task 维度无效");
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (!tag.contains("definition", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("placement task 缺少 definition");
        }
        int total = tag.getInt("total");
        if (total < 0 || total > MAX_TARGETS) throw new IllegalArgumentException("placement total 越界");
        CompoundTag definition = tag.getCompound("definition");
        validateDefinition(definition, total);
        long[] encodedPositions = tag.getLongArray("placed");
        if (encodedPositions.length > total) throw new IllegalArgumentException("placed positions 越界");
        List<BlockPos> positions = new ArrayList<>(encodedPositions.length);
        for (long encoded : encodedPositions) positions.add(BlockPos.of(encoded).immutable());
        int workflow = tag.getInt("workflow");
        PlacementResumePolicy resumePolicy = PlacementResumePolicy.DEFAULT;
        if (tag.getInt("schema") >= 2) {
            if (!tag.contains("resumePolicy", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("placement task 缺少 resumePolicy");
            }
            try {
                resumePolicy = PlacementResumePolicy.valueOf(tag.getString("resumePolicy"));
            } catch (IllegalArgumentException invalidPolicy) {
                throw new IllegalArgumentException("placement task resumePolicy 无效", invalidPolicy);
            }
        }
        boolean creativeOperation = false;
        List<CompoundTag> history = List.of();
        if (tag.getInt("schema") >= 3) {
            if (!tag.contains("creativeOperation", Tag.TAG_BYTE)
                    || !tag.contains("history", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("placement task 缺少历史模式或快照");
            }
            creativeOperation = tag.getBoolean("creativeOperation");
            ListTag encodedHistory = tag.getList("history", Tag.TAG_COMPOUND);
            if (encodedHistory.size() != positions.size()) {
                throw new IllegalArgumentException("placement history 数量与成功位置不一致");
            }
            List<CompoundTag> decodedHistory = new ArrayList<>(encodedHistory.size());
            for (int i = 0; i < encodedHistory.size(); i++) {
                CompoundTag record = encodedHistory.getCompound(i);
                if (!record.contains("pos", Tag.TAG_LONG)
                        || !record.contains("before", Tag.TAG_COMPOUND)
                        || !record.contains("after", Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("placement history record 不完整");
                }
                decodedHistory.add(record.copy());
            }
            history = List.copyOf(decodedHistory);
        }
        PlacementTaskState state = new PlacementTaskState(
                definition, workflow, total,
                tag.getInt("cursor"), tag.getInt("succeeded"), tag.getInt("failed"), positions,
                resumePolicy, creativeOperation, history);
        return new PlacementTaskPayload(tag.getUUID("owner"), dimension, workflow, state);
    }

    private static void validateDefinition(CompoundTag definition, int totalUnits) {
        if (!definition.contains("positions", Tag.TAG_LONG_ARRAY)) {
            throw new IllegalArgumentException("placement definition 缺少 positions");
        }
        int targets = definition.getLongArray("positions").length;
        if (targets != totalUnits || targets > MAX_TARGETS) {
            throw new IllegalArgumentException("placement definition 目标数量与 total 不一致或越界");
        }
    }
}
