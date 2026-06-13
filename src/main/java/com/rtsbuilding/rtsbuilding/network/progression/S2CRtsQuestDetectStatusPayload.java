package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsQuestDetectStatusPayload(
        byte phase,
        int scannedTasks,
        int totalTasks,
        int completedTasks) implements CustomPacketPayload {
    public static final byte PHASE_STARTED = 0;
    public static final byte PHASE_COMPLETE = 1;
    public static final byte PHASE_UNAVAILABLE = 2;
    public static final byte PHASE_ERROR = 3;

    public static final Type<S2CRtsQuestDetectStatusPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_quest_detect_status"), S2CRtsQuestDetectStatusPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsQuestDetectStatusPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.phase());
                        buf.writeVarInt(Math.max(0, payload.scannedTasks()));
                        buf.writeVarInt(Math.max(0, payload.totalTasks()));
                        buf.writeVarInt(Math.max(0, payload.completedTasks()));
                    },
                    (buf) -> new S2CRtsQuestDetectStatusPayload(
                            buf.readByte(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
