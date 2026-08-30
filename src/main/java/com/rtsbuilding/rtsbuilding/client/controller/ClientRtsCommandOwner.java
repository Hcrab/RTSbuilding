package com.rtsbuilding.rtsbuilding.client.controller;


import com.rtsbuilding.rtsbuilding.client.compat.RtsRemoteMenuClientDiagnostics;
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

final class ClientRtsCommandOwner {
    private final ClientRtsController controller;

    ClientRtsCommandOwner(ClientRtsController controller) {
        this.controller = controller;
    }

    void linkStorage(BlockPos pos) {
            controller.storageStateManager.linkStorage(pos);
        }

    void linkStorage(BlockPos pos, boolean allowStore) {
            controller.storageStateManager.linkStorage(pos, allowStore);
        }

    void requestStoragePage(int page) {
            controller.storageStateManager.requestStoragePage(page);
        }

    void updateStoragePageSize(int pageSize) {
            controller.storageStateManager.updateStoragePageSize(pageSize);
        }

    void requestStoragePageIfNoSnapshot(int page) {
            controller.storageStateManager.requestStoragePageIfNoSnapshot(page);
        }

    void refreshStoragePage() {
            controller.storageStateManager.refreshStoragePage();
        }

    void requestCraftables() {
            controller.storageStateManager.requestCraftables();
        }

    void requestMoreCraftables() {
            controller.storageStateManager.requestMoreCraftables();
        }

    void setAutoStoreMinedDrops(boolean enabled) {
            controller.storageStateManager.setAutoStoreMinedDrops(enabled);
        }

    void toggleAutoStoreMinedDrops() {
            controller.storageStateManager.toggleAutoStoreMinedDrops();
        }

    void setStorageSearch(String search) {
            controller.storageStateManager.setStorageSearch(search);
        }

    void setStorageCategory(String category) {
            controller.storageStateManager.setStorageCategory(category);
        }

    void cycleSort() {
            controller.storageStateManager.cycleSort();
        }

    void setStorageSort(RtsStorageSort sort) {
            controller.storageStateManager.setStorageSort(sort);
        }

    void toggleSortDirection() {
            controller.storageStateManager.toggleSortDirection();
        }

    void prevPage() {
            controller.storageStateManager.prevPage();
        }

    void nextPage() {
            controller.storageStateManager.nextPage();
        }

    void setCraftablesSearch(String search) {
            controller.storageStateManager.setCraftablesSearch(search);
        }

    void setCraftablesShowUnavailable(boolean showUnavailable) {
            controller.storageStateManager.setCraftablesShowUnavailable(showUnavailable);
        }

    void toggleCraftablesShowUnavailable() {
            controller.storageStateManager.toggleCraftablesShowUnavailable();
        }

    void craftRecipeToLinked(String recipeId) {
            controller.storageStateManager.craftRecipeToLinked(recipeId);
        }

    void craftRecipeToLinked(String recipeId, int craftCount) {
            controller.storageStateManager.craftRecipeToLinked(recipeId, craftCount);
        }

    void openCraftTerminal() {
            controller.storageStateManager.setStorageSearch("");
            controller.pendingCraftTerminalOpen = true;
            controller.pendingCraftTerminalOpenTicks = 120;
            controller.beginRemoteMenuOpenGrace();
            RtsClientPacketGateway.sendOpenCraftTerminal();
        }

    void detectQuestsNow() {
            controller.beginQuestDetectScan();
            RtsClientPacketGateway.sendQuestDetectManual();
        }

    void beginQuestDetectScan() {
            long now = System.currentTimeMillis();
            controller.questDetectPhase = S2CRtsQuestDetectStatusPayload.PHASE_STARTED;
            controller.questDetectStartedAtMs = now;
            controller.questDetectFinishedAtMs = 0L;
            controller.questDetectExpiryMs = 0L;
            controller.questDetectScannedTasks = 0;
            controller.questDetectTotalTasks = 0;
            controller.questDetectCompletedTasks = 0;
        }

    void rotateBlock(BlockPos pos) {
            if (pos == null) {
                return;
            }
            RtsClientPacketGateway.sendRotateBlock(pos);
        }

    void rotateBlockStep(
                BlockPos pos,
                Direction axisDirection,
                int quarterTurns) {
            controller.buildPlacementService.rotateBlockStep(
                    pos, axisDirection, quarterTurns);
        }

    void storeHotbarSlotToLinked(int slot) {
            RtsClientPacketGateway.sendStoreHotbarSlot(slot);
        }

    void fillInventoryFromLinked() {
            RtsClientPacketGateway.sendFillInventory();
        }

    void unlinkLinkedStorage(BlockPos pos) {
            RtsClientPacketGateway.sendUnlinkStorage(pos);
        }

    void unlinkLinkedStorage(String dimensionId, BlockPos pos) {
            RtsClientPacketGateway.sendUnlinkStorage(dimensionId, pos);
        }

    void updateLinkedStorageSettings(BlockPos pos, boolean extractOnly, int priority) {
            RtsClientPacketGateway.sendUpdateLinkedStorage(pos, extractOnly, priority);
        }

    void updateLinkedStorageSettings(String dimensionId, BlockPos pos, boolean extractOnly, int priority) {
            RtsClientPacketGateway.sendUpdateLinkedStorage(dimensionId, pos, extractOnly, priority);
        }

    void quickDropSelectedItem(String itemId, int amount, Vec3 dropPos) {
            if (itemId == null || itemId.isBlank() || dropPos == null) {
                return;
            }
            RtsClientPacketGateway.sendQuickDrop(itemId, amount, dropPos);
        }

