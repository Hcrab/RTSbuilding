package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 世界旋转圆弧提交的一步旋转意图。
 *
 * <p>客户端只发送带正负号的旋转轴和 ±90° 步数，不发送 BlockState 或任意属性集合；
 * 服务端根据当前位置的真实状态重新解析。</p>
 */
public record C2SRtsOrientBlockPayload(
        BlockPos pos,
        byte axisDirection,
        byte quarterTurns)
        implements CustomPacketPayload {
    public static final Type<C2SRtsOrientBlockPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    RtsbuildingMod.MODID, "c2s_rts_orient_block"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsOrientBlockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos());
                        buf.writeByte(payload.axisDirection());
                        buf.writeByte(payload.quarterTurns());
                    },
                    buf -> new C2SRtsOrientBlockPayload(
                            buf.readBlockPos(),
                            buf.readByte(),
                            buf.readByte()));

    public C2SRtsOrientBlockPayload(
            BlockPos pos,
            Direction axisDirection,
            int quarterTurns) {
        this(
                pos,
                (byte) (axisDirection == null ? -1 : axisDirection.get3DDataValue()),
                (byte) Integer.signum(quarterTurns));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
