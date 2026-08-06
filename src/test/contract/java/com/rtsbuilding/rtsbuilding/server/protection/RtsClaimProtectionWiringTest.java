package com.rtsbuilding.rtsbuilding.server.protection;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RtsClaimProtectionWiringTest {
    @Test
    void worldMutationEntrypointsUseClaimProtectionService() throws IOException {
        List<String> protectedFiles = List.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementQuickBuild.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsInteractionServiceImpl.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/mining/MiningExecutePipe.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningStateMachine.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsUltimineProcessor.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/destruction/RtsDestructionBatch.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/blueprint/BlueprintTickPipe.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/storage/RtsStorageFluids.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/history/HistoryExecutor.java");

        for (String file : protectedFiles) {
            String source = Files.readString(Path.of(file));
            assertTrue(source.contains("RtsClaimProtectionService"),
                    file + " should not bypass claim protection for RTS world changes");
        }
    }

    @Test
    void storageAndRemoteGuiInteractionsUseInteractionProtection() throws IOException {
        List<String> interactionFiles = List.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/bindings/RtsLinkedStorageBindingService.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/storage/RtsGuiBindingHelper.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsFunnelServiceImpl.java");

        for (String file : interactionFiles) {
            String source = Files.readString(Path.of(file));
            assertTrue(source.contains("canInteractBlock"),
                    file + " should respect claim interaction permissions for remote storage/GUI access");
        }
    }

    @Test
    void crossDimensionStorageChecksTargetClaimWithoutRequiringPlayerWorld() throws IOException {
        String service = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/protection/RtsClaimProtectionService.java"));
        String compat = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/ftb/RtsFtbClaimsCompatImpl.java"));

        assertTrue(service.contains("RtsOpenPacCompat.canInteractBlockInWorld(player, level, pos)"),
                "cross-dimension storage should evaluate the actual target world");
        assertFalse(service.contains("requiresPlayerWorldForClaimCheck"),
                "FTB Utilities presence must not disable valid cross-dimension storage");
        assertTrue(compat.contains("chunkDimPosConstructor.newInstance(pos, dimensionId)"),
                "FTB claim lookup must include the target dimension identity");
        assertTrue(compat.contains("getInteractWithBlocksStatus"),
                "target-dimension lookup must preserve FTB team interaction policy");
    }
}
