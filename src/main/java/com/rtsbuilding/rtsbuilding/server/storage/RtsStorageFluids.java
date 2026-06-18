package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidBufferService;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidNetworkOperator;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidWorldPlacer;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Owns RTS storage fluid mutation and linked-fluid behavior.
 *
 * <p>This helper is responsible for internal fluid buffer changes, linked
 * fluid handler fill/drain behavior, fluid container draining, target tank
 * filling, and world fluid placement rules. It deliberately does not own item
 * transfer, crafting, storage page dispatch, or packet registration.
 *
 * <p>World placement logic has been extracted to {@link RtsFluidWorldPlacer},
 * internal buffer management to {@link RtsFluidBufferService}, and network
 * operations (count/extract/insert across linked handlers + buffer) to
 * {@link RtsFluidNetworkOperator}.
 */
public final class RtsStorageFluids {
    private static final int FLUID_TRANSFER_MB = FluidType.BUCKET_VOLUME;

    private RtsStorageFluids() {
    }

    // ======================================================================
    //  Public API: store / place / query
    // ======================================================================

    public static boolean storeFluidFromContainer(FluidTransferGate gate, ServerPlayer player, RtsStorageSession session,
            List<IItemHandler> extractItemHandlers, List<IItemHandler> insertItemHandlers,
            List<LinkedFluidHandler> fluidHandlers, byte sourceType, byte toolSlot, String itemId) {
        List<IItemHandler> safeExtractItemHandlers = extractItemHandlers == null ? List.of() : extractItemHandlers;
        List<IItemHandler> safeInsertItemHandlers = insertItemHandlers == null ? List.of() : insertItemHandlers;
        List<LinkedFluidHandler> safeFluidHandlers = fluidHandlers == null ? List.of() : fluidHandlers;
        return switch (sourceType) {
            case C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM, C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM ->
                storeFluidFromLinkedItem(gate, player, session, safeExtractItemHandlers, safeInsertItemHandlers,
                        safeFluidHandlers, itemId);
            case C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT ->
                storeFluidFromToolSlot(gate, player, session, safeFluidHandlers, clampHotbarSlot(toolSlot));
            default -> false;
        };
    }

