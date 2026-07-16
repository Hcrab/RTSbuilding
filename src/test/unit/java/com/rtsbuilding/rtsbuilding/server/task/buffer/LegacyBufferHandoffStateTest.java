package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBufferHandoffStateTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final RegistryAccess.Frozen REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test
    void migrationIdentityIsStableAndDimensionIndependent() {
        UUID owner = UUID.randomUUID();
        List<ItemStack> source = List.of(new ItemStack(Items.EMERALD, 4));

        LegacyBufferHandoffState first = LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, owner, 123L, source);
        LegacyBufferHandoffState retry = LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, owner, 123L, source.stream().map(ItemStack::copy).toList());

        assertEquals(first.migrationIdentity(), retry.migrationIdentity());
        assertEquals(first.sourceFingerprint(), retry.sourceFingerprint());
        assertFalse(java.util.Arrays.stream(LegacyBufferHandoffState.class.getRecordComponents())
                .anyMatch(component -> component.getType().getName().contains("ResourceKey")));
    }

    @Test
    void differentOwnerTimeOrContentProducesAnotherMigrationIdentity() {
        UUID owner = UUID.randomUUID();
        LegacyBufferHandoffState baseline = LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, owner, 123L, List.of(new ItemStack(Items.EMERALD, 4)));

        assertNotEquals(baseline.migrationIdentity(), LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, UUID.randomUUID(), 123L,
                List.of(new ItemStack(Items.EMERALD, 4))).migrationIdentity());
        assertNotEquals(baseline.migrationIdentity(), LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, owner, 124L,
                List.of(new ItemStack(Items.EMERALD, 4))).migrationIdentity());
        assertNotEquals(baseline.migrationIdentity(), LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, owner, 123L,
                List.of(new ItemStack(Items.EMERALD, 5))).migrationIdentity());
    }

    @Test
    void ownershipAdvancesOnlyAfterBothDurableAcks() {
        LegacyBufferHandoffState sessionOwned = LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, UUID.randomUUID(), 10L,
                List.of(new ItemStack(Items.IRON_INGOT, 3)));

        assertTrue(sessionOwned.sessionMayPayout());
        assertFalse(sessionOwned.taskMayDrain());
        assertThrows(IllegalStateException.class, sessionOwned::acknowledgeSessionClear);

        LegacyBufferHandoffState taskAcked = sessionOwned.acknowledgeTaskPrepared();
        assertEquals(LegacyBufferOwnershipPhase.TASK_PREPARED_ACKED, taskAcked.phase());
        assertFalse(taskAcked.sessionMayPayout());
        assertFalse(taskAcked.taskMayDrain());
        assertEquals(taskAcked, taskAcked.acknowledgeTaskPrepared());

        LegacyBufferHandoffState clearAcked = taskAcked.acknowledgeSessionClear();
        assertEquals(LegacyBufferOwnershipPhase.SESSION_CLEAR_ACKED, clearAcked.phase());
        assertFalse(clearAcked.sessionMayPayout());
        assertTrue(clearAcked.taskMayDrain());
        assertEquals(clearAcked, clearAcked.acknowledgeSessionClear());
    }

    @Test
    void sourceMatchChecksExactCountAndContents() {
        LegacyBufferHandoffState handoff = LegacyBufferHandoffState.freezeSessionOwned(
                REGISTRIES, UUID.randomUUID(), 10L,
                List.of(new ItemStack(Items.GOLD_INGOT, 3)));

        assertTrue(handoff.matchesSource(
                REGISTRIES, List.of(new ItemStack(Items.GOLD_INGOT, 3))));
        assertFalse(handoff.matchesSource(
                REGISTRIES, List.of(new ItemStack(Items.GOLD_INGOT, 2))));
        assertFalse(handoff.matchesSource(
                REGISTRIES, List.of(new ItemStack(Items.IRON_INGOT, 3))));
    }
}
