package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 服务端确认放置成功后下发的纯视觉提示。 */
public final class S2CRtsPlaceAnimationPayload implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private BlockState state = BlockState.defaultState(Blocks.air);
    public S2CRtsPlaceAnimationPayload() {}
    public S2CRtsPlaceAnimationPayload(BlockPos pos, BlockState state) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.state = state == null ? BlockState.defaultState(Blocks.air) : state;
    }
    public BlockPos pos() { return this.pos; }
    public BlockState state() { return this.state; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.pos = BlockPos.fromLong(buffer.readLong());
        this.state = stateById(RtsPacketBuffer.readVarInt(buffer));
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(this.pos.toLong());
        RtsPacketBuffer.writeVarInt(buffer, BlockState.getStateId(this.state));
    }
    private static BlockState stateById(int id) {
        BlockState decoded = BlockState.getStateById(id);
        return decoded == null ? BlockState.defaultState(Blocks.air) : decoded;
    }
}
