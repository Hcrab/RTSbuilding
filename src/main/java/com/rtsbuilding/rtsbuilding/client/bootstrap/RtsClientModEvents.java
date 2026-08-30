package com.rtsbuilding.rtsbuilding.client.bootstrap;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsMovementModeRegistry;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsMenuTypes;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeTextureCache;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsClientModEvents {
    private RtsClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Initialise the built-in movement mode handlers and fire the registration
        // event so other mods can register custom movement modes.
        RtsMovementModeRegistry.init();
        RtsMovementModeRegistry.fireRegistrationEvent();

        event.enqueueWork(() -> {
            for (String error : UiThemeStorage.defaultStorage().loadAll(UiThemeRuntime.registry())) {
                RtsbuildingMod.LOGGER.warn("用户 UI 主题未加载：{}", error);
            }
            UiThemeStorage.defaultStorage().restoreActiveTheme();
        });

        RtsbuildingMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        RtsbuildingMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(RtsMenuTypes.RTS_CRAFT_TERMINAL.get(), RtsCraftTerminalScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RtsEntities.RTS_CAMERA_ENTITY.get(), RtsCameraEntityRenderer::new);
    }

    /** F3+T 或资源包切换后释放旧 Palette 帧；下一次绘制会从新资源重新烘焙。 */
    @SubscribeEvent
    public static void registerThemeReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                UiThemeTextureCache.INSTANCE.clear());
    }
}
