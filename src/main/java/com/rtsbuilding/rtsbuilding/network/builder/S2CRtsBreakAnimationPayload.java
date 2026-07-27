package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 服务端确认破坏成功后下发的视觉状态与最终方块状态。 */
public final class S2CRtsBreakAnimationPayload implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private IBlockState state = Blocks.AIR.getDefaultState();
    private IBlockState resultState = Blocks.AIR.getDefaultState();
    public S2CRtsBreakAnimationPayload() {}
    public S2CRtsBreakAnimationPayload(BlockPos pos, IBlockState state) {
        this(pos, state, Blocks.AIR.getDefaultState());
    }
    public S2CRtsBreakAnimationPayload(BlockPos pos, IBlockState state, IBlockState resultState) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.state = state == null ? Blocks.AIR.getDefaultState() : state;
        this.resultState = resultState == null ? Blocks.AIR.getDefaultState() : resultState;
    }
    public BlockPos pos() { return this.pos; }
    public IBlockState state() { return this.state; }
    public IBlockState resultState() { return this.resultState; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.pos = BlockPos.fromLong(buffer.readLong());
        this.state = stateById(RtsPacketBuffer.readVarInt(buffer));
        this.resultState = stateById(RtsPacketBuffer.readVarInt(buffer));
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(this.pos.toLong());
        RtsPacketBuffer.writeVarInt(buffer, Block.getStateId(this.state));
        RtsPacketBuffer.writeVarInt(buffer, Block.getStateId(this.resultState));
    }
    private static IBlockState stateById(int id) {
        IBlockState decoded = Block.getStateById(id);
        return decoded == null ? Blocks.AIR.getDefaultState() : decoded;
    }
}
