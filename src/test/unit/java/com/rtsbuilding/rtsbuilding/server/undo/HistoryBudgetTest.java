package com.rtsbuilding.rtsbuilding.server.undo;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBudget;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forge 历史摘要的字节、节点、方块实体和凭据列预算契约。 */
class HistoryBudgetTest {
    @Test
    void oversizedBlockEntityIsRejectedBeforeSpendingAnyHistoryBudget() {
        HistoryBudget budget = HistoryBudget.empty(1);
        CompoundTag candidate = record(1L, "minecraft:chest");
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putByteArray("payload", new byte[(int) HistoryBudget.MAX_BLOCK_ENTITY_LOGICAL_BYTES + 1]);
        candidate.put("block_entity", blockEntity);
        assertFalse(budget.canAppend(List.of(candidate)));
        assertEquals(0, budget.recordCount());
        assertTrue(budget.canAppend(List.of(record(2L, "minecraft:stone"))));
    }

    @Test
    void deepNbtAndTooManyCollateralRecordsFailWithoutChangingExistingHistory() {
        HistoryBudget budget = HistoryBudget.empty(1);
        CompoundTag candidate = record(1L, "minecraft:chest");
        CompoundTag child = new CompoundTag();
        candidate.put("block_entity", child);
        for (int i = 0; i < 70; i++) {
            CompoundTag next = new CompoundTag();
            child.put("child", next);
            child = next;
        }
        assertFalse(budget.canAppend(List.of(candidate)));
        assertFalse(budget.canAppend(java.util.Collections.nCopies(8, record(1L, "minecraft:stone"))));
        assertTrue(budget.canAppend(java.util.Collections.nCopies(7, record(1L, "minecraft:stone"))));
        assertEquals(0, budget.recordCount());
    }

    @Test
    void repeatedBlockStatesUseOnePaletteEntryAndStayWithinTaskBudget() {
        List<CompoundTag> records = new ArrayList<>();
        for (int i = 0; i < 4_096; i++) {
            CompoundTag record = new CompoundTag();
            record.putLong("pos", i);
            record.put("state", stateWithProperties());
            records.add(record);
        }

        HistoryBudget budget = HistoryBudget.forTargets(MiningLimits.MAX_VOLUME, records);

        assertEquals(4_096, budget.recordCount());
        assertEquals(1, budget.stateCount());
        assertTrue(budget.accepts());
        assertTrue(budget.estimatedPayloadNodes() < HistoryBudget.MAX_TASK_PAYLOAD_NODES);
    }

    @Test
    void credentialsConsumeRealColumnBudgetAndIncrementalAppendPreservesPrefix() {
        CompoundTag first = record(1L, "minecraft:stone");
        CompoundTag credential = new CompoundTag();
        credential.putString("block", "minecraft:stone");
        credential.putLong("generation", 1L);
        credential.putByte("kind", (byte) 0);
        first.put("credential_before", credential);
        HistoryBudget budget = HistoryBudget.forTargets(2, List.of(first));
        List<CompoundTag> frozen = new ArrayList<>();
        frozen.add(first);
        CompoundTag second = record(2L, "minecraft:dirt");
        HistoryBudget next = budget.append(List.of(first, second), 1);

        assertEquals(2, next.recordCount());
        assertEquals(2, next.stateCount());
        assertTrue(next.accepts());
        assertTrue(next.estimatedPayloadBytes() >= budget.estimatedPayloadBytes());
        assertFalse(next.canAppend(List.of(new CompoundTag())));
        assertSame(first, frozen.get(0));
        assertEquals(1, budget.recordCount(), "增量 append 不得修改旧摘要");
    }

    private static CompoundTag stateWithProperties() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:oak_log");
        CompoundTag properties = new CompoundTag();
        properties.putString("axis", "y");
        properties.putString("waterlogged", "false");
        state.put("Properties", properties);
        return state;
    }

    private static CompoundTag record(long pos, String block) {
        CompoundTag record = new CompoundTag();
        record.putLong("pos", pos);
        CompoundTag state = new CompoundTag();
        state.putString("Name", block);
        record.put("state", state);
        return record;
    }
}
