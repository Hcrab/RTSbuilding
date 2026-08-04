package com.rtsbuilding.rtsbuilding.common.item;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The RTS terminal item — right-clicking it toggles the RTS mode.
 * <p>
 * Uses the native Forge Energy (FE) system: the charge is persisted in a data
 * component ({@link RtsItems#TERMINAL_ENERGY}) and exposed through the standard
 * {@code Capabilities.EnergyStorage.ITEM} item capability, so any charger mod
 * can recharge it. The charge is rendered as a durability-like energy bar.
 * <p>
 * Energy values, bar rendering and the tooltip follow Mekanism's conventions
 * ({@code ItemEnergized}/{@code StorageUtils}/{@code GearConfig} energy tablet):
 * 1,000,000 FE capacity, 5,000 FE/t charge rate, a fixed bright-green bar color
 * (0x3CFE9A) and a "Energy: stored / max" tooltip line.
 */
public class RtsTerminalItem extends Item {

    /** Maximum storable energy in FE (Mekanism energy-tablet tier: 1,000,000 J) */
    public static final int MAX_ENERGY = 1_000_000;
    /** Energy consumed each time RTS mode is turned on */
    public static final int ENERGY_PER_USE = 500;
    /** Maximum receive rate in FE/t (charging; Mekanism tablet: 5,000 J/t) */
    public static final int MAX_RECEIVE = 5_000;
    /** Maximum extract rate in FE/t */
    public static final int MAX_EXTRACT = 5_000;
    /** Mekanism-style energy bar color (bright green, MekanismConfig client.energyColor default) */
    public static final int ENERGY_BAR_COLOR = 0x3CFE9A;
    /** Tooltip label color — Mekanism EnumColor.BRIGHT_GREEN */
    private static final int TOOLTIP_LABEL_COLOR = 0x55FF55;
    /** Tooltip value color — Mekanism EnumColor.GRAY */
    private static final int TOOLTIP_VALUE_COLOR = 0xAAAAAA;

    public RtsTerminalItem(Properties properties) {
        super(properties);
    }

    /**
     * Item energy capability provider — lazily initializes a fresh terminal to
     * full charge and exposes a component-backed energy storage for the stack.
     */
    public static IEnergyStorage createEnergyStorage(ItemStack stack, @Nullable Void context) {
        if (!stack.has(RtsItems.TERMINAL_ENERGY.get())) {
            stack.set(RtsItems.TERMINAL_ENERGY.get(), MAX_ENERGY);
        }
        // 每把终端栈都有一个唯一 UUID，用于 RTS 模式下锁定“开启该模式的那把终端”
        if (!stack.has(RtsItems.TERMINAL_UUID.get())) {
            stack.set(RtsItems.TERMINAL_UUID.get(), UUID.randomUUID().toString());
        }
        return new ComponentEnergyStorage(stack, RtsItems.TERMINAL_ENERGY.get(),
                MAX_ENERGY, MAX_RECEIVE, MAX_EXTRACT);
    }

    /** Current energy of the stack in FE */
    private static int getEnergy(ItemStack stack) {
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy == null ? 0 : energy.getEnergyStored();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            RtsClientKernel kernel = RtsClientKernel.get();
            if (kernel.isInitialized()) {
                CameraModule cam = kernel.module(CameraModule.class);
                boolean currentlyEnabled = cam != null && cam.getState().isEnabled();
                boolean willEnable = !currentlyEnabled;
                if (willEnable && getEnergy(stack) < ENERGY_PER_USE) {
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
        // Mekanism-style: hide the bar when stacked so it never overlaps the stack count
        return stack.getCount() == 1;
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
        return ENERGY_BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy != null) {
            // Mirrors StorageUtils.addStoredEnergy: bright-green label + gray "stored / max FE" value
            Component value = Component.literal(String.format("%,d / %,d FE",
                    energy.getEnergyStored(), energy.getMaxEnergyStored())).withColor(TOOLTIP_VALUE_COLOR);
            tooltipComponents.add(Component.translatable("tooltip.rtsbuilding.energy", value)
                    .withColor(TOOLTIP_LABEL_COLOR));
        }
    }
}
