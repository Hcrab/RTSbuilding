package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端→客户端：同步当前撤回状???
 * <p>
 * 每次撤回操作完成后发送，更新客户端的按钮状???
 *
 * @param undoSize 当前可撤回的步数
 */
public record S2CRtsHistorySyncPayload(
        int undoSize) implements CustomPacketPayload {
    public static final Type<S2CRtsHistorySyncPayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_history_sync"), S2CRtsHistorySyncPayload.class);

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
