package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsBlueprintAPI;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.service.impl.RtsBlueprintServiceImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Implementation of {@link RtsBlueprintAPI} — delegates to the blueprint service layer.
 */
@ApiStatus.Internal
public final class RtsBlueprintAPIImpl implements RtsBlueprintAPI {

    private static final RtsServer REGISTRY = RtsServer.get();
    private static final RtsBlueprintServiceImpl BLUEPRINT = Objects.requireNonNull(
            REGISTRY.blueprint(), "RtsBlueprintServiceImpl not initialized");

    @Override
    public long countMaterial(ServerPlayer player, Item item) {
        return BLUEPRINT.countMaterial(player, item);
    }

    @Override
    public ItemStack extractMaterial(ServerPlayer player, Item item, int count) {
        return BLUEPRINT.extractMaterial(player, item, count);
    }

    @Override
    public long countFluidMb(ServerPlayer player, Fluid fluid) {
        return BLUEPRINT.countFluidMb(player, fluid);
    }

    @Override
    public boolean extractFluid(ServerPlayer player, Fluid fluid, int amountMb) {
        return BLUEPRINT.extractFluid(player, fluid, amountMb);
    }

    @Override
    public void refundMaterial(ServerPlayer player, ItemStack stack) {
        BLUEPRINT.refundMaterial(player, stack);
    }

    @Override
    public void noteBlockPlaced(ServerPlayer player, Object pos, String itemId) {
        if (pos instanceof BlockPos bp) {
            BLUEPRINT.noteBlockPlaced(player, bp, itemId);
        }
    }

    @Override
    public void refreshPage(ServerPlayer player) {
        BLUEPRINT.refreshPage(player);
    }
}
