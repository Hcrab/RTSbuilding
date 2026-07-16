package com.rtsbuilding.rtsbuilding.server.task.buffer;

import com.rtsbuilding.rtsbuilding.server.task.BufferDrainTaskPayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferEscrowCodecTest {
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
    private static final ResourceKey<Level> DIMENSION = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("rtsbuilding", "codec_test"));

    @Test
    void roundTripsExactEscrowPhaseAndItemCount() {
        UUID owner = UUID.randomUUID();
        UUID escrow = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        BufferEscrowState state = new BufferEscrowState(123L, List.of(
                BufferEscrowEntry.prepared(
                        UUID.randomUUID(), 0, source, new ItemStack(Items.DIAMOND, 7))));
        BufferDrainTaskPayload payload = new BufferDrainTaskPayload(owner, DIMENSION, escrow, state);

        BufferDrainTaskPayload decoded = BufferEscrowCodec.decode(
                BufferEscrowCodec.encode(payload, REGISTRIES), REGISTRIES);

        assertEquals(owner, decoded.ownerId());
        assertEquals(escrow, decoded.escrowId());
        assertEquals(DIMENSION, decoded.dimension());
        assertEquals(BufferEscrowPhase.SOURCE_PREPARED, decoded.state().entries().getFirst().phase());
        assertEquals(source, decoded.state().entries().getFirst().sourceEntityId());
        assertEquals(7, decoded.state().entries().getFirst().ownedStack().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(
                state.entries().getFirst().ownedStack(),
                decoded.state().entries().getFirst().ownedStack()));
    }

    @Test
    void rejectsUnknownPhaseInsteadOfGuessing() {
        BufferDrainTaskPayload payload = new BufferDrainTaskPayload(
                UUID.randomUUID(), DIMENSION, UUID.randomUUID(),
                new BufferEscrowState(1L, List.of(BufferEscrowEntry.alreadyEscrowed(
                        UUID.randomUUID(), 0, new ItemStack(Items.STONE, 1)))));
        CompoundTag encoded = BufferEscrowCodec.encode(payload, REGISTRIES);
        encoded.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).putString("phase", "RETRY_AND_DUPLICATE");

        assertThrows(IllegalArgumentException.class,
                () -> BufferEscrowCodec.decode(encoded, REGISTRIES));
    }

    @Test
    void rejectsMoreThanBoundedStackCount() {
        BufferDrainTaskPayload payload = new BufferDrainTaskPayload(
                UUID.randomUUID(), DIMENSION, UUID.randomUUID(), BufferEscrowState.empty());
        CompoundTag encoded = BufferEscrowCodec.encode(payload, REGISTRIES);
        ListTag entries = new ListTag();
        CompoundTag prototype = new CompoundTag();
        prototype.putUUID("claim", UUID.randomUUID());
        prototype.putInt("ordinal", 0);
        prototype.put("source_stack", new ItemStack(Items.STONE).save(REGISTRIES));
        prototype.put("owned_stack", new ItemStack(Items.STONE).save(REGISTRIES));
        prototype.putString("phase", BufferEscrowPhase.ESCROWED.name());
        prototype.putInt("reserved", 0);
        prototype.putString("recovery", BufferRecoveryCode.NONE.name());
        for (int i = 0; i <= BufferEscrowState.MAX_STACKS; i++) entries.add(prototype.copy());
        encoded.put("entries", entries);
        encoded.putLong("queued_at", 1L);

        assertThrows(IllegalArgumentException.class,
                () -> BufferEscrowCodec.decode(encoded, REGISTRIES));
    }

    @Test
    void readsSchemaOneWorldClaimsButStopsUnprovenLegacyOwnership() {
        UUID source = UUID.randomUUID();
        BufferDrainTaskPayload worldPayload = new BufferDrainTaskPayload(
                UUID.randomUUID(), DIMENSION, UUID.randomUUID(),
                new BufferEscrowState(1L, List.of(BufferEscrowEntry.prepared(
                        UUID.randomUUID(), 0, source, new ItemStack(Items.DIRT, 2)))));
        CompoundTag worldEncoded = BufferEscrowCodec.encode(worldPayload, REGISTRIES);
        worldEncoded.putInt("schema", 1);

        BufferDrainTaskPayload decodedWorld = BufferEscrowCodec.decode(worldEncoded, REGISTRIES);

        assertEquals(BufferEscrowPhase.SOURCE_PREPARED,
                decodedWorld.state().entries().getFirst().phase());
        assertEquals(source, decodedWorld.state().entries().getFirst().sourceEntityId());

        BufferDrainTaskPayload legacyPayload = new BufferDrainTaskPayload(
                UUID.randomUUID(), DIMENSION, UUID.randomUUID(),
                new BufferEscrowState(1L, List.of(BufferEscrowEntry.alreadyEscrowed(
                        UUID.randomUUID(), 0, new ItemStack(Items.DIRT, 2)))));
        CompoundTag legacyEncoded = BufferEscrowCodec.encode(legacyPayload, REGISTRIES);
        legacyEncoded.putInt("schema", 1);

        BufferDrainTaskPayload decodedLegacy = BufferEscrowCodec.decode(legacyEncoded, REGISTRIES);

        assertEquals(BufferEscrowPhase.RECOVERY_REQUIRED,
                decodedLegacy.state().entries().getFirst().phase());
        assertEquals(BufferRecoveryCode.LEGACY_OWNERSHIP_UNPROVEN,
                decodedLegacy.state().entries().getFirst().recoveryCode());
    }
}
