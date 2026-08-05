package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 服务端确认破坏成功后下发的视觉状态与最终方块状态。 */
public final class S2CRtsBreakAnimationPayload implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private BlockState state = BlockState.defaultState(Blocks.air);
    private BlockState resultState = BlockState.defaultState(Blocks.air);
    public S2CRtsBreakAnimationPayload() {}
    public S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state) {
        this(pos, state, BlockState.defaultState(Blocks.air));
    }
    public S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state, BlockState resultState) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.state = state == null ? BlockState.defaultState(Blocks.air) : state;
        this.resultState = resultState == null ? BlockState.defaultState(Blocks.air) : resultState;
    }
    public BlockPos pos() { return this.pos; }
    public BlockState state() { return this.state; }
    public BlockState resultState() { return this.resultState; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.pos = BlockPos.fromLong(buffer.readLong());
        this.state = stateById(RtsPacketBuffer.readVarInt(buffer));
        this.resultState = stateById(RtsPacketBuffer.readVarInt(buffer));
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(this.pos.toLong());
        RtsPacketBuffer.writeVarInt(buffer, BlockState.getStateId(this.state));
        RtsPacketBuffer.writeVarInt(buffer, BlockState.getStateId(this.resultState));
    }
    private static BlockState stateById(int id) {
        BlockState decoded = BlockState.getStateById(id);
        return decoded == null ? BlockState.defaultState(Blocks.air) : decoded;
    }
}
