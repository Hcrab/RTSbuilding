package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

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
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_camera_anchor"),
            S2CRtsCameraAnchorPayload.class);

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
