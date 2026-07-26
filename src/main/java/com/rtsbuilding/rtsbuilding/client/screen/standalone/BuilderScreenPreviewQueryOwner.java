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
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的PreviewQueryOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenPreviewQueryOwner {
    private final BuilderScreen screen;

    BuilderScreenPreviewQueryOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    BlueprintGhostPreview getBlueprintGhostPreview() {
            if (screen.bottomPanel.bottomPanelTab != BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                    || BlueprintPanel.isCaptureModeActive()
                    || !BlueprintPanel.hasSelectedBlueprint()) {
                return BlueprintGhostPreview.EMPTY;
            }
            BlockPos anchor = BlueprintPanel.getPinnedAnchor();
            if (anchor == null) {
                anchor = BlueprintPanel.anchorForCursorTarget(
                        screen.cursorPicker.resolveBlueprintAnchor(screen.cursorPicker.pickBlueprintPlacementHit()));
            }
            if (anchor == null) {
                return BlueprintGhostPreview.EMPTY;
            }
            BlueprintGhostPreview preview = BlueprintPanel.createGhostPreview(
                    anchor, BlueprintPanel.getYRotationSteps(), screen.controller);
            if (preview.blocks().isEmpty()) {
                return BlueprintGhostPreview.EMPTY;
            }
            return preview;
        }

    List<BlockPos> collectUltiminePreviewBlocks() {
            if (screen.getMinecraft() == null || screen.getMinecraft().level == null) {
                return List.of();
            }
            if (!screen.isQuickBuildRangeDestroyChainMode()) {
                return List.of();
            }
            BlockPos seed = screen.controller.getMineProgressPos();
            if (seed == null || screen.getMinecraft().level.getBlockState(seed).isAir()) {
                BlockHitResult hit = screen.cursorPicker.pickBlockHit();
                if (hit == null) {
                    return List.of();
                }
                seed = hit.getBlockPos();
            }
            BlockState seedState = screen.getMinecraft().level.getBlockState(seed);
            if (seedState.isAir()) {
                return List.of();
            }
            boolean creative = screen.getMinecraft().player != null && screen.getMinecraft().player.isCreative();
            List<BlockPos> raw = RtsUltimineCollector.collect(
                    screen.getMinecraft().level,
                    seed,
                    screen.getUltimineLimit(),
                    (pos, state, originalState) -> {
                        if (state.isAir()
                                || !state.getFluidState().isEmpty()
                                || (!creative && state.getDestroySpeed(screen.getMinecraft().level, pos) < 0.0F)) {
                            return false;
                        }
                        return state.getBlock() == originalState.getBlock();
                    });
            return screen.filterToBounds(raw);
        }

    List<BlockPos> filterToBounds(List<BlockPos> blocks) {
            if (!screen.controller.hasBounds() || blocks == null || blocks.isEmpty()) {
                return blocks;
            }
            return RenderingUtil.filterBlocksWithinBounds(blocks,
                    screen.controller.getAnchorX(), screen.controller.getAnchorZ(), screen.controller.getMaxRadius());
        }

    boolean isMovePlayerActionMouse(int button) {
            return ClientKeyMappings.MOVE_PLAYER.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button));
        }

    boolean isMovePlayerActionKey(int keyCode, int scanCode) {
            return ClientKeyMappings.MOVE_PLAYER.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
        }

    boolean handleMovePlayerActionAt(double mouseX, double mouseY) {
            if (!screen.isWorldArea(mouseX, mouseY)) {
                return true;
            }
            // 移动玩家键位默认是 Ctrl+右键；双击仍保留“飞到目标上方”的精确落点。
            long now = System.currentTimeMillis();
            boolean isDoubleClick = (now - screen.lastCtrlRightClickTime) < screen.CTRL_DOUBLE_CLICK_THRESHOLD_MS;
            screen.lastCtrlRightClickTime = now;
    
            BlockHitResult hit = screen.cursorPicker.pickBlockHit();
            if (hit != null) {
                if (isDoubleClick) {
                    screen.lastCtrlRightClickTime = 0;
                    RtsClientPathfinding.goToAbove(hit.getBlockPos(), 1);
                } else {
                    RtsClientPathfinding.goTo(hit.getBlockPos());
                }
            }
            return true;
        }

    void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
            screen.guiScaleCoordinator.enableScissor(g, x1, y1, x2, y2);
        }

    String trimToWidth(String text, int maxWidth) {
            return RtsClientUiUtil.trimToWidth(screen.font(), text, maxWidth);
        }

    String text(String key, Object... args) {
            return Component.translatable(key, args).getString();
        }

    String selectedItemStatusLabel() {
            ItemStack preview = screen.controller.getSelectedItemPreview();
            String label = screen.controller.getSelectedItemLabel();
            if (preview != null && !preview.isEmpty() && preview.isDamageableItem()) {
                int max = preview.getMaxDamage();
                int durability = Math.max(0, max - preview.getDamageValue());
                return label + " " + durability + "/" + max;
            }
            return label;
        }

    ItemStack resolveCursorPreview() {
            if (screen.controller.hasSelectedItem()) {
                return screen.controller.getSelectedItemPreview();
            }
            if (screen.controller.hasSelectedFluid()) {
                return screen.controller.getSelectedFluidPreview();
            }
            if (screen.controller.isEmptyHandSelected()) {
                return ItemStack.EMPTY;
            }
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return ItemStack.EMPTY;
            }
            ItemStack hand = screen.getMinecraft().player.getMainHandItem();
            return hand.isEmpty() ? ItemStack.EMPTY : hand;
        }

    boolean shouldRenderFunnelCursor() {
            return screen.controller.isEnabled()
                    && screen.controller.getMode() == BuilderMode.FUNNEL
                    && screen.controller.isFunnelEnabled()
                    && !screen.isSearchFocused()
                    && !screen.isMouseOverFloatingWindow(screen.currentMouseX(), screen.currentMouseY());
        }

    Vec3 computeCursorRayDirection() {
            return screen.cursorPicker.computeCursorRayDirection();
        }

    Vec3 currentRayOrigin() {
            return screen.cursorPicker.currentRayOrigin();
        }

    Direction currentCameraHorizontalDirection() {
            if (screen.getMinecraft() != null && screen.getMinecraft().gameRenderer != null) {
                return Direction.fromYRot(
                        screen.getMinecraft().gameRenderer.getMainCamera().getYRot());
            }
            return Direction.NORTH;
        }

    PlacedBlockRotationHandles getRotationHandles() {
            return screen.rotationHandles;
        }

    BlockHitResult pickBlockHit() {
            return screen.cursorPicker.pickBlockHit();
        }

    InteractionTypes.InteractionTarget pickInteractionTarget(boolean includeFluidSource) {
            return screen.cursorPicker.pickInteractionTarget(includeFluidSource);
        }

    ScreenShapeController getShapeController() {
            return screen.shapeController;
        }

    String fillModeLabel(ShapeFillMode mode) {
            return screen.shapeController.fillModeLabel(mode);
        }

    String currentShapeSizeText() {
            return screen.shapeController.currentShapeSizeText();
        }

    String currentShapeCostText() {
            return screen.shapeController.currentShapeCostText();
        }

    String pendingShapeStatusText() {
            return screen.shapeController.pendingShapeStatusText();
        }

    String shapeLabel(BuildShape shape) {
            return screen.shapeController.shapeLabel(shape);
        }

    boolean isAltDown() {
            if (screen.getMinecraft() == null) return false;
            long window = screen.getMinecraft().getWindow().getWindow();
            return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        }

    double currentMouseX() {
            return screen.lastMouseX;
        }

    double currentMouseY() {
            return screen.lastMouseY;
        }

}
