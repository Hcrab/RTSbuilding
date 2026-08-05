package com.rtsbuilding.rtsbuilding.platform.storage;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/**
 * 1.7.10 流体容器兼容入口。
 *
 * <p>旧 Forge 同时存在“注册表式水桶/单元”和会直接修改 ItemStack NBT 的
 * {@link IFluidContainerItem}。本类始终在单件真实副本上执行，并把变异后的余物显式返回，
 * 防止 GTNH 电池/储液单元的 NBT 在强搬过程中丢失。</p>
 */
public final class FluidContainerCompat {
    private FluidContainerCompat() {
    }

    public static DrainResult drain(ItemStack container, int amount, boolean execute) {
        if (StackCompat.isEmpty(container) || amount <= 0) return DrainResult.EMPTY;
        ItemStack working = container.copy();
        working.stackSize = 1;

        if (working.getItem() instanceof IFluidContainerItem) {
            IFluidContainerItem fluidItem = (IFluidContainerItem) working.getItem();
            ItemStack probe = working.copy();
            FluidStack preview = fluidItem.drain(probe, amount, false);
            if (isEmpty(preview)) return DrainResult.EMPTY;
            if (!execute) return new DrainResult(preview.copy(), working.copy());

            FluidStack drained = fluidItem.drain(working, Math.min(amount, preview.amount), true);
            if (isEmpty(drained) || drained.getFluid() != preview.getFluid()
                    || !FluidStack.areFluidStackTagsEqual(drained, preview)) {
                return DrainResult.EMPTY;
            }
            return new DrainResult(drained.copy(), working.copy());
        }

        FluidStack registered = FluidContainerRegistry.getFluidForFilledItem(working);
        if (isEmpty(registered) || registered.amount > amount) return DrainResult.EMPTY;
        ItemStack remainder = FluidContainerRegistry.drainFluidContainer(working);
        if (remainder == null) return DrainResult.EMPTY;
        return new DrainResult(registered.copy(), remainder.copy());
    }

    public static ItemStack getFilledBucket(FluidStack fluid) {
        if (isEmpty(fluid)) return null;
        ItemStack filled = FluidContainerRegistry.fillFluidContainer(
                fluid, new ItemStack(Items.bucket));
        return filled == null ? null : filled.copy();
    }

    private static boolean isEmpty(FluidStack stack) {
        return stack == null || stack.getFluid() == null || stack.amount <= 0;
    }

    public static final class DrainResult {
        public static final DrainResult EMPTY = new DrainResult(null, null);
        private final FluidStack fluid;
        private final ItemStack remainder;

        public DrainResult(FluidStack fluid, ItemStack remainder) {
            this.fluid = fluid;
            this.remainder = remainder;
        }

        public FluidStack fluid() {
            return this.fluid;
        }

        public ItemStack remainder() {
            return this.remainder;
        }
    }
}
