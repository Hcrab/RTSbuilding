package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsBlueprintAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsBlueprintService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

public final class RtsBlueprintAPIImpl implements RtsBlueprintAPI {
    @Override
    public long countMaterial(ServerPlayer player, Item item) {
        return RtsBlueprintService.countBlueprintMaterial(player, item);
    }

    @Override
    public ItemStack extractMaterial(ServerPlayer player, Item item, int count) {
        return RtsBlueprintService.extractBlueprintMaterial(player, item, count);
    }

    @Override
    public long countFluidMb(ServerPlayer player, Fluid fluid) {
        return RtsBlueprintService.countBlueprintFluidMb(player, fluid);
    }

    @Override
    public boolean extractFluid(ServerPlayer player, Fluid fluid, int amountMb) {
        return RtsBlueprintService.extractBlueprintFluid(player, fluid, amountMb);
    }

    @Override
    public void refundMaterial(ServerPlayer player, ItemStack stack) {
        RtsBlueprintService.refundBlueprintMaterial(player, stack);
    }

    @Override
    public void noteBlockPlaced(ServerPlayer player, Object pos, String itemId) {
        if (pos instanceof BlockPos bp) {
            RtsBlueprintService.noteBlueprintBlockPlaced(player, bp, itemId);
        }
    }

    @Override
    public void refreshPage(ServerPlayer player) {
        RtsBlueprintService.refreshBlueprintStoragePage(player);
    }
}
