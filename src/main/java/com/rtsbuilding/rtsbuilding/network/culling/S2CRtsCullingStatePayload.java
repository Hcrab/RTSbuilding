package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record S2CRtsCullingStatePayload(
        String dimension,
        List<RtsCullingBoxSnapshot> boxes,
        List<BlockPos> revealed) implements CustomPacketPayload {
    public static final Type<S2CRtsCullingStatePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_culling_state"),
            S2CRtsCullingStatePayload.class);
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsCullingStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.dimension(), 128);
                        RtsCullingPayloadCodec.write(buf, payload.boxes(), payload.revealed());
                    },
                    buf -> {
                        String dimension = buf.readUtf(128);
                        RtsCullingPayloadCodec.Decoded decoded = RtsCullingPayloadCodec.read(buf);
                        return new S2CRtsCullingStatePayload(
                                dimension, decoded.boxes(), decoded.revealed());
                    });

    public S2CRtsCullingStatePayload {
        dimension = dimension == null ? "" : dimension;
        boxes = boxes == null ? List.of() : List.copyOf(boxes);
        revealed = revealed == null ? List.of() : List.copyOf(revealed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
