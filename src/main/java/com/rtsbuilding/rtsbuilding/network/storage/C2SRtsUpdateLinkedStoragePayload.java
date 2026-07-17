package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsUpdateLinkedStoragePayload(BlockPos pos, byte linkMode, int priority) implements CustomPacketPayload {
    public static final Type<C2SRtsUpdateLinkedStoragePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_update_linked_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUpdateLinkedStoragePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.linkMode());
                buf.writeVarInt(payload.priority());
            },
            (buf) -> new C2SRtsUpdateLinkedStoragePayload(buf.readBlockPos(), buf.readByte(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
