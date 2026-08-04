package com.rtsbuilding.rtsbuilding.common.terminal;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The RTS terminal item — right-clicking it toggles the RTS mode.
 * <p>
 * The client side sends a camera toggle request through
 * {@link RtsClientPacketGateway#sendToggleCamera(boolean)}; the server handles
 * the actual mode switch via the {@code TOGGLE_CAMERA} action, so the server-side
 * {@link #use} call performs no additional work.
 */
public class RtsTerminalItem extends Item {

    public RtsTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            RtsClientKernel kernel = RtsClientKernel.get();
            if (kernel.isInitialized()) {
                CameraModule cam = kernel.module(CameraModule.class);
                boolean currentlyEnabled = cam != null && cam.getState().isEnabled();
                RtsClientPacketGateway.sendToggleCamera(!currentlyEnabled);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
        }
        return InteractionResultHolder.pass(stack);
    }
}
