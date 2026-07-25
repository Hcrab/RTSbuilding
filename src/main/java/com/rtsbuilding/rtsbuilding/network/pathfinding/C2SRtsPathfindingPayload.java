package com.rtsbuilding.rtsbuilding.network.pathfinding;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S payload: request the player character to auto-pathfind to a target position.
 * <p>
 * Only processed when RTS mode is active ({@code RtsCameraManager.isActive(player)}).
 */
public record C2SRtsPathfindingPayload(BlockPos target) implements CustomPacketPayload {

    public static final Type<C2SRtsPathfindingPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_pathfinding"), C2SRtsPathfindingPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPathfindingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.target()),
            buf -> new C2SRtsPathfindingPayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
