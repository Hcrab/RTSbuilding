package com.rtsbuilding.rtsbuilding.network.config;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigWire;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** 客户端请求当前连接世界的权威 SERVER 配置视图。 */
public record C2SRtsServerConfigQueryPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<C2SRtsServerConfigQueryPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_server_config_query"), C2SRtsServerConfigQueryPayload.class);
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsServerConfigQueryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeByteArray(value.data()),
            buf -> new C2SRtsServerConfigQueryPayload(buf.readByteArray(RtsServerConfigWire.MAX_PACKET_BYTES)));
    public C2SRtsServerConfigQueryPayload { if (!RtsServerConfigWire.decodeRequest(data).isQuery()) throw new IllegalArgumentException("configuration query payload contains an update"); data = data.clone(); }
    @Override public byte[] data() { return data.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
