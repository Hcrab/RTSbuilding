package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferSourceClaimPlannerTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void acceptsNaturalMergeWhenSurvivingSourceKeepsExactGroupTotal() {
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        BufferEscrowEntry first = prepared(0, sourceA, Items.DIRT, 1);
        BufferEscrowEntry second = prepared(1, sourceB, Items.DIRT, 1);

        BufferSourceClaimPlanner.Plan plan = BufferSourceClaimPlanner.plan(
                List.of(first, second), Map.of(sourceA, new ItemStack(Items.DIRT, 2)), 32);

        assertEquals(2, plan.processedStacks());
        assertEquals(2, plan.claimedClaimIds().size());
        assertTrue(plan.recoveryClaimIds().isEmpty());
        assertEquals(java.util.Set.of(sourceA), plan.sourceEntityIdsToDiscard());
    }

    @Test
    void rejectsPickupOrForeignMergeInsteadOfInventingItems() {
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        List<BufferEscrowEntry> entries = List.of(
                prepared(0, sourceA, Items.DIRT, 1),
                prepared(1, sourceB, Items.DIRT, 1));

        BufferSourceClaimPlanner.Plan missing = BufferSourceClaimPlanner.plan(
                entries, Map.of(sourceA, new ItemStack(Items.DIRT, 1)), 32);
        BufferSourceClaimPlanner.Plan contaminated = BufferSourceClaimPlanner.plan(
                entries, Map.of(sourceA, new ItemStack(Items.DIRT, 3)), 32);

        assertTrue(missing.claimedClaimIds().isEmpty());
        assertEquals(2, missing.recoveryClaimIds().size());
        assertTrue(missing.sourceEntityIdsToDiscard().isEmpty());
        assertTrue(contaminated.claimedClaimIds().isEmpty());
        assertEquals(2, contaminated.recoveryClaimIds().size());
        assertTrue(contaminated.sourceEntityIdsToDiscard().isEmpty());
    }

    @Test
    void keepsMergeGroupAtomicWhenItSlightlyExceedsSliceAllowance() {
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        List<BufferEscrowEntry> entries = List.of(
                prepared(0, sourceA, Items.STONE, 1),
                prepared(1, sourceB, Items.STONE, 1));

        BufferSourceClaimPlanner.Plan plan = BufferSourceClaimPlanner.plan(
                entries, Map.of(sourceA, new ItemStack(Items.STONE, 2)), 1);

        assertEquals(2, plan.processedStacks());
        assertEquals(2, plan.claimedClaimIds().size());
    }

    private static BufferEscrowEntry prepared(
            int ordinal, UUID source, net.minecraft.world.item.Item item, int count) {
        return BufferEscrowEntry.prepared(
                UUID.randomUUID(), ordinal, source, new ItemStack(item, count));
    }
}
