package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidBufferService;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidNetworkOperator;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidWorldPlacer;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidVolume;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 拥有 RTS 存储流体变更和链接流体行为。
 *
 * <p>此辅助类负责内部流体缓冲变更、链接流体处理器的填充/排出行为、
 * 流体容器清空、目标容器填充和世界流体放置规则。
 * 它刻意不拥有物品传输、合成、存储页面分发或数据包注册。
 *
 * <p>世界放置逻辑已提取到 {@link RtsFluidWorldPlacer}，
 * 内部缓冲管理到 {@link RtsFluidBufferService}，
 * 网络操作（跨链接处理器和缓冲区的计数/提取/插入）到 {@link RtsFluidNetworkOperator}。
 */
public final class RtsStorageFluids {
    private static final int FLUID_TRANSFER_MB = RtsFluidVolume.BUCKET_AMOUNT;

    private RtsStorageFluids() {
    }

    // ======================================================================
    //  公开 API：存储 / 放置 / 查询
    // ======================================================================

    public static boolean storeFluidFromContainer(FluidTransferGate gate, ServerPlayer player, RtsStorageSession session,
            List<RtsItemStorage> extractItemHandlers, List<RtsItemStorage> insertItemHandlers,
            List<LinkedFluidHandler> fluidHandlers, byte sourceType, byte toolSlot, String itemId) {
        List<RtsItemStorage> safeExtractItemHandlers = extractItemHandlers == null ? List.of() : extractItemHandlers;
        List<RtsItemStorage> safeInsertItemHandlers = insertItemHandlers == null ? List.of() : insertItemHandlers;
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
        Identifier fluidKey = Identifier.tryParse(fluidId);
        if (fluidKey == null || !BuiltInRegistries.FLUID.containsKey(fluidKey)) {
            return false;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(fluidKey);
        if (fluid == null) {
            return false;
        }

        List<LinkedFluidHandler> safeFluidHandlers = fluidHandlers == null ? List.of() : fluidHandlers;
        if (extractFluidFromNetwork(session, safeFluidHandlers, fluid, FLUID_TRANSFER_MB, false) < FLUID_TRANSFER_MB) {
            return false;
        }

        ServerLevel level = player.level();
        RtsFluidVolume transfer = new RtsFluidVolume(fluid, FLUID_TRANSFER_MB);
        int filledIntoBlock = RtsClaimProtectionService.canInteractBlock(
                player, clickedPos, face, InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                ? RtsFluidWorldPlacer.fillFluidHandlerAtTarget(level, clickedPos, face, transfer)
                : 0;
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
        if (!RtsClaimProtectionService.canPlaceBlock(player, placePos)) {
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
    //  网络操作（委托给 RtsFluidNetworkOperator）
    // ======================================================================

    public static long countFluidInNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers, Fluid fluid) {
        return RtsFluidNetworkOperator.countFluidInNetwork(session, fluidHandlers, fluid);
    }

    public static int extractFluidFromNetwork(RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers,
            Fluid fluid, int amount, boolean execute) {
        return RtsFluidNetworkOperator.extractFluidFromNetwork(session, fluidHandlers, fluid, amount, execute);
    }

    // ======================================================================
    //  内部：从物品处理器存储流体
    // ======================================================================

    private static boolean storeFluidFromLinkedItem(FluidTransferGate gate, ServerPlayer player, RtsStorageSession session,
            List<RtsItemStorage> extractItemHandlers, List<RtsItemStorage> insertItemHandlers,
            List<LinkedFluidHandler> fluidHandlers, String itemId) {
        if (itemId == null || itemId.isBlank() || extractItemHandlers.isEmpty()) {
            return false;
        }
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.getValue(id);
        ItemStack extracted = gate.extractOneFromNetwork(extractItemHandlers, player, item);
        if (extracted.isEmpty()) {
            return false;
        }

        RtsFluidBufferService.DrainOutcome simulated = RtsFluidBufferService.drainContainer(extracted, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().amount() < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        RtsFluidVolume targetFluid = simulated.fluid().withAmount(FLUID_TRANSFER_MB);
        if (RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, targetFluid.fluid(), targetFluid.amount(), false)
                < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }

        RtsFluidBufferService.DrainOutcome executed = RtsFluidBufferService.drainContainer(extracted, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().amount() < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        RtsFluidVolume insertFluid = executed.fluid().withAmount(FLUID_TRANSFER_MB);
        int inserted = RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, insertFluid.fluid(), insertFluid.amount(), true);
        if (inserted < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }

        if (!executed.remainder().isEmpty()) {
            gate.refundToLinked(insertItemHandlers, player, executed.remainder());
        }
        Identifier fluidId = BuiltInRegistries.FLUID.getKey(insertFluid.fluid());
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
        if (simulated.isEmpty() || simulated.fluid().amount() < FLUID_TRANSFER_MB) {
            return false;
        }
        RtsFluidVolume targetFluid = simulated.fluid().withAmount(FLUID_TRANSFER_MB);
        if (RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, targetFluid.fluid(), targetFluid.amount(), false)
                < FLUID_TRANSFER_MB) {
            return false;
        }

        RtsFluidBufferService.DrainOutcome executed = RtsFluidBufferService.drainContainer(single, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().amount() < FLUID_TRANSFER_MB) {
            return false;
        }
        RtsFluidVolume insertFluid = executed.fluid().withAmount(FLUID_TRANSFER_MB);
        int inserted = RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, insertFluid.fluid(), insertFluid.amount(), true);
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
        Identifier fluidId = BuiltInRegistries.FLUID.getKey(insertFluid.fluid());
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
    //  实用方法
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
