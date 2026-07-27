package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * 内部流体缓冲区管理器，存储少量流体至 {@link RtsStorageSession} 会话对象中。
 *
 * <p>将会话中的少量流体直接缓存在会话标识中（{@code session.sessionFlags.internalFluidMb}），
 * 提供常用流体的快速读写缓存，避免每次都要访问链接的流体处理器。
 * 缓冲区容量由科技树升级动态决定（{@link #internalFluidCapacityMb}）。
 *
 * <p><b>职责边界：</b>
 * <ul>
 *   <li>仅操作会话内部缓冲区，不触及世界或链接存储的流体处理器</li>
 *   <li>提供计数（{@link #countInBuffer}）、插入（{@link #insertIntoBuffer}）、
 *   提取（{@link #extractFromBuffer}）三个核心操作</li>
 *   <li>容器排空工具（{@link #drainContainer}）返回 {@link DrainOutcome} 记录</li>
 * </ul>
 *
 * <p>跨链接流体的网络级操作由 {@link RtsFluidNetworkOperator} 处理，
 * 世界放置由 {@link RtsFluidWorldPlacer} 处理。
 */
public final class RtsFluidBufferService {

    private RtsFluidBufferService() {
    }

    /**
     * 返回给定玩家的最大内部流体缓冲区容量（以 mb 为单位），
     * 已将科技树升级纳入考虑。
     */
    public static long internalFluidCapacityMb(EntityPlayerMP player) {
        if (player == null) {
            return Config.internalFluidCapacityMb();
        }
        return Math.max(0L, (long) RtsProgressionManager.getFluidCapacityBuckets(player) * Fluid.BUCKET_VOLUME);
    }

    /**
     * 统计会话内部缓冲区中存储的特定流体总量。
     */
    public static long countInBuffer(RtsStorageSession session, Fluid fluid) {
        if (session == null || fluid == null) {
            return 0L;
        }
        String fluidId = FluidRegistry.getFluidName(fluid);
        if (fluidId == null) {
            return 0L;
        }
        return Math.max(0L, session.sessionFlags.internalFluidMb.getOrDefault(fluidId, 0L));
    }

    /**
     * 将流体插入会话的内部缓冲区。返回实际存储的量（以 mb 为单位），
     * 可能少于请求的量，如果缓冲区接近容量上限。
     */
    public static int insertIntoBuffer(RtsStorageSession session, EntityPlayerMP player, FluidStack fluidStack, boolean execute) {
        if (session == null || player == null || fluidStack == null || fluidStack.amount <= 0) {
            return 0;
        }
        String fluidId = FluidRegistry.getFluidName(fluidStack);
        if (fluidId == null) {
            return 0;
        }
        long stored = session.sessionFlags.internalFluidMb.getOrDefault(fluidId, 0L);
        long space = Math.max(0L, internalFluidCapacityMb(player) - stored);
        int toInternal = (int) Math.min((long) fluidStack.amount, space);
        if (toInternal > 0 && execute) {
            session.sessionFlags.internalFluidMb.put(fluidId, stored + toInternal);
        }
        return toInternal;
    }

    /**
     * 从会话的内部缓冲区提取流体。返回实际提取的量（以 mb 为单位）。
     */
    public static int extractFromBuffer(RtsStorageSession session, Fluid fluid, int amount, boolean execute) {
        if (session == null || fluid == null || amount <= 0) {
            return 0;
        }
        String fluidId = FluidRegistry.getFluidName(fluid);
        if (fluidId == null) {
            return 0;
        }
        long internal = session.sessionFlags.internalFluidMb.getOrDefault(fluidId, 0L);
        int drained = (int) Math.min((long) amount, Math.max(0L, internal));
        if (drained > 0 && execute) {
            long left = internal - drained;
            if (left > 0L) {
                session.sessionFlags.internalFluidMb.put(fluidId, left);
            } else {
                session.sessionFlags.internalFluidMb.remove(fluidId);
            }
        }
        return drained;
    }

    /**
     * 排空流体容器物品。返回包含排出的流体和剩余容器的排出结果，
     * 如果物品无法排出或请求的量超过可用流体，则返回空结果。
     */
    public static DrainOutcome drainContainer(ItemStack container, int amount, boolean execute) {
        if (container.isEmpty() || amount <= 0) {
            return DrainOutcome.EMPTY;
        }
        ItemStack single = container.copy();
        single.setCount(1);
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(single);
        if (handler == null) {
            return DrainOutcome.EMPTY;
        }
        FluidStack simulated = handler.drain(amount, false);
        if (isEmpty(simulated)) {
            return DrainOutcome.EMPTY;
        }
        if (!execute) {
            return new DrainOutcome(simulated.copy(), handler.getContainer().copy());
        }

        FluidStack request = simulated.copy();
        request.amount = Math.min(amount, simulated.amount);
        FluidStack drained = handler.drain(request, true);
        if (isEmpty(drained) || drained.getFluid() != simulated.getFluid()
                || !FluidStack.areFluidStackTagsEqual(drained, simulated)) {
            return DrainOutcome.EMPTY;
        }
        return new DrainOutcome(drained.copy(), handler.getContainer().copy());
    }

    /**
     * 排空流体容器物品的结果。
     */
    public static final class DrainOutcome {
        public static final DrainOutcome EMPTY = new DrainOutcome(null, ItemStack.EMPTY);
        private final FluidStack fluid;
        private final ItemStack remainder;

        public DrainOutcome(FluidStack fluid, ItemStack remainder) {
            this.fluid = fluid;
            this.remainder = remainder == null ? ItemStack.EMPTY : remainder;
        }

        public FluidStack fluid() { return fluid; }
        public ItemStack remainder() { return remainder; }

        public boolean isEmpty() {
            return RtsFluidBufferService.isEmpty(this.fluid);
        }
    }

    static boolean isEmpty(FluidStack stack) {
        return stack == null || stack.getFluid() == null || stack.amount <= 0;
    }
}
