package com.rtsbuilding.rtsbuilding.client.controller;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.*;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsHomeScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.service.BuildPlacementService;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraft.util.ChatComponentTranslation;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import org.lwjgl.input.Keyboard;

import java.util.List;

final class ClientRtsInteractionOwner {
    private final ClientRtsController controller;

    ClientRtsInteractionOwner(ClientRtsController controller) {
        this.controller = controller;
    }

    void selectStorageEntry(int index) {
            controller.buildPlacementService.selectStorageEntry(index, controller.storageStateManager.getStorageEntries(),
                    () -> controller.setMode(BuilderMode.INTERACT));
        }

    void selectFluidEntry(int index) {
            controller.buildPlacementService.selectFluidEntry(index, controller.storageStateManager.getFluidEntries(),
                    () -> controller.setMode(BuilderMode.INTERACT));
        }

    void clearSelectedItem() {
            controller.buildPlacementService.clearSelectedItem(() -> controller.setMode(BuilderMode.INTERACT));
        }

    void clearPlacementSelectionPreserveMode() {
            controller.buildPlacementService.clearPlacementSelectionPreserveMode();
        }

    void selectEmptyHand() {
            controller.buildPlacementService.selectEmptyHand(() -> controller.setMode(BuilderMode.INTERACT));
        }

    void selectRecentEntry(int index) {
            controller.buildPlacementService.selectRecentEntry(index, controller.storageStateManager.getRecentEntries(),
                    () -> controller.setMode(BuilderMode.INTERACT));
        }

    void assignQuickSlotFromSelected(int index) {
            controller.storageStateManager.assignQuickSlotFromSelected(index,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview());
        }

    void assignQuickSlotFromToolItem(int index, ItemStack stack) {
            controller.storageStateManager.assignQuickSlotFromToolItem(index, stack);
        }

    void clearQuickSlot(int index) {
            controller.storageStateManager.clearQuickSlot(index);
        }

    void selectQuickSlot(int index) {
            if (index < 0 || index >= StorageStateManager.QUICK_SLOT_COUNT) {
                return;
            }
            controller.buildPlacementService.selectQuickSlot(index,
                    controller.storageStateManager.getQuickSlotItemId(index),
                    controller.storageStateManager.getQuickSlotPreview(index),
                    controller.storageStateManager.getQuickSlotLabel(index),
                    () -> controller.setMode(BuilderMode.INTERACT));
        }

    void selectItemForPlacement(String itemId, String label, ItemStack preview) {
            controller.buildPlacementService.selectItemForPlacement(itemId, label, preview,
                    () -> controller.setMode(BuilderMode.INTERACT));
        }

    void setGuiBinding(int index, BlockPos pos, EnumFacing face, String itemIdHint) {
            controller.storageStateManager.setGuiBinding(index, pos, face, itemIdHint);
        }

    void clearGuiBinding(int index) {
            controller.storageStateManager.clearGuiBinding(index);
        }

    void openGuiBinding(int index) {
            controller.storageStateManager.openGuiBinding(index);
        }

    void placeSelected(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir) {
            controller.placeSelected(hit, forcePlace, rayOrigin, rayDir, false, false);
        }

