package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.craft.RtsCraftQuantityWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import net.minecraft.world.phys.BlockHitResult;

/**
 * BuilderScreen 的WindowActionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。
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

  boolean isQuickBuildSmartFillMode() {
    return screen.isQuickBuildOpen() && screen.quickBuildPanel.isSmartFillMode();
  }

  ShapeDataRecords.GhostPreview getSmartFillGhostPreview() {
    return screen.quickBuildPanel.smartFillGhostPreview();
  }

  boolean handleQuickBuildSmartFillClick() {
    if (!isQuickBuildSmartFillMode()) {
      return false;
    }
    BlockHitResult hit = screen.cursorPicker.pickBlockHit();
    if (hit != null) {
      screen.quickBuildPanel.submitOrAnchorSmartFill(
          hit,
          screen.cursorPicker.currentRayOrigin(),
          screen.cursorPicker.computeCursorRayDirection());
    }
    return true;
  }

  boolean cancelQuickBuildSmartFillAnchor() {
    return screen.isQuickBuildOpen() && screen.quickBuildPanel.cancelSmartFillAnchor();
  }

  boolean isQuickBuildRangeDestroyChainMode() {
    return screen.isQuickBuildOpen() && screen.quickBuildPanel.isRangeDestroyChainMode();
  }

  boolean isQuickBuildConvenienceDestroyMode() {
    return screen.isQuickBuildOpen() && screen.quickBuildPanel.isConvenienceDestroyMode();
  }

  ShapeDataRecords.GhostPreview getConvenienceDestroyGhostPreview() {
    return screen.quickBuildPanel.convenienceGhostPreview();
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
    if (screen.isQuickBuildSmartFillMode()) {
      return screen.text("screen.rtsbuilding.quick_build.mode_smart_fill");
    }
    if (screen.isQuickBuildConvenienceDestroyMode()) {
      return screen.quickBuildPanel.getConvenienceToolLabel();
    }
    if (screen.isQuickBuildRangeDestroyChainMode()) {
      return screen.text("screen.rtsbuilding.shape.chain");
    }
    return screen.shapeLabel(screen.controller.getBuildShape());
  }

  boolean handleQuickBuildRangeDestroyClick(double mouseX, double mouseY) {
    return handleQuickBuildRangeDestroyClick(mouseX, mouseY, RtsTraceInputKind.UNKNOWN);
  }

  boolean handleQuickBuildRangeDestroyClick(
      double mouseX, double mouseY, RtsTraceInputKind inputKind) {
    if (!screen.isQuickBuildRangeDestroyMode()
        || screen.isQuickBuildRangeDestroyChainMode()
        || !screen.isWorldArea(mouseX, mouseY)) {
      return false;
    }
    if (screen.isQuickBuildConvenienceDestroyMode()) {
      InteractionTypes.InteractionTarget target = screen.cursorPicker.pickInteractionTarget(false);
      return target == null
          || target.blockHit() == null
          || screen.quickBuildPanel.submitConvenienceDestroy(target.blockHit(), inputKind);
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
      return screen.shapeController.tryConfirmPendingRangeDestroy(inputKind);
    }
    InteractionTypes.InteractionTarget target = screen.cursorPicker.pickInteractionTarget(false);
    if (target != null && target.blockHit() != null) {
      screen.shapeController.selectRangeDestroyShape(
          target.blockHit(), mouseY, target.rayDir(), inputKind);
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
    RtsCraftQuantityWindowPanel.Request request =
        screen.craftQuantityWindowPanel.consumePendingRequest();
    if (request != null) {
      screen.controller.craftRecipeToLinked(request.recipeId(), request.craftCount());
    }
  }

  boolean handleFloatingWindowClick(double mouseX, double mouseY, int button) {
    return screen.floatingWindowLayer.mouseClicked(mouseX, mouseY, button);
  }

  boolean handleFloatingWindowDrag(
      double mouseX, double mouseY, int button, double dragX, double dragY) {
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
