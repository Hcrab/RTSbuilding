package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 服务端下发的完整相机会话快照。 */
public final class S2CRtsCameraStatePayload implements IMessage {
    private boolean enabled;
    private int cameraEntityId;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private double maxRadius;
    private double heightOffset;
    private float yawDeg;
    private float pitchDeg;
    private boolean homeSelection;
    private boolean closeRangeAllowed;

    public S2CRtsCameraStatePayload() {
    }

    public S2CRtsCameraStatePayload(boolean enabled, int cameraEntityId, double anchorX, double anchorY,
            double anchorZ, double maxRadius, double heightOffset, float yawDeg, float pitchDeg,
            boolean homeSelection, boolean closeRangeAllowed) {
        this.enabled = enabled;
        this.cameraEntityId = cameraEntityId;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.maxRadius = maxRadius;
        this.heightOffset = heightOffset;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.homeSelection = homeSelection;
        this.closeRangeAllowed = closeRangeAllowed;
    }

    public boolean enabled() { return enabled; }
    public int cameraEntityId() { return cameraEntityId; }
    public double anchorX() { return anchorX; }
    public double anchorY() { return anchorY; }
    public double anchorZ() { return anchorZ; }
    public double maxRadius() { return maxRadius; }
    public double heightOffset() { return heightOffset; }
    public float yawDeg() { return yawDeg; }
    public float pitchDeg() { return pitchDeg; }
    public boolean homeSelection() { return homeSelection; }
    public boolean closeRangeAllowed() { return closeRangeAllowed; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        enabled = buffer.readBoolean();
        cameraEntityId = RtsPacketBuffer.readVarInt(buffer);
        anchorX = buffer.readDouble();
        anchorY = buffer.readDouble();
        anchorZ = buffer.readDouble();
        maxRadius = buffer.readDouble();
        heightOffset = buffer.readDouble();
        yawDeg = buffer.readFloat();
        pitchDeg = buffer.readFloat();
        homeSelection = buffer.readBoolean();
        closeRangeAllowed = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(enabled);
        RtsPacketBuffer.writeVarInt(buffer, cameraEntityId);
        buffer.writeDouble(anchorX);
        buffer.writeDouble(anchorY);
        buffer.writeDouble(anchorZ);
        buffer.writeDouble(maxRadius);
        buffer.writeDouble(heightOffset);
        buffer.writeFloat(yawDeg);
        buffer.writeFloat(pitchDeg);
        buffer.writeBoolean(homeSelection);
        buffer.writeBoolean(closeRangeAllowed);
    }
}
