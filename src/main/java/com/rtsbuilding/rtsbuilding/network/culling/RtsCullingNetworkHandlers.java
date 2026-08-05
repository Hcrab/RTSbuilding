package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import net.minecraft.entity.player.EntityPlayerMP;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 连接已移植协议与尚待移植的服务端持久化实现；边界缺失会明确失败，不会伪装保存成功。
 */
public final class RtsCullingNetworkHandlers {
    private static final String PERSISTENCE =
            "com.rtsbuilding.rtsbuilding.server.culling.RtsCullingPersistence";
    private static final String CLIENT_STATE =
            "com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState";

    private RtsCullingNetworkHandlers() { }

    public static void handleRequest(EntityPlayerMP player) {
        Object state = invokeStatic(PERSISTENCE, "load", new Class<?>[] {EntityPlayerMP.class}, player);
        List<RtsCullingBoxSnapshot> boxes = castList(invokeAccessor(state, "boxes"));
        List<com.rtsbuilding.rtsbuilding.platform.math.BlockPos> revealed = castList(invokeAccessor(state, "revealed"));
        RtsPayloadRegistrar.sendToPlayer(player, new S2CRtsCullingStatePayload(
                Integer.toString(player.dimension), boxes, revealed));
    }

    public static void handleSave(EntityPlayerMP player, C2SRtsSaveCullingStatePayload payload) {
        String currentDimension = Integer.toString(player.dimension);
        if (!currentDimension.equals(payload.dimension())) return;
        invokeStatic(PERSISTENCE, "save",
                new Class<?>[] {EntityPlayerMP.class, List.class, List.class},
                player, payload.boxes(), payload.revealed());
    }

    public static void handleClientState(S2CRtsCullingStatePayload payload) {
        invokeStatic(CLIENT_STATE, "applyCurrentWorldState",
                new Class<?>[] {S2CRtsCullingStatePayload.class}, payload);
    }

    private static Object invokeAccessor(Object target, String method) {
        if (target == null) throw new IllegalStateException("Culling persistence returned null state");
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Culling persistence state lacks " + method + "()", exception);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access culling persistence state", exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception);
        }
    }

    private static Object invokeStatic(String className, String method, Class<?>[] types, Object... args) {
        try {
            Class<?> owner = Class.forName(className);
            Method target = owner.getMethod(method, types);
            return target.invoke(null, args);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Culling boundary unavailable: " + className, exception);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Culling boundary method unavailable: " + method, exception);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Culling boundary method inaccessible: " + method, exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception);
        }
    }

    private static RuntimeException propagate(InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof RuntimeException ? (RuntimeException) cause
                : new IllegalStateException("Culling boundary invocation failed", cause);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        if (!(value instanceof List)) throw new IllegalStateException("Culling persistence returned non-list data");
        return (List<T>) value;
    }
}
