package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import net.minecraft.entity.player.EntityPlayerMP;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/** 范围剔除协议注册 facade；建议固定 ID 79-81，待总注册器统一接入。 */
public final class RtsCullingPackets {
    public static final int REQUEST_STATE_ID = 79;
    public static final int SAVE_STATE_ID = 80;
    public static final int STATE_ID = 81;

    private RtsCullingPackets() { }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(REQUEST_STATE_ID, RequestHandler.class,
                C2SRtsRequestCullingStatePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(SAVE_STATE_ID, SaveHandler.class,
                C2SRtsSaveCullingStatePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(STATE_ID, StateHandler.class,
                S2CRtsCullingStatePayload.class, Side.CLIENT);
    }

    public static final class RequestHandler
            implements IMessageHandler<C2SRtsRequestCullingStatePayload, IMessage> {
        @Override public IMessage onMessage(C2SRtsRequestCullingStatePayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() {
                @Override public void run() { RtsCullingNetworkHandlers.handleRequest(player); }
            });
            return null;
        }
    }

    public static final class SaveHandler
            implements IMessageHandler<C2SRtsSaveCullingStatePayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsSaveCullingStatePayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() {
                @Override public void run() { RtsCullingNetworkHandlers.handleSave(player, message); }
            });
            return null;
        }
    }

    public static final class StateHandler
            implements IMessageHandler<S2CRtsCullingStatePayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsCullingStatePayload message, MessageContext context) {

            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.schedule(context, new Runnable() {
                @Override public void run() { RtsCullingNetworkHandlers.handleClientState(message); }
            });
            return null;
        }
    }
}
