package com.rtsbuilding.rtsbuilding.platform.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * RTSBuilding 内部流体堆栈，数量统一使用 Fabric droplets。
 *
 * <p>内部保存完整 {@link FluidVariant}，因此第三方流体附带的数据不会在模拟、提取或退款时
 * 被悄悄抹掉；面向旧业务层仍提供 Fluid/amount 访问器。
 */
public final class RtsFluidStack {
    public static final RtsFluidStack EMPTY = new RtsFluidStack(FluidVariant.blank(), 0);

    private final FluidVariant variant;
    private int amount;

    public RtsFluidStack(Fluid fluid, int amount) {
        this(fluid == null || fluid == Fluids.EMPTY ? FluidVariant.blank() : FluidVariant.of(fluid), amount);
    }

    public RtsFluidStack(FluidVariant variant, long amount) {
        this.variant = variant == null ? FluidVariant.blank() : variant;
        this.amount = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
    }

    public FluidVariant variant() {
        return this.variant;
    }

    public Fluid getFluid() {
        return this.variant.isBlank() ? Fluids.EMPTY : this.variant.getFluid();
    }

    public int getAmount() {
        return this.amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    public boolean isEmpty() {
        return this.amount <= 0 || this.variant.isBlank();
    }

    public RtsFluidStack copy() {
        return isEmpty() ? EMPTY : new RtsFluidStack(this.variant, this.amount);
    }
}
