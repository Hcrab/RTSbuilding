package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidBufferService;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidNetworkOperator;
import com.rtsbuilding.rtsbuilding.server.service.fluids.RtsFluidWorldPlacer;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;

import java.util.Collections;
import java.util.List;

/**
 * 拥有 RTS 存储流体变更和链接流体行为。
 *
 * <p>容器排液必须先模拟，再对同一份完整物品栈执行；执行结果的流体、NBT 和容器余物都以
 * Forge 流体能力返回的真实值为准。本类不把容器降级为物品 ID，也不丢弃 metadata/NBT。
 */
public final class RtsStorageFluids {
    private static final int FLUID_TRANSFER_MB = Fluid.BUCKET_VOLUME;

    private RtsStorageFluids() {
    }

    public static boolean storeFluidFromContainer(FluidTransferGate gate, EntityPlayerMP player,
            RtsStorageSession session, List<IItemHandler> extractItemHandlers,
            List<IItemHandler> insertItemHandlers, List<LinkedFluidHandler> fluidHandlers,
            byte sourceType, byte toolSlot, String itemId) {
        if (gate == null || player == null || session == null) {
            return false;
        }
        List<IItemHandler> safeExtract = extractItemHandlers == null
                ? Collections.<IItemHandler>emptyList() : extractItemHandlers;
        List<IItemHandler> safeInsert = insertItemHandlers == null
                ? Collections.<IItemHandler>emptyList() : insertItemHandlers;
        List<LinkedFluidHandler> safeFluids = fluidHandlers == null
                ? Collections.<LinkedFluidHandler>emptyList() : fluidHandlers;

        if (sourceType == C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM
                || sourceType == C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM) {
            return storeFluidFromLinkedItem(gate, player, session, safeExtract, safeInsert, safeFluids, itemId);
        }
        if (sourceType == C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT) {
            return storeFluidFromToolSlot(gate, player, session, safeFluids, clampHotbarSlot(toolSlot));
        }
        return false;
    }

    public static boolean placeFluid(EntityPlayerMP player, RtsStorageSession session,
            List<LinkedFluidHandler> fluidHandlers, BlockPos clickedPos, EnumFacing face,
            double hitX, double hitY, double hitZ, String fluidId) {
        if (player == null || session == null || clickedPos == null || isBlank(fluidId)) {
            return false;
        }
        Fluid fluid = FluidRegistry.getFluid(fluidId);
        if (fluid == null) {
            return false;
        }

        List<LinkedFluidHandler> safeHandlers = fluidHandlers == null
                ? Collections.<LinkedFluidHandler>emptyList() : fluidHandlers;
        if (extractFluidFromNetwork(session, safeHandlers, fluid, FLUID_TRANSFER_MB, false)
                < FLUID_TRANSFER_MB) {
            return false;
        }

        WorldServer level = player.getServerWorld();
        EnumFacing safeFace = face == null ? EnumFacing.UP : face;
        FluidStack transfer = new FluidStack(fluid, FLUID_TRANSFER_MB);
        int filledIntoBlock = RtsClaimProtectionService.canInteractBlock(
                player, clickedPos, safeFace, EnumHand.MAIN_HAND, ItemStack.EMPTY)
                ? RtsFluidWorldPlacer.fillFluidHandlerAtTarget(level, clickedPos, safeFace, transfer)
                : 0;
        if (filledIntoBlock > 0) {
            int consumed = extractFluidFromNetwork(session, safeHandlers, fluid, filledIntoBlock, true);
            if (consumed <= 0) {
                return false;
            }
            recordRecentFluid(session, fluid, S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, consumed);
            return true;
        }

        RayTraceResult hit = new RayTraceResult(new Vec3d(hitX, hitY, hitZ), safeFace, clickedPos);
        BlockPos placePos = RtsFluidWorldPlacer.resolveFluidPlacementPos(level, player, hit, transfer);
        if (placePos == null || !RtsClaimProtectionService.canPlaceBlock(player, placePos)) {
            return false;
        }
        RayTraceResult placementHit = resolveFluidPlacementHit(hit, placePos);
        if (!RtsFluidWorldPlacer.placeFluidBlock(level, player, placePos, transfer, placementHit)) {
            return false;
        }

        int extracted = extractFluidFromNetwork(session, safeHandlers, fluid, FLUID_TRANSFER_MB, true);
        if (extracted <= 0) {
            return false;
        }
        recordRecentFluid(session, fluid, S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, extracted);
        return true;
    }

