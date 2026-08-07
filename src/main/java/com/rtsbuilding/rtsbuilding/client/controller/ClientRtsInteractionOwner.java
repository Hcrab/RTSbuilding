package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;

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
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
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
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

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

    void setGuiBinding(int index, BlockPos pos, Direction face, String itemIdHint) {
            controller.storageStateManager.setGuiBinding(index, pos, face, itemIdHint);
        }

    void clearGuiBinding(int index) {
            controller.storageStateManager.clearGuiBinding(index);
        }

    void openGuiBinding(int index) {
            controller.storageStateManager.openGuiBinding(index);
        }

    void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
            controller.placeSelected(hit, forcePlace, rayOrigin, rayDir, false, false);
        }

    void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied) {
            controller.placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, false);
        }

    void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied,
                boolean quickBuild) {
            String itemId = controller.buildPlacementService.getSelectedItemId();
            controller.buildPlacementService.placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, quickBuild,
                    controller::beginRemoteMenuOpenGrace,
                    () -> {
                        if (controller.isLocalPlayerCreative()) return false;
                        ItemStack preview = controller.buildPlacementService.getSelectedItemPreview();
                        return preview != null && !preview.isEmpty()
                                && preview.getItem() instanceof BlockItem
                                && controller.storageStateManager.hasStoragePageSnapshot()
                                && controller.storageStateManager.getStorageTotalCount(itemId) <= 0L;
                    },
                    () -> controller.requestStoragePage(controller.storageStateManager.getStoragePage()),
                    controller.isLocalPlayerCreative(),
                    controller.storageStateManager.getStorageTotalCount(itemId),
                    controller.storageStateManager.hasStoragePageSnapshot());
        }

    void placeSelectedBatch(List<BlockHitResult> hits, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir,
                boolean skipIfOccupied) {
            controller.placeSelectedBatch(hits, hits == null || hits.isEmpty() ? null : hits.get(0), forcePlace, rayOrigin, rayDir,
                    skipIfOccupied);
        }

    void placeSelectedBatch(List<BlockHitResult> hits, BlockHitResult templateHit, boolean forcePlace,
                Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied) {
            controller.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, skipIfOccupied, false);
        }

    void placeSelectedBatch(List<BlockHitResult> hits, BlockHitResult templateHit, boolean forcePlace,
                Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied, boolean overwriteExisting) {
            String itemId = controller.buildPlacementService.getSelectedItemId();
            controller.buildPlacementService.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir,
                    skipIfOccupied && !overwriteExisting,
                    controller::beginRemoteMenuOpenGrace,
                    () -> {
                        if (controller.isLocalPlayerCreative()) return false;
                        ItemStack preview = controller.buildPlacementService.getSelectedItemPreview();
                        return preview != null && !preview.isEmpty()
                                && preview.getItem() instanceof BlockItem
                                && controller.storageStateManager.hasStoragePageSnapshot()
                                && controller.storageStateManager.getStorageTotalCount(itemId) <= 0L;
                    },
                    () -> controller.requestStoragePage(controller.storageStateManager.getStoragePage()),
                    controller.isLocalPlayerCreative(),
                    controller.storageStateManager.getStorageTotalCount(itemId),
                    controller.storageStateManager.hasStoragePageSnapshot());
        }

    void placeSelectedFluid(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
            controller.buildPlacementService.placeSelectedFluid(hit, forcePlace, rayOrigin, rayDir);
        }

    void confirmSmartFill(
            BlockHitResult hit,
            int maxBlocks,
            int detectionDiameter,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        controller.buildPlacementService.confirmSmartFill(
                hit, maxBlocks, detectionDiameter, rayOrigin, rayDirection);
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

    void interactEmpty(BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
            controller.buildPlacementService.interactEmpty(hit, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactEntityEmpty(int entityId, Vec3 hitLocation, Vec3 rayOrigin, Vec3 rayDir) {
            controller.buildPlacementService.interactEntityEmpty(entityId, hitLocation, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir,
            boolean shiftDown, boolean localScreenOpened) {
            controller.buildPlacementService.interactBlockWithToolSlot(hit, toolSlot, rayOrigin, rayDir,
                    controller::beginRemoteMenuOpenGrace);
        }

    void useItemInAirWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir,
            boolean shiftDown, boolean localScreenOpened) {
            controller.buildPlacementService.useItemInAirWithToolSlot(hit, toolSlot, rayOrigin, rayDir,
                    controller::beginRemoteMenuOpenGrace);
        }

    void interactBlockWithPinnedItem(BlockHitResult hit, String itemId, Vec3 rayOrigin, Vec3 rayDir,
            boolean shiftDown) {
            controller.buildPlacementService.interactBlockWithPinnedItem(hit, itemId, rayOrigin, rayDir,
                    controller::beginRemoteMenuOpenGrace);
        }

    void interactEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
            controller.buildPlacementService.interactEntityWithToolSlot(entityId, hitLocation, toolSlot, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void interactEntityWithPinnedItem(int entityId, Vec3 hitLocation, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
            controller.buildPlacementService.interactEntityWithPinnedItem(entityId, hitLocation, itemId, rayOrigin, rayDir, controller::beginRemoteMenuOpenGrace);
        }

    void breakPlaced(BlockPos pos) {
            controller.buildPlacementService.breakPlaced(pos, Direction.UP, false);
        }

    void breakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
            controller.buildPlacementService.breakPlaced(pos, face, allowAdjacentFallback);
        }

    void startMining(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir, boolean shiftDown) {
            startMining(hit, toolSlot, rayOrigin, rayDir, shiftDown, RtsTraceInputKind.UNKNOWN);
        }

    void startMining(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir, boolean shiftDown,
            RtsTraceInputKind inputKind) {
            controller.miningOperationService.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(), toolSlot,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview(),
                    controller.isAllowPlacedBlockRecovery(), controller.isToolProtectionEnabled());
        }

    void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode) {
            startUltimine(pos, face, toolSlot, limit, mode, RtsTraceInputKind.UNKNOWN);
        }

    void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode,
            RtsTraceInputKind inputKind) {
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
            confirmAreaMine(toolSlot, fillMode, RtsTraceInputKind.UNKNOWN);
        }

    void confirmAreaMine(int toolSlot, ShapeFillMode fillMode, RtsTraceInputKind inputKind) {
            controller.miningOperationService.confirmAreaMine(toolSlot, fillMode,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview(),
                    controller.isToolProtectionEnabled());
        }

    void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot) {
            confirmShapeAreaDestroy(targets, toolSlot, RtsTraceInputKind.UNKNOWN);
        }

    void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot, RtsTraceInputKind inputKind) {
            controller.miningOperationService.confirmShapeAreaDestroy(targets, toolSlot,
                    controller.buildPlacementService.getSelectedItemId(),
                    controller.buildPlacementService.getSelectedItemPreview(),
                    controller.isToolProtectionEnabled());
    }

    void confirmConvenienceDestroy(RtsConvenienceDestroyMode mode,
            BlockHitResult hit, RtsConvenienceDestroySettings settings, int toolSlot) {
        confirmConvenienceDestroy(mode, hit, settings, toolSlot, RtsTraceInputKind.UNKNOWN);
    }

    void confirmConvenienceDestroy(RtsConvenienceDestroyMode mode,
            BlockHitResult hit, RtsConvenienceDestroySettings settings, int toolSlot,
            RtsTraceInputKind inputKind) {
        controller.miningOperationService.confirmConvenienceDestroy(
                mode, hit, settings, toolSlot,
                controller.buildPlacementService.getSelectedItemId(),
                controller.buildPlacementService.getSelectedItemPreview(),
                controller.isToolProtectionEnabled());
    }

    void abortMining(int toolSlot) {
            abortMining(toolSlot, RtsMiningStopOrigin.EXPLICIT_CANCEL);
        }

    void abortMining(int toolSlot, RtsMiningStopOrigin stopOrigin) {
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
            Minecraft minecraft = Minecraft.getInstance();
            controller.cameraOrbitService.syncVisualCameraFrame(minecraft, controller.anchorX, controller.anchorY, controller.anchorZ, controller.maxRadius, controller.enabled);
        }

}

