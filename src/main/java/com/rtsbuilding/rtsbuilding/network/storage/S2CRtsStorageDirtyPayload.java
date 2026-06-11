package com.rtsbuilding.rtsbuilding.network.storage;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsStorageDirtyPayload(boolean dirty) implements CustomPacketPayload {
    public static final Type<S2CRtsStorageDirtyPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_storage_dirty"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsStorageDirtyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.dirty()),
            (buf) -> new S2CRtsStorageDirtyPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
