package com.rtsbuilding.rtsbuilding.server.storage.port;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Loader 无关的流体类型与数量。
 *
 * <p>目前 RTS 储存页和世界放置只按原版 {@link Fluid} 身份区分流体，
 * 因此这里不泄漏 NeoForge 的 FluidStack。若未来玩法需要流体组件，
 * 应在此领域值中显式扩展，而不是让 Loader 类型重新进入业务层。</p>
 */
public record RtsFluidVolume(Fluid fluid, int amount) {
    public static final RtsFluidVolume EMPTY = new RtsFluidVolume(Fluids.EMPTY, 0);

    public RtsFluidVolume {
        amount = Math.max(0, amount);
    }

    public boolean isEmpty() {
        return fluid == null || fluid == Fluids.EMPTY || amount <= 0;
    }

    public RtsFluidVolume withAmount(int newAmount) {
        return new RtsFluidVolume(fluid, newAmount);
    }
}
