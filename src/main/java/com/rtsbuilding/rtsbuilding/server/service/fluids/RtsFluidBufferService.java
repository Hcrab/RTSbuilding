package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.Optional;

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
    public static long internalFluidCapacityMb(ServerPlayer player) {
        if (player == null) {
            return Config.internalFluidCapacityMb();
        }
        return Math.max(0L, (long) RtsProgressionManager.getFluidCapacityBuckets(player) * FluidType.BUCKET_VOLUME);
    }

    /**
     * 统计会话内部缓冲区中存储的特定流体总量。
     */
    public static long countInBuffer(RtsStorageSession session, Fluid fluid) {
        if (session == null || fluid == null) {
            return 0L;
        }
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            return 0L;
        }
        return Math.max(0L, session.sessionFlags.internalFluidMb.getOrDefault(id.toString(), 0L));
    }

    /**
     * 将流体插入会话的内部缓冲区。返回实际存储的量（以 mb 为单位），
     * 可能少于请求的量，如果缓冲区接近容量上限。
     */
    public static int insertIntoBuffer(RtsStorageSession session, ServerPlayer player, FluidStack fluidStack, boolean execute) {
        if (session == null || player == null || fluidStack == null || fluidStack.isEmpty()) {
            return 0;
        }
        Identifier id = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
        if (id == null) {
            return 0;
        }
        String fluidId = id.toString();
        long stored = session.sessionFlags.internalFluidMb.getOrDefault(fluidId, 0L);
        long space = Math.max(0L, internalFluidCapacityMb(player) - stored);
        int toInternal = (int) Math.min((long) fluidStack.getAmount(), space);
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
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            return 0;
        }
        String fluidId = id.toString();
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
        ItemStack single = container.copyWithCount(1);
        Optional<IFluidHandlerItem> optHandler = FluidUtil.getFluidHandler(single);
        if (optHandler.isEmpty()) {
            return DrainOutcome.EMPTY;
        }

        IFluidHandlerItem handler = optHandler.get();
        FluidStack simulated = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) {
            return DrainOutcome.EMPTY;
        }
        if (!execute) {
            return new DrainOutcome(simulated.copy(), handler.getContainer().copy());
        }

        FluidStack drained = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return DrainOutcome.EMPTY;
        }
        return new DrainOutcome(drained.copy(), handler.getContainer().copy());
    }

    /**
     * 排空流体容器物品的结果。
     */
    public record DrainOutcome(FluidStack fluid, ItemStack remainder) {
        public static final DrainOutcome EMPTY = new DrainOutcome(FluidStack.EMPTY, ItemStack.EMPTY);

        public boolean isEmpty() {
            return this.fluid.isEmpty();
        }
    }
}
