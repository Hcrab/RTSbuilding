package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.server.task.PlacementTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementTaskCodecTest {

    @Test
    void roundTripPreservesPurePlacementSnapshot() {
        UUID owner = UUID.randomUUID();
        int dimension = 0;
        NBTTagCompound definition = new NBTTagCompound();
        NbtCompat.setLongArray(definition, "positions", new long[]{new BlockPos(1, 2, 3).toLong(), 9L});
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
        NBTTagCompound legacy = validTag();
        legacy.setInteger("schema", 1);
        legacy.removeTag("resumePolicy");

        assertEquals(PlacementResumePolicy.DEFAULT,
                PlacementTaskCodec.decode(legacy).state().resumePolicy());
    }

    @Test
    void decodeFailsClosedForUnknownSchemaAndOversizedTargetCount() {
        NBTTagCompound unknown = validTag();
        unknown.setInteger("schema", 99);
        assertThrows(IllegalArgumentException.class, () -> PlacementTaskCodec.decode(unknown));

        NBTTagCompound oversized = validTag();
        oversized.setInteger("total", PlacementTaskCodec.MAX_TARGETS + 1);
        assertThrows(IllegalArgumentException.class, () -> PlacementTaskCodec.decode(oversized));
    }

    @Test
    void payloadRejectsWorkflowIdentityDrift() {
        int dimension = 0;
        PlacementTaskState state = new PlacementTaskState(
                definition(), 3, 1, 0, 0, 0, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementTaskPayload(UUID.randomUUID(), dimension, 4, state));
    }

    private static NBTTagCompound validTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", PlacementTaskCodec.SCHEMA_VERSION);
        tag.setUniqueId("owner", UUID.randomUUID());
        tag.setString("dimension", "minecraft:overworld");
        tag.setInteger("workflow", -1);
        tag.setTag("definition", definition());
        tag.setInteger("total", 1);
        tag.setInteger("cursor", 0);
        tag.setInteger("succeeded", 0);
        tag.setInteger("failed", 0);
        tag.setString("resumePolicy", PlacementResumePolicy.DEFAULT.name());
        NbtCompat.setLongArray(tag, "placed", new long[0]);
        return tag;
    }

    private static NBTTagCompound definition() {
        NBTTagCompound definition = new NBTTagCompound();
        NbtCompat.setLongArray(definition, "positions", new long[]{1L});
        return definition;
    }
}
