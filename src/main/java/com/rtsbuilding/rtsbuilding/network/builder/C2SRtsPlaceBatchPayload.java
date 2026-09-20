package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record C2SRtsPlaceBatchPayload(
        List<BlockPos> clickedPositions,
        byte face,
        double hitOffsetX,
        double hitOffsetY,
        double hitOffsetZ,
        byte rotateSteps,
        String statePreset,
        boolean forcePlace,
        boolean skipIfOccupied,
        boolean overwriteExisting,
        String itemId,
        ItemStack itemPrototype,
        double rayOriginX,
        double rayOriginY,
        double rayOriginZ,
        double rayDirX,
        double rayDirY,
        double rayDirZ) implements CustomPacketPayload {
    /** 批量建造的完整目标表示边界；编码不能静默裁掉后半部分。 */
    public static final int MAX_POSITIONS = MiningLimits.MAX_VOLUME;

    public static final Type<C2SRtsPlaceBatchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_place_batch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPlaceBatchPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                List<BlockPos> payloadPositions = payload.clickedPositions() == null
                        ? List.of() : payload.clickedPositions();
                if (payloadPositions.size() > MAX_POSITIONS) {
                    throw new IllegalArgumentException(
                            "RTS place batch target count exceeds implementation limit: "
                                    + payloadPositions.size() + " > " + MAX_POSITIONS);
                }
                int size = payloadPositions.size();
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeBlockPos(payloadPositions.get(i));
                }
                buf.writeByte(payload.face());
                buf.writeDouble(payload.hitOffsetX());
                buf.writeDouble(payload.hitOffsetY());
                buf.writeDouble(payload.hitOffsetZ());
                buf.writeByte(payload.rotateSteps());
                buf.writeUtf(payload.statePreset(), 256);
                buf.writeBoolean(payload.forcePlace());
                buf.writeBoolean(payload.skipIfOccupied());
                buf.writeBoolean(payload.overwriteExisting());
                buf.writeUtf(payload.itemId(), 128);
                ItemStack itemPrototype = payload.itemPrototype() == null ? ItemStack.EMPTY : payload.itemPrototype();
                buf.writeBoolean(!itemPrototype.isEmpty());
                if (!itemPrototype.isEmpty()) {
                    ItemStack.STREAM_CODEC.encode(buf, itemPrototype);
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
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readByte(),
                        buf.readUtf(256),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readUtf(128),
                        buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY,
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
