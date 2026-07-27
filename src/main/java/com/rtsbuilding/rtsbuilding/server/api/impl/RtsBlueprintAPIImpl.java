package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsBlueprintAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.BlueprintService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;

import java.util.Objects;

/**
 * {@link RtsBlueprintAPI} 的实现——委托给蓝图服务层。
 */
public final class RtsBlueprintAPIImpl implements RtsBlueprintAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();
    private static final BlueprintService BLUEPRINT = Objects.requireNonNull(
            REGISTRY.blueprint(), "BlueprintService not initialized");

    @Override
    public long countMaterial(EntityPlayerMP player, Item item) {
        return BLUEPRINT.countMaterial(player, item);
    }

    @Override
    public ItemStack extractMaterial(EntityPlayerMP player, Item item, int count) {
        return BLUEPRINT.extractMaterial(player, item, count);
    }

    @Override
    public long countFluidMb(EntityPlayerMP player, Fluid fluid) {
        return BLUEPRINT.countFluidMb(player, fluid);
    }

    @Override
    public boolean extractFluid(EntityPlayerMP player, Fluid fluid, int amountMb) {
        return BLUEPRINT.extractFluid(player, fluid, amountMb);
    }

    @Override
    public void refundMaterial(EntityPlayerMP player, ItemStack stack) {
        BLUEPRINT.refundMaterial(player, stack);
    }

    @Override
    public void noteBlockPlaced(EntityPlayerMP player, BlockPos pos, String itemId) {
        BLUEPRINT.noteBlockPlaced(player, pos, itemId);
    }

    @Override
    public void refreshPage(EntityPlayerMP player) {
        BLUEPRINT.refreshPage(player);
    }
}
