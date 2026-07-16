package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BufferEscrowDurabilityGateTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }


    @Test
    void sourceEntityCannotBeClaimedBeforePreparedRevisionAck() {
        BufferEscrowState prepared = new BufferEscrowState(1L, List.of(
                BufferEscrowEntry.prepared(UUID.randomUUID(), 0,
                        UUID.randomUUID(), new ItemStack(Items.STONE, 1))));

        assertEquals(BufferEscrowDurabilityGate.Action.WAIT_DURABLE_ACK,
                BufferEscrowDurabilityGate.nextAction(prepared, false));
        assertEquals(BufferEscrowDurabilityGate.Action.CLAIM_SOURCE,
                BufferEscrowDurabilityGate.nextAction(prepared, true));
    }

    @Test
    void externalStorageCannotBeTouchedBeforeReservationRevisionAck() {
        BufferEscrowState reserved = escrowed().reserveDrainBatch(UUID.randomUUID(), 1);

        assertEquals(BufferEscrowDurabilityGate.Action.WAIT_DURABLE_ACK,
                BufferEscrowDurabilityGate.nextAction(reserved, false));
        assertEquals(BufferEscrowDurabilityGate.Action.EXECUTE_DRAIN,
                BufferEscrowDurabilityGate.nextAction(reserved, true));
    }

    @Test
    void appliedRemainderCannotBeReleasedBeforeWriteBehindRevisionAck() {
        UUID claim = UUID.randomUUID();
        BufferEscrowState applied = new BufferEscrowState(1L, List.of(
                BufferEscrowEntry.alreadyEscrowed(claim, 0, new ItemStack(Items.STONE, 2))))
                .reserveDrainBatch(UUID.randomUUID(), 1)
                .drainApplied(claim, ItemStack.EMPTY);

        assertEquals(BufferEscrowDurabilityGate.Action.WAIT_DURABLE_ACK,
                BufferEscrowDurabilityGate.nextAction(applied, false));
        assertEquals(BufferEscrowDurabilityGate.Action.CONFIRM_APPLIED,
                BufferEscrowDurabilityGate.nextAction(applied, true));
    }

    private static BufferEscrowState escrowed() {
        return new BufferEscrowState(1L, List.of(BufferEscrowEntry.alreadyEscrowed(
                UUID.randomUUID(), 0, new ItemStack(Items.STONE, 1))));
    }
}
