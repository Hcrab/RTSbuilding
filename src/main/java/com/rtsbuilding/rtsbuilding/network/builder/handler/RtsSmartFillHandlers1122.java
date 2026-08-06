package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsConfirmSmartFillPayload;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsSmartFillService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 将 Netty 收到的智能填坑意图切回服务端主线程。 */
public final class RtsSmartFillHandlers1122 {
    private RtsSmartFillHandlers1122() {
    }

    public static final class Confirm implements IMessageHandler<C2SRtsConfirmSmartFillPayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsConfirmSmartFillPayload message,
                MessageContext context) {
            if (message == null || !message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    RtsSmartFillService.confirm(player, message);
                }
            });
            return null;
        }
    }
}
