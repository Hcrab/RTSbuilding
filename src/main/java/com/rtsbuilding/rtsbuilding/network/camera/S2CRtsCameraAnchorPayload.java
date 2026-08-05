package com.rtsbuilding.rtsbuilding.network.camera;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 服务端更新的相机锚点，用于同步移动边界和建筑区域显示。 */
public final class S2CRtsCameraAnchorPayload implements IMessage {
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private double maxRadius;

    public S2CRtsCameraAnchorPayload() {
    }

    public S2CRtsCameraAnchorPayload(double anchorX, double anchorY, double anchorZ, double maxRadius) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.maxRadius = maxRadius;
    }

    public double anchorX() { return anchorX; }
    public double anchorY() { return anchorY; }
    public double anchorZ() { return anchorZ; }
    public double maxRadius() { return maxRadius; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        anchorX = buffer.readDouble();
        anchorY = buffer.readDouble();
        anchorZ = buffer.readDouble();
        maxRadius = buffer.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeDouble(anchorX);
        buffer.writeDouble(anchorY);
        buffer.writeDouble(anchorZ);
        buffer.writeDouble(maxRadius);
    }
}
