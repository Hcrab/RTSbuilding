package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 覆盖 Forge 蓝图库的代次隔离、有界发布、同名替换和延后选择。 */
class BlueprintLibraryAsyncLoadingTest {
    @TempDir
    Path tempDir;

    @Test
    void publishesOneRowPerTickAndReadsNeverDrainTheQueue() throws IOException {
        FakeLoader loader = new FakeLoader();
        BlueprintLibraryRepository repository = repository(loader, tempDir);
        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        Submission submission = loader.latest();

        for (int i = 0; i < 2; i++) {
            Path path = file("row-" + i + ".nbt");
            submission.results.add(BlueprintLibraryLoadResult.entry(
                    submission.generation, path, path.getFileName().toString(), 0L,
                    emptyBlueprint("row-" + i)));
        }
        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        assertEquals(0, repository.size());
        assertEquals(0, submission.results.remainingCapacity());
        repository.pump(null);
        assertEquals(1, repository.size());

        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        assertEquals(1, repository.size());
        repository.pump(null);
        assertEquals(2, repository.size());
        submission.results.add(BlueprintLibraryLoadResult.complete(submission.generation, ""));
        repository.pump(null);
    }

    @Test
    void reloadAndCloseDropResultsFromAnOlderGeneration() throws IOException {
        FakeLoader loader = new FakeLoader();
        BlueprintLibraryRepository repository = repository(loader, tempDir);
        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        Submission first = loader.latest();

        repository.reload(null);
        assertTrue(first.cancelled);
        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        Submission second = loader.latest();
        Path stalePath = file("stale.nbt");
        Path currentPath = file("current.nbt");
        first.results.add(BlueprintLibraryLoadResult.entry(
                first.generation, stalePath, "stale.nbt", 0L,
                emptyBlueprint("stale")));
        second.results.add(BlueprintLibraryLoadResult.entry(
                second.generation, currentPath, "current.nbt", 0L,
                emptyBlueprint("current")));

        repository.pump(null);
        assertEquals(1, repository.size());
        assertEquals("current.nbt", repository.get(0).fileName());

        repository.close();
        assertTrue(second.cancelled);
        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        Submission afterClose = loader.latest();
        first.results.add(BlueprintLibraryLoadResult.entry(
                first.generation, stalePath, "stale.nbt", 0L,
                emptyBlueprint("stale-again")));
        afterClose.results.add(BlueprintLibraryLoadResult.entry(
                afterClose.generation, currentPath, "current.nbt", 0L,
                emptyBlueprint("after-close")));
        repository.pump(null);
        assertEquals("after-close", repository.get(0).blueprint().name());
    }

    @Test
    void captureReplacementWinsOverAnAlreadyQueuedSameFileResult() throws IOException {
        FakeLoader loader = new FakeLoader();
        BlueprintLibraryRepository repository = repository(loader, tempDir);
        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        Submission submission = loader.latest();
        Path path = file("captured.nbt");
        RtsBlueprint stale = emptyBlueprint("stale");
        RtsBlueprint captured = emptyBlueprint("captured");
        submission.results.add(BlueprintLibraryLoadResult.entry(
                submission.generation, path, "captured.nbt", 0L, stale));

        repository.addOrReplace(path, captured);
        repository.pump(null);

        assertSame(captured, repository.findByFileName("captured.nbt").blueprint());
    }

    @Test
    void parseFailureIsPublishedAsAnErrorRow() throws IOException {
        FakeLoader loader = new FakeLoader();
        BlueprintLibraryRepository repository = repository(loader, tempDir);
        repository.ensureLoaded(null, RegistryAccess.EMPTY);
        Submission submission = loader.latest();
        Path path = file("broken.nbt");
        submission.results.add(BlueprintLibraryLoadResult.error(
                submission.generation, path, "broken.nbt", 0L, "bad blueprint"));

        repository.pump(null);

        assertEquals("bad blueprint", repository.findByFileName("broken.nbt").error());
    }

    @Test
    void reloadSelectionWaitsForNamedRowAndDoesNotFollowItsIndex() throws IOException {
        try (var defaults = org.mockito.Mockito.mockStatic(BlueprintRotationDefaults.class)) {
            FakeLoader loader = new FakeLoader();
            BlueprintLibraryRepository repository = repository(loader, tempDir);
            List<BlueprintEntry> selectionEvents = new ArrayList<>();
            BlueprintLibrarySession session = new BlueprintLibrarySession(
                    repository, (status, key, detail) -> { }, selectionEvents::add);

            session.applyFileOperation(BlueprintLibraryFileOperations.Result.reloadAndSelect(
                    (byte) 1, "imported", "target.nbt", "target.nbt"));
            session.ensureLoaded();
            Submission submission = loader.latest();
            Path before = file("before.nbt");
            Path target = file("target.nbt");
            submission.results.add(BlueprintLibraryLoadResult.entry(
                    submission.generation, before, "before.nbt", 0L,
                    emptyBlueprint("before")));
            submission.results.add(BlueprintLibraryLoadResult.entry(
                    submission.generation, target, "target.nbt", 0L,
                    emptyBlueprint("target")));

            session.entries();
            org.junit.jupiter.api.Assertions.assertNull(session.selectedEntry());
            session.tick();
            session.tick();
            assertEquals("target.nbt", session.selectedEntry().fileName());
            assertSame(session.selectedEntry(), selectionEvents.get(selectionEvents.size() - 1));
        }
    }

    private Path file(String name) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, new byte[] {1});
        return path;
    }

    private static BlueprintLibraryRepository repository(FakeLoader loader, Path folder) {
        return new BlueprintLibraryRepository(
                loader::submit,
                () -> RegistryAccess.EMPTY,
                () -> folder);
    }

    private static RtsBlueprint emptyBlueprint(String name) {
        return RtsBlueprint.create(name, name + ".nbt", BlueprintFormat.VANILLA_NBT,
                Vec3i.ZERO, List.of());
    }

    private static final class FakeLoader {
        private final List<Submission> submissions = new ArrayList<>();

        private BlueprintLibraryRepository.LoadHandle submit(long generation, Path folder,
                RegistryAccess registryAccess, ConcurrentMap<String, Long> fileRevisions,
                BlockingQueue<BlueprintLibraryLoadResult> results) {
            Submission submission = new Submission(generation, results);
            submissions.add(submission);
            return () -> submission.cancelled = true;
        }

        private Submission latest() {
            return submissions.get(submissions.size() - 1);
        }
    }

    private static final class Submission {
        private final long generation;
        private final BlockingQueue<BlueprintLibraryLoadResult> results;
        private boolean cancelled;

        private Submission(long generation, BlockingQueue<BlueprintLibraryLoadResult> results) {
            this.generation = generation;
            this.results = results;
        }
    }
}
