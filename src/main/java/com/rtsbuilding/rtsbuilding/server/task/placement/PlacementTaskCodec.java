package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.server.task.PlacementTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** PlacementTaskPayload 的有界、版本化 NBT 编解码器。 */
public final class PlacementTaskCodec {
    public static final int SCHEMA_VERSION = 2;
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
        tag.putLongArray("placed", state.placedPositions().stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    public static PlacementTaskPayload decode(CompoundTag tag) {
        if (tag == null
                || !tag.contains("schema", Tag.TAG_INT)
                || (tag.getInt("schema") != 1 && tag.getInt("schema") != SCHEMA_VERSION)
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
        PlacementTaskState state = new PlacementTaskState(
                definition, workflow, total,
                tag.getInt("cursor"), tag.getInt("succeeded"), tag.getInt("failed"), positions,
                resumePolicy);
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
