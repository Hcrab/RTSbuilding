package com.rtsbuilding.rtsbuilding.network.progression;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 提交家园坐标；合法性和可提交条件由服务端进度管理器决定。 */
public final class C2SRtsSetHomePayload implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;

    public C2SRtsSetHomePayload() {
    }

    public C2SRtsSetHomePayload(BlockPos pos) {
        if (pos == null) throw new IllegalArgumentException("pos");
        this.pos = pos;
    }

    public BlockPos pos() { return this.pos; }

    @Override public void fromBytes(ByteBuf buffer) { this.pos = BlockPos.fromLong(buffer.readLong()); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeLong(this.pos.toLong()); }
}
