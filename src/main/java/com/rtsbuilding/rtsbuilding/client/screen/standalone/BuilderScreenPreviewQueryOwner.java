package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Collections;
import net.minecraft.client.resources.I18n;
import net.minecraft.block.material.Material;

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
            if (screen.getMinecraft() == null || screen.getMinecraft().theWorld == null) {
                return Collections.emptyList();
            }
            if (!screen.isQuickBuildRangeDestroyChainMode()) {
                return Collections.emptyList();
            }
            BlockPos seed = screen.controller.getMineProgressPos();
            if (seed == null || com.rtsbuilding.rtsbuilding.platform.world.WorldCompat
                    .isAirBlock(screen.getMinecraft().theWorld, seed)) {
                RayTraceResult hit = screen.cursorPicker.pickBlockHit();
                if (hit == null) {
                    return Collections.emptyList();
                }
                seed = hit.getBlockPos();
            }
            BlockState seedState = BlockState.fromWorld(screen.getMinecraft().theWorld, seed);
            if (seedState.getMaterial() == Material.air) {
                return Collections.emptyList();
            }
            boolean creative = screen.getMinecraft().thePlayer != null && screen.getMinecraft().thePlayer.capabilities.isCreativeMode;
            List<BlockPos> raw = RtsUltimineCollector.collect(
                    screen.getMinecraft().theWorld,
                    seed,
                    screen.getUltimineLimit(),
                    (pos, state, originalState) -> {
                        if (state.getMaterial() == Material.air
                                || state.getMaterial().isLiquid()
                                || (!creative && state.getBlockHardness(screen.getMinecraft().theWorld, pos) < 0.0F)) {
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
            return GuiScreen.isCtrlKeyDown() && ClientKeyMappings.MOVE_PLAYER.getKeyCode() == button - 100;
        }

    boolean isMovePlayerActionKey(int keyCode, int scanCode) {
            return ClientKeyMappings.MOVE_PLAYER.getKeyCode() == keyCode;
        }

    boolean handleMovePlayerActionAt(double mouseX, double mouseY) {
            if (!screen.isWorldArea(mouseX, mouseY)) {
                return true;
            }
            // 移动玩家键位默认是 Ctrl+右键；双击仍保留“飞到目标上方”的精确落点。
            long now = System.currentTimeMillis();
            boolean isDoubleClick = (now - screen.lastCtrlRightClickTime) < screen.CTRL_DOUBLE_CLICK_THRESHOLD_MS;
            screen.lastCtrlRightClickTime = now;

            RayTraceResult hit = screen.cursorPicker.pickBlockHit();
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

    void enableRtsScissor(LegacyGuiGraphics g, int x1, int y1, int x2, int y2) {
            screen.guiScaleCoordinator.enableScissor(g, x1, y1, x2, y2);
        }

    String trimToWidth(String text, int maxWidth) {
            return RtsClientUiUtil.trimToWidth(screen.font(), text, maxWidth);
        }

    String text(String key, Object... args) {
            return I18n.format(key, args);
        }

    String selectedItemStatusLabel() {
            ItemStack preview = screen.controller.getSelectedItemPreview();
            String label = screen.controller.getSelectedItemLabel();
            if (preview != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview) && preview.isItemStackDamageable()) {
                int max = preview.getMaxDamage();
                int durability = Math.max(0, max - preview.getItemDamage());
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
                return null;
            }
            if (screen.getMinecraft() == null || screen.getMinecraft().thePlayer == null) {
                return null;
            }
            ItemStack hand = screen.getMinecraft().thePlayer.getHeldItem();
            return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(hand) ? null : hand;
        }

    boolean shouldRenderFunnelCursor() {
            return screen.controller.isEnabled()
                    && screen.controller.getMode() == BuilderMode.FUNNEL
                    && screen.controller.isFunnelEnabled()
                    && !screen.isSearchFocused()
                    && !screen.isMouseOverFloatingWindow(screen.currentMouseX(), screen.currentMouseY());
        }

    Vec3d computeCursorRayDirection() {
            return screen.cursorPicker.computeCursorRayDirection();
        }

    Vec3d currentRayOrigin() {
            return screen.cursorPicker.currentRayOrigin();
        }

    EnumFacing currentCameraHorizontalDirection() {
            if (screen.getMinecraft() != null && screen.getMinecraft().renderViewEntity != null) {
                return EnumFacing.fromAngle(screen.getMinecraft().renderViewEntity.rotationYaw);
            }
            return EnumFacing.NORTH;
        }

    PlacedBlockRotationHandles getRotationHandles() {
            return screen.rotationHandles;
        }

    RayTraceResult pickBlockHit() {
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
            return Keyboard.isKeyDown(Keyboard.KEY_LMENU)
                    || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        }

    double currentMouseX() {
            return screen.lastMouseX;
        }

    double currentMouseY() {
            return screen.lastMouseY;
        }

}
