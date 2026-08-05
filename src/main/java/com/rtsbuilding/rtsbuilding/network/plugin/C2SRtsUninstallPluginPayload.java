package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

public final class C2SRtsUninstallPluginPayload implements IMessage {
    private static final int MAX_PLUGIN_ID_CHARS = 128;
    private String pluginId = "";

    public C2SRtsUninstallPluginPayload() {
    }

    public C2SRtsUninstallPluginPayload(String pluginId) {
        this.pluginId = pluginId == null ? "" : pluginId;
    }

    public String pluginId() { return pluginId; }
    public boolean isValid() { return !pluginId.trim().isEmpty() && pluginId.length() <= MAX_PLUGIN_ID_CHARS; }

    @Override public void fromBytes(ByteBuf buffer) {
        pluginId = RtsPacketBuffer.readString(buffer, MAX_PLUGIN_ID_CHARS, "plugin id");
    }

    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("plugin id is invalid");
        RtsPacketBuffer.writeString(buffer, pluginId, MAX_PLUGIN_ID_CHARS, "plugin id");
    }
}
