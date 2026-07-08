package com.rtsbuilding.rtsbuilding.server.plugin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Real inventory item used to install one RTS plugin.
 *
 * <p>The item only adapts right-click use into the server service. It does not
 * decide install legality or mutate persistent state directly.
 */
public class RtsPluginItem extends Item {
    public RtsPluginItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (RtsPluginService.installHeldPlugin(serverPlayer, usedHand)) {
                return InteractionResultHolder.sidedSuccess(serverPlayer.getItemInHand(usedHand), false);
            }
        }
        return InteractionResultHolder.pass(stack);
    }
}
