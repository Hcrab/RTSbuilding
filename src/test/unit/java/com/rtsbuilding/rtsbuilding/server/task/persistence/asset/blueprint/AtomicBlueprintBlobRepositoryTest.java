package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicBlueprintBlobRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deterministicPerTaskIdWritesOnceAndReloadsWithHashVerification() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        TaskId taskId = TaskId.create();
        BlueprintBlobRecord record = codec.freeze(
                taskId, 1, "house", "test", "VANILLA_NBT", structure(new byte[]{1, 2, 3}));

        assertEquals(TaskAssetId.forTask(taskId, "blueprint"), record.assetId());
        assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN, repository.writeOnce(record));
        assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.ALREADY_PRESENT, repository.writeOnce(record));

        var found = assertInstanceOf(
                AtomicBlueprintBlobRepository.LoadResult.Found.class, repository.load(record.assetId()));
        assertEquals(record.sha256(), found.record().sha256());
        assertEquals(taskId, found.record().taskId());
        assertArrayEquals(new byte[]{1, 2, 3},
                found.record().structure().getByteArray("payload"));
        assertEquals(1, repository.scan().assetIds().size());
    }

    @Test
    void canonicalHashIgnoresCompoundInsertionOrderAndSurvivesRoundTrip() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        TaskId taskId = TaskId.create();
        CompoundTag first = new CompoundTag();
        first.putInt("z", 9);
        first.putString("a", "stable");
        CompoundTag second = new CompoundTag();
        second.putString("a", "stable");
        second.putInt("z", 9);

        BlueprintBlobRecord firstRecord = codec.freeze(
                taskId, 2, "ordered", "test", "VANILLA_NBT", first);
        BlueprintBlobRecord secondRecord = codec.freeze(
                taskId, 2, "ordered", "test", "VANILLA_NBT", second);

        assertEquals(firstRecord.sha256(), secondRecord.sha256());
        assertEquals(firstRecord.sha256(), codec.decodeCompressed(
                codec.encodeCompressed(firstRecord)).sha256());
    }

    @Test
    void denseNbtUsesLogicalLimitAndDecodeAccountingHeadroomConsistently() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        CompoundTag structure = new CompoundTag();
        ListTag nodes = new ListTag();
        for (int i = 0; i < 50_000; i++) nodes.add(IntTag.valueOf(i));
        structure.put("nodes", nodes);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "dense", "test", "VANILLA_NBT", structure);

        BlueprintBlobRecord decoded = codec.decodeCompressed(codec.encodeCompressed(record));

        assertEquals(record.sha256(), decoded.sha256());
        assertEquals(50_000, decoded.structure().getList("nodes", Tag.TAG_INT).size());
        assertTrue(BlueprintBlobCodec.MAX_DECODE_ACCOUNTING_BYTES > BlueprintBlobCodec.MAX_LOGICAL_BYTES);
    }

    @Test
    void sameDeterministicIdWithDifferentContentFailsClosedInsteadOfOverwriting() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        TaskId taskId = TaskId.create();
        BlueprintBlobRecord first = codec.freeze(
                taskId, 1, "first", "test", "VANILLA_NBT", structure(new byte[]{1}));
        BlueprintBlobRecord conflicting = codec.freeze(
                taskId, 1, "second", "test", "VANILLA_NBT", structure(new byte[]{2}));
        assertNotEquals(first.sha256(), conflicting.sha256());
        repository.writeOnce(first);

        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(conflicting));
        var found = assertInstanceOf(
                AtomicBlueprintBlobRepository.LoadResult.Found.class, repository.load(first.assetId()));
        assertEquals(first.sha256(), found.record().sha256());
    }

    @Test
    void corruptExistingBlobIsNotTreatedAsMissingOrOverwritten() throws IOException {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "house", "test", "VANILLA_NBT", structure(new byte[]{1}));
        Files.createDirectories(temporaryDirectory);
        Files.write(temporaryDirectory.resolve(record.assetId() + ".nbt"), new byte[]{9, 8, 7});

        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Failed.class,
                repository.load(record.assetId()));
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(record));
        assertArrayEquals(new byte[]{9, 8, 7},
                Files.readAllBytes(temporaryDirectory.resolve(record.assetId() + ".nbt")));
    }

    @Test
    void blobLargerThanFourMiBRemainsCompatibleWithExistingBlueprintLimits() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        byte[] logicalPayload = new byte[5 * 1024 * 1024];
        for (int i = 0; i < logicalPayload.length; i += 4096) logicalPayload[i] = (byte) (i / 4096);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 200_000, "large", "compat", "VANILLA_NBT", structure(logicalPayload));

        assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN, repository.writeOnce(record));
        var found = assertInstanceOf(
                AtomicBlueprintBlobRepository.LoadResult.Found.class, repository.load(record.assetId()));
        assertEquals(logicalPayload.length, found.record().structure().getByteArray("payload").length);
        assertTrue(found.compressedBytes() <= BlueprintBlobCodec.MAX_COMPRESSED_BYTES);
    }

    @Test
    void hashGuardPreventsDeletingDifferentPhysicalContent() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "house", "test", "VANILLA_NBT", structure(new byte[]{1}));
        repository.writeOnce(record);

        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.deleteIfMatches(record.assetId(), "0".repeat(64)));
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository.load(record.assetId()));
        assertTrue(repository.deleteIfMatches(record.assetId(), record.sha256()));
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Missing.class,
                repository.load(record.assetId()));
    }

    @Test
    void startupScanDeletesOnlyRecognizedCrashTemporaryFiles() throws IOException {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        TaskAssetId id = TaskAssetId.forTask(TaskId.create(), "blueprint");
        Path temporary = temporaryDirectory.resolve(id + ".nbt." + java.util.UUID.randomUUID() + ".tmp");
        Files.createDirectories(temporaryDirectory);
        Files.write(temporary, new byte[]{1, 2, 3});

        AtomicBlueprintBlobRepository.ScanResult scan = repository.scan();

        assertEquals(1, scan.removedTemporaryFiles());
        assertTrue(Files.notExists(temporary));
    }

    @Test
    void scanCannotDeleteTemporaryFileWhileSameAssetIsPublishing() throws Exception {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "house", "scan-race", "VANILLA_NBT", structure(new byte[]{1}));
        CountDownLatch moveReached = new CountDownLatch(1);
        CountDownLatch allowMove = new CountDownLatch(1);
        AtomicBlueprintBlobRepository repository = new AtomicBlueprintBlobRepository(
                temporaryDirectory, codec, (source, target) -> {
                    moveReached.countDown();
                    try {
                        if (!allowMove.await(2, TimeUnit.SECONDS)) throw new IOException("test timeout");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException(interrupted);
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var write = executor.submit(() -> repository.writeOnce(record));
            assertTrue(moveReached.await(2, TimeUnit.SECONDS));
            var scan = executor.submit(repository::scan);
            assertThrows(TimeoutException.class, () -> scan.get(100, TimeUnit.MILLISECONDS));
            allowMove.countDown();
            assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN,
                    write.get(2, TimeUnit.SECONDS));
            assertEquals(0, scan.get(2, TimeUnit.SECONDS).removedTemporaryFiles());
        }
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository.load(record.assetId()));
    }

    @Test
    void atomicMoveUnsupportedFailsClosedWithoutPublishingOrOrdinaryFallback() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "house", "atomic", "VANILLA_NBT", structure(new byte[]{1}));
        AtomicBlueprintBlobRepository repository = new AtomicBlueprintBlobRepository(
                temporaryDirectory, codec, (source, target) -> {
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test");
                });

        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(record));
        assertFalse(Files.exists(temporaryDirectory.resolve(record.assetId() + ".nbt")));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(0L, files.count());
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void ordinaryDirectoryForceIoFailureIsNeverHiddenAsWindowsLimitation() {
        IOException diskFailure = new IOException("simulated disk failure");

        AtomicBlueprintBlobRepository.BlobRepositoryException thrown = assertThrows(
                AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> AtomicBlueprintBlobRepository.forceDirectoryBestEffort(
                        temporaryDirectory, ignored -> { throw diskFailure; }));

        assertEquals(diskFailure, thrown.getCause());
    }

    @Test
    void unsupportedBlueprintFormatFailsBeforeWrite() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        CompoundTag structure = structure(new byte[]{1});
        TaskId taskId = TaskId.create();
        BlueprintBlobRecord invalid = new BlueprintBlobRecord(
                TaskAssetId.forTask(taskId, "blueprint"), taskId, 1,
                "bad", "test", "NOT_A_REAL_FORMAT", "0".repeat(64), structure);

        assertThrows(BlueprintBlobCodec.BlobCodecException.class, () -> codec.encode(invalid));
    }

    @Test
    void competingWriteOnceCallsConvergeOnSameContent() throws Exception {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "house", "race", "VANILLA_NBT", structure(new byte[]{1, 2, 3}));
        AtomicBlueprintBlobRepository first = repository(codec);
        AtomicBlueprintBlobRepository second = repository(codec);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstWrite = executor.submit(() -> {
                start.await();
                return first.writeOnce(record);
            });
            var secondWrite = executor.submit(() -> {
                start.await();
                return second.writeOnce(record);
            });
            start.countDown();
            List<AtomicBlueprintBlobRepository.WriteOutcome> outcomes =
                    List.of(firstWrite.get(2, TimeUnit.SECONDS), secondWrite.get(2, TimeUnit.SECONDS));
            assertTrue(outcomes.contains(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN));
            assertTrue(outcomes.contains(AtomicBlueprintBlobRepository.WriteOutcome.ALREADY_PRESENT));
        }
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository(codec).load(record.assetId()));
    }

    @Test
    void repeatedCompetingWritesStayIdempotentWithoutPerTaskResidentLocks() throws Exception {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "house", "striped", "VANILLA_NBT", structure(new byte[]{4, 5, 6}));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<
                    AtomicBlueprintBlobRepository.WriteOutcome>>();
            for (int i = 0; i < 32; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return repository(codec).writeOnce(record);
                }));
            }
            start.countDown();
            int written = 0;
            for (var future : futures) {
                if (future.get(3, TimeUnit.SECONDS)
                        == AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN) written++;
            }
            assertEquals(1, written);
        }
        var found = assertInstanceOf(
                AtomicBlueprintBlobRepository.LoadResult.Found.class, repository(codec).load(record.assetId()));
        assertEquals(record.sha256(), found.record().sha256());
    }

    private AtomicBlueprintBlobRepository repository(BlueprintBlobCodec codec) {
        return new AtomicBlueprintBlobRepository(temporaryDirectory, codec);
    }

    private static CompoundTag structure(byte[] payload) {
        CompoundTag structure = new CompoundTag();
        structure.putByteArray("payload", payload);
        return structure;
    }
}
