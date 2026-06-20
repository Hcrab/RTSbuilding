package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsInstallPluginPayload(int inventorySlot) implements CustomPacketPayload {
    public static final Type<C2SRtsInstallPluginPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_install_plugin"), C2SRtsInstallPluginPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsInstallPluginPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.inventorySlot()),
            (buf) -> new C2SRtsInstallPluginPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
