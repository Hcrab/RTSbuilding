package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * 把方块自身的放置/破坏音色发送给操作玩家。
 *
 * <p>客户端以相对监听器、无距离衰减的方式播放；服务端仍负责选择真实方块音色和限流。</p>
 */
public final class S2CRtsBlockActionSoundPayload implements IMessage {
    private String soundId;
    private float volume;
    private float pitch;
    private boolean breakAction;

    public S2CRtsBlockActionSoundPayload() {
    }

    public S2CRtsBlockActionSoundPayload(String soundId, float volume, float pitch, boolean breakAction) {
        this.soundId = soundId == null ? "" : soundId;
        this.volume = volume;
        this.pitch = pitch;
        this.breakAction = breakAction;
    }

    public String soundId() { return soundId; }
    public float volume() { return volume; }
    public float pitch() { return pitch; }
    public boolean breakAction() { return breakAction; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        soundId = RtsPacketBuffer.readString(buffer, 128, "sound id");
        volume = buffer.readFloat();
        pitch = buffer.readFloat();
        breakAction = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeString(buffer, soundId, 128, "sound id");
        buffer.writeFloat(volume);
        buffer.writeFloat(pitch);
        buffer.writeBoolean(breakAction);
    }
}
