package com.rtsbuilding.rtsbuilding.network;

/**
 * 公共网络注册层到客户端实现的惰性桥。
 *
 * <p>公共源码只保存数据包领域，不引用任何 Minecraft 客户端类。Fabric 客户端入口在安全的
 * 客户端环境中安装实际分发器，因此独立服务端可以加载全部公共注册类而不会触发客户端类加载。
 */
public final class ClientPayloadDispatcher {
    private static volatile ClientSink sink;

    private ClientPayloadDispatcher() {
    }

    public static void install(ClientSink clientSink) {
        sink = clientSink;
    }

    public static void dispatchCamera(Object payload, RtsPayloadContext context) {
        dispatch(Domain.CAMERA, payload, context);
    }

    public static void dispatchStorage(Object payload, RtsPayloadContext context) {
        dispatch(Domain.STORAGE, payload, context);
    }

    public static void dispatchBuilder(Object payload, RtsPayloadContext context) {
        dispatch(Domain.BUILDER, payload, context);
    }

    public static void dispatchCraft(Object payload, RtsPayloadContext context) {
        dispatch(Domain.CRAFT, payload, context);
    }

    public static void dispatchProgression(Object payload, RtsPayloadContext context) {
        dispatch(Domain.PROGRESSION, payload, context);
    }

    public static void dispatchCulling(Object payload, RtsPayloadContext context) {
        dispatch(Domain.CULLING, payload, context);
    }

    public static void dispatchPlugin(Object payload, RtsPayloadContext context) {
        dispatch(Domain.PLUGIN, payload, context);
    }

    public static void dispatchFeedback(Object payload, RtsPayloadContext context) {
        dispatch(Domain.FEEDBACK, payload, context);
    }

    public static void dispatchBlueprintStatus(Object payload, RtsPayloadContext context) {
        dispatch(Domain.BLUEPRINT, payload, context);
    }

    private static void dispatch(Domain domain, Object payload, RtsPayloadContext context) {
        ClientSink current = sink;
        if (current != null) {
            current.dispatch(domain, payload, context);
        }
    }

    public enum Domain {
        CAMERA,
        STORAGE,
        BUILDER,
        CRAFT,
        PROGRESSION,
        CULLING,
        PLUGIN,
        FEEDBACK,
        BLUEPRINT
    }

    @FunctionalInterface
    public interface ClientSink {
        void dispatch(Domain domain, Object payload, RtsPayloadContext context);
    }
}
