package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsUpdateLinkedStoragePayload(BlockPos pos, byte linkMode, int priority) implements CustomPacketPayload {
    public static final Type<C2SRtsUpdateLinkedStoragePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_update_linked_storage"), C2SRtsUpdateLinkedStoragePayload.class);

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
