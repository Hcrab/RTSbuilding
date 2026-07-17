package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsUninstallPluginPayload(String pluginId) implements CustomPacketPayload {
    public static final Type<C2SRtsUninstallPluginPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_uninstall_plugin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUninstallPluginPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.pluginId() == null ? "" : payload.pluginId(), 128),
            (buf) -> new C2SRtsUninstallPluginPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
