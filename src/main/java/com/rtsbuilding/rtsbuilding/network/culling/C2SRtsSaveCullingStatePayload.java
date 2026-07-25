package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record C2SRtsSaveCullingStatePayload(
        String dimension,
        List<RtsCullingBoxSnapshot> boxes,
        List<BlockPos> revealed) implements CustomPacketPayload {
    public static final Type<C2SRtsSaveCullingStatePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_save_culling_state"),
            C2SRtsSaveCullingStatePayload.class);
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSaveCullingStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.dimension(), 128);
                        RtsCullingPayloadCodec.write(buf, payload.boxes(), payload.revealed());
                    },
                    buf -> {
                        String dimension = buf.readUtf(128);
                        RtsCullingPayloadCodec.Decoded decoded = RtsCullingPayloadCodec.read(buf);
                        return new C2SRtsSaveCullingStatePayload(
                                dimension, decoded.boxes(), decoded.revealed());
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
