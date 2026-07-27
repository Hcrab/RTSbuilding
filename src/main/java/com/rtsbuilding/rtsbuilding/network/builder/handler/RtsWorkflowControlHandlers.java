package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsDeleteWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetWorkflowProtectedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUndoPayload;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 工作流控制的服务端网络边界。
 *
 * <p>身份只来自连接上下文，业务调用也始终携带该玩家，因此底层引擎按玩家槽位查询，
 * 客户端无法仅凭猜测 entryId 操作其他玩家的任务。实际修改排入 WorldServer 主线程。</p>
 */
public final class RtsWorkflowControlHandlers {
    private static final String ENGINE_CLASS =
            "com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine";
    private static final String CAMERA_MANAGER_CLASS =
            "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";

    private RtsWorkflowControlHandlers() {
    }

    public static final class DeleteHandler implements IMessageHandler<C2SRtsDeleteWorkflowPayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsDeleteWorkflowPayload message, MessageContext context) {
            if (!message.isValid()) {
                return null;
            }
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    invokeEngine("deleteWorkflow", new Class<?>[]{EntityPlayerMP.class, int.class},
                            player, message.workflowEntryId());
                }
            });
            return null;
        }
    }

    public static final class ProtectHandler
            implements IMessageHandler<C2SRtsSetWorkflowProtectedPayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsSetWorkflowProtectedPayload message, MessageContext context) {
            if (!message.isValid()) {
                return null;
            }
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    invokeEngine("setWorkflowProtected",
                            new Class<?>[]{EntityPlayerMP.class, int.class, boolean.class},
                            player, message.workflowEntryId(), message.protectedWorkflow());
                }
            });
            return null;
        }
    }

    public static final class UndoHandler implements IMessageHandler<C2SRtsUndoPayload, IMessage> {
        @Override
        public IMessage onMessage(C2SRtsUndoPayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    // 撤回只允许在玩家自己的活跃 RTS 会话中执行，历史管理器再按玩家取栈。
                    if (invokeStaticBoolean(CAMERA_MANAGER_CLASS, "isActive",
                            new Class<?>[]{EntityPlayerMP.class}, player)) {
                        ServerHistoryManager.executeUndo(player);
                    }
                }
            });
            return null;
        }
    }

    private static void invokeEngine(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Class<?> engineClass = Class.forName(ENGINE_CLASS);
            Object engine = engineClass.getMethod("getInstance").invoke(null);
            Method method = engineClass.getMethod(methodName, parameterTypes);
            method.invoke(engine, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 workflow engine adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Workflow engine rejected network control", cause);
        }
    }

    private static boolean invokeStaticBoolean(
            String className, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Class<?> targetClass = Class.forName(className);
            Object result = targetClass.getMethod(methodName, parameterTypes).invoke(null, arguments);
            return Boolean.TRUE.equals(result);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 server authority adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Server authority check failed", cause);
        }
    }
}
