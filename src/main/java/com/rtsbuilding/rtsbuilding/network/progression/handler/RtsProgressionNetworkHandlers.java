package com.rtsbuilding.rtsbuilding.network.progression.handler;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsBeginHomeSelectionPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsQuestDetectPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsRequestProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetHomePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetSurvivalProgressionPayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;

/**
 * Forge 1.12 的进度消息适配器。所有 C2S 业务先切回服务端世界线程，管理员设置还会在
 * 执行业务前重新校验权限；S2C 则延迟加载客户端入口，保持专用服务端类加载安全。
 */
public final class RtsProgressionNetworkHandlers {
    private static final String CLIENT_HANDLERS =
            "com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers";

    private RtsProgressionNetworkHandlers() {
    }

    public static final class QuestDetect implements IMessageHandler<C2SRtsQuestDetectPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsQuestDetectPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() { QuestService.detectQuests(player, message.mode()); }
            });
            return null;
        }
    }

    public static final class SetSurvivalProgression
            implements IMessageHandler<C2SRtsSetSurvivalProgressionPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsSetSurvivalProgressionPayload message,
                                            MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() {
                    if (!player.canUseCommand(2, "rtsbuilding")) return;
                    Config.setSurvivalProgressionEnabled(message.enabled());
                    if (player.getServer() == null) return;
                    for (EntityPlayerMP onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
                        RtsPluginService.syncToPlayer(onlinePlayer);
                        RtsProgressionManager.syncToPlayer(onlinePlayer);
                    }
                }
            });
            return null;
        }
    }

    public static final class SetHome implements IMessageHandler<C2SRtsSetHomePayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsSetHomePayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() {
                    if (RtsProgressionManager.commitHome(player, message.pos())) {
                        RtsCameraManager.restartNormalFromHomeSelection(player);
                    }
                }
            });
            return null;
        }
    }

    public static final class BeginHomeSelection
            implements IMessageHandler<C2SRtsBeginHomeSelectionPayload, IMessage> {
        @Override public IMessage onMessage(C2SRtsBeginHomeSelectionPayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() { RtsCameraManager.startHomeSelectionFromPanel(player); }
            });
            return null;
        }
    }

    public static final class RequestProgressionState
            implements IMessageHandler<C2SRtsRequestProgressionStatePayload, IMessage> {
        @Override public IMessage onMessage(C2SRtsRequestProgressionStatePayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() { RtsProgressionManager.syncToPlayer(player); }
            });
            return null;
        }
    }

    public static final class ClientQuestDetectStatus
            implements IMessageHandler<S2CRtsQuestDetectStatusPayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsQuestDetectStatusPayload message, MessageContext context) {
            scheduleClient(context, new Runnable() {
                @Override public void run() {
                    invokeClient("handleQuestDetectStatus", S2CRtsQuestDetectStatusPayload.class, message);
                }
            });
            return null;
        }
    }

    public static final class ClientProgressionState
            implements IMessageHandler<S2CRtsProgressionStatePayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsProgressionStatePayload message, MessageContext context) {
            scheduleClient(context, new Runnable() {
                @Override public void run() {
                    invokeClient("handleProgressionState", S2CRtsProgressionStatePayload.class, message);
                }
            });
            return null;
        }
    }

    private static void scheduleClient(MessageContext context, Runnable task) {
        IThreadListener thread = FMLCommonHandler.instance().getWorldThread(context.netHandler);
        thread.addScheduledTask(task);
    }

    private static void invokeClient(String method, Class<?> payloadType, Object payload) {
        try {
            Class.forName(CLIENT_HANDLERS).getMethod(method, payloadType).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("RTS progression client handler is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("RTS progression client handler failed", cause);
        }
    }
}
