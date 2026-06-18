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
 * Owns the fluid network operations across linked fluid handlers and the
 * internal session buffer.
 *
 * <p>This service handles counting, extracting from, and inserting into the
 * fluid network (linked handlers first, then internal buffer as overflow).
 * It deliberately does not handle world placement, container draining, or
 * item-level fluid transfer — those belong to {@link RtsFluidWorldPlacer}
 * and {@link RtsFluidBufferService} respectively.
 *
 * <p>Extracted from {@link com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids}
 * to isolate the network-layer fluid logic from the public-facing fluid API.
 */
public final class RtsFluidNetworkOperator {

    private RtsFluidNetworkOperator() {
    }

    // ======================================================================
    //  Counting
    // ======================================================================

    /**
     * Counts the total amount of a specific fluid across all linked fluid
     * handlers and the internal buffer.
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
     * Extracts fluid from the network (linked handlers first, then internal
     * buffer). Returns the amount (in mb) actually drained.
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
            FluidStack drained = drainMatchingFluid(linked.handler(), fluid, remaining, execute);
            if (!drained.isEmpty()) {
                remaining -= drained.getAmount();
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
     * Inserts fluid into the network (linked handlers first, then internal
     * buffer as overflow). Returns the amount (in mb) actually stored.
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
     * Drains a specific fluid from a single handler. Tries exact-match drain
     * first, then falls back to generic drain if the handler does not support
     * FluidStack-based drain filtering.
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
        if (genericPreview.isEmpty() || genericPreview.getFluid() != fluid) {
            return FluidStack.EMPTY;
        }
        if (!execute) {
            return genericPreview;
        }
        FluidStack generic = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return !generic.isEmpty() && generic.getFluid() == fluid ? generic : FluidStack.EMPTY;
    }
}
