package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataClusterPersistenceTest {

    private static final DataComponent<Integer> ALPHA = integerComponent("alpha");
    private static final DataComponent<Integer> BETA = integerComponent("beta");
    private static final DataComponent<CompoundTag> MUTABLE = new DataComponent<>("mutable", NbtCodec.of(
            CompoundTag::copy,
            CompoundTag::merge), CompoundTag::new);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearSchedulerCache() throws Exception {
        schedulerClusters().clear();
    }

    @Test
    void flushRetainsLoadedSiblingComponent() {
        FakeStore store = new FakeStore(rootWith(1, 2));
        DataCluster cluster = new DataCluster(store);

        assertEquals(2, cluster.get(BETA));
        cluster.set(ALPHA, 11);

        assertTrue(cluster.flush());
        assertEquals(11, valueOf(store.lastWritten, "alpha"));
        assertEquals(2, valueOf(store.lastWritten, "beta"));
    }

    @Test
    void flushRetainsSiblingThatWasNeverLoadedIntoCellCache() {
        FakeStore store = new FakeStore(rootWith(1, 2));
        DataCluster cluster = new DataCluster(store);

        cluster.set(ALPHA, 12);

        assertTrue(cluster.flush());
        assertEquals(12, valueOf(store.lastWritten, "alpha"));
        assertEquals(2, valueOf(store.lastWritten, "beta"));
        assertEquals(1, cluster.componentCount());
    }

    @Test
    void failedWriteKeepsRevisionDirtyForRetry() {
        FakeStore store = new FakeStore(new CompoundTag());
        store.failWrites = true;
        DataCluster cluster = new DataCluster(store);
        cluster.set(ALPHA, 21);

        assertFalse(cluster.flush());
        assertEquals(1, store.writeAttempts);

        store.failWrites = false;
        assertTrue(cluster.flush());
        assertEquals(2, store.writeAttempts);
        assertEquals(21, valueOf(store.lastWritten, "alpha"));

        assertTrue(cluster.flush());
        assertEquals(2, store.writeAttempts, "成功确认 revision 后不应重复写盘");
    }

    @Test
    void failedFlushAndCloseKeepsCacheOpenUntilRetrySucceeds() {
        FakeStore store = new FakeStore(new CompoundTag());
        store.failWrites = true;
        DataCluster cluster = new DataCluster(store);
        cluster.set(ALPHA, 31);

        assertFalse(cluster.flushAndClose());
        assertEquals(1, cluster.componentCount());

        store.failWrites = false;
        assertTrue(cluster.flushAndClose());
        assertEquals(0, cluster.componentCount());
        assertEquals(31, valueOf(store.lastWritten, "alpha"));
    }

    @Test
    void flushAndClosePreservesLegacyInPlaceMutationBehavior() {
        FakeStore store = new FakeStore(rootWith(1, 2));
        DataCluster cluster = new DataCluster(store);

        cluster.get(MUTABLE).putInt("changed", 71);

        assertTrue(cluster.flushAndClose());
        assertEquals(71, store.lastWritten.getCompound("mutable").getInt("changed"));
        assertEquals(2, valueOf(store.lastWritten, "beta"));
    }

    @Test
    void corruptReadCannotBecomeEmptyRootOrTriggerOverwrite() throws Exception {
        Path file = tempDir.resolve("corrupt.dat");
        byte[] corruptBytes = new byte[]{1, 7, 3, 9, 0, 4};
        Files.write(file, corruptBytes);
        RtsAtomicNbtStore store = new RtsAtomicNbtStore(file, "test/corrupt.dat");

        assertInstanceOf(RtsNbtStore.ReadResult.Failed.class, store.readResult());
        assertThrows(IllegalStateException.class, store::read);

        DataCluster cluster = new DataCluster(store);
        assertThrows(IllegalStateException.class, () -> cluster.set(ALPHA, 41));
        assertArrayEquals(corruptBytes, Files.readAllBytes(file));
    }

    @Test
    void logoutAndStopRetainFailedClustersThenRemoveThemAfterRetry() throws Exception {
        UUID playerId = UUID.randomUUID();
        String playerKey = playerId + "::session";
        FakeStore logoutStore = failingStore();
        DataCluster logoutCluster = new DataCluster(logoutStore);
        logoutCluster.set(ALPHA, 51);
        schedulerClusters().put(playerKey, logoutCluster);

        SaveScheduler.INSTANCE.onPlayerLogout(playerId);
        assertTrue(schedulerClusters().containsKey(playerKey));

        logoutStore.failWrites = false;
        SaveScheduler.INSTANCE.onPlayerLogout(playerId);
        assertFalse(schedulerClusters().containsKey(playerKey));

        String stopKey = UUID.randomUUID() + "::workflow";
        FakeStore stopStore = failingStore();
        DataCluster stopCluster = new DataCluster(stopStore);
        stopCluster.set(ALPHA, 61);
        schedulerClusters().put(stopKey, stopCluster);

        SaveScheduler.INSTANCE.onServerStopped();
        assertTrue(schedulerClusters().containsKey(stopKey));

        stopStore.failWrites = false;
        SaveScheduler.INSTANCE.onServerStopped();
        assertFalse(schedulerClusters().containsKey(stopKey));
    }

    private static FakeStore failingStore() {
        FakeStore store = new FakeStore(new CompoundTag());
        store.failWrites = true;
        return store;
    }

    private static DataComponent<Integer> integerComponent(String key) {
        return new DataComponent<>(key, NbtCodec.of(
                tag -> tag.getInt("value"),
                (tag, value) -> tag.putInt("value", value)), () -> 0);
    }

    private static CompoundTag rootWith(int alpha, int beta) {
        CompoundTag root = new CompoundTag();
        root.put("alpha", slot(alpha));
        root.put("beta", slot(beta));
        return root;
    }

    private static CompoundTag slot(int value) {
        CompoundTag slot = new CompoundTag();
        slot.putInt("value", value);
        return slot;
    }

    private static int valueOf(CompoundTag root, String key) {
        return root.getCompound(key).getInt("value");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, DataCluster> schedulerClusters() throws Exception {
        Field field = SaveScheduler.class.getDeclaredField("clusters");
        field.setAccessible(true);
        return (Map<String, DataCluster>) field.get(SaveScheduler.INSTANCE);
    }

    private static final class FakeStore implements RtsNbtStore {
        private final CompoundTag initialRoot;
        private boolean failWrites;
        private int writeAttempts;
        private CompoundTag lastWritten;

        private FakeStore(CompoundTag initialRoot) {
            this.initialRoot = initialRoot.copy();
        }

        @Override
        public RtsNbtStore.ReadResult readResult() {
            return RtsNbtStore.ReadResult.found(initialRoot.copy());
        }

        @Override
        public boolean write(CompoundTag tag) {
            writeAttempts++;
            if (failWrites) return false;
            lastWritten = tag.copy();
            return true;
        }

        @Override
        public String label() {
            return "fake";
        }
    }
}
