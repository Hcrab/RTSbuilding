package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.compat.RtsVanillaCursorHitBridge;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.craft.RtsCraftQuantityWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.RtsAiChatPanel;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.client.screen.handler.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.StorageLinkDetailHandler;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.mode.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationGesture;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.LeftDockedTooltipRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.RtsScreenOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.storage.LinkedStoragePanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsBlueprintResumePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsWorkflowPanel;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftDockStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.TooltipStyle;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的LifecycleOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenLifecycleOwner {
    private final BuilderScreen screen;

    BuilderScreenLifecycleOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    void syncQuickBuildActiveState() {
            if (!screen.quickBuildPanel.isOpen() || !screen.canUseQuickBuild()) {
                screen.controller.setBuildShape(BuildShape.BLOCK);
                screen.controller.clearAreaMineSession();
                screen.shapeController.clearShapeBuildSession();
                screen.ensureFillModeForShape(BuildShape.BLOCK);
                return;
            }
            if (screen.quickBuildPanel.isSmartFillMode()) {
                screen.controller.setBuildShape(BuildShape.BLOCK);
                screen.controller.clearAreaMineSession();
                screen.shapeController.clearShapeBuildSession();
            } else if (screen.quickBuildPanel.isRangeDestroyMode()) {
                screen.shapeController.applyDestroyStateAsActive();
            } else {
                screen.shapeController.applyBuildStateAsActive();
            }
            screen.ensureFillModeForShape(screen.controller.getBuildShape());
        }

    void init() {

            // Enter the RTS scale frame so that clamps in applyStoredUiState use the virtual coordinate space (rather than the GUI-scaled width)
            RtsUiScaleFrame frame = screen.guiScaleCoordinator.enterLayoutFrame();
            try {
                screen.uiStateManager.applyStoredUiState();
            } finally {
                if (frame != null) {
                    frame.close();
                }
            }
            // 持久化加载后，将当前模式的独立状态同步到活跃字段
            screen.syncQuickBuildActiveState();
            WindowTextBox storageSearchBox = new WindowTextBox(screen.font(), 8, screen.uiHeight() - 52, 150, 14);
            storageSearchBox.setPlaceholder("Search");
            screen.searchBox = storageSearchBox;
            screen.searchBox.setMaxLength(128);
            screen.searchBox.setCanLoseFocus(true);
            screen.searchBox.setValue(screen.controller.getStorageSearch());
            screen.craftSearchBox = new EditBox(screen.font(), 8, screen.uiHeight() - 52, 74, 10, Component.literal("Craft Search"));
            screen.craftSearchBox.setMaxLength(128);
            screen.craftSearchBox.setBordered(false);
            screen.craftSearchBox.setCanLoseFocus(true);
            screen.craftSearchBox.setTextColor(BottomPanelCraftStyle.SEARCH_TEXT.toArgb());
            screen.craftSearchBox.setTextColorUneditable(
                    BottomPanelCraftStyle.SEARCH_UNEDITABLE_TEXT.toArgb());
            if (screen.bottomPanel.craftSearchDraft == null) {
                screen.bottomPanel.craftSearchDraft = screen.controller.getCraftablesSearch();
            }
            screen.craftSearchBox.setValue(screen.bottomPanel.craftSearchDraft);
            screen.craftSearchBox.setResponder(value -> screen.bottomPanel.craftSearchDraft = value == null ? "" : value);
            screen.controller.requestCraftables();
        }

    void onClose() {
            screen.floatingWindowLayer.clearTransientInputState();
            screen.topBarPanel.clearTransientInputState();
            screen.shapeController.clearShapeBuildSession();
            screen.cullingManager.closeManagementMode();
            screen.controller.clearAreaMineSession();
            screen.persistUiState();
            screen.uiStateManager.flush();
            screen.pendingGuiBindSlot = -1;
            if (screen.funnelHotkeyTemporaryMode && screen.controller.getMode() == BuilderMode.FUNNEL) {
                screen.controller.setMode(screen.modeBeforeFunnelHotkey);
            }
            screen.funnelHotkeyHeld = false;
            screen.funnelHotkeyTemporaryMode = false;
            screen.funnelMouseHoldButton = -1;
            screen.modeWheel.close();
            screen.modeWheelConsumedMouseButton = -1;
            screen.rotationHandles.clear();
            screen.closePlacementStateWheelImmediately();
            screen.placementStateWheelConsumedMouseButton = -1;
            screen.cameraInput.resetCameraVerticalHeld();
            screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.SCREEN_CLOSE);
            if (screen.controller.isFunnelEnabled()) {
                screen.controller.setFunnelEnabled(false);
            }
            if (screen.controller.isEnabled()) {
                RtsClientPacketGateway.sendToggleCamera(screen.controller.isStartCameraAtPlayerHead());
            }
            screen.aiChatPanel.close();
            screen.craftQuantityWindowPanel.close();
            screen.overlayRenderer.updateNativeCursorVisibility(false);
            RtsCullingClientState.clearActiveManager(screen.cullingManager);
        }

    void removed() {

            screen.aiChatPanel.close();
            screen.floatingWindowLayer.clearTransientInputState();
            screen.topBarPanel.clearTransientInputState();
            screen.cameraInput.resetCameraVerticalHeld();
            screen.modeWheel.close();
            screen.modeWheelAltWasDown = false;
            screen.modeWheelConsumedMouseButton = -1;
            screen.rotationHandles.clear();
            screen.closePlacementStateWheelImmediately();
            screen.placementStateWheelConsumedMouseButton = -1;
            boolean restoreModeAfterFunnel = screen.funnelHotkeyTemporaryMode;
            screen.funnelHotkeyHeld = false;
            screen.funnelMouseHoldButton = -1;
            if (screen.controller.isFunnelEnabled()) {
                screen.controller.setFunnelEnabled(false);
            }
            if (restoreModeAfterFunnel && screen.controller.getMode() == BuilderMode.FUNNEL) {
                screen.controller.setMode(screen.modeBeforeFunnelHotkey);
            }
            screen.funnelHotkeyTemporaryMode = false;
            screen.overlayRenderer.updateNativeCursorVisibility(false);
            RtsCullingClientState.clearActiveManager(screen.cullingManager);
        }

    void tick() {

            // 机械动力强力胶等第三方预览只读取原版 hitResult；每 tick 同步一次 RTS 自由光标。
            RtsVanillaCursorHitBridge.publish(screen);

            // 每 tick 写入脏状态（无脏时零开销）
            screen.uiStateManager.flush();
            screen.enforceBlueprintPlacementModeLock();
            screen.updateModeWheelAltState();
            if (screen.controller.getMode() != BuilderMode.ROTATE
                    || !screen.rotationHandles.targetStillMatches(screen.getMinecraft().level)) {
                screen.rotationHandles.clear();
            }
            if (screen.placementStateWheel.isOpen()
                    && !(screen.controller.getSelectedItemPreview().getItem() instanceof BlockItem)
                    && !(screen.getMinecraft().player != null
                    && screen.getMinecraft().player.getMainHandItem().getItem() instanceof BlockItem)) {
                screen.closePlacementStateWheel();
            }
            if (screen.rtsFlightToggleCooldownTicks > 0) {
                screen.rtsFlightToggleCooldownTicks--;
            }
            if (screen.controller.getMode() == BuilderMode.FUNNEL && screen.controller.isFunnelEnabled()) {
                BlockHitResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.updateFunnelTarget(hit.getBlockPos());
                }
            }
            screen.bottomPanel.syncCraftablesPanelState();
            if (!screen.cameraInput.isLeftMiningActive()) {
                return;
            }
            if (screen.getMinecraft() == null || !screen.controller.isEnabled()) {
                screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.RTS_DISABLED);
                return;
            }
            long window = screen.getMinecraft().getWindow().getWindow();
            boolean miningInputDown = screen.cameraInput.isKeyboardMining()
                    ? ClientKeyMappings.ACTION_BREAK.isDown()
                    : screen.cameraInput.getActiveMiningMouseButton() >= 0
                            && GLFW.glfwGetMouseButton(window, screen.cameraInput.getActiveMiningMouseButton()) == GLFW.GLFW_PRESS;
            if (!miningInputDown) {
                screen.cameraInput.stopActiveMining(screen.cameraInput.isKeyboardMining()
                        ? RtsMiningStopOrigin.LIFECYCLE_KEY_NOT_DOWN
                        : RtsMiningStopOrigin.LIFECYCLE_MOUSE_NOT_DOWN);
                return;
            }
        }

}
