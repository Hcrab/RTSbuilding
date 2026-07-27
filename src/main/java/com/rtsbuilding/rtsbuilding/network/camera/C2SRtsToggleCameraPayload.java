package com.rtsbuilding.rtsbuilding.network.camera;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 客户端请求切换自己的 RTS 相机会话。 */
public final class C2SRtsToggleCameraPayload implements IMessage {
    private boolean startAtPlayerHead;

    public C2SRtsToggleCameraPayload() {
    }

    public C2SRtsToggleCameraPayload(boolean startAtPlayerHead) {
        this.startAtPlayerHead = startAtPlayerHead;
    }

    public boolean startAtPlayerHead() {
        return startAtPlayerHead;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        startAtPlayerHead = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(startAtPlayerHead);
    }
}
