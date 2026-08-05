package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.compat.RtsGuiCompatMatrixSync;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceFluidPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlacePayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsRemoteMenuResultPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteInteractionResult;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
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
                    List<BlockPos> positions = RtsPositionBatchAssembler1122.accept(
                            player.getUniqueID(), "place_batch", message.submissionId(),
                            message.chunkIndex(), message.chunkCount(), message.totalPositions(),
                            C2SRtsPlaceBatchPayload.MAX_POSITIONS, message.metadataSignature(),
                            message.clickedPositions());
                    if (positions == null || !allInRange(player, positions)) return;
                    invokeService("placement", "enqueuePlaceBatch", new Class<?>[]{
                                    EntityPlayerMP.class, List.class, EnumFacing.class,
                                    double.class, double.class, double.class, byte.class, String.class,
                                    boolean.class, boolean.class, boolean.class, String.class, ItemStack.class,
                                    double.class, double.class, double.class, double.class, double.class, double.class},
                            player, positions, EnumFacing.byIndex(message.face()),
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
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() {
                @Override public void run() {
                    // 不在 Netty 线程读取世界状态；诊断上下文和交互都在服务端计划任务中处理。
                    logReceived(player, message);
                    if (!cameraBoolean("isActive", new Class<?>[]{EntityPlayerMP.class}, player)) {
                        sendTerminal(player, message.traceId(), RtsRemoteInteractionResult.rejected(
                                S2CRtsRemoteMenuResultPayload.REASON_RTS_INACTIVE));
                        return;
                    }
                    BlockPos authorityTarget = message.clickedPos();
                    if (message.entityId() >= 0) {
                        Entity entity = player.getServerForPlayer().getEntityByID(message.entityId());
                        if (entity == null) {
                            sendTerminal(player, message.traceId(), RtsRemoteInteractionResult.rejected(
                                    S2CRtsRemoteMenuResultPayload.REASON_TARGET_MISSING));
                            return;
                        }
                        authorityTarget = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat
                                .blockPosition(entity);
                    }
                    if (!inRange(player, authorityTarget)) {
                        sendTerminal(player, message.traceId(), RtsRemoteInteractionResult.rejected(
                                S2CRtsRemoteMenuResultPayload.REASON_OUT_OF_RANGE));
                        return;
                    }
                    RtsRemoteMenuCompat.beginServerRemoteMenuOpen(player, message.traceId());
                    try {
                        RtsRemoteInteractionResult result = (RtsRemoteInteractionResult) invokeService(
                                "interaction", "interactTarget", new Class<?>[]{
                                        EntityPlayerMP.class, int.class, BlockPos.class, EnumFacing.class,
                                        double.class, double.class, double.class, byte.class, byte.class,
                                        String.class, double.class, double.class, double.class,
                                        double.class, double.class, double.class, long.class},
                                player, message.entityId(), message.clickedPos(),
                                EnumFacing.byIndex(message.face()), message.hitX(), message.hitY(),
                                message.hitZ(), message.sourceType(), message.toolSlot(), message.itemId(),
                                message.rayOriginX(), message.rayOriginY(), message.rayOriginZ(),
                                message.rayDirX(), message.rayDirY(), message.rayDirZ(), message.traceId());
                        sendTerminal(player, message.traceId(), result);
                        if (result == null || result.outcome() != S2CRtsRemoteMenuResultPayload.MENU_OPENED) {
                            RtsRemoteMenuCompat.cancelServerRemoteMenuOpen(player, message.traceId(), "NO_MENU");
                        }
                        RtsGuiCompatMatrixSync.markInteractionProcessed(message.clickedPos());
                    } catch (RuntimeException | LinkageError failure) {
                        RtsbuildingMod.LOGGER.error(
                                "[RTS-TRACE] side=S event=RESULT trace={} kind=REMOTE_GUI outcome=FAILED reason=EXCEPTION failure={}",
                                RtsTraceIds.format(message.traceId()), failure.getClass().getName(), failure);
                        sendTerminal(player, message.traceId(), RtsRemoteInteractionResult.failed());
                        RtsRemoteMenuCompat.cancelServerRemoteMenuOpen(player, message.traceId(), "EXCEPTION");
                        // 真实矩阵会故意激活缺少结构或物品前置的孤立方块。仅在探针模式下把第三方异常
                        // 变成可报告 ACK；普通玩家路径仍按原语义抛出，避免吞掉实际服务端故障。
                        if (!RtsGuiCompatMatrixSync.isEnabled()) throw failure;
                        RtsGuiCompatMatrixSync.markInteractionFailed(message.clickedPos(), failure);
                    }
                }
            });
            return null;
        }

        private static void logReceived(EntityPlayerMP player, C2SRtsInteractPayload message) {
            BlockPos pos = message.clickedPos();
            boolean loaded = pos != null && com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(player.getServerForPlayer(), pos);
            String blockId = "unloaded";
            if (loaded) {
                BlockState state = BlockState.fromWorld(player.getServerForPlayer(), pos);
                ResourceLocation id = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS.getNameForObject(state.getBlock());
                blockId = id == null ? state.getBlock().getClass().getName() : id.toString();
            }
            long distance = pos == null ? -1L
                    : Math.round(Math.sqrt(com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.distanceSqToCenter(player, pos)));
            RtsbuildingMod.LOGGER.info(
                    "[RTS-TRACE] side=S event=C2S_RECEIVED trace={} kind=REMOTE_GUI player={} target={} entity={} source={} distance={} loadedBefore={} block={}",
                    RtsTraceIds.format(message.traceId()), com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.name(player), pos, message.entityId(),
                    sourceName(message.sourceType()), distance, loaded, blockId);
        }

        private static void sendTerminal(
                EntityPlayerMP player, long traceId, RtsRemoteInteractionResult result) {
            if (result == null) result = RtsRemoteInteractionResult.failed();
            RtsbuildingMod.LOGGER.info(
                    "[RTS-TRACE] side=S event=RESULT trace={} kind=REMOTE_GUI outcome={} reason={} window={}",
                    RtsTraceIds.format(traceId),
                    S2CRtsRemoteMenuResultPayload.outcomeName(result.outcome()),
                    S2CRtsRemoteMenuResultPayload.reasonName(result.reason()), result.windowId());
            if (RtsTraceIds.isPresent(traceId)) {
                RtsClientboundPackets.sendToPlayer(player, new S2CRtsRemoteMenuResultPayload(
                        traceId, result.outcome(), result.reason(), result.windowId()));
            }
        }

        private static String sourceName(byte source) {
            switch (source) {
                case C2SRtsInteractPayload.SOURCE_TOOL_SLOT: return "TOOL_SLOT";
                case C2SRtsInteractPayload.SOURCE_PIN_ITEM: return "PINNED_ITEM";
                case C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR: return "TOOL_SLOT_AIR";
                case C2SRtsInteractPayload.SOURCE_EMPTY_HAND: return "EMPTY_HAND";
                default: return "UNKNOWN";
            }
        }
    }

    private interface Action {
        void run(EntityPlayerMP player);
    }

    private static void schedule(MessageContext context, final Action action) {
        final EntityPlayerMP player = context.getServerHandler().playerEntity;
        com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() {
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

    private static Object invokeService(String accessor, String name, Class<?>[] types, Object... arguments) {
        try {
            Class<?> registryClass = Class.forName(REGISTRY);
            Object registry = registryClass.getMethod("getInstance").invoke(null);
            Object service = registryClass.getMethod(accessor).invoke(registry);
            return service.getClass().getMethod(name, types).invoke(service, arguments);
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
