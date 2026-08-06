package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalSortField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CraftTerminalSortAdapterTest {
    @Test
    void 名称和数量保持双向一一映射() {
        assertEquals(CraftTerminalSortField.NAME,
                CraftTerminalSortAdapter.fromStorage(RtsStorageSort.NAME));
        assertEquals(CraftTerminalSortField.QUANTITY,
                CraftTerminalSortAdapter.fromStorage(RtsStorageSort.QUANTITY));
        assertEquals(RtsStorageSort.NAME,
                CraftTerminalSortAdapter.toStorage(CraftTerminalSortField.NAME));
        assertEquals(RtsStorageSort.QUANTITY,
                CraftTerminalSortAdapter.toStorage(CraftTerminalSortField.QUANTITY));
    }

    @Test
    void 底部栏独有的模组排序进入终端时规范化为数量() {
        assertEquals(RtsStorageSort.QUANTITY,
                CraftTerminalSortAdapter.normalize(RtsStorageSort.MOD));
        assertEquals(CraftTerminalSortField.QUANTITY,
                CraftTerminalSortAdapter.fromStorage(RtsStorageSort.MOD));
    }
}
