package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
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
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的WindowActionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenWindowActionOwner {
    private final BuilderScreen screen;

    BuilderScreenWindowActionOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    void persistUiState() {
            screen.uiStateManager.persistUiState();
        }

    void adjustRtsGuiScale(double delta) {
            screen.uiStateManager.adjustRtsGuiScale(delta);
        }

    double getRtsGuiScale() {
            return screen.uiStateManager.fixedRtsGuiScale();
        }

    String rtsGuiScaleLabel() {
            return screen.uiStateManager.rtsGuiScaleLabel();
        }

    RtsFloatingWindowLayer getFloatingWindowLayer() {
            return screen.floatingWindowLayer;
        }

    ClientRtsController uiController() {
            return screen.controller;
        }

    boolean isQuickBuildRangeDestroyMode() {
            return screen.isQuickBuildOpen() && screen.quickBuildPanel.isRangeDestroyMode();
        }

    boolean isQuickBuildRangeDestroyChainMode() {
            return screen.isQuickBuildOpen() && screen.quickBuildPanel.isRangeDestroyChainMode();
        }

    boolean isQuickBuildCreativeOverwriteEnabled() {
            return screen.isQuickBuildOpen() && screen.quickBuildPanel.isCreativeOverwriteEnabled();
        }

    boolean isAdvancedRangeDestroyBoxMode() {
            return screen.isAdvancedShapeMode();
        }

    boolean isAdvancedRangeDestroyShapeMode() {
            return screen.isQuickBuildOpen() && screen.quickBuildPanel.isAdvancedRangeDestroyShapeMode();
        }

    boolean isAdvancedShapeMode() {
            return screen.isQuickBuildOpen() && screen.quickBuildPanel.isAdvancedShapeMode();
        }

    boolean isRoundShapeVertical(BuildShape shape) {
            return screen.isQuickBuildOpen() && screen.quickBuildPanel.isRoundShapeVertical(shape);
        }

    String activeQuickBuildShapeLabel() {
            if (screen.isQuickBuildRangeDestroyChainMode()) {
                return screen.text("screen.rtsbuilding.shape.chain");
            }
            return screen.shapeLabel(screen.controller.getBuildShape());
        }

    boolean handleQuickBuildRangeDestroyClick(double mouseX, double mouseY) {
            if (!screen.isQuickBuildRangeDestroyMode() || screen.isQuickBuildRangeDestroyChainMode() || !screen.isWorldArea(mouseX, mouseY)) {
                return false;
            }
            if (screen.isAdvancedShapeMode()
                    && screen.shapeController.clickAdvancedRangeDestroyHandle(
                            screen.cursorPicker.currentRayOrigin(),
                            screen.cursorPicker.computeCursorRayDirection())) {
                return true;
            }
            if (screen.shapeController.isAwaitingBatchDestroyConfirm()) {
                if (Config.isKeyboardBatchConfirmEnabled()) {
                    return true;
                }
                return screen.shapeController.tryConfirmPendingRangeDestroy();
            }
            InteractionTypes.InteractionTarget target = screen.cursorPicker.pickInteractionTarget(false);
            if (target != null && target.blockHit() != null) {
                screen.shapeController.selectRangeDestroyShape(target.blockHit(), mouseY, target.rayDir());
                return true;
            }
            return true;
        }

    void setQuickBuildMode(QuickBuildMode mode) {
            screen.quickBuildPanel.setMode(mode);
        }

    int getUltimineLimit() {
            return screen.quickBuildPanel.getChainDestroyLimit();
        }

    boolean isAreaMineHeightPreview() {
            if (!screen.isQuickBuildRangeDestroyMode()) {
                return false;
            }
            int phase = screen.controller.getAreaMinePhase();
            return phase == MiningOperationService.AREA_MINE_PHASE_NEED_SECOND
                    || phase == MiningOperationService.AREA_MINE_PHASE_NEED_HEIGHT;
        }

    int getShapeUndoSize() {
            return screen.shapeController.getShapeUndoSize();
        }

    int getPendingGuiBindSlot() {
            return screen.pendingGuiBindSlot;
        }

    void setPendingGuiBindSlot(int slot) {
            screen.pendingGuiBindSlot = slot;
        }

    void clearPendingGuiBind() {
            screen.pendingGuiBindSlot = -1;
        }

    void toggleQuickBuild() {
            if (!screen.canUseQuickBuild()) {
                screen.showQuickBuildLockedMessage();
                screen.quickBuildPanel.setOpen(false);
                return;
            }
            screen.quickBuildPanel.toggleOpen();
        }

    void openCraftQuantityWindow(CraftableEntry entry) {
            screen.craftQuantityWindowPanel.open(entry);
        }

    void submitCraftQuantityWindowIfReady() {
            RtsCraftQuantityWindowPanel.Request request = screen.craftQuantityWindowPanel.consumePendingRequest();
            if (request != null) {
                screen.controller.craftRecipeToLinked(request.recipeId(), request.craftCount());
            }
        }

    boolean handleFloatingWindowClick(double mouseX, double mouseY, int button) {
            return screen.floatingWindowLayer.mouseClicked(mouseX, mouseY, button);
        }

    boolean handleFloatingWindowDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return screen.floatingWindowLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

    boolean handleFloatingWindowRelease(double mouseX, double mouseY, int button) {
            boolean handled = screen.floatingWindowLayer.mouseReleased(mouseX, mouseY, button);
            if (screen.floatingWindowLayer.consumeAnyBoundsDirty()) {
                screen.persistUiState();
                return true;
            }
            screen.submitCraftQuantityWindowIfReady();
            return handled;
        }

    boolean isMouseOverFloatingWindow(double mouseX, double mouseY) {
            return screen.floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY);
        }

    void closeGearMenu() {
            screen.gearMenuPanel.close();
        }

    void toggleGearMenu() {
            if (screen.gearMenuPanel.isOpen()) {
                screen.gearMenuPanel.close();
            } else {
                screen.gearMenuPanel.open();
            }
        }

    void toggleTopGuide(int x, int y) {
            if (screen.guidePanel.isOpen() && screen.guidePanel.getContext() == GuideUiContext.TOP) {
                screen.guidePanel.close();
            } else {
                screen.guidePanel.open(GuideUiContext.TOP, x, y);
            }
        }

    void openAiChat() {
            screen.aiChatPanel.open();
        }

}
