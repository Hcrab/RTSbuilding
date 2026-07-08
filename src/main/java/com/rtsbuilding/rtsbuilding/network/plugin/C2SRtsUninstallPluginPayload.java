package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsUninstallPluginPayload(String pluginId) implements CustomPacketPayload {
    public static final Type<C2SRtsUninstallPluginPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_uninstall_plugin"),
            C2SRtsUninstallPluginPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUninstallPluginPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.pluginId() == null ? "" : payload.pluginId(), 128),
            (buf) -> new C2SRtsUninstallPluginPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
