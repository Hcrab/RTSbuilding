package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderPackets;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.craft.RtsCraftPackets;
import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.pathfinding.RtsPathfindingPackets;
import com.rtsbuilding.rtsbuilding.network.plugin.RtsPluginPackets;
import com.rtsbuilding.rtsbuilding.network.progression.RtsProgressionPackets;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStoragePackets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric 1.21.1 数据包注册器。
 *
 * <p>C2S 类型与接收器在公共入口直接注册；S2C 类型在此注册编码器，并把接收器描述留给
 * 客户端入口安装。这样既保留原来的分领域注册结构，也避免公共源码引用客户端网络 API。
 */
public final class RtsPayloadRegistrar {
    private static final List<ClientRegistration<?>> CLIENT_REGISTRATIONS = new ArrayList<>();
    private static boolean registered;

    private RtsPayloadRegistrar() {
    }

    public static synchronized void registerCommon() {
        if (registered) {
            return;
        }
        registered = true;
        RtsPayloadRegistrar registrar = new RtsPayloadRegistrar();
        RtsCameraPackets.register(registrar);
        RtsStoragePackets.register(registrar);
        RtsBuilderPackets.register(registrar);
        RtsCraftPackets.register(registrar);
        RtsCullingPackets.register(registrar);
        RtsProgressionPackets.register(registrar);
        RtsPluginPackets.register(registrar);
        RtsFeedbackPackets.register(registrar);
        RtsPathfindingPackets.register(registrar);
        BlueprintPayloadRegistrar.register(registrar);
    }

    public <T extends CustomPacketPayload> void playToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            RtsPayloadHandler<T> handler) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> handler.handle(payload, new FabricServerPayloadContext(context)));
    }

    public <T extends CustomPacketPayload> void playToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            RtsPayloadHandler<T> handler) {
        PayloadTypeRegistry.playS2C().register(type, codec);
        CLIENT_REGISTRATIONS.add(new ClientRegistration<>(type, handler));
    }

    public static List<ClientRegistration<?>> clientRegistrations() {
        return List.copyOf(CLIENT_REGISTRATIONS);
    }

    public record ClientRegistration<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            RtsPayloadHandler<T> handler) {
    }
}
