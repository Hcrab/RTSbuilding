package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPauseWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanResumePlacementPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * 挂起工作流的 1.12 服务端网络边界。反射只隔离仍在并行迁移的服务实现类型；消息到达后
 * 仍调用真实工作流、储存会话和放置服务，不复制或伪造业务状态。
 */
public final class RtsBuilderSyncHandlers1122 {
    private static final String WORKFLOW_ENGINE =
            "com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine";
    private static final String TASK_ENGINE =
            "com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine";
    private static final String SERVICE_REGISTRY =
            "com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";
    private static final String PENDING_PLACEMENT =
            "com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService";
    private static final String BLUEPRINT_JOB =
            "com.rtsbuilding.rtsbuilding.server.service.RtsBlueprintJobService";

    private RtsBuilderSyncHandlers1122() {}

    public static final class PauseWorkflow
            implements IMessageHandler<C2SRtsPauseWorkflowPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsPauseWorkflowPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() {
                @Override public void run() { togglePause(player, message.entryId()); }
            });
            return null;
        }
    }

    public static final class ScanResumePlacement
            implements IMessageHandler<C2SRtsScanResumePlacementPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsScanResumePlacementPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() {
                @Override public void run() { scanResumePlacement(player, message.workflowEntryId()); }
            });
            return null;
        }
    }

    public static final class ResumePlacementAction
            implements IMessageHandler<C2SRtsResumePlacementActionPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsResumePlacementActionPayload message,
                                            MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() {
                @Override public void run() {
                    resumePlacement(player, message.strategy(), message.workflowEntryId());
                }
            });
            return null;
        }
    }

    private static void scanResumePlacement(EntityPlayerMP player, int entryId) {
        Object session = storageSession(player);
        if (session == null) return;
        Object result = invokeStatic(PENDING_PLACEMENT, "scanPendingJob", player, session, entryId);
        if (result == null) return;
        S2CRtsResumePlacementScanPayload payload = new S2CRtsResumePlacementScanPayload(
                (String) invoke(result, "itemId"),
                (String) invoke(result, "itemLabel"),
                ((Number) invoke(result, "totalRemaining")).intValue(),
                ((Number) invoke(result, "alreadyPlacedCount")).intValue(),
                ((Number) invoke(result, "conflictCount")).intValue(),
                ((Number) invoke(result, "availableItems")).longValue(),
                ((Number) invoke(result, "neededItems")).intValue(),
                ((Number) invoke(result, "missingItems")).longValue(),
                ((Number) invoke(result, "workflowEntryId")).intValue());
        RtsPayloadRegistrar.sendToPlayer(player, payload);
    }

    private static void resumePlacement(EntityPlayerMP player, int strategy, int entryId) {
        Object engine = invokeStatic(WORKFLOW_ENGINE, "getInstance");
        Object status = invoke(engine, "getProgress", player, entryId);
        if (Boolean.TRUE.equals(invoke(status, "isActive"))) {
            Object type = invoke(status, "type");
            if (type instanceof Enum && "BLUEPRINT_BUILD".equals(((Enum<?>) type).name())) {
                invokeStatic(BLUEPRINT_JOB, "resumeBlueprintWorkflow", player, entryId);
                return;
            }
        }
        Object session = storageSession(player);
        if (session != null) invokeStatic(PENDING_PLACEMENT, "resumeWithStrategy", player, session, strategy, entryId);
    }

    private static void togglePause(EntityPlayerMP player, int entryId) {
        Object engine = invokeStatic(WORKFLOW_ENGINE, "getInstance");
        Object status = invoke(engine, "getProgress", player, entryId);
        Object entry = invoke(engine, "findEntryByPlayer", player, entryId);
        if (!Boolean.TRUE.equals(invoke(status, "isActive")) || entry == null
                || Boolean.TRUE.equals(invoke(entry, "terminal"))) return;
        Object optionalValue = invoke(engine, "from", player, entryId);
        if (!(optionalValue instanceof Optional) || !((Optional<?>) optionalValue).isPresent()) return;
        Object token = ((Optional<?>) optionalValue).get();
        boolean suspended = Boolean.TRUE.equals(invoke(status, "suspended"));
        boolean paused = Boolean.TRUE.equals(invoke(token, "isPaused"));
        boolean nextPaused = !suspended && !paused;
        Object taskEngine = staticField(TASK_ENGINE, "INSTANCE");
        invoke(taskEngine, "setWorkflowPaused", player, entryId, nextPaused);
        String key = suspended ? "message.rtsbuilding.workflow.resumed"
                : paused ? "message.rtsbuilding.workflow.thread_resumed"
                : "message.rtsbuilding.workflow.paused";
        com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(player, new ChatComponentTranslation(key), true);
    }

    private static Object storageSession(EntityPlayerMP player) {
        Object registry = invokeStatic(SERVICE_REGISTRY, "getInstance");
        Object sessionService = invoke(registry, "session");
        return invoke(sessionService, "getIfPresent", player);
    }

    private static Object staticField(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getField(fieldName);
            return field.get(null);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12 builder service field is unavailable: " + className, exception);
        }
    }

    private static Object invokeStatic(String className, String methodName, Object... arguments) {
        try {
            Class<?> type = Class.forName(className);
            return invokeMethod(null, compatibleMethod(type, methodName, true, arguments), arguments);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("1.12 builder service is unavailable: " + className, exception);
        }
    }

    private static Object invoke(Object target, String methodName, Object... arguments) {
        if (target == null) return null;
        return invokeMethod(target, compatibleMethod(target.getClass(), methodName, false, arguments), arguments);
    }

    private static Method compatibleMethod(Class<?> type, String name, boolean requireStatic, Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers()) != requireStatic
                    || method.getParameterTypes().length != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                if (!isCompatible(parameters[i], arguments[i])) { compatible = false; break; }
            }
            if (compatible) return method;
        }
        throw new IllegalStateException("1.12 builder service method is unavailable: " + type.getName() + '#' + name);
    }

    private static boolean isCompatible(Class<?> parameter, Object argument) {
        if (argument == null) return !parameter.isPrimitive();
        if (!parameter.isPrimitive()) return parameter.isInstance(argument);
        if (parameter == int.class) return argument instanceof Integer;
        if (parameter == boolean.class) return argument instanceof Boolean;
        if (parameter == long.class) return argument instanceof Long;
        if (parameter == byte.class) return argument instanceof Byte;
        return false;
    }

    private static Object invokeMethod(Object target, Method method, Object[] arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access 1.12 builder service", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("1.12 builder service rejected network action", cause);
        }
    }
}
