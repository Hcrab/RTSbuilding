package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client payload carrying an updated RTS camera anchor position.
 *
 * <p>Sent when the server updates the building anchor to follow the player
 * entity's movement. The client uses these values to keep camera bounds and
 * build-zone visuals in sync with the server-authoritative anchor.
 */
public record S2CRtsCameraAnchorPayload(
        double anchorX,
        double anchorY,
        double anchorZ,
        double maxRadius) implements CustomPacketPayload {

    public static final Type<S2CRtsCameraAnchorPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_camera_anchor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsCameraAnchorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.anchorX());
                buf.writeDouble(payload.anchorY());
                buf.writeDouble(payload.anchorZ());
                buf.writeDouble(payload.maxRadius());
            },
            (buf) -> new S2CRtsCameraAnchorPayload(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
