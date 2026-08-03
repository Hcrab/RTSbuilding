package com.rtsbuilding.rtsbuilding.network.craft.handler;

import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRecipePayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRefillPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiTransferPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiContainerTransferPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsOpenCraftTerminalPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsRequestCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;

/** Forge 1.12 合成消息处理器；C2S 参数先验证，再切回服务端主线程。 */
public final class RtsCraftNetworkHandlers {
    private static final String CLIENT_HANDLERS =
            "com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers";

    private RtsCraftNetworkHandlers() {
    }

    public static final class RequestCraftables
            implements IMessageHandler<C2SRtsRequestCraftablesPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsRequestCraftablesPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            scheduleServer(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    ServiceRegistry.getInstance().crafting().requestCraftables(player,
                            message.search(), message.showUnavailable(), message.offset(),
                            message.limit(), message.pinyinSearchEnabled(),
                            message.localizedSearchMatches());
                }
            });
            return null;
        }
    }

    public static final class OpenTerminal
            implements IMessageHandler<C2SRtsOpenCraftTerminalPayload, IMessage> {
        @Override public IMessage onMessage(C2SRtsOpenCraftTerminalPayload message,
                                            MessageContext context) {
            scheduleServer(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    ServiceRegistry.getInstance().crafting().openCraftTerminal(player);
                }
            });
            return null;
        }
    }

    public static final class CraftRefill
            implements IMessageHandler<C2SRtsCraftRefillPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsCraftRefillPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            scheduleServer(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    ServiceRegistry.getInstance().crafting()
                            .refillCurrentCraftGridFromBlueprintStacks(player,
                                    message.blueprintStacks(), message.craftedItemId(),
                                    message.craftedCount());
                }
            });
            return null;
        }
    }

    public static final class CraftRecipe
            implements IMessageHandler<C2SRtsCraftRecipePayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsCraftRecipePayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            scheduleServer(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    ServiceRegistry.getInstance().crafting().craftRecipeToLinked(player,
                            message.recipeId(), message.craftCount());
                }
            });
            return null;
        }
    }

    public static final class JeiTransfer
            implements IMessageHandler<C2SRtsJeiTransferPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsJeiTransferPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            scheduleServer(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    ServiceRegistry.getInstance().crafting().applyJeiTransfer(player,
                            message.recipeId(), message.ingredientPrototypes(),
                            message.maxTransfer(), message.clearGridFirst());
                }
            });
            return null;
        }
    }

    /** 普通机器 GUI 的 JEI/HEI 转移仍在服务端当前容器与链接存储上重新验证。 */
    public static final class JeiContainerTransfer
            implements IMessageHandler<C2SRtsJeiContainerTransferPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsJeiContainerTransferPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            scheduleServer(context, new ServerAction() {
                @Override public void run(EntityPlayerMP player) {
                    ServiceRegistry.getInstance().crafting().applyJeiContainerTransfer(
                            player, message.windowId(), message.targetSlots(),
                            message.alternatives(), message.maxTransfer(),
                            message.requireCompleteSets());
                }
            });
            return null;
        }
    }

    public static final class ClientCraftables
            implements IMessageHandler<S2CRtsCraftablesPayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsCraftablesPayload message,
                                            MessageContext context) {
            scheduleClient(context, new Runnable() {
                @Override public void run() {
                    invokeClient("handleCraftables", S2CRtsCraftablesPayload.class, message);
                }
            });
            return null;
        }
    }

    public static final class ClientFeedback
            implements IMessageHandler<S2CRtsCraftFeedbackPayload, IMessage> {
        @Override public IMessage onMessage(final S2CRtsCraftFeedbackPayload message,
                                            MessageContext context) {
            scheduleClient(context, new Runnable() {
                @Override public void run() {
                    invokeClient("handleCraftFeedback", S2CRtsCraftFeedbackPayload.class, message);
                }
            });
            return null;
        }
    }

    private static void scheduleServer(MessageContext context, final ServerAction action) {
        final EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(new Runnable() {
            @Override public void run() { action.run(player); }
        });
    }

    private static void scheduleClient(MessageContext context, Runnable task) {
        IThreadListener thread = FMLCommonHandler.instance().getWorldThread(context.netHandler);
        thread.addScheduledTask(task);
    }

    private static void invokeClient(String method, Class<?> payloadType, Object payload) {
        try {
            Class.forName(CLIENT_HANDLERS).getMethod(method, payloadType).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("RTS craft client handler is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("RTS craft client handler failed", cause);
        }
    }

    private interface ServerAction {
        void run(EntityPlayerMP player);
    }
}
