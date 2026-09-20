package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.network.RtsNetworkProtocol;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderPackets;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingPackets;
import com.rtsbuilding.rtsbuilding.network.create.RtsCreateValueSettingsPackets;
import com.rtsbuilding.rtsbuilding.network.craft.RtsCraftPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.pathfinding.RtsPathfindingPackets;
import com.rtsbuilding.rtsbuilding.network.plugin.RtsPluginPackets;
import com.rtsbuilding.rtsbuilding.network.progression.RtsProgressionPackets;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStoragePackets;
import com.rtsbuilding.rtsbuilding.network.config.RtsServerConfigPackets;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 1.20.1 transport entry point for all RTSBuilding payloads.
 *
 * <p>The gameplay-facing registration remains split by domain through
 * {@code Rts*Packets}, matching the main NeoForge architecture. This class owns
 * only the Forge SimpleChannel and blueprint bridge that the 1.20.1 loader
 * requires.
 */
public final class RtsForgePayloadRegistrar {
    // 本 beta 的 payload 字段已变更；拒绝旧客户端按旧顺序解码。

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RtsbuildingMod.MODID, "main"),
            () -> RtsNetworkProtocol.VERSION,
            RtsNetworkProtocol.VERSION::equals,
            RtsNetworkProtocol.VERSION::equals);

    private static boolean registered;

    private RtsForgePayloadRegistrar() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ForgePayloadRegistrar registrar = new ForgePayloadRegistrar(CHANNEL);
        RtsCameraPackets.register(registrar);
        RtsCullingPackets.register(registrar);
        RtsCreateValueSettingsPackets.register(registrar);
        RtsStoragePackets.register(registrar);
        RtsServerConfigPackets.register(registrar);
        RtsBuilderPackets.register(registrar);
        RtsCraftPackets.register(registrar);
        RtsProgressionPackets.register(registrar);
        RtsPluginPackets.register(registrar);
        RtsFeedbackPackets.register(registrar);
        RtsPathfindingPackets.register(registrar);
        BlueprintPayloadRegistrar.register(registrar);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
