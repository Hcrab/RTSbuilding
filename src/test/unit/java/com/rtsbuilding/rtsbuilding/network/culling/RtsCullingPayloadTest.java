package com.rtsbuilding.rtsbuilding.network.culling;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RtsCullingPayloadTest {
    @Test
    void savePayloadDefensivelySnapshotsAndNormalizesClientState() {
        List<RtsCullingBoxSnapshot> boxes = new ArrayList<>(List.of(
                new RtsCullingBoxSnapshot(new BlockPos(8, 72, 8), new BlockPos(3, 64, 3))));
        List<BlockPos> revealed = new ArrayList<>(List.of(new BlockPos(5, 65, 5)));

        C2SRtsSaveCullingStatePayload payload = new C2SRtsSaveCullingStatePayload(
                null, boxes, revealed);
        boxes.clear();
        revealed.clear();

        assertEquals("", payload.dimension());
        assertEquals(new BlockPos(3, 64, 3), payload.boxes().getFirst().min());
        assertEquals(new BlockPos(8, 72, 8), payload.boxes().getFirst().max());
        assertEquals(List.of(new BlockPos(5, 65, 5)), payload.revealed());
        assertThrows(UnsupportedOperationException.class,
                () -> payload.boxes().add(new RtsCullingBoxSnapshot(BlockPos.ZERO, BlockPos.ZERO)));
    }
}
