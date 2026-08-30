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
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    void durableReceiptIsBoundToIssuingRepositoryInstance() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository issuer = repository(codec);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 2, "receipt", "test", "VANILLA_NBT", structure(new byte[]{8, 9}));

        var receipt = issuer.writeDurably(record);
        var verified = issuer.requireIssued(receipt);
        var metadata = verified.metadata();
        assertEquals(record.assetId(), metadata.assetId());
        assertEquals(record.taskId(), metadata.taskId());
        assertEquals(record.sha256(), metadata.sha256());
        assertEquals(record.blockCount(), verified.blockCount());
        assertTrue(metadata.compressedBytes() > 0L);
        assertTrue(metadata.logicalBytes() > 0L);

        AtomicBlueprintBlobRepository foreign = new AtomicBlueprintBlobRepository(
                temporaryDirectory.resolve("foreign"), codec);
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> foreign.requireIssued(receipt));
    }

    @Test
    void durableReceiptIsRevalidatedAgainstPhysicalBlobBeforeRootAdmission() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "receipt-gc", "test", "VANILLA_NBT", structure(new byte[]{7}));
        var receipt = repository.writeDurably(record);

        assertTrue(repository.deleteIfMatches(record.assetId(), record.sha256()));

        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.requireIssued(receipt),
                "仅持有旧 receipt 不能在 blob 已被删除后建立悬空 root 引用");
    }

    @Test
    void admissionProofPinsBlobAndIdempotentConsumeRequiresExistingRootLease() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "proof-pin", "test", "VANILLA_NBT", structure(new byte[]{6}));
        var firstProof = repository.verifyForAdmission(repository.writeDurably(record));

        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.deleteIfMatches(record.assetId(), record.sha256()),
                "队列持有 proof 时任何 GC 都不能删除对应 blob");
        assertEquals(record.assetId(), repository.requireIssued(firstProof).metadata().assetId());
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.consumeAdmissionProof(firstProof),
                "新 root 不能绕过 ACK promotion 直接消费 proof");

        repository.promoteAdmissionProof(firstProof);
        var duplicateProof = repository.verifyForAdmission(repository.writeDurably(record));
        repository.consumeAdmissionProof(duplicateProof);

        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.requireIssued(duplicateProof), "proof 只能完成一次 Coordinator 交接");
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.deleteIfMatches(record.assetId(), record.sha256()));
        assertTrue(repository.deleteAfterRootRemovalIfMatches(record.assetId(), record.sha256()));
    }

    @Test
    void rejectedNewAdmissionProofReleasesPinAndCleansExactBlob() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "proof-reject", "test", "VANILLA_NBT", structure(new byte[]{5}));
        var proof = repository.verifyForAdmission(repository.writeDurably(record));

        repository.rejectAdmissionProof(proof);

        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Missing.class,
                repository.load(record.assetId()));
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.requireIssued(proof));
    }

    @Test
    void delayedOldRootCleanupCannotDeleteNewGenerationWithSameAssetAndHash() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "lease-generation", "test", "VANILLA_NBT", structure(new byte[]{4}));
        var oldProof = repository.verifyForAdmission(repository.writeDurably(record));
        repository.promoteAdmissionProof(oldProof);
        var newProof = repository.verifyForAdmission(repository.writeDurably(record));
        repository.promoteAdmissionProof(newProof);

        assertFalse(repository.deleteAfterRootRemovalIfMatches(
                record.assetId(), record.sha256()),
                "延迟的旧 root cleanup 只能释放旧 lease，不能删除新 generation 的 blob");
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository.load(record.assetId()));
        assertTrue(repository.deleteAfterRootRemovalIfMatches(
                record.assetId(), record.sha256()));
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Missing.class,
                repository.load(record.assetId()));
    }

    @Test
    void reconciliationKeepsStartupLiveSetDeletesOrphansAndReopensAdmission() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord live = codec.freeze(
                TaskId.create(), 1, "live", "test", "VANILLA_NBT", structure(new byte[]{1}));
        BlueprintBlobRecord orphan = codec.freeze(
                TaskId.create(), 1, "orphan", "test", "VANILLA_NBT", structure(new byte[]{2}));
        BlueprintBlobRecord after = codec.freeze(
                TaskId.create(), 1, "after", "test", "VANILLA_NBT", structure(new byte[]{3}));
        repository.writeOnce(live);
        repository.writeOnce(orphan);

        repository.beginReconciliation(Set.of(live.assetId()));
        assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.ALREADY_PRESENT,
                repository.writeOnce(live), "保护集中的幂等写仍可用");
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(orphan), "旧 orphan 不能在维护中被重新接纳");
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(after), "维护期间必须关闭新资产 admission");

        var reconciled = repository.reconcileOrphans();
        assertTrue(reconciled.complete());
        assertFalse(reconciled.failed());
        assertEquals(1, reconciled.deletedOrphans());
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository.load(live.assetId()));
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Missing.class,
                repository.load(orphan.assetId()));
        assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN,
                repository.writeOnce(after), "完整 rescan 后应恢复 admission");
    }

    @Test
    void reconciliationFailureKeepsAdmissionClosedWithoutDeletingLiveBlob() throws IOException {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord live = codec.freeze(
                TaskId.create(), 1, "live", "test", "VANILLA_NBT", structure(new byte[]{4}));
        BlueprintBlobRecord after = codec.freeze(
                TaskId.create(), 1, "after", "test", "VANILLA_NBT", structure(new byte[]{5}));
        repository.writeOnce(live);
        Files.writeString(temporaryDirectory.resolve("unknown.txt"), "do not guess");

        repository.beginReconciliation(Set.of(live.assetId()));
        var reconciled = repository.reconcileOrphans();
        assertTrue(reconciled.failed());
        assertFalse(reconciled.complete());
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository.load(live.assetId()));
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(after));
    }

    @Test
    void reconciliationDeletesRecognizedCrashTempsSoMaintenanceCanMakeBoundedProgress() throws IOException {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        TaskAssetId assetId = TaskAssetId.forTask(TaskId.create(), "blueprint");
        Files.createDirectories(temporaryDirectory);
        Path crashTemp = temporaryDirectory.resolve(assetId + ".nbt." + java.util.UUID.randomUUID() + ".tmp");
        Files.write(crashTemp, new byte[]{1});

        repository.beginReconciliation(Set.of());
        var result = repository.reconcileOrphans();

        assertTrue(result.complete());
        assertFalse(result.failed());
        assertEquals(1, result.deletedOrphans());
        assertFalse(Files.exists(crashTemp));
    }

    @Test
    void reconciliationKeepsLookalikeUnknownTempAndFailsClosed() throws IOException {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        TaskAssetId assetId = TaskAssetId.forTask(TaskId.create(), "blueprint");
        Path lookalike = temporaryDirectory.resolve(assetId + ".nbt.crash.tmp");
        Files.write(lookalike, new byte[]{1});

        repository.beginReconciliation(Set.of());
        var result = repository.reconcileOrphans();

        assertTrue(result.failed());
        assertFalse(result.complete());
        assertTrue(Files.exists(lookalike), "未知近似文件不能被猜测为本仓库临时文件而删除");
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
    void canonicalHashNormalizesNanAndRejectsAmbiguousUtf16() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        TaskId taskId = TaskId.create();
        CompoundTag first = new CompoundTag();
        first.putFloat("float_nan", Float.intBitsToFloat(0x7fc00001));
        first.putDouble("double_nan", Double.longBitsToDouble(0x7ff8000000000001L));
        CompoundTag second = new CompoundTag();
        second.putFloat("float_nan", Float.intBitsToFloat(0x7fc12345));
        second.putDouble("double_nan", Double.longBitsToDouble(0x7ff8123456789abCL));

        BlueprintBlobRecord firstRecord = codec.freeze(
                taskId, 1, "nan", "test", "VANILLA_NBT", first);
        BlueprintBlobRecord secondRecord = codec.freeze(
                taskId, 1, "nan", "test", "VANILLA_NBT", second);
        assertEquals(firstRecord.sha256(), secondRecord.sha256());

        CompoundTag invalid = new CompoundTag();
        invalid.putString("text", "broken-\uD800");
        assertThrows(IllegalArgumentException.class,
                () -> codec.freeze(TaskId.create(), 1, "utf", "test", "VANILLA_NBT", invalid));

        CompoundTag valid = new CompoundTag();
        valid.putString("text", "paired-\uD83D\uDE80");
        BlueprintBlobRecord validRecord = codec.freeze(
                TaskId.create(), 1, "utf", "test", "VANILLA_NBT", valid);
        assertEquals(validRecord.sha256(),
                codec.decodeCompressed(codec.encodeCompressed(validRecord)).sha256());
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
    void streamingCodecDoesNotCloseCallerOutputStream() throws IOException {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        BlueprintBlobRecord record = codec.freeze(
                TaskId.create(), 1, "stream", "test", "VANILLA_NBT", structure(new byte[]{1, 2}));
        AtomicBoolean closed = new AtomicBoolean();
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };

        codec.writeCompressed(record, output);

        assertFalse(closed.get());
        assertEquals(record.sha256(), codec.decodeCompressed(output.toByteArray()).sha256());
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
    void truncatedScanKeepsExactLoadsAvailableAndRejectsOnlyNewAdmissions() throws IOException {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord first = codec.freeze(
                TaskId.create(), 1, "first", "quota", "VANILLA_NBT", structure(new byte[]{1}));
        BlueprintBlobRecord second = codec.freeze(
                TaskId.create(), 1, "second", "quota", "VANILLA_NBT", structure(new byte[]{2}));
        BlueprintBlobRecord third = codec.freeze(
                TaskId.create(), 1, "third", "quota", "VANILLA_NBT", structure(new byte[]{3}));
        repository.writeOnce(first);
        repository.writeOnce(second);

        AtomicBlueprintBlobRepository.ScanResult scan = repository.scan(1, Long.MAX_VALUE);

        assertFalse(scan.complete());
        assertTrue(scan.quotaExceeded());
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository.load(first.assetId()));
        assertInstanceOf(AtomicBlueprintBlobRepository.LoadResult.Found.class,
                repository.load(second.assetId()));
        assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.ALREADY_PRESENT,
                repository.writeOnce(first));
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(third));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(2L, files.filter(path -> path.getFileName().toString().endsWith(".nbt")).count());
        }
    }

    @Test
    void completeRescanAfterCleanupRestoresNewAssetAdmission() {
        BlueprintBlobCodec codec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository repository = repository(codec);
        BlueprintBlobRecord first = codec.freeze(
                TaskId.create(), 1, "first", "recover", "VANILLA_NBT", structure(new byte[]{1}));
        BlueprintBlobRecord second = codec.freeze(
                TaskId.create(), 1, "second", "recover", "VANILLA_NBT", structure(new byte[]{2}));
        BlueprintBlobRecord afterCleanup = codec.freeze(
                TaskId.create(), 1, "third", "recover", "VANILLA_NBT", structure(new byte[]{3}));
        repository.writeOnce(first);
        repository.writeOnce(second);
        assertTrue(repository.scan(1, Long.MAX_VALUE).quotaExceeded());
        assertThrows(AtomicBlueprintBlobRepository.BlobRepositoryException.class,
                () -> repository.writeOnce(afterCleanup));

        assertTrue(repository.deleteIfMatches(second.assetId(), second.sha256()));
        AtomicBlueprintBlobRepository.ScanResult rescan = repository.scan();

        assertTrue(rescan.complete());
        assertFalse(rescan.quotaExceeded());
        assertEquals(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN,
                repository.writeOnce(afterCleanup));
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
            var scan = executor.submit(() -> { return repository.scan(); });
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
