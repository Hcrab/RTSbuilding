package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record C2SRtsSaveCullingStatePayload(
        String dimension,
        List<RtsCullingBoxSnapshot> boxes,
        List<BlockPos> revealed) implements CustomPacketPayload {
    public static final Type<C2SRtsSaveCullingStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_save_culling_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSaveCullingStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.dimension(), 128);
                RtsCullingPayloadCodec.write(buf, payload.boxes(), payload.revealed());
            },
            buf -> {
                String dimension = buf.readUtf(128);
                RtsCullingPayloadCodec.Decoded decoded = RtsCullingPayloadCodec.read(buf);
                return new C2SRtsSaveCullingStatePayload(dimension, decoded.boxes(), decoded.revealed());
            });

    public C2SRtsSaveCullingStatePayload {
        dimension = dimension == null ? "" : dimension;
        boxes = boxes == null ? List.of() : List.copyOf(boxes);
        revealed = revealed == null ? List.of() : List.copyOf(revealed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
