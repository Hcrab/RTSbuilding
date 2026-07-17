package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server confirmation that an RTS block placement actually succeeded.
 *
 * <p>The client treats this as a purely visual cue. It must not drive gameplay
 * state, inventory counts, undo history, or placement retries; those stay
 * authoritative on the server-side placement path.
 */
public record S2CRtsPlaceAnimationPayload(BlockPos pos, BlockState state) implements CustomPacketPayload {
    public static final Type<S2CRtsPlaceAnimationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_place_animation"));

    public S2CRtsPlaceAnimationPayload {
        pos = pos == null ? BlockPos.ZERO : pos;
        state = state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsPlaceAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(Block.getId(payload.state()));
            },
            (buf) -> new S2CRtsPlaceAnimationPayload(
                    buf.readBlockPos(),
                    Block.stateById(buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
