package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.server.task.PlacementTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.persistence.DimensionIdCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/** PlacementTaskPayload 的有界、版本化 NBT 编解码器。 */
public final class PlacementTaskCodec {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_TARGETS = 32_768;

    private PlacementTaskCodec() {
    }

    public static NBTTagCompound encode(PlacementTaskPayload payload) {
        PlacementTaskState state = payload.state();
        validateDefinition(state.definition(), state.totalUnits());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", SCHEMA_VERSION);
        NbtCompat.setUuid(tag, "owner", payload.ownerId());
        tag.setString("dimension", DimensionIdCodec.fromDimension(payload.dimension()));
        tag.setInteger("workflow", payload.workflowEntryId());
        tag.setTag("definition", state.definition());
        tag.setInteger("total", state.totalUnits());
        tag.setInteger("cursor", state.cursorUnits());
        tag.setInteger("succeeded", state.succeededUnits());
        tag.setInteger("failed", state.failedUnits());
        tag.setString("resumePolicy", state.resumePolicy().name());
        long[] placed = new long[state.placedPositions().size()];
        for (int i = 0; i < placed.length; i++) placed[i] = state.placedPositions().get(i).toLong();
        NbtCompat.setLongArray(tag, "placed", placed);
        return tag;
    }

    public static PlacementTaskPayload decode(NBTTagCompound tag) {
        if (tag == null
                || !NbtCompat.hasType(tag, "schema", Constants.NBT.TAG_INT)
                || (tag.getInteger("schema") != 1 && tag.getInteger("schema") != SCHEMA_VERSION)
                || !NbtCompat.hasUuid(tag, "owner")
                || !NbtCompat.hasType(tag, "dimension", Constants.NBT.TAG_STRING)
                || !NbtCompat.hasType(tag, "workflow", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "total", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "cursor", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "succeeded", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "failed", Constants.NBT.TAG_INT)
                || !NbtCompat.hasType(tag, "placed", Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException("不支持或不完整的 placement task payload");
        }
        String dimensionId = tag.getString("dimension");
        if (!DimensionIdCodec.isCanonical(dimensionId)) {
            throw new IllegalArgumentException("placement task 维度无效");
        }
        int dimension = DimensionIdCodec.toDimension(dimensionId);
        if (!NbtCompat.hasType(tag, "definition", Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("placement task 缺少 definition");
        }
        int total = tag.getInteger("total");
        if (total < 0 || total > MAX_TARGETS) throw new IllegalArgumentException("placement total 越界");
        NBTTagCompound definition = tag.getCompoundTag("definition");
        validateDefinition(definition, total);
        long[] encodedPositions = NbtCompat.getLongArray(tag, "placed");
        if (encodedPositions.length > total) throw new IllegalArgumentException("placed positions 越界");
        List<BlockPos> positions = new ArrayList<>(encodedPositions.length);
        for (long encoded : encodedPositions) positions.add(BlockPos.fromLong(encoded));
        int workflow = tag.getInteger("workflow");
        PlacementResumePolicy resumePolicy = PlacementResumePolicy.DEFAULT;
        if (tag.getInteger("schema") >= 2) {
            if (!NbtCompat.hasType(tag, "resumePolicy", Constants.NBT.TAG_STRING)) {
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
                tag.getInteger("cursor"), tag.getInteger("succeeded"), tag.getInteger("failed"), positions,
                resumePolicy);
        return new PlacementTaskPayload(NbtCompat.getUuid(tag, "owner"), dimension, workflow, state);
    }

    private static void validateDefinition(NBTTagCompound definition, int totalUnits) {
        if (!NbtCompat.hasType(definition, "positions", Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException("placement definition 缺少 positions");
        }
        int targets = NbtCompat.getLongArray(definition, "positions").length;
        if (targets != totalUnits || targets > MAX_TARGETS) {
            throw new IllegalArgumentException("placement definition 目标数量与 total 不一致或越界");
        }
    }
}
