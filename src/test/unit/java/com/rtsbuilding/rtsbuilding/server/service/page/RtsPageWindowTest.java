package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class RtsPageWindowTest {
    private MockedStatic<Config> config;

    @BeforeEach
    void serverAllowsAllSupportedPageSizes() {
        // 只注入服务器配置；分页归一化和偏移计算仍使用完整生产方法。
        config = mockStatic(Config.class);
        config.when(Config::maxStoragePageSize).thenReturn(8192);
    }

    @AfterEach
    void restoreConfigFacade() {
        config.close();
    }

    @Test
    void serverCapDeterminesTheEffectiveWindow() {
        config.when(Config::maxStoragePageSize).thenReturn(17);
        RtsPageWindow window = RtsPageWindow.calculate(1, 8192, 18);
        assertEquals(8192, window.requestedPageSize());
        assertEquals(17, window.effectivePageSize());
        assertEquals(17L, window.globalIndex());
        assertEquals(1, window.itemCount());
    }

    @Test
    void normalizesEmptyAndBoundaryPagesForSupportedSizes() {
        for (int requestedSize : new int[]{1, 17, 90, 180, 256, 8192}) {
            int effective = RtsPageSharedHelpers.sanitizePageSize(requestedSize);
            RtsPageWindow empty = RtsPageWindow.calculate(-1, requestedSize, 0);
            assertEquals(1, empty.totalPages());
            assertEquals(0L, empty.globalIndex());
            assertEquals(0, empty.itemCount());
            assertEquals(effective, empty.effectivePageSize());

            RtsPageWindow onePast = RtsPageWindow.calculate(1, requestedSize, effective + 1);
            assertEquals(1, onePast.safePage());
            assertEquals((long) effective, onePast.globalIndex());
            assertEquals(1, onePast.itemCount());
        }
    }

    @Test
    void largeOffsetsUseLongArithmeticAndStayInsideEntries() {
        RtsPageWindow window = RtsPageWindow.calculate(
                Integer.MAX_VALUE, 8192, Integer.MAX_VALUE);
        assertTrue(window.globalIndex() >= 0L);
        assertTrue(window.globalIndex() <= Integer.MAX_VALUE);
        assertTrue(window.toIndex() <= Integer.MAX_VALUE);
        assertTrue(window.toIndex() >= window.fromIndex());
    }
}
