package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventBus;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsWorkflowTimeoutServiceTest {

    @Test
    void serviceDoesNotScanBeforeStartOrAfterStop() {
        Fixture fixture = fixture(11);

        fixture.service.tick(null, 100L);

        assertTrue(fixture.events.isEmpty());
        assertNotNull(fixture.slots.findEntryById(11));

        fixture.service.start(Duration.ofMillis(50L), Duration.ofMillis(1L));
        fixture.service.tick(null, 101L); // 首次 Tick 只建立 deadline。
        fixture.service.stop();
        fixture.service.tick(null, 102L);

        assertTrue(fixture.events.isEmpty());
        assertNotNull(fixture.slots.findEntryById(11));
    }

    @Test
    void startedServiceScansOnlyWhenGameTimeDeadlineIsDue() {
        Fixture fixture = fixture(12);
        fixture.service.start(Duration.ofMillis(150L), Duration.ofMillis(1L));

        fixture.service.tick(null, 200L);
        fixture.service.tick(null, 202L);

        assertTrue(fixture.events.isEmpty(), "三个 Tick 间隔到期前不得扫描");
        assertNotNull(fixture.slots.findEntryById(12));

        fixture.service.tick(null, 203L);

        assertEquals(List.of(WorkflowEventType.TIMEOUT), eventTypes(fixture.events));
        assertEquals(-1, fixture.slots.findIndexByEntryId(12));
        assertEquals(List.of(new DirtyMark(fixture.playerId, Level.OVERWORLD)), fixture.dirtyMarks);
    }

    @Test
    void sameExpiredEntryProducesOneTerminalEventAndOneDirtyMark() {
        Fixture fixture = fixture(13);
        fixture.service.start(Duration.ofMillis(50L), Duration.ofMillis(1L));

        fixture.service.tick(null, 300L);
        fixture.service.tick(null, 301L);
        fixture.service.tick(null, 302L);
        fixture.service.tick(null, 303L);

        assertEquals(List.of(WorkflowEventType.TIMEOUT), eventTypes(fixture.events));
        assertEquals(1, fixture.dirtyMarks.size(), "重复扫描不得重复提交工作流同步意图");
    }

    @Test
    void manualCancellationAndTimeoutHaveOnlyOneWinner() {
        Fixture manualWins = fixture(14);
        manualWins.service.start(Duration.ofMillis(50L), Duration.ofMillis(1L));
        manualWins.service.tick(null, 400L);
        assertTrue(manualWins.slots.removeEntryById(14));
        manualWins.service.tick(null, 401L);

        assertTrue(manualWins.events.isEmpty(), "手动删除已获胜时不得再发 TIMEOUT");
        assertTrue(manualWins.dirtyMarks.isEmpty());

        Fixture timeoutWins = fixture(15);
        timeoutWins.service.start(Duration.ofMillis(50L), Duration.ofMillis(1L));
        timeoutWins.service.tick(null, 500L);
        timeoutWins.service.tick(null, 501L);

        assertEquals(List.of(WorkflowEventType.TIMEOUT), eventTypes(timeoutWins.events));
        assertFalse(timeoutWins.slots.removeEntryById(15), "超时已获胜时手动删除必须成为幂等空操作");
    }

    @Test
    void enabledServiceRejectsMutationOutsideServerThread() {
        Fixture fixture = fixture(16, false);
        fixture.service.start(Duration.ofMillis(50L), Duration.ofMillis(1L));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fixture.service.tick(null, 600L));

        assertTrue(error.getMessage().contains("server thread"));
        assertNotNull(fixture.slots.findEntryById(16));
        assertTrue(fixture.events.isEmpty());
    }

    private static Fixture fixture(int entryId) {
        return fixture(entryId, true);
    }

    private static Fixture fixture(int entryId, boolean serverThread) {
        UUID playerId = UUID.randomUUID();
        RtsWorkflowSlotManager slots = staleSlots(entryId);
        Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> allSlots = new HashMap<>();
        allSlots.put(playerId, new HashMap<>(Map.of(Level.OVERWORLD, slots)));

        RtsWorkflowEventBus eventBus = new RtsWorkflowEventBus();
        List<WorkflowEvent> events = new ArrayList<>();
        List<DirtyMark> dirtyMarks = new ArrayList<>();
        eventBus.addListener(events::add);

        RtsWorkflowTimeoutService service = new RtsWorkflowTimeoutService(
                allSlots,
                eventBus,
                (owner, dimension) -> dirtyMarks.add(new DirtyMark(owner, dimension)),
                ignored -> serverThread);
        return new Fixture(playerId, slots, service, events, dirtyMarks);
    }

    /** 使用持久化入口构造确定过期的真实槽位，避免用源码字符串或 Mockito 冒充行为测试。 */
    private static RtsWorkflowSlotManager staleSlots(int entryId) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("id", entryId);
        entry.putString("type", RtsWorkflowType.PLACE_BATCH.name());
        entry.putInt("priority", RtsWorkflowPriority.NORMAL.rank());
        entry.putInt("total_blocks", 1);
        entry.putLong("created_at", 0L);
        entry.putLong("last_updated_at", 0L);

        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag root = new CompoundTag();
        root.putInt("next_id", entryId + 1);
        root.put("entries", entries);
        return RtsWorkflowSlotManager.loadFromNbt(root);
    }

    private static List<WorkflowEventType> eventTypes(List<WorkflowEvent> events) {
        return events.stream().map(WorkflowEvent::type).toList();
    }

    private record DirtyMark(UUID playerId, ResourceKey<Level> dimension) {
    }

    private record Fixture(
            UUID playerId,
            RtsWorkflowSlotManager slots,
            RtsWorkflowTimeoutService service,
            List<WorkflowEvent> events,
            List<DirtyMark> dirtyMarks) {
    }
}
