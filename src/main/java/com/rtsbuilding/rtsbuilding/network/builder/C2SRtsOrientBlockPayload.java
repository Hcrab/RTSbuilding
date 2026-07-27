package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 客户端只提交旋转轴与正负一步；方块状态始终由服务端重新读取。 */
public final class C2SRtsOrientBlockPayload implements IMessage {
    private BlockPos pos;
    private byte axisDirection;
    private byte quarterTurns;

    public C2SRtsOrientBlockPayload() {
    }

    public C2SRtsOrientBlockPayload(BlockPos pos, byte axisDirection, byte quarterTurns) {
        this.pos = pos;
        this.axisDirection = axisDirection;
        this.quarterTurns = quarterTurns;
    }

    public C2SRtsOrientBlockPayload(BlockPos pos, EnumFacing axisDirection, int quarterTurns) {
        this(pos, (byte) (axisDirection == null ? -1 : axisDirection.getIndex()),
                (byte) Integer.signum(quarterTurns));
    }

    public BlockPos pos() { return pos; }
    public byte axisDirection() { return axisDirection; }
    public byte quarterTurns() { return quarterTurns; }

    @Override public void fromBytes(ByteBuf buffer) {
        pos = BlockPos.fromLong(buffer.readLong());
        axisDirection = buffer.readByte();
        quarterTurns = buffer.readByte();
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (pos == null) throw new IllegalArgumentException("orientation position must not be null");
        buffer.writeLong(pos.toLong());
        buffer.writeByte(axisDirection);
        buffer.writeByte(quarterTurns);
    }
    public boolean isValid() {
        return pos != null && axisDirection >= 0 && axisDirection < EnumFacing.values().length
                && Math.abs((int) quarterTurns) == 1;
    }
}
