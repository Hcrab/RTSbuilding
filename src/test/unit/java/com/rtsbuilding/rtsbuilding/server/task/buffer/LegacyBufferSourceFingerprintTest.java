package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LegacyBufferSourceFingerprintTest {
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
    void sameOrderedItemStacksProduceStableFingerprint() {
        List<ItemStack> first = List.of(
                new ItemStack(Items.STONE, 7), new ItemStack(Items.DIAMOND, 2));
        List<ItemStack> second = first.stream().map(ItemStack::copy).toList();

        assertEquals(
                LegacyBufferSourceFingerprint.freeze(REGISTRIES, first),
                LegacyBufferSourceFingerprint.freeze(REGISTRIES, second));
    }

    @Test
    void itemCountItemTypeAndStackOrderParticipateInFingerprint() {
        var baseline = LegacyBufferSourceFingerprint.freeze(REGISTRIES,
                List.of(new ItemStack(Items.STONE, 7), new ItemStack(Items.DIAMOND, 2)));

        assertNotEquals(baseline, LegacyBufferSourceFingerprint.freeze(REGISTRIES,
                List.of(new ItemStack(Items.STONE, 8), new ItemStack(Items.DIAMOND, 2))));
        assertNotEquals(baseline, LegacyBufferSourceFingerprint.freeze(REGISTRIES,
                List.of(new ItemStack(Items.DIRT, 7), new ItemStack(Items.DIAMOND, 2))));
        assertNotEquals(baseline, LegacyBufferSourceFingerprint.freeze(REGISTRIES,
                List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.STONE, 7))));
    }

    @Test
    void canonicalNbtIgnoresCompoundInsertionOrderButDetectsNestedContentChanges() {
        CompoundTag first = new CompoundTag();
        first.putString("id", "minecraft:stone");
        CompoundTag firstComponents = new CompoundTag();
        firstComponents.putInt("damage", 3);
        firstComponents.putString("owner", "alice");
        first.put("components", firstComponents);

        CompoundTag reordered = new CompoundTag();
        CompoundTag reorderedComponents = new CompoundTag();
        reorderedComponents.putString("owner", "alice");
        reorderedComponents.putInt("damage", 3);
        reordered.put("components", reorderedComponents);
        reordered.putString("id", "minecraft:stone");

        CompoundTag changed = reordered.copy();
        changed.getCompound("components").putString("owner", "bob");

        assertEquals(
                LegacyBufferSourceFingerprint.freezeSerialized(List.of(first)),
                LegacyBufferSourceFingerprint.freezeSerialized(List.of(reordered)));
        assertNotEquals(
                LegacyBufferSourceFingerprint.freezeSerialized(List.of(first)),
                LegacyBufferSourceFingerprint.freezeSerialized(List.of(changed)));
    }
}
