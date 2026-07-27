package com.rtsbuilding.rtsbuilding.network.progression;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 管理员切换生存进度规则。权限仍由服务端 handler 校验。 */
public final class C2SRtsSetSurvivalProgressionPayload implements IMessage {
    private boolean enabled;

    public C2SRtsSetSurvivalProgressionPayload() {
    }

    public C2SRtsSetSurvivalProgressionPayload(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() { return this.enabled; }

    @Override public void fromBytes(ByteBuf buffer) { this.enabled = buffer.readBoolean(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeBoolean(this.enabled); }
}
