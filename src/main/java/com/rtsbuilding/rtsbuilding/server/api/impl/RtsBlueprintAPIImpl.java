package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsBlueprintAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.BlueprintService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import java.util.Objects;

/**
 * {@link RtsBlueprintAPI} 的实现——委托给蓝图服务层。
 */
public final class RtsBlueprintAPIImpl implements RtsBlueprintAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();
    private static final BlueprintService BLUEPRINT = Objects.requireNonNull(
            REGISTRY.blueprint(), "BlueprintService not initialized");

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
