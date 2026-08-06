package com.rtsbuilding.rtsbuilding.client.bootstrap;


import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingMixinVerifier;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeTextureCache;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RtsClientModEvents {
    private RtsClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        RtsbuildingMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        RtsbuildingMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        event.enqueueWork(() -> {
            RtsCullingMixinVerifier.verifyOptionalRendererHooks();
            for (String error : UiThemeStorage.defaultStorage().loadAll(UiThemeRuntime.registry())) {
                RtsbuildingMod.LOGGER.warn("用户 UI 主题未加载：{}", error);
            }
            UiThemeStorage.defaultStorage().restoreActiveTheme();
        });
    }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RtsEntities.RTS_CAMERA_ENTITY.get(), RtsCameraEntityRenderer::new);
    }

    /** 资源包重载后释放旧 Palette 纹理，下一帧按当前主题重新烘焙。 */
    @SubscribeEvent
    public static void registerThemeReloadListener(final RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                UiThemeTextureCache.INSTANCE.clear());
    }
}
