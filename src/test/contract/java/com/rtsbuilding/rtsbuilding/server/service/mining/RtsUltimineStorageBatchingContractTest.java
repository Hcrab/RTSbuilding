package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsUltimineStorageBatchingContractTest {
    @Test
    void ultimineDropAbsorptionBatchesStorageWorkPerTick() throws IOException {
        String processor = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsUltimineProcessor.java"));
        String absorber = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsDropAbsorber.java"));

        assertTrue(processor.contains("dropsToAbsorb")
                        && processor.contains("absorbMinedDropsBatch(player, session, dropsToAbsorb)"),
                "ultimine should collect this tick's broken positions, then absorb drops in one batch");
        assertFalse(processor.contains("absorbMinedDropsImmediately(player, session, target)"),
                "ultimine queue processing should not re-resolve storage for every target");
        assertTrue(absorber.contains("RtsStorageTickService.INSTANCE.getStorage(player)")
                        && absorber.contains("aggregate.insert(stack, false)")
                        && absorber.contains("absorbMinedDropsBatch"),
                "drop absorption should reuse aggregate storage and expose a batch entry point");
    }

    @Test
    void ultimineMidBatchDirtyMarkDoesNotForceSynchronousStorageRefresh() throws IOException {
        String processor = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsUltimineProcessor.java"));
        String absorber = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsDropAbsorber.java"));

        assertTrue(absorber.contains("RtsStorageTickService.INSTANCE.alert(player.getUUID())"),
                "batch storage work should wake the next storage tick instead of forcing synchronous refresh");
        assertTrue(processor.contains("RtsPageService.markStorageViewDirty(player, session)"),
                "Forge should mark the storage page dirty after a changed batch");
        assertFalse(processor.contains("RtsStorageTickService.INSTANCE.forceRefresh"),
                "ultimine mid-batch handling should not synchronously force refresh large linked storage");
    }
}
