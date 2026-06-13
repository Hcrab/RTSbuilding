package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsToggleCameraPayload(boolean startAtPlayerHead) implements CustomPacketPayload {
    public static final Type<C2SRtsToggleCameraPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_toggle_camera"), C2SRtsToggleCameraPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsToggleCameraPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.startAtPlayerHead()),
            (buf) -> new C2SRtsToggleCameraPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
