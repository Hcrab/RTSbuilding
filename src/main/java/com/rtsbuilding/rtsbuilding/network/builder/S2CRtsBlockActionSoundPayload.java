package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 把方块自身的放置/破坏音色发送给操作玩家。
 *
 * <p>客户端以相对监听器、无距离衰减的方式播放；服务端仍负责选择真实方块音色和限流。</p>
 */
public record S2CRtsBlockActionSoundPayload(
        String soundId,
        float volume,
        float pitch,
        boolean breakAction) implements CustomPacketPayload {
    public static final Type<S2CRtsBlockActionSoundPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_block_action_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsBlockActionSoundPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.soundId() == null ? "" : payload.soundId(), 128);
                        buf.writeFloat(payload.volume());
                        buf.writeFloat(payload.pitch());
                        buf.writeBoolean(payload.breakAction());
                    },
                    buf -> new S2CRtsBlockActionSoundPayload(
                            buf.readUtf(128),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