    public static long internalFluidCapacityMb(EntityPlayerMP player) {
        return RtsFluidBufferService.internalFluidCapacityMb(player);
    }

    public static long countFluidInNetwork(RtsStorageSession session,
            List<LinkedFluidHandler> fluidHandlers, Fluid fluid) {
        return RtsFluidNetworkOperator.countFluidInNetwork(session, fluidHandlers, fluid);
    }

    public static int extractFluidFromNetwork(RtsStorageSession session,
            List<LinkedFluidHandler> fluidHandlers, Fluid fluid, int amount, boolean execute) {
        return RtsFluidNetworkOperator.extractFluidFromNetwork(session, fluidHandlers, fluid, amount, execute);
    }

    private static boolean storeFluidFromLinkedItem(FluidTransferGate gate, EntityPlayerMP player,
            RtsStorageSession session, List<IItemHandler> extractItemHandlers,
            List<IItemHandler> insertItemHandlers, List<LinkedFluidHandler> fluidHandlers, String itemId) {
        if (isBlank(itemId) || extractItemHandlers.isEmpty()) {
            return false;
        }
        ResourceLocation id = resourceLocation(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return false;
        }

        ItemStack extracted = gate.extractOneFromNetwork(extractItemHandlers, player, item);
        if (extracted.isEmpty()) {
            return false;
        }

        RtsFluidBufferService.DrainOutcome simulated =
                RtsFluidBufferService.drainContainer(extracted, FLUID_TRANSFER_MB, false);
        if (!isFullBucket(simulated)) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        FluidStack targetFluid = copyAmount(simulated.fluid(), FLUID_TRANSFER_MB);
        if (RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }

        RtsFluidBufferService.DrainOutcome executed =
                RtsFluidBufferService.drainContainer(extracted, FLUID_TRANSFER_MB, true);
        if (!isFullBucket(executed) || !sameFluid(simulated.fluid(), executed.fluid())) {
            gate.refundToLinked(insertItemHandlers, player, extracted);
            return false;
        }
        FluidStack insertFluid = copyAmount(executed.fluid(), FLUID_TRANSFER_MB);
        int inserted = RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            // 处理器在模拟与执行间改变时，只有完整撤回已插入流体后才能退还原满容器。
            int rolledBack = inserted <= 0 ? 0 : extractFluidFromNetwork(
                    session, fluidHandlers, insertFluid.getFluid(), inserted, true);
            if (rolledBack == inserted) {
                gate.refundToLinked(insertItemHandlers, player, extracted);
            } else if (!executed.remainder().isEmpty()) {
                gate.refundToLinked(insertItemHandlers, player, executed.remainder());
            }
            return false;
        }

