package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedPlacementItemStatusTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void mainHandBlockFallsBackToItsLocalizedName() {
        ItemStack dirt = new ItemStack(Items.DIRT);

        ItemStack resolved = BuilderScreenPreviewQueryOwner.resolvePlacementItemPreview(
                ItemStack.EMPTY, false, false, dirt);

        assertEquals(dirt, resolved);
        assertEquals(dirt.getHoverName().getString(),
                BuilderScreenPreviewQueryOwner.formatSelectedItemStatusLabel(resolved));
    }

    @Test
    void mainHandToolKeepsCurrentDurability() {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        int damage = 17;
        pickaxe.setDamageValue(damage);

        String label = BuilderScreenPreviewQueryOwner.formatSelectedItemStatusLabel(
                BuilderScreenPreviewQueryOwner.resolvePlacementItemPreview(
                        ItemStack.EMPTY, false, false, pickaxe));

        assertEquals(pickaxe.getHoverName().getString() + " "
                        + (pickaxe.getMaxDamage() - damage) + "/" + pickaxe.getMaxDamage(),
                label);
    }

    @Test
    void explicitStorageSelectionWinsOverMainHand() {
        ItemStack storageStone = new ItemStack(Items.STONE);
        ItemStack mainHandDirt = new ItemStack(Items.DIRT);

        ItemStack resolved = BuilderScreenPreviewQueryOwner.resolvePlacementItemPreview(
                storageStone, true, false, mainHandDirt);

        assertEquals(storageStone, resolved);
        assertEquals(storageStone.getHoverName().getString(),
                BuilderScreenPreviewQueryOwner.formatSelectedItemStatusLabel(resolved));
    }

    @Test
    void explicitEmptyHandDoesNotFallBackToMainHand() {
        ItemStack resolved = BuilderScreenPreviewQueryOwner.resolvePlacementItemPreview(
                ItemStack.EMPTY, false, true, new ItemStack(Items.DIRT));

        assertTrue(resolved.isEmpty());
        assertEquals("", BuilderScreenPreviewQueryOwner.formatSelectedItemStatusLabel(resolved));
    }

    @Test
    void unavailableClientPlayerHasNoPreview() {
        ItemStack resolved = BuilderScreenPreviewQueryOwner.resolvePlacementItemPreview(
                ItemStack.EMPTY, false, false, null);

        assertTrue(resolved.isEmpty());
        assertEquals("", BuilderScreenPreviewQueryOwner.formatSelectedItemStatusLabel(resolved));
    }
}
