package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server payload: toggle pause state for a specific workflow entry.
 *
 * <p>The server infers the player from the connection context, looks up the
 * workflow entry by ID, and toggles its paused state.</p>
 *
 * @param entryId the immutable workflow entry ID to pause/resume; -1 toggles all entries
 */
public record C2SRtsPauseWorkflowPayload(int entryId) implements CustomPacketPayload {
    public static final Type<C2SRtsPauseWorkflowPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_pause_workflow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPauseWorkflowPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.entryId()),
            (buf) -> new C2SRtsPauseWorkflowPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
