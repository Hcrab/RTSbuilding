package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsProgressionStatePayload(
        boolean enabled,
        boolean homeSet,
        BlockPos homePos,
        String homeDimension,
        long homeCooldownTicks,
        int radiusBlocks,
        int fluidCapacityBuckets,
        int ultimineLimit,
        boolean bypassHomeRadius) implements CustomPacketPayload {
    public static final Type<S2CRtsProgressionStatePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_progression_state"), S2CRtsProgressionStatePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsProgressionStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.enabled());
                buf.writeBoolean(payload.homeSet());
                buf.writeBlockPos(payload.homePos());
                buf.writeUtf(payload.homeDimension() == null ? "" : payload.homeDimension(), 128);
                buf.writeLong(Math.max(0L, payload.homeCooldownTicks()));
                buf.writeVarInt(Math.max(0, payload.radiusBlocks()));
                buf.writeVarInt(Math.max(0, payload.fluidCapacityBuckets()));
                buf.writeVarInt(Math.max(0, payload.ultimineLimit()));
                buf.writeBoolean(payload.bypassHomeRadius());
            },
            (buf) -> new S2CRtsProgressionStatePayload(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBlockPos(),
                    buf.readUtf(128),
                    buf.readLong(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
