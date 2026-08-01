package com.rtsbuilding.rtsbuilding.client.bootstrap;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsMovementModeRegistry;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;

public final class RtsClientModEvents {
    private RtsClientModEvents() {
    }

    public static void initialize() {
        // Initialise the built-in movement mode handlers and fire the registration
        // event so other mods can register custom movement modes.
        RtsMovementModeRegistry.init();
        RtsMovementModeRegistry.fireRegistrationEvent();

        EntityRendererRegistry.register(
                RtsEntities.RTS_CAMERA_ENTITY.get(), RtsCameraEntityRenderer::new);

        RtsbuildingMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        RtsbuildingMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