    public static boolean placeFluid(ServerPlayer player, RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers,
            BlockPos clickedPos, Direction face, double hitX, double hitY, double hitZ, String fluidId) {
        if (session == null || fluidId == null || fluidId.isBlank()) {
            return false;
        }
        ResourceLocation fluidKey = ResourceLocation.tryParse(fluidId);
        if (fluidKey == null || !BuiltInRegistries.FLUID.containsKey(fluidKey)) {
            return false;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidKey);
        if (fluid == null) {
            return false;
        }

        List<LinkedFluidHandler> safeFluidHandlers = fluidHandlers == null ? List.of() : fluidHandlers;
        if (extractFluidFromNetwork(session, safeFluidHandlers, fluid, FLUID_TRANSFER_MB, false) < FLUID_TRANSFER_MB) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        FluidStack transfer = new FluidStack(fluid, FLUID_TRANSFER_MB);
        int filledIntoBlock = RtsFluidWorldPlacer.fillFluidHandlerAtTarget(level, clickedPos, face, transfer);
        if (filledIntoBlock > 0) {
            int consumed = extractFluidFromNetwork(session, safeFluidHandlers, fluid, filledIntoBlock, true);
            if (consumed > 0) {
                RtsStorageRecentEntries.recordRecentFluid(
                        session,
                        fluidId,
                        S2CRtsStoragePagePayload.RECENT_FLUID_PLACED,
                        consumed,
                        FLUID_TRANSFER_MB);
                return true;
            }
            return false;
        }

        BlockHitResult hit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), face, clickedPos, false);
        BlockPos placePos = RtsFluidWorldPlacer.resolveFluidPlacementPos(level, player, hit, transfer);
        if (placePos == null) {
            return false;
        }
        BlockHitResult placementHit = resolveFluidPlacementHit(hit, placePos);

        if (!RtsFluidWorldPlacer.placeFluidBlock(level, player, placePos, transfer, placementHit)) {
            return false;
        }

        int extracted = extractFluidFromNetwork(session, safeFluidHandlers, fluid, FLUID_TRANSFER_MB, true);
        if (extracted > 0) {
            RtsStorageRecentEntries.recordRecentFluid(
                    session,
                    fluidId,
                    S2CRtsStoragePagePayload.RECENT_FLUID_PLACED,
                    extracted,
                    FLUID_TRANSFER_MB);
            return true;
        }
        return false;
    }

    public static long internalFluidCapacityMb(ServerPlayer player) {
        return RtsFluidBufferService.internalFluidCapacityMb(player);
    }

    // ======================================================================
    //  Network operations (delegated to RtsFluidNetworkOperator)
    // ======================================================================

    public static long countFluidInNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers, Fluid fluid) {
        return RtsFluidNetworkOperator.countFluidInNetwork(session, fluidHandlers, fluid);
    }

    public static int extractFluidFromNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers,
            Fluid fluid, int amount, boolean execute) {
        return RtsFluidNetworkOperator.extractFluidFromNetwork(session, fluidHandlers, fluid, amount, execute);
    }

    // ======================================================================
    //  Internal: store fluid from item handlers
    // ======================================================================

    private static boolean storeFluidFromLinkedItem(FluidTransferGate gate, ServerPlayer player, RtsStorageSession session,
            List<IItemHandler> extractItemHandlers, List<IItemHandler> insertItemHandlers,
            List<LinkedFluidHandler> fluidHandlers, String itemId) {
        if (itemId == null || itemId.isBlank() || extractItemHandlers.isEmpty()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack extracted = gate.extractOneFromNetwork(extractItemHandlers, player, item);
        if (extracted.isEmpty()) {
            return false;
        }

        RtsFluidBufferService.DrainOutcome simulated = RtsFluidBufferService.drainContainer(extracted, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (RtsFluidNetworkOperator.insertFluidIntoNetwork(player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }

        RtsFluidBufferService.DrainOutcome executed = RtsFluidBufferService.drainContainer(extracted, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = RtsFluidNetworkOperator.insertFluidIntoNetwork(player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }

        if (!executed.remainder().isEmpty()) {
            gate.refundToLinked(insertItemHandlers, player, executed.remainder());
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(insertFluid.getFluid());
        if (fluidId != null) {
            RtsStorageRecentEntries.recordRecentFluid(
                    session,
                    fluidId.toString(),
                    S2CRtsStoragePagePayload.RECENT_FLUID_USED,
                    inserted,
                    FLUID_TRANSFER_MB);
        }
        return true;
    }

    private static boolean storeFluidFromToolSlot(FluidTransferGate gate, ServerPlayer player, RtsStorageSession session,
            List<LinkedFluidHandler> fluidHandlers, int toolSlot) {
        int slot = clampHotbarSlot(toolSlot);
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty()) {
            return false;
        }

        ItemStack single = inSlot.copyWithCount(1);
        RtsFluidBufferService.DrainOutcome simulated = RtsFluidBufferService.drainContainer(single, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (RtsFluidNetworkOperator.insertFluidIntoNetwork(player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            return false;
        }

        RtsFluidBufferService.DrainOutcome executed = RtsFluidBufferService.drainContainer(single, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = RtsFluidNetworkOperator.insertFluidIntoNetwork(player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            return false;
        }

        ItemStack remainingInSlot = inSlot.copy();
        remainingInSlot.shrink(1);
        if (remainingInSlot.isEmpty()) {
            player.getInventory().setItem(slot, executed.remainder());
        } else {
            player.getInventory().setItem(slot, remainingInSlot);
            moveToPlayerInventoryOrDrop(gate, player, executed.remainder());
        }
        player.containerMenu.broadcastChanges();
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(insertFluid.getFluid());
        if (fluidId != null) {
            RtsStorageRecentEntries.recordRecentFluid(
                    session,
                    fluidId.toString(),
                    S2CRtsStoragePagePayload.RECENT_FLUID_USED,
                    inserted,
                    FLUID_TRANSFER_MB);
        }
        return true;
    }

    // ======================================================================
    //  Utility
    // ======================================================================

    private static void moveToPlayerInventoryOrDrop(FluidTransferGate gate, ServerPlayer player, ItemStack stack) {
        ItemStack remainder = gate.moveToPlayerInventoryOnly(player, stack);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private static BlockHitResult resolveFluidPlacementHit(BlockHitResult sourceHit, BlockPos targetPos) {
        if (targetPos == null) {
            return new BlockHitResult(Vec3.atCenterOf(BlockPos.ZERO), Direction.UP, BlockPos.ZERO, false);
        }
        if (sourceHit == null) {
            return new BlockHitResult(Vec3.atCenterOf(targetPos), Direction.UP, targetPos, false);
        }

        BlockPos clicked = sourceHit.getBlockPos();
        Direction face = sourceHit.getDirection();
        if (targetPos.equals(clicked)) {
            return new BlockHitResult(sourceHit.getLocation(), face, targetPos, false);
        }

        if (targetPos.equals(clicked.relative(face))) {
            Direction targetFace = face.getOpposite();
            Vec3 targetLocation = Vec3.atCenterOf(targetPos).add(
                    targetFace.getStepX() * 0.498D,
                    targetFace.getStepY() * 0.498D,
                    targetFace.getStepZ() * 0.498D);
            return new BlockHitResult(targetLocation, targetFace, targetPos, false);
        }

        return new BlockHitResult(Vec3.atCenterOf(targetPos), face, targetPos, false);
    }
}
