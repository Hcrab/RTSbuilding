package com.rtsbuilding.rtsbuilding.network.feedback;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.InvocationTargetException;

/** 注册轻量的服务端到客户端 RTS 反馈消息。 */
public final class RtsFeedbackPackets {
    private static final String CLIENT_HANDLERS =
            "com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers";

    private RtsFeedbackPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(4, DamageHandler.class,
                S2CRtsDamageFeedbackPayload.class, Side.CLIENT);
    }

    /** 通过反射延迟加载客户端入口，避免专用服务端链接客户端类。 */
    public static final class DamageHandler
            implements IMessageHandler<S2CRtsDamageFeedbackPayload, IMessage> {
        @Override
        public IMessage onMessage(final S2CRtsDamageFeedbackPayload message, MessageContext context) {
            IThreadListener thread = FMLCommonHandler.instance().getWorldThread(context.netHandler);
            thread.addScheduledTask(new Runnable() {
                @Override public void run() {
                    invokeClient("handleDamageFeedback", S2CRtsDamageFeedbackPayload.class, message);
                }
            });
            return null;
        }
    }

    private static void invokeClient(String method, Class<?> payloadType, Object payload) {
        try {
            Class.forName(CLIENT_HANDLERS).getMethod(method, payloadType).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("RTS feedback client handler is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("RTS feedback client handler failed", cause);
        }
    }
}
