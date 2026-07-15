package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * 跨链接流体处理器和内部缓冲区的流体网络操作器。
 *
 * <p>提供流体网络层的三个核心操作，均按"先链接处理器，再内部缓冲区回退"的顺序执行：
 * <ul>
 *   <li>{@link #countFluidInNetwork} — 统计网络中指定流体的总量</li>
 *   <li>{@link #extractFluidFromNetwork} — 从网络提取流体（先排空链接处理器，再排空内部缓冲区）</li>
 *   <li>{@link #insertFluidIntoNetwork} — 将流体插入网络（先填充链接处理器，再溢入内部缓冲区）</li>
 * </ul>
 *
 * <p><b>职责边界：</b>
 * <ul>
 *   <li>不处理世界级流体放置（由 {@link RtsFluidWorldPlacer} 负责）</li>
 *   <li>不处理物品容器的排空/填充（由 {@link RtsFluidBufferService#drainContainer} 负责）</li>
 *   <li>不暴露在 {@code service} API 层之外——通过 {@link com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids} 桥接</li>
 * </ul>
 */
public final class RtsFluidNetworkOperator {

    private RtsFluidNetworkOperator() {
    }

    // ======================================================================
    //  Counting
    // ======================================================================

    /**
     * 统计所有链接流体处理器和内部缓冲区中特定流体的总量。
     */
    public static long countFluidInNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers, Fluid fluid) {
        if (session == null || fluidHandlers == null || fluid == null) {
            return 0L;
        }
        long total = 0L;
        for (LinkedFluidHandler linked : RtsLinkedHandlerResolutionService.orderFluidHandlersForExtract(fluidHandlers)) {
            IFluidHandler handler = linked == null ? null : linked.handler();
            if (handler == null) {
                continue;
            }
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty() && stack.getFluid() == fluid) {
                    total = RtsCountUtil.saturatedAdd(total, stack.getAmount());
                }
            }
        }
        return RtsCountUtil.saturatedAdd(total, RtsFluidBufferService.countInBuffer(session, fluid));
    }

    // ======================================================================
    //  Extraction (linked handlers first, then internal buffer)
    // ======================================================================

    /**
     * 从网络提取流体（先链接处理器，再内部缓冲区）。
     * 返回实际排出的量（以 mb 为单位）。
     */
    public static int extractFluidFromNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers,
            Fluid fluid, int amount, boolean execute) {
        if (fluid == null || amount <= 0) {
            return 0;
        }

        int remaining = amount;
        for (LinkedFluidHandler linked : RtsLinkedHandlerResolutionService.orderFluidHandlersForExtract(fluidHandlers)) {
            if (remaining <= 0) break;
            FluidStack drained = drainMatchingFluid(linked.handler(), fluid, remaining, execute);
            if (!drained.isEmpty()) {
                remaining -= drained.getAmount();
            }
            // 标准 drain 路径失败时尝试按 tank 直接提取
            if (remaining > 0) {
                int byTank = drainByTank(linked.handler(), fluid, remaining, execute);
                remaining -= byTank;
            }
        }

        if (remaining > 0) {
            int drainedFromBuffer = RtsFluidBufferService.extractFromBuffer(session, fluid, remaining, execute);
            remaining -= drainedFromBuffer;
        }

        if (execute && remaining < amount) session.transfer.pageDataVersion.incrementAndGet();
        return amount - remaining;
    }

    // ======================================================================
    //  Insertion (linked handlers first, then internal buffer)
    // ======================================================================

    /**
     * 将流体插入网络（先链接处理器，再内部缓冲区作为溢出）。
     * 返回实际存储的量（以 mb 为单位）。
     */
    public static int insertFluidIntoNetwork(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session,
            List<LinkedFluidHandler> fluidHandlers, FluidStack fluidStack, boolean execute) {
        if (fluidStack.isEmpty() || fluidStack.getAmount() <= 0) {
            return 0;
        }
        int remaining = fluidStack.getAmount();

        for (LinkedFluidHandler linked : RtsLinkedHandlerResolutionService.orderFluidHandlersForInsert(fluidHandlers)) {
            if (remaining <= 0) {
                break;
            }
            FluidStack candidate = fluidStack.copy();
            candidate.setAmount(remaining);
            int filled = linked.handler().fill(candidate,
                    execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                remaining -= filled;
            }
        }

        if (remaining <= 0) {
            if (execute) session.transfer.pageDataVersion.incrementAndGet();
            return fluidStack.getAmount();
        }

        int intoBuffer = RtsFluidBufferService.insertIntoBuffer(session, player, fluidStack, execute);
        remaining -= intoBuffer;

        if (execute && remaining < fluidStack.getAmount()) session.transfer.pageDataVersion.incrementAndGet();
        return fluidStack.getAmount() - remaining;
    }

    // ======================================================================
    //  Internal: handler drain
    // ======================================================================

    /**
     * 从单个处理器排空特定流体。先尝试精确匹配排空，
     * 如果处理器不支持基于 FluidStack 的排空过滤，则回退到通用排空。
     */
    private static FluidStack drainMatchingFluid(IFluidHandler handler, Fluid fluid, int amount, boolean execute) {
        if (handler == null || fluid == null || amount <= 0) {
            return FluidStack.EMPTY;
        }
        IFluidHandler.FluidAction action = execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
        FluidStack request = new FluidStack(fluid, amount);
        FluidStack exact = handler.drain(request, action);
        if (!exact.isEmpty()) {
            return exact;
        }

        FluidStack genericPreview = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (genericPreview.isEmpty() || !genericPreview.getFluid().isSame(fluid)) {
            return FluidStack.EMPTY;
        }
        if (!execute) {
            return genericPreview;
        }
        FluidStack generic = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return !generic.isEmpty() && generic.getFluid().isSame(fluid) ? generic : FluidStack.EMPTY;
    }

    /**
     * 按 tank 直接提取 + 简单通用 drain 回退。
     */
    private static int drainByTank(IFluidHandler handler, Fluid fluid, int amount, boolean execute) {
        if (handler == null || fluid == null || amount <= 0) return 0;
        IFluidHandler.FluidAction action = execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
        int remaining = amount;

        // 1) 按 tank 精确 drain
        for (int tank = 0; tank < handler.getTanks() && remaining > 0; tank++) {
            FluidStack inTank = handler.getFluidInTank(tank);
            if (inTank.isEmpty() || !inTank.getFluid().isSame(fluid)) continue;
            int toDrain = Math.min(remaining, inTank.getAmount());
            FluidStack drained = handler.drain(inTank.copyWithAmount(toDrain), action);
            if (!drained.isEmpty()) {
                remaining -= drained.getAmount();
            }
        }

        // 2) tank 路径也失败 → 尝试无类型通用 drain
        if (remaining == amount) {
            FluidStack generic = handler.drain(amount, action);
            if (!generic.isEmpty() && generic.getFluid().isSame(fluid)) {
                remaining -= generic.getAmount();
            }
        }

        return amount - remaining;
    }
}
