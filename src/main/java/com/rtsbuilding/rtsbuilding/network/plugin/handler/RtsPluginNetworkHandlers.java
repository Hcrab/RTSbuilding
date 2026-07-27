package com.rtsbuilding.rtsbuilding.network.plugin.handler;

import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsInstallPluginPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsRequestPluginsPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsUninstallPluginPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;

/** Forge 1.12 插件消息处理器；所有 C2S 业务均回到服务端主线程执行。 */
public final class RtsPluginNetworkHandlers {
    private static final String CLIENT_HANDLERS =
            "com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers";

    private RtsPluginNetworkHandlers() {
    }

    public static final class Install implements IMessageHandler<C2SRtsInstallPluginPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsInstallPluginPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() {
                    if (message.inventorySlot() < player.inventory.getSizeInventory()) {
                        RtsPluginService.installFromInventorySlot(player, message.inventorySlot());
                    }
                }
            });
            return null;
        }
    }

    public static final class Uninstall implements IMessageHandler<C2SRtsUninstallPluginPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsUninstallPluginPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            final ResourceLocation pluginId;
            try {
                pluginId = new ResourceLocation(message.pluginId());
            } catch (IllegalArgumentException invalidId) {
                return null;
            }
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() { RtsPluginService.uninstall(player, pluginId); }
            });
            return null;
        }
    }

    public static final class Request implements IMessageHandler<C2SRtsRequestPluginsPayload, IMessage> {
        @Override public IMessage onMessage(C2SRtsRequestPluginsPayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() { RtsPluginService.syncToPlayer(player); }
            });
            return null;
        }
    }

    /** 字符串边界避免公共注册类在专用服务端链接 client 包。 */
    public static final class ClientState implements IMessageHandler<S2CRtsPluginStatePayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsPluginStatePayload message,
                                            MessageContext context) {
            scheduleClient(context, new Runnable() {
                @Override public void run() {
                    invokeClient("handlePluginState", S2CRtsPluginStatePayload.class, message);
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
            throw new IllegalStateException("RTS plugin client handler is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("RTS plugin client handler failed", cause);
        }
    }
}
