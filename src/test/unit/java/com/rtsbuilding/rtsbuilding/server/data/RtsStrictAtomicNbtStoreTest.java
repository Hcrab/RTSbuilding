package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsStrictAtomicNbtStoreTest {
    @TempDir
    Path directory;

    @Test
    void strictRootRoundTripReloadsMultiMiBPayloadUnderProductionAccounter() {
        Path file = directory.resolve("durable_tasks.dat");
        RtsStrictAtomicNbtStore store = new RtsStrictAtomicNbtStore(file, "test/root");
        byte[] payload = new byte[5 * 1024 * 1024];
        new Random(0x525453L).nextBytes(payload);
        CompoundTag root = new CompoundTag();
        root.putByteArray("payload", payload);

        assertTrue(store.write(root));
        var found = assertInstanceOf(
                RtsNbtStore.ReadResult.Found.class, store.readResult());
        assertArrayEquals(payload, found.root().getByteArray("payload"));
    }

    @Test
    void unsupportedAtomicMoveNeverFallsBackOrReplacesExistingTarget() throws IOException {
        Path file = directory.resolve("durable_tasks.dat");
        Files.write(file, new byte[]{1, 2, 3});
        RtsStrictAtomicNbtStore store = new RtsStrictAtomicNbtStore(
                file, "test/root",
                (source, target) -> {
                    throw new AtomicMoveNotSupportedException(
                            source.toString(), target.toString(), "test");
                }, ignored -> { }, ignored -> { });
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 2);

        assertFalse(store.write(root));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }

    @Test
    void ordinaryDirectoryForceIoFailureIsNeverReportedAsSuccess() {
        Path file = directory.resolve("durable_tasks.dat");
        RtsStrictAtomicNbtStore store = new RtsStrictAtomicNbtStore(
                file, "test/root",
                (source, target) -> Files.move(source, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE),
                ignored -> { }, ignored -> { throw new IOException("simulated disk failure"); });
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 2);

        assertFalse(store.write(root));
    }

    @Test
    void targetFileForceFailureIsNeverReportedAsSuccess() {
        Path file = directory.resolve("durable_tasks.dat");
        RtsStrictAtomicNbtStore store = new RtsStrictAtomicNbtStore(
                file, "test/root",
                (source, target) -> Files.move(source, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE),
                ignored -> { throw new IOException("simulated force failure"); }, ignored -> { });
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 2);

        assertFalse(store.write(root));
    }
}
