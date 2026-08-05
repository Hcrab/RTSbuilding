package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraRenderSync;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.platform.event.LegacyEventRegistrar;
import net.minecraft.client.Minecraft;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Forge 1.12 客户端注册入口。
 *
 * <p>既可由客户端 proxy 在 preInit 调用 {@link #register()}，也会在仅客户端触发的
 * {@link ModelRegistryEvent} 中兜底注册。公共模组入口无需静态引用本类。</p>
 */
@SideOnly(Side.CLIENT)
public final class RtsClientModEvents {
    private static final Logger LOGGER = Logger.getLogger("rtsbuilding");
    private static boolean registered;

    private RtsClientModEvents() {
    }

    public static synchronized void register() {
        if (registered) return;
        ClientKeyMappings.register();
        RenderingRegistry.registerEntityRenderingHandler(
                RtsCameraEntity.class,
                new RtsCameraEntityRenderer(net.minecraft.client.renderer.entity.RenderManager.instance));
        LegacyEventRegistrar.registerInstance(RtsCameraRenderSync.INSTANCE);
        registerLegacyClientSubscribers();
        initializeMovementModes();
        registered = true;

        Minecraft minecraft = Minecraft.getMinecraft();
        String username = minecraft.getSession() == null
                ? "unknown" : minecraft.getSession().getUsername();
        LOGGER.info("RTSBuilding 客户端初始化完成，当前用户 " + username);
    }

    private static void registerLegacyClientSubscribers() {
        String prefix = "com.rtsbuilding.rtsbuilding.client.";
        String[] subscribers = {
                "compat.RtsClientOnboardingReminder",
                "compat.RtsGuiCompatMatrixProbe",
                "compat.RtsGuiCompatProbe",
                "compat.RtsClientStartupSmoke",
                "event.ClientGuiEventHandler",
                "input.ClientInputHandler",
                "input.RtsClientInputGate",
                "input.RtsClientInputEvents1122",
                "plugin.RtsPluginInventoryScreenEvents",
                "rendering.RtsVisualOverlayRenderer"
        };
        for (String subscriber : subscribers) {
            LegacyEventRegistrar.registerByName(prefix + subscriber);
        }
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
