package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsCameraMovePayload(
        float forward,
        float strafe,
        float vertical,
        float panX,
        float panY,
        float rotateX,
        float rotateY,
        float scroll,
        int rotateSteps,
        boolean fast) implements CustomPacketPayload {

    public static final Type<C2SRtsCameraMovePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_camera_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCameraMovePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeFloat(payload.forward());
                buf.writeFloat(payload.strafe());
                buf.writeFloat(payload.vertical());
                buf.writeFloat(payload.panX());
                buf.writeFloat(payload.panY());
                buf.writeFloat(payload.rotateX());
                buf.writeFloat(payload.rotateY());
                buf.writeFloat(payload.scroll());
                buf.writeVarInt(payload.rotateSteps());
                buf.writeBoolean(payload.fast());
            },
            (buf) -> new C2SRtsCameraMovePayload(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
