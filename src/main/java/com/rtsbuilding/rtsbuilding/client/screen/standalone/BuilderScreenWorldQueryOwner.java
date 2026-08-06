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
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientItemUseRegistry;
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
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的WorldQueryOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenWorldQueryOwner {
    private final BuilderScreen screen;

    BuilderScreenWorldQueryOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    boolean tryUseMainHandItemInAir(boolean shiftDown) {
            if (!screen.canUseMainHandItemInAir()) {
                return false;
            }
            InteractionTypes.InteractionTarget target = screen.cursorPicker.pickItemAirInteractionTarget();
            if (target == null || target.blockHit() == null) {
                return false;
            }
            screen.shapeController.clearShapeBuildSession();
            boolean localScreenOpened = RtsClientItemUseRegistry.tryOpenRegisteredScreen(null, shiftDown);
            if (!localScreenOpened) {
                screen.controller.useItemInAirWithToolSlot(
                        target.blockHit(),
                        screen.getSelectedToolSlot(),
                        target.rayOrigin(),
                        target.rayDir());
            }
            return true;
        }

    boolean mainHandItemIsBlock() {
            return screen.getMinecraft().player != null
                    && screen.getMinecraft().player.getMainHandItem().getItem() instanceof BlockItem;
        }

    boolean handleRangeCullingSelectionClick(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !screen.cullingManager.isManagementMode() || !screen.isWorldArea(mouseX, mouseY)) {
                return false;
            }
            return screen.handleRangeCullingWorldAction(mouseX, mouseY);
        }

    boolean handleRangeCullingWorldAction(double mouseX, double mouseY) {
            if (!screen.cullingManager.isManagementMode() || !screen.isWorldArea(mouseX, mouseY)) {
                return false;
            }
            return RtsCullingWorldInput.handleWorldAction(screen.cullingManager, screen.cursorPicker);
        }

    void blurSearchFocus() {
            boolean blurred = false;
            if (screen.searchBox != null && screen.searchBox.isFocused()) {
                screen.searchBox.setFocused(false);
                blurred = true;
            }
            if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
                screen.craftSearchBox.setFocused(false);
                blurred = true;
            }
            if (blurred) {
                screen.setFocused(null);
            }
        }

    void focusStorageSearchBox() {
            if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
                screen.craftSearchBox.setFocused(false);
            }
            if (screen.searchBox != null) {
                screen.searchBox.setFocused(true);
                screen.setFocused(screen.searchBox);
            }
        }

    void focusCraftSearchBox() {
            if (screen.searchBox != null && screen.searchBox.isFocused()) {
                screen.searchBox.setFocused(false);
            }
            if (screen.craftSearchBox != null) {
                screen.craftSearchBox.setFocused(true);
                screen.setFocused(screen.craftSearchBox);
            }
        }

    boolean isWorldArea(double mouseX, double mouseY) {
            return mouseY > TOP_H && !screen.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
        }

    int getBottomY() {
            return screen.bottomPanel.getBottomY();
        }

    int getFloatingPanelAvailableHeight(int panelY) {
            return Math.max(0, screen.getBottomY() - panelY - 6);
        }

    boolean isInsideBottomPanel(double mouseX, double mouseY) {
            return screen.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
        }

    boolean isSearchFocused() {
            return (screen.searchBox != null && screen.searchBox.isFocused())
                    || (screen.craftSearchBox != null && screen.craftSearchBox.isFocused())
                    || screen.aiChatPanel.isInputFocused();
        }

    int getSelectedToolSlot() {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return 0;
            }
            return Mth.clamp(screen.getMinecraft().player.getInventory().selected, 0, 8);
        }

    ItemStack getSelectedToolStack() {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return ItemStack.EMPTY;
            }
            return screen.getMinecraft().player.getInventory().getItem(screen.getSelectedToolSlot());
        }

    String resolveGuiBindingItemId(BlockHitResult hit) {
            if (hit == null || screen.getMinecraft() == null || screen.getMinecraft().level == null) {
                return "";
            }
            BlockPos pos = hit.getBlockPos();
            if (!screen.getMinecraft().level.hasChunkAt(pos)) {
                return "";
            }
            BlockState state = screen.getMinecraft().level.getBlockState(pos);
            ItemStack preview = state.getBlock().getCloneItemStack(screen.getMinecraft().level, pos, state);
            if (preview.isEmpty()) {
                preview = new ItemStack(state.getBlock().asItem());
            }
            if (preview.isEmpty() || preview.is(Items.AIR)) {
                return RtsAe2IconResolver.resolveGuiBindingIconItemId(screen.getMinecraft().level, pos, hit.getDirection(), "");
            }
            var id = BuiltInRegistries.ITEM.getKey(preview.getItem());
            return id == null ? "" : id.toString();
        }

    boolean canUseToolSlotShapeSource() {
            if (screen.controller.hasSelectedItem() || screen.controller.hasSelectedFluid() || screen.controller.isEmptyHandSelected()) {
                return false;
            }
            ItemStack stack = screen.getSelectedToolStack();
            return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
        }

    boolean tryAssignQuickSlotFromToolSelection(int pinIndex) {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return false;
            }
            if (screen.controller.isEmptyHandSelected()) {
                return false;
            }
            int slot = screen.bottomPanel.hoveredToolSlot >= 0 ? screen.bottomPanel.hoveredToolSlot : screen.getSelectedToolSlot();
            slot = Mth.clamp(slot, 0, 8);
            ItemStack stack = screen.getMinecraft().player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                return false;
            }
            screen.controller.assignQuickSlotFromToolItem(pinIndex, stack);
            return true;
        }

    void setSelectedToolSlot(int slot) {
            if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
                return;
            }
            screen.getMinecraft().player.getInventory().selected = Mth.clamp(slot, 0, 8);
        }

    boolean hasMainHandItem() {
            return screen.getMinecraft() != null
                    && screen.getMinecraft().player != null
                    && !screen.getMinecraft().player.getMainHandItem().isEmpty();
        }

    boolean isAltDownForInput() {
            return screen.isAltDown();
        }

}
