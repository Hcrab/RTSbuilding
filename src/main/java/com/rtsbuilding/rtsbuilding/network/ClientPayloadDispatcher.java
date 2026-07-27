package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * S2C 的专服安全分派器。
 *
 * <p>公共注册路径不链接任何 net.minecraft.client 或项目 client 包。只有消息真正
 * 到达客户端并切回客户端主线程后，才按类名加载客户端控制器。这样专用服务端在
 * 注册客户端消息处理器时不会触发客户端类验证。</p>
 */
public final class ClientPayloadDispatcher {
    private static final String CLIENT_CONTROLLER =
            "com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController";
    private static final String BLUEPRINT_PANEL =
            "com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel";
    private static final String MINECRAFT_CLIENT = "net.minecraft.client.Minecraft";
    private static final String BUILDER_SCREEN =
            "com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen";

    private ClientPayloadDispatcher() {
    }

    public static final class CameraStateHandler implements IMessageHandler<S2CRtsCameraStatePayload, IMessage> {
        @Override
        public IMessage onMessage(final S2CRtsCameraStatePayload message, MessageContext context) {
            schedule(context, new Runnable() {
                @Override
                public void run() {
                    invokeController("applyServerCameraState", S2CRtsCameraStatePayload.class, message);
                }
            });
            return null;
        }
    }

    public static final class CameraAnchorHandler implements IMessageHandler<S2CRtsCameraAnchorPayload, IMessage> {
        @Override
        public IMessage onMessage(final S2CRtsCameraAnchorPayload message, MessageContext context) {
            schedule(context, new Runnable() {
                @Override
                public void run() {
                    invokeController("applyServerCameraAnchor", S2CRtsCameraAnchorPayload.class, message);
                }
            });
            return null;
        }
    }

    public static final class BlueprintStatusHandler implements IMessageHandler<S2CBlueprintStatusPayload, IMessage> {
        @Override
        public IMessage onMessage(final S2CBlueprintStatusPayload message, MessageContext context) {
            schedule(context, new Runnable() {
                @Override
                public void run() {
                    invokeStatic(BLUEPRINT_PANEL, "setStatus",
                            new Class<?>[]{byte.class, String.class, String.class},
                            message.status(), message.messageKey(), message.detail());
                }
            });
            return null;
        }
    }

    public static final class BlueprintResumeScanHandler
            implements IMessageHandler<S2CRtsBlueprintResumeScanPayload, IMessage> {
        @Override
        public IMessage onMessage(final S2CRtsBlueprintResumeScanPayload message, MessageContext context) {
            schedule(context, new Runnable() {
                @Override
                public void run() {
                    openBlueprintResumePanel(message);
                }
            });
            return null;
        }
    }

    /** 仅在客户端消息真正到达后，才按字符串加载客户端控制器。 */
    public static final class StoragePageHandler implements IMessageHandler<S2CRtsStoragePagePayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsStoragePagePayload message, MessageContext context) {
            schedule(context, new Runnable() {@Override public void run() {
                invokeController("applyStoragePage", S2CRtsStoragePagePayload.class, message);
            }});
            return null;
        }
    }

    public static final class StorageDirtyHandler implements IMessageHandler<S2CRtsStorageDirtyPayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsStorageDirtyPayload message, MessageContext context) {
            schedule(context, new Runnable() {@Override public void run() {
                invokeController("applyStorageDirty", S2CRtsStorageDirtyPayload.class, message);
            }});
            return null;
        }
    }

    public static final class RemoteMenuHintHandler
            implements IMessageHandler<S2CRtsRemoteMenuHintPayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsRemoteMenuHintPayload message, MessageContext context) {
            schedule(context, new Runnable() {@Override public void run() {
                invokeController("applyRemoteMenuHint", S2CRtsRemoteMenuHintPayload.class, message);
            }});
            return null;
        }
    }

    private static void schedule(MessageContext context, Runnable task) {
        IThreadListener thread = FMLCommonHandler.instance().getWorldThread(context.netHandler);
        thread.addScheduledTask(task);
    }

    private static void invokeController(String methodName, Class<?> payloadType, Object payload) {
        try {
            Class<?> controllerClass = Class.forName(CLIENT_CONTROLLER);
            Object controller = controllerClass.getMethod("get").invoke(null);
            Method method = controllerClass.getMethod(methodName, payloadType);
            method.invoke(controller, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 client camera adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Client camera handler failed", cause);
        }
    }

    private static void invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... values) {
        try {
            Class<?> target = Class.forName(className);
            target.getMethod(methodName, parameterTypes).invoke(null, values);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 client adapter is unavailable: " + className, exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Client static handler failed", exception);
        }
    }

    private static void openBlueprintResumePanel(S2CRtsBlueprintResumeScanPayload payload) {
        try {
            Class<?> minecraftClass = Class.forName(MINECRAFT_CLIENT);
            Object minecraft = minecraftClass.getMethod("getMinecraft").invoke(null);
            Object screen = minecraftClass.getField("currentScreen").get(minecraft);
            Class<?> builderScreenClass = Class.forName(BUILDER_SCREEN);
            if (!builderScreenClass.isInstance(screen)) return;
            Object panel = builderScreenClass.getMethod("getBlueprintResumePanel").invoke(screen);
            panel.getClass().getMethod("openWithData", S2CRtsBlueprintResumeScanPayload.class).invoke(panel, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 blueprint resume UI adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Blueprint resume UI failed", exception);
        }
    }

    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new IllegalStateException(message, cause);
    }
}
