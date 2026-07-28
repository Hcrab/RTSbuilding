package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltiminePayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 1.12.2 挖掘消息的服务端边界。
 *
 * <p>客户端提交的工具栈保留完整 NBT，但它只用于描述玩家当时选择的工具；挖掘服务仍必须从
 * 服务端会话/储存重新租借真实工具，并把耐久、能量和能力数据的变化归还原来源。</p>
 */
public final class RtsMiningHandlers1122 {
    private static final String CAMERA_MANAGER =
            "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";
    private static final String SERVICE_REGISTRY =
            "com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";

    private RtsMiningHandlers1122() {
    }

    public static final class Mine implements IMessageHandler<C2SRtsMinePayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsMinePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    if (!inRange(player, message.pos())) return;
                    invokeMining("mine", new Class<?>[]{
                                    EntityPlayerMP.class, BlockPos.class, EnumFacing.class, boolean.class,
                                    byte.class, String.class, ItemStack.class, boolean.class, boolean.class},
                            player, message.pos(), EnumFacing.byIndex(message.face()), message.start(),
                            message.toolSlot(), message.toolItemId(), message.toolPrototype().copy(),
                            message.allowPlacedBlockRecovery(), message.toolProtectionEnabled());
                }
            });
            return null;
        }
    }

    public static final class Ultimine implements IMessageHandler<C2SRtsUltiminePayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsUltiminePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    if (!inRange(player, message.pos())) return;
                    invokeMining("startUltimine", new Class<?>[]{
                                    EntityPlayerMP.class, BlockPos.class, EnumFacing.class, byte.class,
                                    String.class, ItemStack.class, int.class, byte.class, boolean.class},
                            player, message.pos(), EnumFacing.byIndex(message.face()), message.toolSlot(),
                            message.toolItemId(), message.toolPrototype().copy(), message.limit(),
                            message.mode(), message.toolProtectionEnabled());
                }
            });
            return null;
        }
    }

    public static final class AreaMine implements IMessageHandler<C2SRtsAreaMinePayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsAreaMinePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    if (!allBoxCornersInRange(player, message)) return;
                    invokeMining("areaMine", new Class<?>[]{
                                    EntityPlayerMP.class, int.class, int.class, int.class, int.class,
                                    int.class, int.class, byte.class, String.class, ItemStack.class,
                                    byte.class, byte.class, boolean.class},
                            player, message.minX(), message.maxX(), message.minY(), message.maxY(),
                            message.minZ(), message.maxZ(), message.toolSlot(), message.toolItemId(),
                            message.toolPrototype().copy(), message.shapeType(), message.fillType(),
                            message.toolProtectionEnabled());
                }
            });
            return null;
        }
    }

    public static final class AreaDestroy implements IMessageHandler<C2SRtsAreaDestroyPayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsAreaDestroyPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    if (!allPositionsInRange(player, message.positions())) return;
                    invokeMining("areaDestroy", new Class<?>[]{
                                    EntityPlayerMP.class, List.class, byte.class, String.class,
                                    ItemStack.class, boolean.class},
                            player, message.positions(), message.toolSlot(), message.toolItemId(),
                            message.toolPrototype().copy(), message.toolProtectionEnabled());
                }
            });
            return null;
        }
    }

    private interface ServerAction {
        void run(EntityPlayerMP player);
    }

    private static void schedule(MessageContext context, final ServerAction action) {
        final EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(new Runnable() {
            @Override public void run() {
                if (cameraBoolean("isActive", new Class<?>[]{EntityPlayerMP.class}, player)) {
                    action.run(player);
                }
            }
        });
    }

    private static boolean inRange(EntityPlayerMP player, BlockPos pos) {
        return cameraBoolean("isWithinActionRange",
                new Class<?>[]{EntityPlayerMP.class, BlockPos.class}, player, pos);
    }

    private static boolean allBoxCornersInRange(EntityPlayerMP player, C2SRtsAreaMinePayload message) {
        int[] xs = {message.minX(), message.maxX()};
        int[] ys = {message.minY(), message.maxY()};
        int[] zs = {message.minZ(), message.maxZ()};
        for (int x : xs) for (int y : ys) for (int z : zs) {
            if (!inRange(player, new BlockPos(x, y, z))) return false;
        }
        return true;
    }

    private static boolean allPositionsInRange(EntityPlayerMP player, List<BlockPos> positions) {
        try {
            Class<?> camera = Class.forName(CAMERA_MANAGER);
            Method method = camera.getMethod("isWithinActionRange", EntityPlayerMP.class, BlockPos.class);
            for (BlockPos pos : positions) {
                if (!Boolean.TRUE.equals(method.invoke(null, player, pos))) return false;
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 camera range adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Camera range validation failed", exception);
        }
    }

    private static boolean cameraBoolean(String methodName, Class<?>[] types, Object... arguments) {
        try {
            Class<?> camera = Class.forName(CAMERA_MANAGER);
            return Boolean.TRUE.equals(camera.getMethod(methodName, types).invoke(null, arguments));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 camera authority adapter is unavailable: " + methodName,
                    exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Camera authority check failed: " + methodName, exception);
        }
    }

    private static void invokeMining(String methodName, Class<?>[] types, Object... arguments) {
        try {
            Class<?> registryClass = Class.forName(SERVICE_REGISTRY);
            Object registry = registryClass.getMethod("getInstance").invoke(null);
            Object mining = registryClass.getMethod("mining").invoke(registry);
            mining.getClass().getMethod(methodName, types).invoke(mining, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 mining service adapter is unavailable: " + methodName,
                    exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Mining service failed: " + methodName, exception);
        }
    }

    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new IllegalStateException(message, cause);
    }
}
