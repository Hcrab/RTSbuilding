package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.blueprint.BlueprintModule;
import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.module.overlay.OverlayModule;
import com.rtsbuilding.rtsbuilding.client.module.plugin.PluginModule;
import com.rtsbuilding.rtsbuilding.client.module.progression.ProgressionModule;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 客户端引导——初始化 TLK 内核并注册所有 Feature Module。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsClientBootstrap {

    private RtsClientBootstrap() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RtsEntities.RTS_CAMERA_ENTITY.get(), RtsCameraEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        RtsKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            RtsClientKernel kernel = RtsClientKernel.get();

            // 注册所有 Feature Module
            kernel.register(new CameraModule());
            kernel.register(new StorageModule());
            kernel.register(new BuildingModule());
            kernel.register(new MiningModule());
            kernel.register(new BlueprintModule());
            kernel.register(new WorkflowModule());
            kernel.register(new PluginModule());
            kernel.register(new ProgressionModule());
            kernel.register(new OverlayModule());

            // 初始化内核（创建 InputPipeline、RenderPipeline）
            kernel.initialize();
            RtsbuildingMod.LOGGER.info("RTS client2 kernel initialized with all modules");
        });
    }
}
