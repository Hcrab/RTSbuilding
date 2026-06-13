package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsRemoteMenuHintPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<S2CRtsRemoteMenuHintPayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_remote_menu_hint"), S2CRtsRemoteMenuHintPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsRemoteMenuHintPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.pos()),
            (buf) -> new S2CRtsRemoteMenuHintPayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
