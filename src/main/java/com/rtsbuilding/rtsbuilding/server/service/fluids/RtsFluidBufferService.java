package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.Optional;

/**
 * Manages the internal fluid buffer within an {@link RtsStorageSession}.
 *
 * <p>The internal fluid buffer stores small amounts of fluid (up to a tech-tree
 * unlockable capacity) directly in the session object, providing a fast cache
 * for commonly used fluids without requiring a linked fluid handler access.
 *
 * <p>This service deliberately does not interact with world-level fluid
 * handlers or linked storage fluid handlers; those are managed by
 * {@link RtsFluidWorldPlacer} and the main fluid network operator.
 */
public final class RtsFluidBufferService {

    private static final long DEFAULT_INTERNAL_FLUID_CAPACITY_MB = 100L * FluidType.BUCKET_VOLUME;

    private RtsFluidBufferService() {
    }

    /**
     * Returns the maximum internal fluid buffer capacity (in mb) for the
     * given player, factoring in tech-tree upgrades.
     */
    public static long internalFluidCapacityMb(ServerPlayer player) {
        if (player == null) {
            return DEFAULT_INTERNAL_FLUID_CAPACITY_MB;
        }
        return Math.max(0L, (long) RtsProgressionManager.getFluidCapacityBuckets(player) * FluidType.BUCKET_VOLUME);
    }

    /**
     * Counts the amount of a specific fluid stored in the session's internal buffer.
     */
    public static long countInBuffer(RtsStorageSession session, Fluid fluid) {
        if (session == null || fluid == null) {
            return 0L;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            return 0L;
        }
        return Math.max(0L, session.internalFluidMb.getOrDefault(id.toString(), 0L));
    }

    /**
     * Inserts fluid into the session's internal buffer. Returns the amount
     * actually stored (in mb), which may be less than requested if the
     * buffer is near capacity.
     */
    public static int insertIntoBuffer(RtsStorageSession session, ServerPlayer player, FluidStack fluidStack, boolean execute) {
        if (session == null || player == null || fluidStack == null || fluidStack.isEmpty()) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
        if (id == null) {
            return 0;
        }
        String fluidId = id.toString();
        long stored = session.internalFluidMb.getOrDefault(fluidId, 0L);
        long space = Math.max(0L, internalFluidCapacityMb(player) - stored);
        int toInternal = (int) Math.min((long) fluidStack.getAmount(), space);
        if (toInternal > 0 && execute) {
            session.internalFluidMb.put(fluidId, stored + toInternal);
        }
        return toInternal;
    }

    /**
     * Extracts fluid from the session's internal buffer. Returns the amount
     * actually extracted (in mb).
     */
    public static int extractFromBuffer(RtsStorageSession session, Fluid fluid, int amount, boolean execute) {
        if (session == null || fluid == null || amount <= 0) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            return 0;
        }
        String fluidId = id.toString();
        long internal = session.internalFluidMb.getOrDefault(fluidId, 0L);
        int drained = (int) Math.min((long) amount, Math.max(0L, internal));
        if (drained > 0 && execute) {
            long left = internal - drained;
            if (left > 0L) {
                session.internalFluidMb.put(fluidId, left);
            } else {
                session.internalFluidMb.remove(fluidId);
            }
        }
        return drained;
    }

    /**
     * Drains a fluid container item. Returns the drain outcome containing
     * the drained fluid and the remaining container, or an empty outcome
     * if the item cannot be drained or the requested amount exceeds
     * available fluid.
     */
    public static DrainOutcome drainContainer(ItemStack container, int amount, boolean execute) {
        if (container.isEmpty() || amount <= 0) {
            return DrainOutcome.EMPTY;
        }
        ItemStack single = container.copyWithCount(1);
        Optional<IFluidHandlerItem> optHandler = FluidUtil.getFluidHandler(single).resolve();
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
     * Outcome of draining a fluid container item.
     */
    public record DrainOutcome(FluidStack fluid, ItemStack remainder) {
        public static final DrainOutcome EMPTY = new DrainOutcome(FluidStack.EMPTY, ItemStack.EMPTY);

        public boolean isEmpty() {
            return this.fluid.isEmpty();
        }
    }
}
