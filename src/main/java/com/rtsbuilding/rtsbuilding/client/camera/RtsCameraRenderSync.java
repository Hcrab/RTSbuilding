package com.rtsbuilding.rtsbuilding.client.camera;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 在 1.12 RenderTick START 阶段、GameRenderer 使用视角前推进本帧视觉相机。 */
@SideOnly(Side.CLIENT)
public final class RtsCameraRenderSync {
    public static final RtsCameraRenderSync INSTANCE = new RtsCameraRenderSync();
    private static final Logger LOGGER = Logger.getLogger("rtsbuilding");

    private Object controller;
    private Method syncMethod;
    private boolean unavailable;

    private RtsCameraRenderSync() {
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || unavailable) return;
        try {
            resolveControllerIfNeeded();
            syncMethod.invoke(controller);
        } catch (ReflectiveOperationException failure) {
            unavailable = true;
            LOGGER.log(Level.SEVERE,
                    "RTS 视觉相机同步入口不可用；本次客户端会话将停止帧前同步", failure);
        }
    }

    private void resolveControllerIfNeeded() throws ReflectiveOperationException {
        if (controller != null && syncMethod != null) return;
        Class<?> controllerType = Class.forName(
                "com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController");
        controller = controllerType.getMethod("get").invoke(null);
        syncMethod = controllerType.getMethod("syncVisualCameraFrame");
    }
}
