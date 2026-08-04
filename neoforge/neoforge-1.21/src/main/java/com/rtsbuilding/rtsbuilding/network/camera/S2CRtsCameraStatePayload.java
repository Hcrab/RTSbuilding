package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

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
        boolean closeRangeAllowed,
        @Nullable String terminalUuid) implements CustomPacketPayload {
    public static final Type<S2CRtsCameraStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_camera_state"));

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
                buf.writeNullable(payload.terminalUuid(), FriendlyByteBuf::writeUtf);
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
                    buf.readBoolean(),
                    buf.readNullable(FriendlyByteBuf::readUtf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
