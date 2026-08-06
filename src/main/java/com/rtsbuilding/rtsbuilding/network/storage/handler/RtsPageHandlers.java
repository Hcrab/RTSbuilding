package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** 储存页面请求的 1.12 服务端边界；页面内容始终按连接玩家的当前会话构建。 */
public final class RtsPageHandlers {
    private static final String REGISTRY = "com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";
    private RtsPageHandlers() {}

    public static final class Request implements IMessageHandler<C2SRtsRequestStoragePagePayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsRequestStoragePagePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() { @Override public void run() {
                // 与正式 1.12.2 入口保持一致：可见 overlay 的只读刷新不依赖相机状态。
                invokePage(player, message);
            }});
            return null;
        }
    }
    private static void invokePage(EntityPlayerMP player, C2SRtsRequestStoragePagePayload message) {
        try {
            Class<?> registryType = Class.forName(REGISTRY);
            Object registry = registryType.getMethod("getInstance").invoke(null);
            Object page = registryType.getMethod("page").invoke(registry);
            page.getClass().getMethod("requestPage", EntityPlayerMP.class, int.class, String.class,
                    String.class, RtsStorageSort.class, boolean.class, int.class, boolean.class, List.class)
                    .invoke(page, player, message.page(), message.search(), message.category(),
                            RtsStorageSort.byId(message.sort()), message.ascending(), message.pageSize(),
                            message.pinyinSearchEnabled(), message.localizedSearchMatches());
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12 storage page adapter unavailable", exception);
        } catch (InvocationTargetException exception) { throw propagate("Storage page request failed", exception); }
    }
    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(message, cause);
    }
}
