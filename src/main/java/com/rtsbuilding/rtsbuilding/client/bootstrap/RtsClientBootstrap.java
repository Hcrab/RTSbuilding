package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;

import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.pathfinding.PathfindingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.plugin.PluginModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.progression.ProgressionModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.remote.RemoteMenuModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import net.neoforged.neoforge.common.NeoForge;
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
            CompositionRoot.init();
            RtsClientKernel kernel = CompositionRoot.get().kernel();

            // 创建模块实例，同时注册到新旧两系统
            var reg = CompositionRoot.get().moduleManager();
            var cameraModule = new CameraModule();
            kernel.register(cameraModule);
            reg.registerInstance("camera", cameraModule);
            var storageModule = new StorageModule();
            kernel.register(storageModule);
            reg.registerInstance("storage", storageModule);
            var buildingModule = new BuildingModule();
            kernel.register(buildingModule);
            reg.registerInstance("building", buildingModule);
            var miningModule = new MiningModule();
            kernel.register(miningModule);
            reg.registerInstance("mining", miningModule);
            var workflowModule = new WorkflowModule();
            kernel.register(workflowModule);
            reg.registerInstance("workflow", workflowModule);
            var pluginModule = new PluginModule();
            kernel.register(pluginModule);
            reg.registerInstance("plugin", pluginModule);
            var progressionModule = new ProgressionModule();
            kernel.register(progressionModule);
            reg.registerInstance("progression", progressionModule);
            var remoteMenuModule = new RemoteMenuModule();
            kernel.register(remoteMenuModule);
            reg.registerInstance("remote_menu", remoteMenuModule);
            var pathfindingModule = new PathfindingModule();
            kernel.register(pathfindingModule);
            reg.registerInstance("pathfinding", pathfindingModule);

            // 初始化内核（创建 InputPipeline、RenderPipeline）
            kernel.initialize();
            RtsbuildingMod.LOGGER.info("RTS client2 kernel initialized with all modules");
        });
    }
}
