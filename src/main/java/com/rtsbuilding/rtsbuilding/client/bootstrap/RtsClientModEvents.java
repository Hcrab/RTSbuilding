package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraRenderSync;
import com.rtsbuilding.rtsbuilding.client.rendering.RtsVisualOverlayRenderer;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Forge 1.12 客户端注册入口。
 *
 * <p>既可由客户端 proxy 在 preInit 调用 {@link #register()}，也会在仅客户端触发的
 * {@link ModelRegistryEvent} 中兜底注册。公共模组入口无需静态引用本类。</p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = "rtsbuilding", value = Side.CLIENT)
public final class RtsClientModEvents {
    private static final Logger LOGGER = Logger.getLogger("rtsbuilding");
    private static boolean registered;

    private RtsClientModEvents() {
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        register();
    }

    public static synchronized void register() {
        if (registered) return;
        ClientKeyMappings.register();
        RenderingRegistry.registerEntityRenderingHandler(
                RtsCameraEntity.class, RtsCameraEntityRenderer::new);
        MinecraftForge.EVENT_BUS.register(RtsCameraRenderSync.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RtsVisualOverlayRenderer.class);
        initializeMovementModes();
        registered = true;

        Minecraft minecraft = Minecraft.getMinecraft();
        String username = minecraft.getSession() == null
                ? "unknown" : minecraft.getSession().getUsername();
        LOGGER.info("RTSBuilding 客户端初始化完成，当前用户 " + username);
    }

    private static void initializeMovementModes() {
        try {
            Class<?> registry = Class.forName(
                    "com.rtsbuilding.rtsbuilding.client.pathfinding.RtsMovementModeRegistry");
            Method init = registry.getMethod("init");
            Method fireRegistrationEvent = registry.getMethod("fireRegistrationEvent");
            init.invoke(null);
            fireRegistrationEvent.invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("初始化 RTS 客户端移动模式失败", failure);
        }
    }
}