    void placeSelected(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied) {
            controller.placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, false);
        }

    void placeSelected(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied,
                boolean quickBuild) {
            String itemId = controller.buildPlacementService.getSelectedItemId();
            controller.buildPlacementService.placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, quickBuild,
                    controller::beginRemoteMenuOpenGrace,
                    () -> {
                        if (controller.isLocalPlayerCreative()) return false;
                        ItemStack preview = controller.buildPlacementService.getSelectedItemPreview();
                        return preview != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview)
                                && preview.getItem() instanceof ItemBlock
                                && controller.storageStateManager.hasStoragePageSnapshot()
                                && controller.storageStateManager.getStorageTotalCount(itemId) <= 0L;
                    },
                    () -> controller.requestStoragePage(controller.storageStateManager.getStoragePage()),
                    controller.isLocalPlayerCreative(),
                    controller.storageStateManager.getStorageTotalCount(itemId),
                    controller.storageStateManager.hasStoragePageSnapshot());
        }

    void placeSelectedBatch(List<RayTraceResult> hits, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir,
                boolean skipIfOccupied) {
            controller.placeSelectedBatch(hits, hits == null || hits.isEmpty() ? null : hits.get(0), forcePlace, rayOrigin, rayDir,
                    skipIfOccupied);
        }

    void placeSelectedBatch(List<RayTraceResult> hits, RayTraceResult templateHit, boolean forcePlace,
                Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied) {
            controller.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, skipIfOccupied, false);
        }

    void placeSelectedBatch(List<RayTraceResult> hits, RayTraceResult templateHit, boolean forcePlace,
                Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied, boolean overwriteExisting) {
            String itemId = controller.buildPlacementService.getSelectedItemId();
            controller.buildPlacementService.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, skipIfOccupied,
                    overwriteExisting,
                    controller::beginRemoteMenuOpenGrace,
                    () -> {
                        if (controller.isLocalPlayerCreative()) return false;
                        ItemStack preview = controller.buildPlacementService.getSelectedItemPreview();
                        return preview != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview)
                                && preview.getItem() instanceof ItemBlock
                                && controller.storageStateManager.hasStoragePageSnapshot()
                                && controller.storageStateManager.getStorageTotalCount(itemId) <= 0L;
                    },
                    () -> controller.requestStoragePage(controller.storageStateManager.getStoragePage()),
                    controller.isLocalPlayerCreative(),
                    controller.storageStateManager.getStorageTotalCount(itemId),
                    controller.storageStateManager.hasStoragePageSnapshot());
        }

    void placeSelectedFluid(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.placeSelectedFluid(hit, forcePlace, rayOrigin, rayDir);
        }

    void storeFluidFromStorageItem(String itemId) {
            controller.buildPlacementService.storeFluidFromStorageItem(itemId);
        }

    void storeFluidFromPinnedItem(String itemId) {
            controller.buildPlacementService.storeFluidFromPinnedItem(itemId);
        }

    void storeFluidFromToolSlot(int toolSlot) {
            controller.buildPlacementService.storeFluidFromToolSlot(toolSlot);
        }

    void interactEmpty(RayTraceResult hit, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.interactEmpty(hit, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactEntityEmpty(int entityId, Vec3d hitLocation, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.interactEntityEmpty(entityId, hitLocation, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactBlockWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.interactBlockWithToolSlot(hit, toolSlot, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void useItemInAirWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.useItemInAirWithToolSlot(hit, toolSlot, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactBlockWithPinnedItem(RayTraceResult hit, String itemId, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.interactBlockWithPinnedItem(hit, itemId, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactEntityWithToolSlot(int entityId, Vec3d hitLocation, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.interactEntityWithToolSlot(entityId, hitLocation, toolSlot, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactEntityWithPinnedItem(int entityId, Vec3d hitLocation, String itemId, Vec3d rayOrigin, Vec3d rayDir) {
            controller.buildPlacementService.interactEntityWithPinnedItem(entityId, hitLocation, itemId, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void breakPlaced(BlockPos pos) {
            controller.buildPlacementService.breakPlaced(pos, EnumFacing.UP, false);
        }

    void breakPlaced(BlockPos pos, EnumFacing face, boolean allowAdjacentFallback) {
            controller.buildPlacementService.breakPlaced(pos, face, allowAdjacentFallback);
        }

    void startMining(BlockPos pos, int face, int toolSlot) {
            controller.miningOperationService.startMining(pos, face, toolSlot,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview(),
                    controller.isAllowPlacedBlockRecovery(), controller.isToolProtectionEnabled());
        }

    void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode) {
            controller.miningOperationService.startUltimine(pos, face, toolSlot, limit, mode,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview(),
                    controller.isToolProtectionEnabled());
        }

    void continueMining(int toolSlot) {
            controller.miningOperationService.continueMining(toolSlot);
        }

    int getAreaMinePhase() {
            return controller.miningOperationService.getAreaMinePhase();
        }

    BlockPos getAreaMinePointA() {
            return controller.miningOperationService.getAreaMinePointA();
        }

    BlockPos getAreaMinePointB() {
            return controller.miningOperationService.getAreaMinePointB();
        }

    int getAreaMineHeightOffset() {
            return controller.miningOperationService.getAreaMineHeightOffset();
        }

    void setAreaMineHeightOffset(int offset) {
            controller.miningOperationService.setAreaMineHeightOffset(offset);
        }

    void adjustAreaMineHeightOffset(int delta) {
            controller.miningOperationService.adjustAreaMineHeightOffset(delta);
        }

    void setAreaMinePointA(BlockPos pos) {
            controller.miningOperationService.setAreaMinePointA(pos, controller.anchorX, controller.anchorZ, controller.maxRadius, controller.hasBounds());
        }

    void setAreaMinePointB(BlockPos pos) {
            controller.miningOperationService.setAreaMinePointB(pos, controller.anchorX, controller.anchorZ, controller.maxRadius, controller.hasBounds());
        }

    void clearAreaMineSession() {
            controller.miningOperationService.clearAreaMineSession();
        }

    void confirmAreaMine(int toolSlot, ShapeFillMode fillMode) {
            controller.miningOperationService.confirmAreaMine(toolSlot, fillMode,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview(),
                    controller.isToolProtectionEnabled());
        }

    void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot) {
            controller.miningOperationService.confirmShapeAreaDestroy(targets, toolSlot,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview(),
                    controller.isToolProtectionEnabled());
        }

    void abortMining(int toolSlot) {
            controller.miningOperationService.abortMining(toolSlot);
        }

    int getMineProgressStage() {
            return controller.miningOperationService.getMineProgressStage();
        }

    BlockPos getMineProgressPos() {
            return controller.miningOperationService.getMineProgressPos();
        }

    BlockPos getMineProgressCompletedPos() {
            return controller.miningOperationService.getMineProgressCompletedPos();
        }

    long getMineProgressCompletedAtMs() {
            return controller.miningOperationService.getMineProgressCompletedAtMs();
        }

    int getUltimineProgressProcessed() {
            return controller.miningOperationService.getUltimineProgressProcessed();
        }

    int getUltimineProgressTotal() {
            return controller.miningOperationService.getUltimineProgressTotal();
        }

    void applyUltimineProgress(S2CRtsUltimineProgressPayload payload) {
            controller.miningOperationService.applyUltimineProgress(payload.processed(), payload.total());
        }

    void rotatePlacementClockwise() {
            controller.buildPlacementService.rotatePlacementClockwise();
        }

    void rotatePlacementCounterClockwise() {
            controller.buildPlacementService.rotatePlacementCounterClockwise();
        }

    void setPlacementStateProperty(String propertyName, String valueName) {
            controller.buildPlacementService.setPlacementStateProperty(propertyName, valueName);
        }

    void copyPlacementState(BlockState state) {
            controller.buildPlacementService.copyPlacementState(state);
        }

    void syncVisualCameraFrame() {
            Minecraft minecraft = Minecraft.getMinecraft();
            controller.cameraOrbitService.syncVisualCameraFrame(minecraft, controller.anchorX, controller.anchorY, controller.anchorZ, controller.maxRadius, controller.enabled);
        }

}
