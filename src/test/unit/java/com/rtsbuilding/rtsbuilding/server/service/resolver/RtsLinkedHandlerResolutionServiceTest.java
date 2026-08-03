package com.rtsbuilding.rtsbuilding.server.service.resolver;

import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 验证读写候选列表在进入实际传输器前就严格执行链接模式。 */
class RtsLinkedHandlerResolutionServiceTest {
    @Test
    void extractOnlyEndpointMustNeverEnterInsertCandidates() {
        IItemHandler extractOnly = new ItemStackHandler(1);
        IItemHandler bidirectional = new ItemStackHandler(1);
        List<LinkedHandler> linked = List.of(
                linked(0, extractOnly, false, 100),
                linked(1, bidirectional, true, -100));

        List<IItemHandler> insert = RtsLinkedHandlerResolutionService.itemHandlersForInsert(linked);
        List<IItemHandler> extract = RtsLinkedHandlerResolutionService.itemHandlersForExtract(linked);

        assertEquals(1, insert.size());
        assertSame(bidirectional, insert.get(0),
                "高优先级也不能让 Extract-only 进入写入候选");
        assertEquals(2, extract.size(), "Extract-only 必须继续参与提取");
        assertSame(bidirectional, extract.get(0), "提取仍按低优先级优先排序");
        assertSame(extractOnly, extract.get(1));
    }

    private static LinkedHandler linked(int x, IItemHandler handler, boolean allowStore, int priority) {
        return new LinkedHandler(new LinkedStorageRef(0, new BlockPos(x, 64, 0)),
                "test", handler, allowStore, priority);
    }
}
