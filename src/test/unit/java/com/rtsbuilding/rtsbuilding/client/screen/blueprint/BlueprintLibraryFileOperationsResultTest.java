package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件操作结果必须保持纯数据，UI owner 才能确定地按“重载、选择、状态”顺序消费。
 */
class BlueprintLibraryFileOperationsResultTest {
    @Test
    void statusResultDoesNotInventReloadOrSelection() {
        BlueprintLibraryFileOperations.Result result =
                BlueprintLibraryFileOperations.Result.status(
                        (byte) 2, "status.key", null);

        assertFalse(result.reload());
        assertEquals("", result.selectedFileName());
        assertEquals(BlueprintLibraryFileOperations.SelectionMode.NONE,
                result.selectionMode());
        assertEquals((byte) 2, result.status());
        assertEquals("status.key", result.messageKey());
        assertEquals("", result.detail());
    }

    @Test
    void reloadResultCarriesIndexOnlySelectionWithoutUiMutation() {
        BlueprintLibraryFileOperations.Result result =
                BlueprintLibraryFileOperations.Result.reloadAndSelect(
                        (byte) 1, "imported", "demo.nbt", "demo.nbt");

        assertTrue(result.reload());
        assertEquals("demo.nbt", result.selectedFileName());
        assertEquals(BlueprintLibraryFileOperations.SelectionMode.INDEX_ONLY,
                result.selectionMode());
        assertEquals((byte) 1, result.status());
    }

    @Test
    void fullSelectionCanIntentionallyLeaveStatusUntouched() {
        BlueprintLibraryFileOperations.Result result =
                BlueprintLibraryFileOperations.Result.selectFully("same.nbt");

        assertFalse(result.reload());
        assertEquals("same.nbt", result.selectedFileName());
        assertEquals(BlueprintLibraryFileOperations.SelectionMode.FULL,
                result.selectionMode());
        assertNull(result.status());
        assertEquals("", result.messageKey());
    }

    @Test
    void canonicalConstructorNormalizesNullableTextAndSelectionMode() {
        BlueprintLibraryFileOperations.Result result =
                new BlueprintLibraryFileOperations.Result(
                        false, null, null, null, null, null);

        assertEquals("", result.selectedFileName());
        assertEquals(BlueprintLibraryFileOperations.SelectionMode.NONE,
                result.selectionMode());
        assertEquals("", result.messageKey());
        assertEquals("", result.detail());
    }
}