    void applyStoragePage(S2CRtsStoragePagePayload payload) {
            controller.storageStateManager.applyStoragePage(payload, controller::refreshSelectedItemPreviewFromStorage);
        }

    void applyCraftables(S2CRtsCraftablesPayload payload) {
            controller.storageStateManager.applyCraftables(payload);
        }

    void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
            controller.storageStateManager.applyCraftFeedback(payload);
        }

    void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
            controller.storageStateManager.applyStorageDirty(payload);
        }

    void refreshSelectedItemPreviewFromStorage() {
            controller.buildPlacementService.syncSelectedPreviewFromStorage(
                    controller.storageStateManager.getInternalStorageEntries(),
                    controller.storageStateManager.hasStoragePageSnapshot(),
                    controller.storageStateManager.getStorageTotalCount(controller.buildPlacementService.getSelectedItemId()));
        }

    void applyRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload) {
            RtsRemoteMenuClientDiagnostics.receiveServerHint(
                    payload == null ? null : payload.pos(),
                    ClientRtsController.REMOTE_MENU_OPEN_GRACE_TICKS);
            controller.beginRemoteMenuOpenGrace();
        }

    void applyDamageFeedback(S2CRtsDamageFeedbackPayload payload) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            if (minecraft.screen instanceof BuilderScreen builderScreen) {
                builderScreen.triggerDamageFlash();
            }
            if (RtsClientUiStateStore.isRtsSoundsEnabled() && controller.isDamageSoundEnabled()) {
                float volume = Mth.clamp(0.45F + Math.max(0.0F, payload.amount()) * 0.08F, 0.45F, 1.2F);
                minecraft.player.playSound(SoundEvents.PLAYER_HURT, volume, 1.0F);
            }
            if (payload.lowHealth() && controller.isDamageAutoReturnEnabled() && controller.enabled) {
                RtsClientPacketGateway.sendToggleCamera(controller.isStartCameraAtPlayerHead());
            }
        }

    void applyQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload) {
            if (payload == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (payload.phase() == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
                if (controller.questDetectPhase != S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
                    controller.beginQuestDetectScan();
                }
                controller.questDetectScannedTasks = Math.max(0, payload.scannedTasks());
                controller.questDetectTotalTasks = Math.max(0, payload.totalTasks());
                controller.questDetectCompletedTasks = Math.max(0, payload.completedTasks());
                return;
            }
            if (controller.questDetectStartedAtMs <= 0L) {
                controller.questDetectStartedAtMs = now;
            }
            controller.questDetectPhase = payload.phase();
            controller.questDetectFinishedAtMs = now;
            controller.questDetectExpiryMs = now + ClientRtsController.QUEST_DETECT_RESULT_VISIBLE_MS;
            controller.questDetectScannedTasks = Math.max(0, payload.scannedTasks());
            controller.questDetectTotalTasks = Math.max(0, payload.totalTasks());
            controller.questDetectCompletedTasks = Math.max(0, payload.completedTasks());
        }

    void applyMineProgress(S2CRtsMineProgressPayload payload) {
            controller.miningOperationService.applyMineProgress(payload.pos(), payload.stage());
        }

    void applyProgressionState(S2CRtsProgressionStatePayload payload) {
            controller.progressionStateManager.applyProgressionState(payload, () -> controller.homeSelectionMode = false);
        }

    void applyPluginState(S2CRtsPluginStatePayload payload) {
            controller.pluginStateManager.applyPluginState(payload);
        }

    void requestPluginState() {
            RtsClientPacketGateway.sendRequestPlugins();
        }

    void installPluginFromInventorySlot(int inventorySlot) {
            RtsClientPacketGateway.sendInstallPluginFromInventorySlot(inventorySlot);
        }

    void uninstallPlugin(String pluginId) {
            RtsClientPacketGateway.sendUninstallPlugin(pluginId);
        }

    void requestProgressionState() {
            controller.progressionStateManager.requestProgressionState();
        }

    void setSurvivalProgressionEnabled(boolean enabled) {
            controller.progressionStateManager.setSurvivalProgressionEnabled(enabled, () -> controller.homeSelectionMode = false);
        }

    void setHome(BlockPos pos) {
            controller.progressionStateManager.setHome(pos);
        }

    void beginHomeSelection() {
            controller.progressionStateManager.beginHomeSelection();
        }

    void beginRemoteMenuOpenGrace() {
            controller.pendingRemoteMenuOpenTicks = Math.max(
                    controller.pendingRemoteMenuOpenTicks,
                    ClientRtsController.REMOTE_MENU_OPEN_GRACE_TICKS);
            controller.screenlessRemoteMenuTicks = 0;
            RtsRemoteMenuCompat.beginClientRemoteMenuOpen();
        }

    void handleRemoteMenuOpenFailure(Minecraft minecraft, Throwable throwable) {
            RtsRemoteMenuClientDiagnostics.compatFailure(minecraft, throwable);
            controller.clearRemoteMenuValidationState();
            controller.pendingRemoteMenuOpenTicks = 0;
            if (minecraft.player != null) {
                RtsClientPacketGateway.sendCloseRemoteMenu();
                minecraft.player.closeContainer();
                minecraft.player.displayClientMessage(Component.literal("Open failed."), true);
            }
            minecraft.setScreen(null);
        }

    void clearRemoteMenuValidationState() {
            controller.relaxedRemoteMenu = null;
            RtsRemoteMenuCompat.clearClientRemoteMenu();
        }

    boolean isLocalPlayerCreative() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        }

}
