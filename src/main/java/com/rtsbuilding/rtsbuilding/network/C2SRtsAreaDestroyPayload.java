package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsAreaDestroyPayload(
        List<BlockPos> positions,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean protectTool,
        boolean replaceTool) implements CustomPacketPayload {
    public static final int MAX_POSITIONS = 32768;

    public static final Type<C2SRtsAreaDestroyPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_area_destroy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaDestroyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                List<BlockPos> payloadPositions = payload.positions() == null ? List.of() : payload.positions();
                int size = Math.min(payloadPositions.size(), MAX_POSITIONS);
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeBlockPos(payloadPositions.get(i));
                }
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                ItemStack toolPrototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
                buf.writeBoolean(!toolPrototype.isEmpty());
                if (!toolPrototype.isEmpty()) {
                    buf.writeItem(toolPrototype);
                }
                buf.writeBoolean(payload.protectTool());
                buf.writeBoolean(payload.replaceTool());
            },
            (buf) -> {
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_POSITIONS) {
                    throw new IllegalArgumentException("Invalid RTS area destroy target count: " + size);
                }
                List<BlockPos> positions = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    positions.add(buf.readBlockPos().immutable());
                }
                return new C2SRtsAreaDestroyPayload(
                        positions,
                        buf.readByte(),
                        buf.readUtf(256),
                        buf.readBoolean() ? buf.readItem() : ItemStack.EMPTY,
                        buf.readBoolean(),
                        buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
