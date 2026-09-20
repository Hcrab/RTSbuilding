package com.rtsbuilding.rtsbuilding.network.config;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端请求当前连接世界的权威 SERVER 配置视图。 */
public record C2SRtsServerConfigQueryPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<C2SRtsServerConfigQueryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_server_config_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsServerConfigQueryPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> buf.writeByteArray(value.data()),
                    buf -> new C2SRtsServerConfigQueryPayload(buf.readByteArray(RtsServerConfigWire.MAX_PACKET_BYTES)));

    public C2SRtsServerConfigQueryPayload {
        if (!RtsServerConfigWire.decodeRequest(data).isQuery()) {
            throw new IllegalArgumentException("configuration query payload contains an update");
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
