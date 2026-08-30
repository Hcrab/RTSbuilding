package com.rtsbuilding.rtsbuilding.server.protection;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
