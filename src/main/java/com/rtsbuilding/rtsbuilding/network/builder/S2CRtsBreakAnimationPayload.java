package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server confirmation that an RTS block break actually succeeded.
 *
 * <p>The client treats this as a visual cue plus the server-confirmed post-break
 * block state for local fake-air cleanup. It must not drive tool durability,
 * drops, or retry behaviour; those remain authoritative on the server-side
 * mining path.
 */
public record S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state, BlockState resultState) implements CustomPacketPayload {
    public static final Type<S2CRtsBreakAnimationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_break_animation"));

    public S2CRtsBreakAnimationPayload {
        pos = pos == null ? BlockPos.ZERO : pos;
        state = state == null ? Blocks.AIR.defaultBlockState() : state;
        resultState = resultState == null ? Blocks.AIR.defaultBlockState() : resultState;
    }

    public S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state) {
        this(pos, state, Blocks.AIR.defaultBlockState());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsBreakAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(Block.getId(payload.state()));
                buf.writeVarInt(Block.getId(payload.resultState()));
            },
            (buf) -> new S2CRtsBreakAnimationPayload(
                    buf.readBlockPos(),
                    Block.stateById(buf.readVarInt()),
                    Block.stateById(buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
