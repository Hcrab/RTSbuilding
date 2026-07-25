package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端回传因采掘等级插件不足而从范围破坏任务中剔除的坐标。
 *
 * <p>该包只修正客户端预览，不参与权限判定，也不把错误工具或领地拒绝
 * 伪装成采掘等级不足。</p>
 */
public record S2CRtsHarvestTierSkippedPayload(
        List<BlockPos> positions) implements CustomPacketPayload {
    public static final int MAX_POSITIONS = C2SRtsAreaDestroyPayload.MAX_POSITIONS;

    public static final Type<S2CRtsHarvestTierSkippedPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_harvest_tier_skipped"),
            S2CRtsHarvestTierSkippedPayload.class);

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
