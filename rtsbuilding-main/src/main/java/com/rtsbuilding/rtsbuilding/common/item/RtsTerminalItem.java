package com.rtsbuilding.rtsbuilding.common.item;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.common.RtsTerminalEnergy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;
import java.util.UUID;

/**
 * The RTS terminal item — right-clicking it toggles the RTS mode.
 * <p>
 * By default the terminal is a plain, durability-free item: it can be used
 * unlimited times and shows no energy bar. When the built-in
 * {@code rtsbuilding_technologized} addon is present it installs an energy
 * provider (plus the standard {@code IEnergyStorage} item capability), turning
 * the terminal into an energy-powered tool with a Mekanism-style energy bar and
 * tooltip.
 */
public class RtsTerminalItem extends Item {

    public RtsTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        // 每把终端栈都有一个唯一 UUID，用于 RTS 模式下锁定“开启该模式的那把终端”
        if (!stack.has(RtsItems.TERMINAL_UUID.get())) {
            stack.set(RtsItems.TERMINAL_UUID.get(), UUID.randomUUID().toString());
        }
        if (level.isClientSide) {
            RtsClientKernel kernel = RtsClientKernel.get();
            if (kernel.isInitialized()) {
                CameraModule cam = kernel.module(CameraModule.class);
                boolean currentlyEnabled = cam != null && cam.getState().isEnabled();
                boolean willEnable = !currentlyEnabled;
                RtsTerminalEnergy.Provider energy = RtsTerminalEnergy.get();
                if (willEnable && energy != null && !energy.hasEnergy(stack)) {
                    player.displayClientMessage(
                            Component.translatable("message.rtsbuilding.terminal_no_energy"), true);
                    return InteractionResultHolder.fail(stack);
                }
                RtsClientPacketGateway.sendToggleCamera(willEnable);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // Only show an energy bar when the energy addon is present and the stack
        // actually carries an energy capability; also hide it when stacked.
        if (RtsTerminalEnergy.get() == null || stack.getCount() != 1) {
            return false;
        }
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy != null && energy.getMaxEnergyStored() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        // Mirrors StorageUtils.getEnergyBarWidth: round(13 * energy / capacity), clamped to 0..13
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy == null || energy.getMaxEnergyStored() <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(13,
                Math.round(13.0F * energy.getEnergyStored() / energy.getMaxEnergyStored())));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Mekanism-style: fixed energy color, independent of the charge level
        RtsTerminalEnergy.Provider energy = RtsTerminalEnergy.get();
        return energy != null ? energy.energyBarColor() : super.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        RtsTerminalEnergy.Provider energyProvider = RtsTerminalEnergy.get();
        if (energyProvider == null) {
            return;
        }
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy != null) {
            // Mirrors StorageUtils.addStoredEnergy: bright-green label + gray "stored / max FE" value
            Component value = Component.literal(String.format("%,d / %,d FE",
                    energy.getEnergyStored(), energy.getMaxEnergyStored()))
                    .withColor(energyProvider.tooltipValueColor());
            tooltipComponents.add(Component.translatable("tooltip.rtsbuilding.energy", value)
                    .withColor(energyProvider.tooltipLabelColor()));
        }
    }
}
