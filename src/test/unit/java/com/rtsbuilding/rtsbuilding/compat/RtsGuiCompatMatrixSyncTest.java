package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatMatrixSyncTest {
    private static final String REPORT_PROPERTY = "rtsbuilding.guiCompatMatrixReport";

    @Test
    void 服务端ACK必须同时匹配序号目标和候选身份() {
        String previous = System.getProperty(REPORT_PROPERTY);
        System.setProperty(REPORT_PROPERTY, "matrix-test.tsv");
        try {
            BlockPos pos = new BlockPos(12, 64, 120);
            long setupBaseline = RtsGuiCompatMatrixSync.setupSequence();
            RtsGuiCompatMatrixSync.markSetupComplete(pos, "example:machine", 3);
            assertTrue(RtsGuiCompatMatrixSync.isSetupAcknowledgedAfter(
                    setupBaseline, pos, "example:machine", 3));
            assertTrue(RtsGuiCompatMatrixSync.isSetupCompleteAfter(
                    setupBaseline, pos, "example:machine", 3));
            assertEquals("", RtsGuiCompatMatrixSync.setupFailureAfter(
                    setupBaseline, pos, "example:machine", 3));
            assertFalse(RtsGuiCompatMatrixSync.isSetupCompleteAfter(
                    setupBaseline, pos, "example:machine", 2));
            assertFalse(RtsGuiCompatMatrixSync.isSetupCompleteAfter(
                    RtsGuiCompatMatrixSync.setupSequence(), pos, "example:machine", 3));

            long failedSetupBaseline = RtsGuiCompatMatrixSync.setupSequence();
            RtsGuiCompatMatrixSync.markSetupFailed(pos, "example:machine", 4, "state rejected");
            assertTrue(RtsGuiCompatMatrixSync.isSetupAcknowledgedAfter(
                    failedSetupBaseline, pos, "example:machine", 4));
            assertFalse(RtsGuiCompatMatrixSync.isSetupCompleteAfter(
                    failedSetupBaseline, pos, "example:machine", 4));
            assertEquals("state rejected", RtsGuiCompatMatrixSync.setupFailureAfter(
                    failedSetupBaseline, pos, "example:machine", 4));

            long interactionBaseline = RtsGuiCompatMatrixSync.interactionSequence();
            RtsGuiCompatMatrixSync.markInteractionProcessed(pos);
            assertTrue(RtsGuiCompatMatrixSync.isInteractionAcknowledgedAfter(interactionBaseline, pos));
            assertTrue(RtsGuiCompatMatrixSync.isInteractionProcessedAfter(interactionBaseline, pos));
            assertEquals("", RtsGuiCompatMatrixSync.interactionFailureAfter(interactionBaseline, pos));
            assertFalse(RtsGuiCompatMatrixSync.isInteractionProcessedAfter(
                    interactionBaseline, pos.add(0, 0, 1)));

            long failedInteractionBaseline = RtsGuiCompatMatrixSync.interactionSequence();
            RtsGuiCompatMatrixSync.markInteractionFailed(pos,
                    new IllegalArgumentException("missing structure"));
            assertTrue(RtsGuiCompatMatrixSync.isInteractionAcknowledgedAfter(
                    failedInteractionBaseline, pos));
            assertFalse(RtsGuiCompatMatrixSync.isInteractionProcessedAfter(
                    failedInteractionBaseline, pos));
            assertEquals("java.lang.IllegalArgumentException: missing structure",
                    RtsGuiCompatMatrixSync.interactionFailureAfter(failedInteractionBaseline, pos));
        } finally {
            if (previous == null) System.clearProperty(REPORT_PROPERTY);
            else System.setProperty(REPORT_PROPERTY, previous);
        }
    }
}
