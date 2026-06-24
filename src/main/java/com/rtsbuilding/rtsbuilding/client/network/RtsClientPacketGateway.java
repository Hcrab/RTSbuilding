package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRecipePayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsOpenCraftTerminalPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsRequestCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.pathfinding.C2SRtsPathfindingPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsRequestPluginsPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsBeginHomeSelectionPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsRequestProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetHomePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetSurvivalProgressionPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsCloseRemoteMenuPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetAutoStorePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetBdNetworkPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 网络包网关——向服务端发送数据包。
 *
 * <p>与旧版 {@code com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway}
 * 功能完全一致，所有 Feature Module 通过此类发包。</p>
 */
public final class RtsClientPacketGateway {

    private RtsClientPacketGateway() {}

    public static void sendSetMode(BuilderMode mode) {
        PacketDistributor.sendToServer(new C2SRtsSetModePayload((byte) mode.ordinal()));
    }

    public static void sendToggleCamera(boolean startAtPlayerHead) {
        PacketDistributor.sendToServer(new C2SRtsToggleCameraPayload(startAtPlayerHead));
    }

    public static void sendSetFunnelEnabled(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetFunnelPayload(enabled));
    }

    public static void sendSetAutoStoreMinedDrops(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetAutoStorePayload(enabled));
    }

    public static void sendSetBdNetwork(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetBdNetworkPayload(enabled));
    }

    public static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        PacketDistributor.sendToServer(new C2SRtsLinkStoragePayload(
                pos, allowStore ? C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL : C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY));
    }

    public static void sendRequestStoragePage(int page, String search, String category,
                                               com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort sort,
                                               boolean ascending, int pageSize) {
        PacketDistributor.sendToServer(new C2SRtsRequestStoragePagePayload(
                page, search, category, (byte) sort.ordinal(), ascending, pageSize, false, List.of()));
    }

    public static void sendRequestCraftables(String search, boolean showUnavailable, int offset, int limit) {
        PacketDistributor.sendToServer(new C2SRtsRequestCraftablesPayload(
                search, showUnavailable, Math.max(0, offset), Math.max(1, limit), false, List.of()));
    }

    public static void sendCraftRecipe(String recipeId, int count) {
        PacketDistributor.sendToServer(new C2SRtsCraftRecipePayload(recipeId, Math.max(1, count)));
    }

    public static void sendOpenCraftTerminal() {
        PacketDistributor.sendToServer(new C2SRtsOpenCraftTerminalPayload());
    }

    public static void sendCloseRemoteMenu() {
        PacketDistributor.sendToServer(new C2SRtsCloseRemoteMenuPayload());
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied,
                                  String itemId, ItemStack itemPrototype, int rotateSteps,
                                  Vec3 rayOrigin, Vec3 rayDir) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype, rotateSteps, rayOrigin, rayDir, false);
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied,
                                  String itemId, ItemStack itemPrototype, int rotateSteps,
                                  Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild) {
        PacketDistributor.sendToServer(new C2SRtsPlacePayload(
                hit.getBlockPos(), (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                (byte) rotateSteps, forcePlace, skipIfOccupied,
                itemId == null ? "" : itemId,
                itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copyWithCount(1),
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayDir.x, rayDir.y, rayDir.z, quickBuild, false));
    }

    public static void sendPlaceFluid(BlockHitResult hit, boolean forcePlace, String fluidId,
                                       Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsPlaceFluidPayload(
                hit.getBlockPos(), (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                forcePlace, fluidId,
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayDir.x, rayDir.y, rayDir.z));
    }

    public static void sendMineStart(BlockPos pos, int face, int toolSlot,
                                      String toolItemId, ItemStack toolPrototype,
                                      boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        PacketDistributor.sendToServer(new C2SRtsMinePayload(
                pos, (byte) face, true, (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copyWithCount(1),
                allowPlacedBlockRecovery, toolProtectionEnabled));
    }

    public static void sendMineAbort(BlockPos pos, int face, int toolSlot) {
        PacketDistributor.sendToServer(new C2SRtsMinePayload(
                pos, (byte) face, false, (byte) Mth.clamp(toolSlot, 0, 8),
                "", ItemStack.EMPTY, false, false));
    }

    public static void sendUltimineStart(BlockPos pos, int face, int toolSlot, int limit, byte mode,
                                          String toolItemId, ItemStack toolPrototype,
                                          boolean toolProtectionEnabled) {
        PacketDistributor.sendToServer(new C2SRtsUltiminePayload(
                pos, (byte) face, (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copyWithCount(1),
                (short) Mth.clamp(limit, 1, 256), mode, toolProtectionEnabled));
    }

    public static void sendRotateBlock(BlockPos pos) {
        PacketDistributor.sendToServer(new C2SRtsRotateBlockPayload(pos));
    }

    public static void sendRequestPlugins() {
        PacketDistributor.sendToServer(new C2SRtsRequestPluginsPayload());
    }

    public static void sendRequestProgressionState() {
        PacketDistributor.sendToServer(new C2SRtsRequestProgressionStatePayload());
    }

    public static void sendSetSurvivalProgression(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetSurvivalProgressionPayload(enabled));
    }

    public static void sendSetHome(BlockPos pos) {
        PacketDistributor.sendToServer(new C2SRtsSetHomePayload(pos));
    }

    public static void sendBeginHomeSelection() {
        PacketDistributor.sendToServer(new C2SRtsBeginHomeSelectionPayload());
    }

    public static void sendPathfindingGoTo(BlockPos target) {
        PacketDistributor.sendToServer(new C2SRtsPathfindingPayload(target));
    }

    public static void sendUndo() {
        PacketDistributor.sendToServer(new C2SRtsUndoPayload());
    }

    public static void sendBreakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        PacketDistributor.sendToServer(new C2SRtsBreakPayload(
                pos, (byte) face.get3DDataValue(), allowAdjacentFallback));
    }

    public static void sendAreaMine(int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                     int toolSlot, String toolItemId, ItemStack toolPrototype,
                                     byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        PacketDistributor.sendToServer(new C2SRtsAreaMinePayload(
                minX, maxX, minY, maxY, minZ, maxZ,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copyWithCount(1),
                shapeType, fillType, toolProtectionEnabled));
    }

    public static void sendAreaDestroy(List<BlockPos> positions, int toolSlot,
                                        String toolItemId, ItemStack toolPrototype,
                                        boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) return;
        PacketDistributor.sendToServer(new C2SRtsAreaDestroyPayload(
                positions, (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copyWithCount(1),
                toolProtectionEnabled));
    }
}
