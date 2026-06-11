package com.rtsbuilding.rtsbuilding.network.builder;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Server confirmation that an RTS remote placement succeeded. */
public record S2CRtsPlaceAnimationPayload(BlockPos pos, BlockState state) implements CustomPacketPayload {
    public static final Type<S2CRtsPlaceAnimationPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_place_animation"));

    public S2CRtsPlaceAnimationPayload {
        pos = pos == null ? BlockPos.ZERO : pos;
        state = state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsPlaceAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(Block.getId(payload.state()));
            },
            (buf) -> new S2CRtsPlaceAnimationPayload(buf.readBlockPos(), Block.stateById(buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
