package com.rtsbuilding.rtsbuilding.common.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 保存前/写盘后异常都必须回滚内存并再次写回旧值，不能伪装成成功。 */
class RtsServerConfigPersistenceTest {
    @Test
    void successfulSaveIsCommittedWithoutRollback() {
        RtsServerConfigPersistence.Result result = RtsServerConfigPersistence.saveWithRollback(
                () -> { }, () -> { throw new AssertionError("must not restore"); }, () -> { });

        assertTrue(result.committed());
        assertFalse(result.rollbackCompleted());
        assertNull(result.failure());
    }

    @Test
    void writeThenReloadFailureRestoresMemoryAndDisk() {
        List<String> calls = new ArrayList<>();
        boolean[] diskValue = {false};
        int[] memoryValue = {1};

        RtsServerConfigPersistence.Result result = RtsServerConfigPersistence.saveWithRollback(
                () -> {
                    diskValue[0] = true;
                    calls.add("candidate");
                    throw new IllegalStateException("reload callback failed");
                },
                () -> {
                    memoryValue[0] = 1;
                    calls.add("restore-memory");
                },
                () -> {
                    diskValue[0] = false;
                    calls.add("previous");
                });

        assertFalse(result.committed());
        assertTrue(result.rollbackCompleted());
        assertEquals(1, memoryValue[0]);
        assertFalse(diskValue[0]);
        assertEquals(List.of("candidate", "restore-memory", "previous"), calls);
    }

    @Test
    void failedPreviousWriteIsReportedAsUnconfirmedRollback() {
        RtsServerConfigPersistence.Result result = RtsServerConfigPersistence.saveWithRollback(
                () -> { throw new IllegalStateException("candidate failed"); },
                () -> { },
                () -> { throw new IllegalStateException("previous write failed"); });

        assertFalse(result.committed());
        assertFalse(result.rollbackCompleted());
        assertTrue(result.failure().getSuppressed().length > 0);
    }
}
