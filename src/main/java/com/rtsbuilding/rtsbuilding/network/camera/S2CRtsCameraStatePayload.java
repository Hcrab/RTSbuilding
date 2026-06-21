package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsCameraStatePayload(
        boolean enabled,
        int cameraEntityId,
        double anchorX,
        double anchorY,
        double anchorZ,
        double maxRadius,
        double heightOffset,
        float yawDeg,
        float pitchDeg,
        boolean homeSelection,
        boolean closeRangeAllowed) implements CustomPacketPayload {
    public static final Type<S2CRtsCameraStatePayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_camera_state"), S2CRtsCameraStatePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsCameraStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.enabled());
                buf.writeVarInt(payload.cameraEntityId());
                buf.writeDouble(payload.anchorX());
                buf.writeDouble(payload.anchorY());
                buf.writeDouble(payload.anchorZ());
                buf.writeDouble(payload.maxRadius());
                buf.writeDouble(payload.heightOffset());
                buf.writeFloat(payload.yawDeg());
                buf.writeFloat(payload.pitchDeg());
                buf.writeBoolean(payload.homeSelection());
                buf.writeBoolean(payload.closeRangeAllowed());
            },
            (buf) -> new S2CRtsCameraStatePayload(
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
