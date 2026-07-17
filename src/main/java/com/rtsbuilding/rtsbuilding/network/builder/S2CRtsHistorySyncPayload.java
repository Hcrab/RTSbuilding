package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端→客户端：同步当前撤回状态。
 * <p>
 * 每次撤回操作完成后发送，更新客户端的按钮状态。
 *
 * @param undoSize 当前可撤回的步数
 */
public record S2CRtsHistorySyncPayload(
        int undoSize) implements CustomPacketPayload {
    public static final Type<S2CRtsHistorySyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_history_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsHistorySyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.undoSize());
            },
            (buf) -> new S2CRtsHistorySyncPayload(
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
