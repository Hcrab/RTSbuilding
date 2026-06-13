package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.network.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderPackets;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.craft.RtsCraftPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.progression.RtsProgressionPackets;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStoragePackets;

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
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RtsbuildingMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

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
        RtsStoragePackets.register(registrar);
        RtsBuilderPackets.register(registrar);
        RtsCraftPackets.register(registrar);
        RtsProgressionPackets.register(registrar);
        RtsFeedbackPackets.register(registrar);
        BlueprintPayloadRegistrar.register(registrar);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
