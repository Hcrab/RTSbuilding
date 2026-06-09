package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Server confirmation that an RTS remote break succeeded. */
public record S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state) implements CustomPacketPayload {
    public static final Type<S2CRtsBreakAnimationPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_break_animation"));

    public S2CRtsBreakAnimationPayload {
        pos = pos == null ? BlockPos.ZERO : pos;
        state = state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsBreakAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(Block.getId(payload.state()));
            },
            (buf) -> new S2CRtsBreakAnimationPayload(buf.readBlockPos(), Block.stateById(buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
