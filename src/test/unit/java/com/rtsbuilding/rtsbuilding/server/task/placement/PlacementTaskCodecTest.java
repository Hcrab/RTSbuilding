package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.server.task.PlacementTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementTaskCodecTest {

    @Test
    void roundTripPreservesPurePlacementSnapshot() {
        UUID owner = UUID.randomUUID();
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        CompoundTag definition = new CompoundTag();
        definition.putLongArray("positions", new long[]{new BlockPos(1, 2, 3).asLong(), 9L});
        PlacementTaskState state = new PlacementTaskState(
                definition, 12, 2, 1, 1, 0, List.of(new BlockPos(1, 2, 3)),
                PlacementResumePolicy.OVERWRITE_CONFLICTS);
        PlacementTaskPayload payload = new PlacementTaskPayload(owner, dimension, 12, state);

        PlacementTaskPayload decoded = PlacementTaskCodec.decode(PlacementTaskCodec.encode(payload));

        assertEquals(owner, decoded.ownerId());
        assertEquals(dimension, decoded.dimension());
        assertEquals(12, decoded.workflowEntryId());
        assertEquals(1, decoded.state().cursorUnits());
        assertEquals(List.of(new BlockPos(1, 2, 3)), decoded.state().placedPositions());
        assertEquals(PlacementResumePolicy.OVERWRITE_CONFLICTS, decoded.state().resumePolicy());
    }

    @Test
    void schemaOneDefaultsToSafeDefaultResumePolicy() {
        CompoundTag legacy = validTag();
        legacy.putInt("schema", 1);
        legacy.remove("resumePolicy");

        assertEquals(PlacementResumePolicy.DEFAULT,
                PlacementTaskCodec.decode(legacy).state().resumePolicy());
    }

    @Test
    void decodeFailsClosedForUnknownSchemaAndOversizedTargetCount() {
        CompoundTag unknown = validTag();
        unknown.putInt("schema", 99);
        assertThrows(IllegalArgumentException.class, () -> PlacementTaskCodec.decode(unknown));

        CompoundTag oversized = validTag();
        oversized.putInt("total", PlacementTaskCodec.MAX_TARGETS + 1);
        assertThrows(IllegalArgumentException.class, () -> PlacementTaskCodec.decode(oversized));
    }

    @Test
    void payloadRejectsWorkflowIdentityDrift() {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        PlacementTaskState state = new PlacementTaskState(
                definition(), 3, 1, 0, 0, 0, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementTaskPayload(UUID.randomUUID(), dimension, 4, state));
    }

    private static CompoundTag validTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", PlacementTaskCodec.SCHEMA_VERSION);
        tag.putUUID("owner", UUID.randomUUID());
        tag.putString("dimension", "minecraft:overworld");
        tag.putInt("workflow", -1);
        tag.put("definition", definition());
        tag.putInt("total", 1);
        tag.putInt("cursor", 0);
        tag.putInt("succeeded", 0);
        tag.putInt("failed", 0);
        tag.putString("resumePolicy", PlacementResumePolicy.DEFAULT.name());
        tag.putLongArray("placed", new long[0]);
        return tag;
    }

    private static CompoundTag definition() {
        CompoundTag definition = new CompoundTag();
        definition.putLongArray("positions", new long[]{1L});
        return definition;
    }
}
