package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工作流单条/批量负载的真实内存 codec 回归；重点覆盖 VarInt 边界和协议预算。 */
class S2CRtsWorkflowProgressPayloadTest {
    @Test
    void singlePayloadRoundTripsIndexCountReasonMissingAndDetailBeyondByteRange() {
        S2CRtsWorkflowProgressPayload payload = payload(
                255, 256, 9876, List.of("minecraft:stone", "x".repeat(200)),
                "replaced_by_newer_workflow");

        RegistryFriendlyByteBuf buffer = buffer();
        try {
            S2CRtsWorkflowProgressPayload.STREAM_CODEC.encode(buffer, payload);
            assertTrue(buffer.readableBytes() > 0);
            S2CRtsWorkflowProgressPayload decoded =
                    S2CRtsWorkflowProgressPayload.STREAM_CODEC.decode(buffer);

            assertEquals(payload, decoded);
            assertEquals(0, buffer.readableBytes());
            assertEquals(255, decoded.workflowIndex());
            assertEquals(256, decoded.workflowCount());
            assertEquals(9876, decoded.reasonId());
            assertEquals(200, decoded.missingItems().get(1).length());
        } finally {
            buffer.release();
        }
    }

    @Test
    void batchPayloadRoundTripsCountBeyondByteRangeAndIdleUsesNegativeIndex() {
        List<S2CRtsWorkflowProgressPayload> entries = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            entries.add(payload(index, 256, index,
                    List.of("minecraft:stone"), "detail-" + index));
        }
        entries.add(payload(255, 256, 12, List.of(), "last"));
        S2CRtsWorkflowProgressBatchPayload batch = new S2CRtsWorkflowProgressBatchPayload(entries);

        RegistryFriendlyByteBuf batchBuffer = buffer();
        try {
            S2CRtsWorkflowProgressBatchPayload.STREAM_CODEC.encode(batchBuffer, batch);
            S2CRtsWorkflowProgressBatchPayload decoded =
                    S2CRtsWorkflowProgressBatchPayload.STREAM_CODEC.decode(batchBuffer);
            assertEquals(batch, decoded);
            assertEquals(129, decoded.entries().size());
            assertEquals(255, decoded.entries().get(128).workflowIndex());
            assertEquals(0, batchBuffer.readableBytes());
        } finally {
            batchBuffer.release();
        }

        RegistryFriendlyByteBuf idleBuffer = buffer();
        try {
            S2CRtsWorkflowProgressPayload.STREAM_CODEC.encode(
                    idleBuffer, S2CRtsWorkflowProgressPayload.idle());
            S2CRtsWorkflowProgressPayload idle =
                    S2CRtsWorkflowProgressPayload.STREAM_CODEC.decode(idleBuffer);
            assertTrue(idle.isIdle());
            assertEquals(-1, idle.workflowIndex());
            assertEquals(-1, idle.workflowEntryId());
            assertEquals(0, idle.workflowCount());
            assertFalse(idle.missingItems().iterator().hasNext());
        } finally {
            idleBuffer.release();
        }
    }

    @Test
    void constructorRejectsPayloadsOutsideDeclaredBudgets() {
        assertThrows(IllegalArgumentException.class, () -> payload(
                0, 4097, 0, List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> payload(
                0, 1, 0, List.of("x".repeat(257)), ""));
        assertThrows(IllegalArgumentException.class, () -> payload(
                0, 1, 0, List.of(), "x".repeat(1025)));

        List<S2CRtsWorkflowProgressPayload> tooMany = new ArrayList<>();
        for (int index = 0; index <= RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT; index++) {
            tooMany.add(payload(0, 1, 0, List.of(), ""));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new S2CRtsWorkflowProgressBatchPayload(tooMany));
    }

    private static S2CRtsWorkflowProgressPayload payload(
            int index, int count, int reason, List<String> missing, String detail) {
        return new S2CRtsWorkflowProgressPayload(
                index, count, (byte) RtsWorkflowType.PLACE_BATCH.wireId(), (byte) 2,
                1024, 127, 3, missing, detail,
                (byte) 1, (byte) 0, (byte) 1, 123456, reason);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
