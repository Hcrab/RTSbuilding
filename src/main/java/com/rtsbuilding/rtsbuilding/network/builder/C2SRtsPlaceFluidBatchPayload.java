package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能放置批量流体放置请求。
 * <p>携带填充位置列表和流体注册名，服务端批量处理并节流。</p>
 */
public record C2SRtsPlaceFluidBatchPayload(
        List<BlockPos> positions,
        String fluidId)
        implements CustomPacketPayload {

    public static final int MAX_POSITIONS = 32768;

    public static final Type<C2SRtsPlaceFluidBatchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_smart_place_fluid_batch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPlaceFluidBatchPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        int size = Math.min(payload.positions().size(), MAX_POSITIONS);
                        buf.writeVarInt(size);
                        for (int i = 0; i < size; i++) {
                            buf.writeBlockPos(payload.positions().get(i));
                        }
                        buf.writeUtf(payload.fluidId(), 128);
                    },
                    (buf) -> {
                        int size = buf.readVarInt();
                        if (size < 0 || size > MAX_POSITIONS) {
                            throw new IllegalArgumentException("Invalid fluid batch size: " + size);
                        }
                        List<BlockPos> positions = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            positions.add(buf.readBlockPos().immutable());
                        }
                        String fluidId = buf.readUtf(128);
                        return new C2SRtsPlaceFluidBatchPayload(positions, fluidId);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
