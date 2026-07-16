package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferEscrowStateTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }


    @Test
    void itemStacksAreDefensivelyCopiedAcrossTaskRevisions() {
        ItemStack input = new ItemStack(Items.DIAMOND, 8);
        BufferEscrowEntry entry = BufferEscrowEntry.alreadyEscrowed(UUID.randomUUID(), 0, input);
        BufferEscrowState state = new BufferEscrowState(20L, List.of(entry));

        input.setCount(1);
        ItemStack leaked = state.entries().getFirst().ownedStack();
        leaked.setCount(2);

        assertEquals(8, state.entries().getFirst().ownedStack().getCount());
        assertNotSame(leaked, state.entries().getFirst().ownedStack());
    }

    @Test
    void drainUsesWriteAheadReservationAndWriteBehindAppliedAck() {
        UUID claim = UUID.randomUUID();
        BufferEscrowState initial = new BufferEscrowState(20L, List.of(
                BufferEscrowEntry.alreadyEscrowed(claim, 0, new ItemStack(Items.STONE, 10))));

        BufferEscrowState reserved = initial.reserveDrainBatch(UUID.randomUUID(), 1);
        assertEquals(BufferEscrowPhase.DRAIN_RESERVED, reserved.entries().getFirst().phase());
        assertEquals(10, reserved.entries().getFirst().reservedCount());

        BufferEscrowState applied = reserved.drainApplied(claim, new ItemStack(Items.STONE, 3));
        assertEquals(BufferEscrowPhase.DRAIN_APPLIED, applied.entries().getFirst().phase());
        assertEquals(3, applied.entries().getFirst().ownedStack().getCount());

        BufferEscrowState confirmed = applied.confirmAppliedAfterAck();
        assertEquals(BufferEscrowPhase.ESCROWED, confirmed.entries().getFirst().phase());
        assertEquals(3, confirmed.bufferedItems());
    }

    @Test
    void fullyAppliedClaimDisappearsOnlyAfterAppliedRevisionAck() {
        UUID claim = UUID.randomUUID();
        BufferEscrowState reserved = new BufferEscrowState(20L, List.of(
                BufferEscrowEntry.alreadyEscrowed(claim, 0, new ItemStack(Items.STONE, 10))))
                .reserveDrainBatch(UUID.randomUUID(), 1);

        BufferEscrowState applied = reserved.drainApplied(claim, ItemStack.EMPTY);
        assertEquals(1, applied.entries().size());
        assertTrue(applied.confirmAppliedAfterAck().isEmpty());
    }

    @Test
    void durableReservationFromCrashedProcessIsNeverBlindlyRetried() {
        BufferEscrowState reserved = new BufferEscrowState(20L, List.of(
                BufferEscrowEntry.alreadyEscrowed(
                        UUID.randomUUID(), 0, new ItemStack(Items.NETHER_STAR, 1))))
                .reserveDrainBatch(UUID.randomUUID(), 1);

        BufferEscrowState recovered = reserved.recoverLoadedSnapshot();

        assertEquals(BufferEscrowPhase.RECOVERY_REQUIRED, recovered.entries().getFirst().phase());
        assertEquals(BufferRecoveryCode.DRAIN_OUTCOME_UNKNOWN,
                recovered.entries().getFirst().recoveryCode());
        assertTrue(recovered.requiresRecovery());
    }

    @Test
    void sourceClaimRequiresExactIdentityBeforeEscrowOwnsAnything() {
        UUID source = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        BufferEscrowState prepared = new BufferEscrowState(20L, List.of(
                BufferEscrowEntry.prepared(
                        claim, 0, source, new ItemStack(Items.DIAMOND_BLOCK, 2))));

        assertEquals(BufferEscrowPhase.SOURCE_PREPARED, prepared.entries().getFirst().phase());
        assertEquals(BufferEscrowPhase.ESCROWED,
                prepared.sourceClaimed(claim).entries().getFirst().phase());
    }

    @Test
    void rejectsRemainderWithDifferentComponentsOrTooManyItems() {
        UUID claim = UUID.randomUUID();
        BufferEscrowState reserved = new BufferEscrowState(20L, List.of(
                BufferEscrowEntry.alreadyEscrowed(claim, 0, new ItemStack(Items.STONE, 2))))
                .reserveDrainBatch(UUID.randomUUID(), 1);

        assertThrows(IllegalArgumentException.class,
                () -> reserved.drainApplied(claim, new ItemStack(Items.DIRT, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> reserved.drainApplied(claim, new ItemStack(Items.STONE, 3)));
    }
}
