package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 服务端确认放置成功后下发的纯视觉提示。 */
public final class S2CRtsPlaceAnimationPayload implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private IBlockState state = Blocks.AIR.getDefaultState();
    public S2CRtsPlaceAnimationPayload() {}
    public S2CRtsPlaceAnimationPayload(BlockPos pos, IBlockState state) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.state = state == null ? Blocks.AIR.getDefaultState() : state;
    }
    public BlockPos pos() { return this.pos; }
    public IBlockState state() { return this.state; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.pos = BlockPos.fromLong(buffer.readLong());
        this.state = stateById(RtsPacketBuffer.readVarInt(buffer));
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(this.pos.toLong());
        RtsPacketBuffer.writeVarInt(buffer, Block.getStateId(this.state));
    }
    private static IBlockState stateById(int id) {
        IBlockState decoded = Block.getStateById(id);
        return decoded == null ? Blocks.AIR.getDefaultState() : decoded;
    }
}
