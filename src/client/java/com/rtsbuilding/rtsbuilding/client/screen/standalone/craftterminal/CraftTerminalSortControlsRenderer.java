package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalSortField;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalSortControlsLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 合成终端排序控件的 Minecraft 渲染适配器。
 *
 * <p>它拥有两个稳定按钮的短时视觉动画和指针按下状态，但不执行排序、翻页或 网络请求。四种显示语义各自读取一张预处理完成的 24×24 纹理；生产路径不拼接
 * 字符与图标，也不缩放素材。短时动画只在完整状态纹理之间交叉淡化。
 */
public final class CraftTerminalSortControlsRenderer {
  private static final CraftTerminalUiAction[] ACTIONS = {
    CraftTerminalUiAction.SORT, CraftTerminalUiAction.SORT_DIRECTION
  };

  private final UiControlAnimationRegistry<CraftTerminalUiAction> animations =
      new UiControlAnimationRegistry<CraftTerminalUiAction>(SystemUiClock.INSTANCE, ACTIONS.length);
  private CraftTerminalUiAction pressedAction;

  public void render(
      GuiGraphics graphics,
      int left,
      int top,
      CraftTerminalSortControlsLayout.Geometry layout,
      CraftTerminalSortField field,
      boolean ascending,
      int mouseX,
      int mouseY) {
    if (graphics == null || layout == null || field == null) {
      throw new IllegalArgumentException("craft-terminal sort render state must be complete");
    }
    renderButton(
        graphics,
        left,
        top,
        layout.field,
        CraftTerminalUiAction.SORT,
        field,
        ascending,
        mouseX,
        mouseY);
    renderButton(
        graphics,
        left,
        top,
        layout.direction,
        CraftTerminalUiAction.SORT_DIRECTION,
        field,
        ascending,
        mouseX,
        mouseY);
  }

  /** 只捕获两个排序按钮的按下态；业务 Action 不在这里执行。 */
  public void press(CraftTerminalUiAction action) {
    this.pressedAction = isSortAction(action) ? action : null;
  }

  public void release() {
    this.pressedAction = null;
  }

  public void clear() {
    this.pressedAction = null;
    this.animations.clear();
  }

  private void renderButton(
      GuiGraphics graphics,
      int left,
      int top,
      UiRect bounds,
      CraftTerminalUiAction action,
      CraftTerminalSortField field,
      boolean ascending,
      int mouseX,
      int mouseY) {
    boolean hovered = bounds.contains(mouseX - left, mouseY - top);
    boolean pressed = hovered && this.pressedAction == action;
    UiControlAnimationState.Snapshot animation =
        this.animations.update(
            action,
            UiControlState.enabled().withInteraction(hovered, false, pressed),
            Config.isUiAnimationsEnabled());

    int x = left + (int) bounds.getX();
    int y = top + (int) bounds.getY();
    double pressedWeight = animation.press();
    double hoverWeight = (1.0D - pressedWeight) * animation.hover();
    double idleWeight = Math.max(0.0D, 1.0D - pressedWeight - hoverWeight);
    drawFrame(graphics, action, field, ascending, x, y, UiTextureState.INACTIVE, idleWeight);
    drawFrame(graphics, action, field, ascending, x, y, UiTextureState.HOVER, hoverWeight);
    drawFrame(graphics, action, field, ascending, x, y, UiTextureState.PRESSED, pressedWeight);
  }

  /** 三态母版以透明度交叉淡化，几何和命中区域始终保持 24×24。 */
  private static void drawFrame(
      GuiGraphics graphics,
      CraftTerminalUiAction action,
      CraftTerminalSortField field,
      boolean ascending,
      int x,
      int y,
      UiTextureState state,
      double weight) {
    if (weight <= 0.001D) {
      return;
    }
    ResourceLocation texture =
        action == CraftTerminalUiAction.SORT
            ? CraftTerminalSortButtonTextures.resolveField(field, state)
            : CraftTerminalSortButtonTextures.resolveDirection(ascending, state);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    graphics.setColor(1.0F, 1.0F, 1.0F, (float) Math.max(0.0D, Math.min(1.0D, weight)));
    try {
      graphics.blit(
          texture,
          x,
          y,
          0,
          0,
          CraftTerminalSortControlsLayout.BUTTON_WIDTH,
          CraftTerminalSortControlsLayout.BUTTON_HEIGHT,
          CraftTerminalSortControlsLayout.BUTTON_WIDTH,
          CraftTerminalSortControlsLayout.BUTTON_HEIGHT);
    } finally {
      graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
    }
  }

  private static boolean isSortAction(CraftTerminalUiAction action) {
    return action == CraftTerminalUiAction.SORT || action == CraftTerminalUiAction.SORT_DIRECTION;
  }
}
