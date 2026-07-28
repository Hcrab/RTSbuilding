package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsOrientBlockPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsRotateBlockPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetModePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSubmitPendingPayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 轻量放置控制的 C2S 边界。玩家、会话和位置权限均由服务端连接与相机会话决定；
 * 客户端从不提交玩家 UUID、方块状态或可直接执行的任意属性集合。
 */
public final class RtsPlacementControlHandlers {
    private static final String CAMERA_MANAGER =
            "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";
    private static final String SERVICE_REGISTRY =
            "com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";
    private static final String BUILDER_MODE =
            "com.rtsbuilding.rtsbuilding.common.build.BuilderMode";

    private RtsPlacementControlHandlers() {
    }

    public static final class SetModeHandler implements IMessageHandler<C2SRtsSetModePayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsSetModePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() {
                    if (isCameraActive(player)) setMode(player, message.mode());
                }
            });
            return null;
        }
    }

    public static final class RotateHandler implements IMessageHandler<C2SRtsRotateBlockPayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsRotateBlockPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() {
                    if (isWithinActionRange(player, message.pos())) {
                        Object placement = service("placement");
                        invoke(placement, "rotateBlock", new Class<?>[]{EntityPlayerMP.class, BlockPos.class},
                                player, message.pos());
                    }
                }
            });
            return null;
        }
    }

    public static final class OrientHandler implements IMessageHandler<C2SRtsOrientBlockPayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsOrientBlockPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() {
                    if (isWithinActionRange(player, message.pos())) {
                        Object placement = service("placement");
                        invoke(placement, "rotateBlockStep",
                                new Class<?>[]{EntityPlayerMP.class, BlockPos.class, EnumFacing.class, int.class},
                                player, message.pos(), EnumFacing.byIndex(message.axisDirection()),
                                (int) message.quarterTurns());
                    }
                }
            });
            return null;
        }
    }

    public static final class SubmitPendingHandler implements IMessageHandler<C2SRtsSubmitPendingPayload, IMessage> {
        @Override
        public IMessage onMessage(C2SRtsSubmitPendingPayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() {
                    if (isCameraActive(player)) {
                        Object placement = service("placement");
                        invoke(placement, "submitPendingPlacement", new Class<?>[]{EntityPlayerMP.class}, player);
                    }
                }
            });
            return null;
        }
    }

    private static void setMode(EntityPlayerMP player, int modeId) {
        try {
            Class<?> modeClass = Class.forName(BUILDER_MODE);
            Object[] modes = modeClass.getEnumConstants();
            if (modes == null || modeId < 0 || modeId >= modes.length) return;
            Object binding = service("binding");
            invoke(binding, "setMode", new Class<?>[]{EntityPlayerMP.class, modeClass}, player, modes[modeId]);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("1.12.2 builder mode adapter is unavailable", exception);
        }
    }

    private static boolean isCameraActive(EntityPlayerMP player) {
        return invokeCameraBoolean("isActive", new Class<?>[]{EntityPlayerMP.class}, player);
    }

    private static boolean isWithinActionRange(EntityPlayerMP player, BlockPos pos) {
        return invokeCameraBoolean("isWithinActionRange",
                new Class<?>[]{EntityPlayerMP.class, BlockPos.class}, player, pos);
    }

    private static boolean invokeCameraBoolean(String methodName, Class<?>[] types, Object... arguments) {
        try {
            Class<?> camera = Class.forName(CAMERA_MANAGER);
            return Boolean.TRUE.equals(camera.getMethod(methodName, types).invoke(null, arguments));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 camera authority adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Camera authority check failed", exception);
        }
    }

    private static Object service(String accessor) {
        try {
            Class<?> registryClass = Class.forName(SERVICE_REGISTRY);
            Object registry = registryClass.getMethod("getInstance").invoke(null);
            return registryClass.getMethod(accessor).invoke(registry);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 service registry adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Service lookup failed", exception);
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] types, Object... arguments) {
        try {
            Method method = target.getClass().getMethod(methodName, types);
            return method.invoke(target, arguments);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 placement service adapter is unavailable: " + methodName,
                    exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Placement service failed: " + methodName, exception);
        }
    }

    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new IllegalStateException(message, cause);
    }
}
