package com.rtsbuilding.rtsbuilding.network;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsPlaceBatchPayload(
        List<BlockPos> clickedPositions,
        byte face,
        byte rotateSteps,
        boolean forcePlace,
        boolean skipIfOccupied,
        String itemId,
        ItemStack itemPrototype,
        double rayOriginX,
        double rayOriginY,
        double rayOriginZ,
        double rayDirX,
        double rayDirY,
        double rayDirZ) implements CustomPacketPayload {
    public static final int MAX_POSITIONS = 32768;

    public static final Type<C2SRtsPlaceBatchPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_place_batch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPlaceBatchPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                int size = Math.min(payload.clickedPositions().size(), MAX_POSITIONS);
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeBlockPos(payload.clickedPositions().get(i));
                }
                buf.writeByte(payload.face());
                buf.writeByte(payload.rotateSteps());
                buf.writeBoolean(payload.forcePlace());
                buf.writeBoolean(payload.skipIfOccupied());
                buf.writeUtf(payload.itemId(), 128);
                ItemStack itemPrototype = payload.itemPrototype() == null ? ItemStack.EMPTY : payload.itemPrototype();
                buf.writeBoolean(!itemPrototype.isEmpty());
                if (!itemPrototype.isEmpty()) {
                    buf.writeItem(itemPrototype);
                }
                buf.writeDouble(payload.rayOriginX());
                buf.writeDouble(payload.rayOriginY());
                buf.writeDouble(payload.rayOriginZ());
                buf.writeDouble(payload.rayDirX());
                buf.writeDouble(payload.rayDirY());
                buf.writeDouble(payload.rayDirZ());
            },
            (buf) -> {
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_POSITIONS) {
                    throw new IllegalArgumentException("Invalid RTS place batch size: " + size);
                }
                List<BlockPos> positions = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    positions.add(buf.readBlockPos().immutable());
                }
                return new C2SRtsPlaceBatchPayload(
                        positions,
                        buf.readByte(),
                        buf.readByte(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readUtf(128),
                        buf.readBoolean() ? buf.readItem() : ItemStack.EMPTY,
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
