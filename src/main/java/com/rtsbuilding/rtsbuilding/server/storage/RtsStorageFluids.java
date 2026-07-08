package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owns RTS storage fluid mutation and linked-fluid behavior.
 *
 * <p>This helper is responsible for internal fluid buffer changes, linked
 * fluid handler fill/drain behavior, fluid container draining, target tank
 * filling, and world fluid placement rules. It deliberately does not own item
 * transfer, crafting, storage page dispatch, or packet registration; callers
 * extraction/refund, and keep session save/page refresh in
 */
public final class RtsStorageFluids {
    private static final int FLUID_TRANSFER_MB = FluidType.BUCKET_VOLUME;
    private static final long INTERNAL_FLUID_CAPACITY_MB = 100L * FluidType.BUCKET_VOLUME;

    private RtsStorageFluids() {
    }

    public static boolean storeFluidFromContainer(ServerPlayer player, RtsStorageSession session,
            List<IItemHandler> extractItemHandlers, List<IItemHandler> insertItemHandlers,
            List<LinkedFluidHandler> fluidHandlers, byte sourceType, byte toolSlot, String itemId) {
        List<IItemHandler> safeExtractItemHandlers = extractItemHandlers == null ? List.of() : extractItemHandlers;
        List<IItemHandler> safeInsertItemHandlers = insertItemHandlers == null ? List.of() : insertItemHandlers;
        List<LinkedFluidHandler> safeFluidHandlers = fluidHandlers == null ? List.of() : fluidHandlers;
        return switch (sourceType) {
            case C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM, C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM ->
                storeFluidFromLinkedItem(player, session, safeExtractItemHandlers, safeInsertItemHandlers,
                        safeFluidHandlers, itemId);
            case C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT ->
                storeFluidFromToolSlot(player, session, safeFluidHandlers, clampHotbarSlot(toolSlot));
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

        int filledIntoBlock = fillFluidHandlerAtTarget(level, player, clickedPos, face, transfer);
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
        BlockPos placePos = resolveFluidPlacementPos(level, player, hit, transfer);
        if (placePos == null) {
            return false;
        }
        BlockHitResult placementHit = resolveFluidPlacementHit(hit, placePos);

        if (!placeFluidBlock(level, player, placePos, transfer, placementHit)) {
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
        if (player == null) {
            return INTERNAL_FLUID_CAPACITY_MB;
        }
        return Math.max(0L, (long) RtsProgressionManager.getFluidCapacityBuckets(player) * FluidType.BUCKET_VOLUME);
    }

    private static boolean storeFluidFromLinkedItem(ServerPlayer player, RtsStorageSession session,
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
        ItemStack extracted = RtsTransferExtractor.extractOneFromNetwork(extractItemHandlers, player, item);
        if (extracted.isEmpty()) {
            return false;
        }

        ContainerDrainOutcome simulated = drainContainer(extracted, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            RtsTransferInserter.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (insertFluidIntoNetwork(player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            RtsTransferInserter.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }

        ContainerDrainOutcome executed = drainContainer(extracted, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            RtsTransferInserter.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = insertFluidIntoNetwork(player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            RtsTransferInserter.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }

        if (!executed.remainder().isEmpty()) {
            RtsTransferInserter.refundToLinked(insertItemHandlers, player, executed.remainder());
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

    private static boolean storeFluidFromToolSlot(ServerPlayer player, RtsStorageSession session,
            List<LinkedFluidHandler> fluidHandlers, int toolSlot) {
        int slot = clampHotbarSlot(toolSlot);
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty()) {
            return false;
        }

        ItemStack single = inSlot.copyWithCount(1);
        ContainerDrainOutcome simulated = drainContainer(single, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (insertFluidIntoNetwork(player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            return false;
        }

        ContainerDrainOutcome executed = drainContainer(single, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = insertFluidIntoNetwork(player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            return false;
        }

        ItemStack remainingInSlot = inSlot.copy();
        remainingInSlot.shrink(1);
        if (remainingInSlot.isEmpty()) {
            player.getInventory().setItem(slot, executed.remainder());
        } else {
            player.getInventory().setItem(slot, remainingInSlot);
            moveToPlayerInventoryOrDrop(player, executed.remainder());
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

    private static ContainerDrainOutcome drainContainer(ItemStack container, int amount, boolean execute) {
        if (container.isEmpty() || amount <= 0) {
            return ContainerDrainOutcome.EMPTY;
        }
        ItemStack single = container.copyWithCount(1);
        Optional<IFluidHandlerItem> optHandler = FluidUtil.getFluidHandler(single).resolve();
        if (optHandler.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }

        IFluidHandlerItem handler = optHandler.get();
        FluidStack simulated = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }
        if (!execute) {
            return new ContainerDrainOutcome(simulated.copy(), handler.getContainer().copy());
        }

        FluidStack drained = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }
        return new ContainerDrainOutcome(drained.copy(), handler.getContainer().copy());
    }

    private static int insertFluidIntoNetwork(ServerPlayer player, RtsStorageSession session,
            List<LinkedFluidHandler> fluidHandlers, FluidStack fluidStack, boolean execute) {
        if (fluidStack.isEmpty() || fluidStack.getAmount() <= 0) {
            return 0;
        }
        int remaining = fluidStack.getAmount();

        for (LinkedFluidHandler linked : RtsLinkedStorageResolver.orderFluidHandlersForInsert(fluidHandlers)) {
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
            // Bump page data version so the storage browser cache invalidates.
            if (execute) session.transfer.pageDataVersion.incrementAndGet();
            return fluidStack.getAmount();
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
        if (id == null) {
            if (execute && remaining < fluidStack.getAmount()) session.transfer.pageDataVersion.incrementAndGet();
            return fluidStack.getAmount() - remaining;
        }
        String fluidId = id.toString();
        long stored = session.internalFluidMb.getOrDefault(fluidId, 0L);
        long space = Math.max(0L, internalFluidCapacityMb(player) - stored);
        int toInternal = (int) Math.min((long) remaining, space);
        if (toInternal > 0) {
            if (execute) {
                session.internalFluidMb.put(fluidId, stored + toInternal);
            }
            remaining -= toInternal;
        }
        if (execute && remaining < fluidStack.getAmount()) session.transfer.pageDataVersion.incrementAndGet();
        return fluidStack.getAmount() - remaining;
    }

    public static long countFluidInNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers, Fluid fluid) {
        if (session == null || fluidHandlers == null || fluid == null) {
            return 0L;
        }
        long total = 0L;
        for (LinkedFluidHandler linked : RtsLinkedStorageResolver.orderFluidHandlersForExtract(fluidHandlers)) {
            IFluidHandler handler = linked == null ? null : linked.handler();
            if (handler == null) {
                continue;
            }
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty() && stack.getFluid() == fluid) {
                    total = saturatedAdd(total, stack.getAmount());
                }
            }
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id != null) {
            total = saturatedAdd(total, Math.max(0L, session.internalFluidMb.getOrDefault(id.toString(), 0L)));
        }
        return total;
    }

    public static int extractFluidFromNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers,
            Fluid fluid, int amount, boolean execute) {
        if (fluid == null || amount <= 0) {
            return 0;
        }

        int remaining = amount;
        for (LinkedFluidHandler linked : RtsLinkedStorageResolver.orderFluidHandlersForExtract(fluidHandlers)) {
            if (remaining <= 0) {
                break;
            }
            FluidStack drained = drainMatchingFluid(linked.handler(), fluid, remaining, execute);
            if (!drained.isEmpty()) {
                remaining -= drained.getAmount();
            }
        }

        if (remaining > 0) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null) {
                String fluidId = id.toString();
                long internal = session.internalFluidMb.getOrDefault(fluidId, 0L);
                int drainedInternal = (int) Math.min((long) remaining, Math.max(0L, internal));
                if (drainedInternal > 0) {
                    if (execute) {
                        long left = internal - drainedInternal;
                        if (left > 0L) {
                            session.internalFluidMb.put(fluidId, left);
                        } else {
                            session.internalFluidMb.remove(fluidId);
                        }
                    }
                    remaining -= drainedInternal;
                }
            }
        }

        if (execute && remaining < amount) session.transfer.pageDataVersion.incrementAndGet();
        return amount - remaining;
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

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

    private static int fillFluidHandlerAtTarget(ServerLevel level, ServerPlayer player, BlockPos clickedPos, Direction face,
            FluidStack fluidStack) {
        if (fluidStack.isEmpty() || clickedPos == null || face == null || !level.hasChunkAt(clickedPos)) {
            return 0;
        }
        if (!RtsClaimProtectionService.canInteractBlock(
                player, clickedPos, face, InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
            return 0;
        }
        List<IFluidHandler> candidates = new ArrayList<>();
        addFluidHandlerCandidate(level, clickedPos, face, candidates);
        addFluidHandlerCandidate(level, clickedPos, null, candidates);
        for (Direction direction : Direction.values()) {
            addFluidHandlerCandidate(level, clickedPos, direction, candidates);
        }

        BlockPos adjacent = clickedPos.relative(face);
        if (level.hasChunkAt(adjacent) && RtsClaimProtectionService.canInteractBlock(
                player, adjacent, face.getOpposite(), InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
            addFluidHandlerCandidate(level, adjacent, face.getOpposite(), candidates);
            addFluidHandlerCandidate(level, adjacent, null, candidates);
            for (Direction direction : Direction.values()) {
                addFluidHandlerCandidate(level, adjacent, direction, candidates);
            }
        }

        for (IFluidHandler handler : candidates) {
            FluidStack candidate = fluidStack.copy();
            int simulated = handler.fill(candidate, IFluidHandler.FluidAction.SIMULATE);
            if (simulated <= 0) {
                continue;
            }
            candidate.setAmount(simulated);
            return handler.fill(candidate, IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    private static void addFluidHandlerCandidate(ServerLevel level, BlockPos pos, Direction side, List<IFluidHandler> out) {
        IFluidHandler handler = RtsLinkedCapabilities.findFluidHandler(level, pos, side);
        if (handler != null && !out.contains(handler)) {
            out.add(handler);
        }
    }

    private static BlockPos resolveFluidPlacementPos(ServerLevel level, ServerPlayer player, BlockHitResult hit,
            FluidStack fluidStack) {
        BlockPos clicked = hit.getBlockPos();
        if (canPlaceFluidAt(level, player, clicked, fluidStack, resolveFluidPlacementHit(hit, clicked))) {
            return clicked;
        }

        BlockPos adjacent = clicked.relative(hit.getDirection());
        if (level.hasChunkAt(adjacent)
                && canPlaceFluidAt(level, player, adjacent, fluidStack, resolveFluidPlacementHit(hit, adjacent))) {
            return adjacent;
        }
        return null;
    }

    private static boolean placeFluidBlock(ServerLevel level, ServerPlayer player, BlockPos pos, FluidStack fluidStack,
            BlockHitResult placementHit) {
        if (!canPlaceFluidAt(level, player, pos, fluidStack, placementHit)) {
            return false;
        }
        if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) {
            return false;
        }

        Fluid fluid = fluidStack.getFluid();
        BlockState state = level.getBlockState(pos);
        if (fluid.getFluidType().isVaporizedOnPlacement(level, pos, fluidStack)) {
            fluid.getFluidType().onVaporize(player, level, pos, fluidStack);
            return true;
        }

        if (state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(level, pos, state, fluid)) {
            return liquidContainer.placeLiquid(level, pos, state, fluid.defaultFluidState());
        }

        BlockState placeState = fluid.getFluidType().getBlockForFluidState(
                level,
                pos,
                fluid.getFluidType().getStateForPlacement(level, pos, fluidStack));
        if (placeState.isAir()) {
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                placementHit);
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        if ((isDestNonSolid || isDestReplaceable) && !state.liquid()) {
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, placementHit.getDirection())) {
                return false;
            }
            level.destroyBlock(pos, true);
        }
        return level.setBlock(pos, placeState, 11);
    }

    private static boolean canPlaceFluidAt(ServerLevel level, ServerPlayer player, BlockPos pos, FluidStack fluidStack,
            BlockHitResult placementHit) {
        if (fluidStack.isEmpty() || !level.hasChunkAt(pos)) {
            return false;
        }
        Fluid fluid = fluidStack.getFluid();
        if (!fluid.getFluidType().canBePlacedInLevel(level, pos, fluidStack)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                placementHit == null ? new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false) : placementHit);
        boolean canContain = state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(level, pos, state, fluid);
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        return level.isEmptyBlock(pos) || isDestNonSolid || isDestReplaceable || canContain;
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

    private static void moveToPlayerInventoryOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, stack);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private record ContainerDrainOutcome(FluidStack fluid, ItemStack remainder) {
        private static final ContainerDrainOutcome EMPTY = new ContainerDrainOutcome(FluidStack.EMPTY, ItemStack.EMPTY);

        private boolean isEmpty() {
            return this.fluid.isEmpty();
        }
    }
}
