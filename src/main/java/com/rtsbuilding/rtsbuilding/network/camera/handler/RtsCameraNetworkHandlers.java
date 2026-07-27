package com.rtsbuilding.rtsbuilding.network.camera.handler;

import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsCameraMovePayload;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraPayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 相机 C2S 适配层。网络线程只做解包和校验，所有世界状态修改都排入玩家所在
 * WorldServer 的主线程；玩家身份永远取自连接上下文，不接受客户端传入 UUID。
 */
public final class RtsCameraNetworkHandlers {
    private static final String MANAGER_CLASS = "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";

    private RtsCameraNetworkHandlers() {
    }

    public static final class ToggleHandler implements IMessageHandler<C2SRtsToggleCameraPayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsToggleCameraPayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    invokeManager("toggle", new Class<?>[]{EntityPlayerMP.class, boolean.class},
                            player, message.startAtPlayerHead());
                }
            });
            return null;
        }
    }

    public static final class MoveHandler implements IMessageHandler<C2SRtsCameraMovePayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsCameraMovePayload message, MessageContext context) {
            if (!message.isValid()) {
                return null;
            }
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    invokeManager("move", new Class<?>[]{EntityPlayerMP.class, float.class, float.class, float.class,
                                    float.class, float.class, float.class, float.class, float.class, int.class, boolean.class},
                            player, message.forward(), message.strafe(), message.vertical(), message.panX(), message.panY(),
                            message.rotateX(), message.rotateY(), message.scroll(), message.rotateSteps(), message.fast());
                }
            });
            return null;
        }
    }

    private static void invokeManager(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Class<?> manager = Class.forName(MANAGER_CLASS);
            Method method = manager.getMethod(methodName, parameterTypes);
            method.invoke(null, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 camera manager adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Camera manager rejected network input", cause);
        }
    }
}
