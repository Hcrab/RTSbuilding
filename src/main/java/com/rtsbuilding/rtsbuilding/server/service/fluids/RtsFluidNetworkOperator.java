package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidStorage;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidVolume;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.world.level.material.Fluid;

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
            RtsFluidStorage handler = linked == null ? null : linked.handler();
            if (handler == null) {
                continue;
            }
            for (int tank = 0; tank < handler.tankCount(); tank++) {
                RtsFluidVolume stack = handler.fluidInTank(tank);
                if (!stack.isEmpty() && stack.fluid() == fluid) {
                    total = RtsCountUtil.saturatedAdd(total, stack.amount());
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
            if (remaining <= 0) {
                break;
            }
            RtsFluidVolume drained = drainMatchingFluid(linked.handler(), fluid, remaining, execute);
            if (!drained.isEmpty()) {
                remaining -= drained.amount();
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
            List<LinkedFluidHandler> fluidHandlers, Fluid fluid, int amount, boolean execute) {
        if (fluid == null || amount <= 0) {
            return 0;
        }
        int remaining = amount;

        for (LinkedFluidHandler linked : RtsLinkedHandlerResolutionService.orderFluidHandlersForInsert(fluidHandlers)) {
            if (remaining <= 0) {
                break;
            }
            RtsFluidVolume candidate = new RtsFluidVolume(fluid, remaining);
            int filled = linked.handler().fill(candidate, execute);
            if (filled > 0) {
                remaining -= filled;
            }
        }

        if (remaining <= 0) {
            if (execute) session.transfer.pageDataVersion.incrementAndGet();
            return amount;
        }

        int intoBuffer = RtsFluidBufferService.insertIntoBuffer(session, player, fluid, amount, execute);
        remaining -= intoBuffer;

        if (execute && remaining < amount) session.transfer.pageDataVersion.incrementAndGet();
        return amount - remaining;
    }

    // ======================================================================
    //  Internal: handler drain
    // ======================================================================

    /**
     * 从单个处理器排空特定流体。先尝试精确匹配排空，
     * 如果处理器不支持按指定流体排空，则回退到通用排空。
     */
    private static RtsFluidVolume drainMatchingFluid(
            RtsFluidStorage handler, Fluid fluid, int amount, boolean execute) {
        if (handler == null || fluid == null || amount <= 0) {
            return RtsFluidVolume.EMPTY;
        }
        RtsFluidVolume request = new RtsFluidVolume(fluid, amount);
        RtsFluidVolume exact = handler.drain(request, execute);
        if (!exact.isEmpty()) {
            return exact;
        }

        RtsFluidVolume genericPreview = handler.drain(amount, false);
        if (genericPreview.isEmpty() || genericPreview.fluid() != fluid) {
            return RtsFluidVolume.EMPTY;
        }
        if (!execute) {
            return genericPreview;
        }
        RtsFluidVolume generic = handler.drain(amount, true);
        return !generic.isEmpty() && generic.fluid() == fluid ? generic : RtsFluidVolume.EMPTY;
    }
}
