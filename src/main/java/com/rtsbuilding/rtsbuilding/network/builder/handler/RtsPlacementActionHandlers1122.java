package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceFluidPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlacePayload;
import net.minecraft.entity.Entity;
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
 * 放置、流体和远程交互的 1.12.2 服务端边界。
 *
 * <p>这里仅验证消息形状、RTS 会话和目标范围。物品、流体、实体、创造覆盖权及领地权限
 * 必须由服务层按服务端当前状态再次解析。</p>
 */
public final class RtsPlacementActionHandlers1122 {
    private static final String CAMERA = "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";
    private static final String REGISTRY = "com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";

    private RtsPlacementActionHandlers1122() {
    }

    public static final class Place implements IMessageHandler<C2SRtsPlacePayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsPlacePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() {
                @Override public void run(EntityPlayerMP player) {
                    if (!inRange(player, message.clickedPos())) return;
                    invokeService("placement", "placeSelected", new Class<?>[]{
                                    EntityPlayerMP.class, BlockPos.class, EnumFacing.class,
                                    double.class, double.class, double.class, byte.class, String.class,
                                    boolean.class, boolean.class, String.class, ItemStack.class,
                                    double.class, double.class, double.class, double.class, double.class,
                                    double.class, boolean.class, boolean.class},
                            player, message.clickedPos(), EnumFacing.byIndex(message.face()),
                            message.hitX(), message.hitY(), message.hitZ(), message.rotateSteps(),
                            message.statePreset(), message.forcePlace(), message.skipIfOccupied(),
                            message.itemId(), message.itemPrototype().copy(),
                            message.rayOriginX(), message.rayOriginY(), message.rayOriginZ(),
                            message.rayDirX(), message.rayDirY(), message.rayDirZ(),
                            message.quickBuild(), message.forceEmptyHand());
                }
            });
            return null;
        }
    }

    public static final class PlaceBatch implements IMessageHandler<C2SRtsPlaceBatchPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsPlaceBatchPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() {
                @Override public void run(EntityPlayerMP player) {
                    if (!allInRange(player, message.clickedPositions())) return;
                    invokeService("placement", "enqueuePlaceBatch", new Class<?>[]{
                                    EntityPlayerMP.class, List.class, EnumFacing.class,
                                    double.class, double.class, double.class, byte.class, String.class,
                                    boolean.class, boolean.class, boolean.class, String.class, ItemStack.class,
                                    double.class, double.class, double.class, double.class, double.class, double.class},
                            player, message.clickedPositions(), EnumFacing.byIndex(message.face()),
                            message.hitOffsetX(), message.hitOffsetY(), message.hitOffsetZ(),
                            message.rotateSteps(), message.statePreset(), message.forcePlace(),
                            message.skipIfOccupied(), message.overwriteExisting(), message.itemId(),
                            message.itemPrototype().copy(), message.rayOriginX(), message.rayOriginY(),
                            message.rayOriginZ(), message.rayDirX(), message.rayDirY(), message.rayDirZ());
                }
            });
            return null;
        }
    }

    public static final class PlaceFluid implements IMessageHandler<C2SRtsPlaceFluidPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsPlaceFluidPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() {
                @Override public void run(EntityPlayerMP player) {
                    if (!inRange(player, message.clickedPos())) return;
                    invokeService("fluid", "placeFluid", new Class<?>[]{
                                    EntityPlayerMP.class, BlockPos.class, EnumFacing.class,
                                    double.class, double.class, double.class, boolean.class, String.class,
                                    double.class, double.class, double.class, double.class, double.class, double.class},
                            player, message.clickedPos(), EnumFacing.byIndex(message.face()),
                            message.hitX(), message.hitY(), message.hitZ(), message.forcePlace(),
                            message.fluidId(), message.rayOriginX(), message.rayOriginY(),
                            message.rayOriginZ(), message.rayDirX(), message.rayDirY(), message.rayDirZ());
                }
            });
            return null;
        }
    }

    public static final class Interact implements IMessageHandler<C2SRtsInteractPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsInteractPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() {
                @Override public void run(EntityPlayerMP player) {
                    BlockPos authorityTarget = message.clickedPos();
                    if (message.entityId() >= 0) {
                        Entity entity = player.getServerWorld().getEntityByID(message.entityId());
                        if (entity == null) return;
                        authorityTarget = entity.getPosition();
                    }
                    if (!inRange(player, authorityTarget)) return;
                    invokeService("interaction", "interactTarget", new Class<?>[]{
                                    EntityPlayerMP.class, int.class, BlockPos.class, EnumFacing.class,
                                    double.class, double.class, double.class, byte.class, byte.class,
                                    String.class, double.class, double.class, double.class,
                                    double.class, double.class, double.class},
                            player, message.entityId(), message.clickedPos(),
                            EnumFacing.byIndex(message.face()), message.hitX(), message.hitY(),
                            message.hitZ(), message.sourceType(), message.toolSlot(), message.itemId(),
                            message.rayOriginX(), message.rayOriginY(), message.rayOriginZ(),
                            message.rayDirX(), message.rayDirY(), message.rayDirZ());
                }
            });
            return null;
        }
    }

    private interface Action {
        void run(EntityPlayerMP player);
    }

    private static void schedule(MessageContext context, final Action action) {
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

    private static boolean allInRange(EntityPlayerMP player, List<BlockPos> positions) {
        try {
            Method range = Class.forName(CAMERA).getMethod(
                    "isWithinActionRange", EntityPlayerMP.class, BlockPos.class);
            for (BlockPos pos : positions) {
                if (!Boolean.TRUE.equals(range.invoke(null, player, pos))) return false;
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 placement range adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Placement range validation failed", exception);
        }
    }

    private static boolean cameraBoolean(String name, Class<?>[] types, Object... arguments) {
        try {
            return Boolean.TRUE.equals(Class.forName(CAMERA).getMethod(name, types).invoke(null, arguments));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 camera authority adapter is unavailable: " + name, exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Camera authority check failed: " + name, exception);
        }
    }

    private static void invokeService(String accessor, String name, Class<?>[] types, Object... arguments) {
        try {
            Class<?> registryClass = Class.forName(REGISTRY);
            Object registry = registryClass.getMethod("getInstance").invoke(null);
            Object service = registryClass.getMethod(accessor).invoke(registry);
            service.getClass().getMethod(name, types).invoke(service, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 service adapter is unavailable: " + accessor + "." + name,
                    exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Service failed: " + accessor + "." + name, exception);
        }
    }

    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new IllegalStateException(message, cause);
    }
}
