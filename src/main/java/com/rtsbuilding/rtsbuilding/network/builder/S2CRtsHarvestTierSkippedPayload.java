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
 * 服务端回传因采掘等级插件不足而从范围破坏任务中剔除的坐标。
 *
 * <p>该包只修正客户端已经确认的破坏预览，不参与服务端权限判断，也不会把
 * 拿错工具、领地保护或其他失败原因伪装成采掘等级不足。
 */
public record S2CRtsHarvestTierSkippedPayload(
        List<BlockPos> positions) implements CustomPacketPayload {
    public static final int MAX_POSITIONS = C2SRtsAreaDestroyPayload.MAX_POSITIONS;

    public static final Type<S2CRtsHarvestTierSkippedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_harvest_tier_skipped"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsHarvestTierSkippedPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        List<BlockPos> payloadPositions =
                                payload.positions() == null ? List.of() : payload.positions();
                        int size = Math.min(payloadPositions.size(), MAX_POSITIONS);
                        buf.writeVarInt(size);
                        for (int i = 0; i < size; i++) {
                            buf.writeBlockPos(payloadPositions.get(i));
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        if (size < 0 || size > MAX_POSITIONS) {
                            throw new IllegalArgumentException(
                                    "Invalid RTS harvest-tier skipped target count: " + size);
                        }
                        List<BlockPos> positions = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            positions.add(buf.readBlockPos().immutable());
                        }
                        return new S2CRtsHarvestTierSkippedPayload(List.copyOf(positions));
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
