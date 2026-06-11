package com.rtsbuilding.rtsbuilding.network.storage;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsSetGuiBindingPayload(
        byte slot,
        boolean clear,
        BlockPos pos,
        byte faceId,
        String itemIdHint) implements CustomPacketPayload {
    public static final Type<C2SRtsSetGuiBindingPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_set_gui_binding"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetGuiBindingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.slot());
                buf.writeBoolean(payload.clear());
                buf.writeBlockPos(payload.pos() == null ? BlockPos.ZERO : payload.pos());
                buf.writeByte(payload.faceId());
                buf.writeUtf(payload.itemIdHint() == null ? "" : payload.itemIdHint(), 128);
            },
            (buf) -> new C2SRtsSetGuiBindingPayload(
                    buf.readByte(),
                    buf.readBoolean(),
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readUtf(128)));

    public Direction face() {
        return this.faceId >= 0 && this.faceId < Direction.values().length
                ? Direction.from3DDataValue(this.faceId)
                : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
