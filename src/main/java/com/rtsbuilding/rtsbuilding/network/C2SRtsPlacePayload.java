package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsPlacePayload(
        BlockPos clickedPos,
        byte face,
        double hitX,
        double hitY,
        double hitZ,
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
        double rayDirZ,
        boolean quickBuild,
        boolean forceEmptyHand) implements CustomPacketPayload {
    public static final Type<C2SRtsPlacePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_place"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPlacePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.clickedPos());
                buf.writeByte(payload.face());
                buf.writeDouble(payload.hitX());
                buf.writeDouble(payload.hitY());
                buf.writeDouble(payload.hitZ());
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
                buf.writeBoolean(payload.quickBuild());
                buf.writeBoolean(payload.forceEmptyHand());
            },
            (buf) -> new C2SRtsPlacePayload(
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
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
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
