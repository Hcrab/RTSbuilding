package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsMovementModeRegistry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsMenuTypes;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

public final class RtsClientModEvents {
  private RtsClientModEvents() {}

  public static void initialize() {
    // Initialise the built-in movement mode handlers and fire the registration
    // event so other mods can register custom movement modes.
    RtsMovementModeRegistry.init();
    RtsMovementModeRegistry.fireRegistrationEvent();

    for (String error : UiThemeStorage.defaultStorage().loadAll(UiThemeRuntime.registry())) {
      RtsbuildingMod.LOGGER.warn("用户 UI 主题未加载：{}", error);
    }
    UiThemeStorage.defaultStorage().restoreActiveTheme();

    EntityRendererRegistry.register(
        RtsEntities.RTS_CAMERA_ENTITY.get(), RtsCameraEntityRenderer::new);
    MenuScreens.register(RtsMenuTypes.RTS_CRAFT_TERMINAL.get(), RtsCraftTerminalScreen::new);

    RtsbuildingMod.LOGGER.info("HELLO FROM CLIENT SETUP");
    RtsbuildingMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
  }
}
