package com.rtsbuilding.rtsbuilding.network.config;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端提交白名单 typed SERVER 配置变更；服务端不接受文件内容或权限布尔值。 */
public record C2SRtsServerConfigUpdatePayload(byte[] data) implements CustomPacketPayload {
    public static final Type<C2SRtsServerConfigUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_server_config_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsServerConfigUpdatePayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> buf.writeByteArray(value.data()),
                    buf -> new C2SRtsServerConfigUpdatePayload(buf.readByteArray(RtsServerConfigWire.MAX_PACKET_BYTES)));

    public C2SRtsServerConfigUpdatePayload {
        RtsServerConfigWire.Request request = RtsServerConfigWire.decodeRequest(data);
        if (request.isQuery()) {
            throw new IllegalArgumentException("configuration update payload contains a query");
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
