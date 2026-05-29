package com.rtsbuilding.rtsbuilding.network;

import java.util.ArrayList;
import java.util.List;

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
        boolean bypassHomeRadius,
        List<String> unlockedNodes,
        List<String> unlockableNodes,
        List<String> costOverrides) implements CustomPacketPayload {
    public static final Type<S2CRtsProgressionStatePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_progression_state"));

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
                int size = Math.min(payload.unlockedNodes().size(), 256);
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeUtf(payload.unlockedNodes().get(i), 128);
                }
                int unlockableSize = Math.min(payload.unlockableNodes().size(), 256);
                buf.writeVarInt(unlockableSize);
                for (int i = 0; i < unlockableSize; i++) {
                    buf.writeUtf(payload.unlockableNodes().get(i), 128);
                }
                int overrideSize = Math.min(payload.costOverrides().size(), 256);
                buf.writeVarInt(overrideSize);
                for (int i = 0; i < overrideSize; i++) {
                    buf.writeUtf(payload.costOverrides().get(i), 640);
                }
            },
            (buf) -> {
                boolean enabled = buf.readBoolean();
                boolean homeSet = buf.readBoolean();
                BlockPos homePos = buf.readBlockPos();
                String homeDimension = buf.readUtf(128);
                long homeCooldownTicks = buf.readLong();
                int radiusBlocks = buf.readVarInt();
                int fluidCapacityBuckets = buf.readVarInt();
                int ultimineLimit = buf.readVarInt();
                boolean bypassHomeRadius = buf.readBoolean();
                int size = buf.readVarInt();
                List<String> unlockedNodes = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    unlockedNodes.add(buf.readUtf(128));
                }
                int unlockableSize = buf.readVarInt();
                List<String> unlockableNodes = new ArrayList<>(unlockableSize);
                for (int i = 0; i < unlockableSize; i++) {
                    unlockableNodes.add(buf.readUtf(128));
                }
                int overrideSize = buf.readVarInt();
                List<String> costOverrides = new ArrayList<>(overrideSize);
                for (int i = 0; i < overrideSize; i++) {
                    costOverrides.add(buf.readUtf(640));
                }
                return new S2CRtsProgressionStatePayload(
                        enabled,
                        homeSet,
                        homePos,
                        homeDimension,
                        homeCooldownTicks,
                        radiusBlocks,
                        fluidCapacityBuckets,
                        ultimineLimit,
                        bypassHomeRadius,
                        unlockedNodes,
                        unlockableNodes,
                        costOverrides);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
