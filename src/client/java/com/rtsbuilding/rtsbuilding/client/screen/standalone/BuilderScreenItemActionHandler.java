package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.compat.RtsClientItemUseRegistry;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;

/**
 * 主操作中与选中物品、工具槽和空手有关的动作执行器。
 *
 * <p>本类保留“自然交互优先、形状放置、背包强制走放置、工具槽回放与空手交互”的现有 行为。它不决定蓝图、剔除、储存绑定或捕获优先级，也不拥有选择状态；这些仍由
 * PrimaryActionRouter 和 Controller 管理。
 */
final class BuilderScreenItemActionHandler {
  private final BuilderScreenPrimaryActionHost host;
  private final ClientRtsController controller;
  private final ScreenShapeController shapeController;

  BuilderScreenItemActionHandler(
      BuilderScreenPrimaryActionHost host,
      ClientRtsController controller,
      ScreenShapeController shapeController) {
    this.host = host;
    this.controller = controller;
    this.shapeController = shapeController;
  }

  boolean runSelectedItem(
      InteractionTypes.InteractionTarget target,
      boolean forcePlace,
      boolean rangeDestroyMode,
      double mouseY) {
    boolean forceBackpackPlacement =
        RtsBackpackCompat.isBackpackItem(this.controller.getSelectedItemPreview());
    if (target.isEntityTarget() && !forceBackpackPlacement) {
      this.shapeController.clearShapeBuildSession();
      this.controller.interactEntityWithPinnedItem(
          target.entityId(),
          target.hitLocation(),
          this.controller.getSelectedItemId(),
          target.rayOrigin(),
          target.rayDir());
    } else if (target.blockHit() != null) {
      if (!forceBackpackPlacement
          && !forcePlace
          && !rangeDestroyMode
          && this.controller.getPlacementStatePreset().isBlank()
          && this.controller.getBuildShape() == BuildShape.BLOCK) {
        this.shapeController.clearShapeBuildSession();
        this.controller.interactBlockWithPinnedItem(
            target.blockHit(),
            this.controller.getSelectedItemId(),
            target.rayOrigin(),
            target.rayDir());
        return true;
      }
      if (rangeDestroyMode) {
        this.controller.placeSelected(
            target.blockHit(), forcePlace, target.rayOrigin(), target.rayDir());
        this.shapeController.recordSinglePlacementForUndo(
            target.blockHit(),
            InteractionTypes.PlacementReplayKind.PIN_ITEM,
            this.controller.getSelectedItemId(),
            -1);
        return true;
      }
      this.shapeController.placeWithShape(
          target.blockHit(),
          forcePlace || forceBackpackPlacement,
          target.rayOrigin(),
          target.rayDir(),
          mouseY,
          false,
          InteractionTypes.PlacementReplayKind.PIN_ITEM,
          this.controller.getSelectedItemId(),
          -1);
    }
    return true;
  }

  boolean runToolOrEmptyHand(InteractionTypes.InteractionTarget target, boolean forcePlace) {
    this.shapeController.clearShapeBuildSession();
    if (this.controller.isEmptyHandSelected()) {
      if (target.isEntityTarget()) {
        this.controller.interactEntityEmpty(
            target.entityId(), target.hitLocation(), target.rayOrigin(), target.rayDir());
      } else if (target.blockHit() != null) {
        this.controller.interactEmpty(target.blockHit(), target.rayOrigin(), target.rayDir());
      }
      return true;
    }
    if (target.isEntityTarget()) {
      if (host.hasMainHandItem()) {
        this.controller.interactEntityWithToolSlot(
            target.entityId(),
            target.hitLocation(),
            host.selectedToolSlot(),
            target.rayOrigin(),
            target.rayDir());
      }
    } else if (target.blockHit() != null) {
      if (host.hasMainHandItem()) {
        if (forcePlace || !this.controller.getPlacementStatePreset().isBlank()) {
          // R 预选状态只能由放置包携带；普通交互包会重新按命中点计算朝向。
          this.controller.placeSelected(
              target.blockHit(), forcePlace, target.rayOrigin(), target.rayDir());
          this.shapeController.recordSinglePlacementForUndo(
              target.blockHit(),
              InteractionTypes.PlacementReplayKind.TOOL_SLOT,
              "",
              host.selectedToolSlot());
        } else {
          boolean localScreenOpened =
              RtsClientItemUseRegistry.tryOpenRegisteredScreen(target.blockHit(), forcePlace);
          this.controller.interactBlockWithToolSlot(
              target.blockHit(),
              host.selectedToolSlot(),
              target.rayOrigin(),
              target.rayDir(),
              localScreenOpened);
        }
      } else {
        this.controller.interactEmpty(target.blockHit(), target.rayOrigin(), target.rayDir());
      }
    }
    return true;
  }
}
