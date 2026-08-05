package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/** 客户端上传蓝图并请求在指定锚点创建服务端权威放置任务。 */
public final class C2SBlueprintPlacePayload implements IMessage {
    public static final int MAX_FILE_NAME_CHARS = 160;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    private UUID submissionId;
    private String fileName;
    private byte[] data;
    private BlockPos anchor;
    private byte yRotationSteps;
    private byte xRotationSteps;
    private byte zRotationSteps;

    public C2SBlueprintPlacePayload() {
    }

    public C2SBlueprintPlacePayload(UUID submissionId, String fileName, byte[] data, BlockPos anchor,
            byte yRotationSteps, byte xRotationSteps, byte zRotationSteps) {
        this.submissionId = submissionId;
        this.fileName = fileName == null ? "" : fileName;
        this.data = data == null ? new byte[0] : data;
        this.anchor = anchor;
        this.yRotationSteps = yRotationSteps;
        this.xRotationSteps = xRotationSteps;
        this.zRotationSteps = zRotationSteps;
    }

    public UUID submissionId() { return submissionId; }
    public String fileName() { return fileName; }
    public byte[] data() { return data; }
    public BlockPos anchor() { return anchor; }
    public byte yRotationSteps() { return yRotationSteps; }
    public byte xRotationSteps() { return xRotationSteps; }
    public byte zRotationSteps() { return zRotationSteps; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        submissionId = RtsPacketBuffer.readUuid(buffer);
        fileName = RtsPacketBuffer.readString(buffer, MAX_FILE_NAME_CHARS, "blueprint file name");
        data = RtsPacketBuffer.readByteArray(buffer, MAX_FILE_BYTES, "blueprint data");
        anchor = BlockPos.fromLong(buffer.readLong());
        yRotationSteps = buffer.readByte();
        xRotationSteps = buffer.readByte();
        zRotationSteps = buffer.readByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeUuid(buffer, submissionId);
        RtsPacketBuffer.writeString(buffer, fileName, MAX_FILE_NAME_CHARS, "blueprint file name");
        RtsPacketBuffer.writeByteArray(buffer, data, MAX_FILE_BYTES, "blueprint data");
        if (anchor == null) throw new IllegalArgumentException("blueprint anchor must not be null");
        buffer.writeLong(anchor.toLong());
        buffer.writeByte(yRotationSteps);
        buffer.writeByte(xRotationSteps);
        buffer.writeByte(zRotationSteps);
    }

    public boolean isValid() {
        return submissionId != null && fileName != null && !fileName.isEmpty()
                && fileName.length() <= MAX_FILE_NAME_CHARS
                && data != null && data.length > 0 && data.length <= MAX_FILE_BYTES
                && anchor != null && validRotation(yRotationSteps)
                && validRotation(xRotationSteps) && validRotation(zRotationSteps);
    }

    private static boolean validRotation(byte steps) {
        return steps >= -3 && steps <= 3;
    }
}
