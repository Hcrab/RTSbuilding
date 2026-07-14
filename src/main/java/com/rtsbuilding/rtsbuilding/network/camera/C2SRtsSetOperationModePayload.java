package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsSetOperationModePayload(
        boolean operationMode,
        double offsetX,
        double offsetY,
        double offsetZ,
        float cameraYaw,
        float cameraPitch) implements CustomPacketPayload {

    public static final Type<C2SRtsSetOperationModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_set_operation_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetOperationModePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.operationMode());
                buf.writeDouble(payload.offsetX());
                buf.writeDouble(payload.offsetY());
                buf.writeDouble(payload.offsetZ());
                buf.writeFloat(payload.cameraYaw());
                buf.writeFloat(payload.cameraPitch());
            },
            (buf) -> new C2SRtsSetOperationModePayload(
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
