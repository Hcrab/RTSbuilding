package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 客户端提交的一帧相机输入；服务端仍持有会话、位置和边界的最终权威。 */
public final class C2SRtsCameraMovePayload implements IMessage {
    private float forward;
    private float strafe;
    private float vertical;
    private float panX;
    private float panY;
    private float rotateX;
    private float rotateY;
    private float scroll;
    private int rotateSteps;
    private boolean fast;

    public C2SRtsCameraMovePayload() {
    }

    public C2SRtsCameraMovePayload(float forward, float strafe, float vertical, float panX, float panY,
            float rotateX, float rotateY, float scroll, int rotateSteps, boolean fast) {
        this.forward = forward;
        this.strafe = strafe;
        this.vertical = vertical;
        this.panX = panX;
        this.panY = panY;
        this.rotateX = rotateX;
        this.rotateY = rotateY;
        this.scroll = scroll;
        this.rotateSteps = rotateSteps;
        this.fast = fast;
    }

    public float forward() { return forward; }
    public float strafe() { return strafe; }
    public float vertical() { return vertical; }
    public float panX() { return panX; }
    public float panY() { return panY; }
    public float rotateX() { return rotateX; }
    public float rotateY() { return rotateY; }
    public float scroll() { return scroll; }
    public int rotateSteps() { return rotateSteps; }
    public boolean fast() { return fast; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        forward = buffer.readFloat();
        strafe = buffer.readFloat();
        vertical = buffer.readFloat();
        panX = buffer.readFloat();
        panY = buffer.readFloat();
        rotateX = buffer.readFloat();
        rotateY = buffer.readFloat();
        scroll = buffer.readFloat();
        rotateSteps = RtsPacketBuffer.readVarInt(buffer);
        fast = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeFloat(forward);
        buffer.writeFloat(strafe);
        buffer.writeFloat(vertical);
        buffer.writeFloat(panX);
        buffer.writeFloat(panY);
        buffer.writeFloat(rotateX);
        buffer.writeFloat(rotateY);
        buffer.writeFloat(scroll);
        RtsPacketBuffer.writeVarInt(buffer, rotateSteps);
        buffer.writeBoolean(fast);
    }

    /** 在进入服务端业务逻辑前拒绝 NaN、无穷值和不合理的放大输入。 */
    public boolean isValid() {
        return bounded(forward, 1.0F)
                && bounded(strafe, 1.0F)
                && bounded(vertical, 1.0F)
                && bounded(panX, 4096.0F)
                && bounded(panY, 4096.0F)
                && bounded(rotateX, 120.0F)
                && bounded(rotateY, 120.0F)
                && bounded(scroll, 32.0F)
                && rotateSteps >= -4 && rotateSteps <= 4;
    }

    private static boolean bounded(float value, float maximumAbsoluteValue) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && Math.abs(value) <= maximumAbsoluteValue;
    }
}
