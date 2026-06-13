package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload for ultimine overall progress.
 *
 * <p>{@code processed < 0} indicates that ultimine has ended / no progress to display.
 * When {@code processed >= 0} and {@code total > 0}, the UI should draw a progress
 * bar reflecting {@code processed / total}.
 */
public record S2CRtsUltimineProgressPayload(
        int processed,
        int total) implements CustomPacketPayload {
    public static final Type<S2CRtsUltimineProgressPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_ultimine_progress"), S2CRtsUltimineProgressPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsUltimineProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.processed());
                buf.writeInt(payload.total());
            },
            (buf) -> new S2CRtsUltimineProgressPayload(
                    buf.readInt(),
                    buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
