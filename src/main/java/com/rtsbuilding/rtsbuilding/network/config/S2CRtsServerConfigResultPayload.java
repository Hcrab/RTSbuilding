package com.rtsbuilding.rtsbuilding.network.config;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端返回查询/保存 ACK 或无请求号的运行期广播。 */
public record S2CRtsServerConfigResultPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<S2CRtsServerConfigResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_server_config_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsServerConfigResultPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> buf.writeByteArray(value.data()),
                    buf -> new S2CRtsServerConfigResultPayload(buf.readByteArray(RtsServerConfigWire.MAX_PACKET_BYTES)));

    public S2CRtsServerConfigResultPayload {
        RtsServerConfigWire.decodeResponse(data);
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
