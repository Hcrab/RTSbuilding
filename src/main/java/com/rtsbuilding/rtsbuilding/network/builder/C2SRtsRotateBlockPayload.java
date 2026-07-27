package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 客户端请求将范围内的真实服务端方块绕默认轴旋转一步。 */
public final class C2SRtsRotateBlockPayload implements IMessage {
    private BlockPos pos;

    public C2SRtsRotateBlockPayload() {
    }

    public C2SRtsRotateBlockPayload(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos pos() { return pos; }

    @Override public void fromBytes(ByteBuf buffer) { pos = BlockPos.fromLong(buffer.readLong()); }
    @Override public void toBytes(ByteBuf buffer) {
        if (pos == null) throw new IllegalArgumentException("rotation position must not be null");
        buffer.writeLong(pos.toLong());
    }
    public boolean isValid() { return pos != null; }
}