        if (!executed.remainder().isEmpty()) {
            gate.refundToLinked(insertItemHandlers, player, executed.remainder());
        }
        recordRecentFluid(session, insertFluid.getFluid(),
                S2CRtsStoragePagePayload.RECENT_FLUID_USED, inserted);
        return true;
    }

    private static boolean storeFluidFromToolSlot(FluidTransferGate gate, EntityPlayerMP player,
            RtsStorageSession session, List<LinkedFluidHandler> fluidHandlers, int toolSlot) {
        int slot = clampHotbarSlot(toolSlot);
        ItemStack inSlot = player.inventory.getStackInSlot(slot);
        if (inSlot.isEmpty()) {
            return false;
        }

        ItemStack single = copyAmount(inSlot, 1);
        RtsFluidBufferService.DrainOutcome simulated =
                RtsFluidBufferService.drainContainer(single, FLUID_TRANSFER_MB, false);
        if (!isFullBucket(simulated)) {
            return false;
        }
        FluidStack targetFluid = copyAmount(simulated.fluid(), FLUID_TRANSFER_MB);
        if (RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            return false;
        }

        RtsFluidBufferService.DrainOutcome executed =
                RtsFluidBufferService.drainContainer(single, FLUID_TRANSFER_MB, true);
        if (!isFullBucket(executed) || !sameFluid(simulated.fluid(), executed.fluid())) {
            return false;
        }
        FluidStack insertFluid = copyAmount(executed.fluid(), FLUID_TRANSFER_MB);
        int inserted = RtsFluidNetworkOperator.insertFluidIntoNetwork(
                player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            int rolledBack = inserted <= 0 ? 0 : extractFluidFromNetwork(
                    session, fluidHandlers, insertFluid.getFluid(), inserted, true);
            if (rolledBack != inserted) {
                consumeToolContainer(player, gate, slot, inSlot, executed.remainder());
            }
            return false;
        }

        consumeToolContainer(player, gate, slot, inSlot, executed.remainder());
        recordRecentFluid(session, insertFluid.getFluid(),
                S2CRtsStoragePagePayload.RECENT_FLUID_USED, inserted);
        return true;
    }

    private static void consumeToolContainer(EntityPlayerMP player, FluidTransferGate gate, int slot,
            ItemStack originalSlot, ItemStack containerRemainder) {
        ItemStack remainingInSlot = originalSlot.copy();
        remainingInSlot.shrink(1);
        if (remainingInSlot.isEmpty()) {
            player.inventory.setInventorySlotContents(slot, containerRemainder);
        } else {
            player.inventory.setInventorySlotContents(slot, remainingInSlot);
            moveToPlayerInventoryOrDrop(gate, player, containerRemainder);
        }
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

    private static void moveToPlayerInventoryOrDrop(FluidTransferGate gate,
            EntityPlayerMP player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack remainder = gate.moveToPlayerInventoryOnly(player, stack);
        if (!remainder.isEmpty()) {
            player.dropItem(remainder, false);
        }
    }

    private static boolean isFullBucket(RtsFluidBufferService.DrainOutcome outcome) {
        return outcome != null && !outcome.isEmpty() && outcome.fluid() != null
                && outcome.fluid().amount >= FLUID_TRANSFER_MB;
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        return first != null && second != null && first.getFluid() == second.getFluid()
                && FluidStack.areFluidStackTagsEqual(first, second);
    }

    private static FluidStack copyAmount(FluidStack source, int amount) {
        FluidStack copy = source.copy();
        copy.amount = amount;
        return copy;
    }

    private static ItemStack copyAmount(ItemStack source, int amount) {
        ItemStack copy = source.copy();
        copy.setCount(amount);
        return copy;
    }

    private static void recordRecentFluid(RtsStorageSession session, Fluid fluid, byte action, int amount) {
        String fluidId = fluid == null ? null : FluidRegistry.getFluidName(fluid);
        if (!isBlank(fluidId)) {
            RtsStorageRecentEntries.recordRecentFluid(
                    session, fluidId, action, amount, FLUID_TRANSFER_MB);
        }
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private static RayTraceResult resolveFluidPlacementHit(RayTraceResult sourceHit, BlockPos targetPos) {
        if (targetPos == null) {
            return new RayTraceResult(center(BlockPos.ORIGIN), EnumFacing.UP, BlockPos.ORIGIN);
        }
        if (sourceHit == null) {
            return new RayTraceResult(center(targetPos), EnumFacing.UP, targetPos);
        }

        BlockPos clicked = sourceHit.getBlockPos();
        EnumFacing face = sourceHit.sideHit == null ? EnumFacing.UP : sourceHit.sideHit;
        if (targetPos.equals(clicked)) {
            return new RayTraceResult(sourceHit.hitVec, face, targetPos);
        }
        if (targetPos.equals(clicked.offset(face))) {
            EnumFacing targetFace = face.getOpposite();
            Vec3d targetLocation = center(targetPos).add(
                    targetFace.getXOffset() * 0.498D,
                    targetFace.getYOffset() * 0.498D,
                    targetFace.getZOffset() * 0.498D);
            return new RayTraceResult(targetLocation, targetFace, targetPos);
        }
        return new RayTraceResult(center(targetPos), face, targetPos);
    }

    private static Vec3d center(BlockPos pos) {
        return new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
    }

    private static ResourceLocation resourceLocation(String value) {
        try {
            return isBlank(value) ? null : new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
