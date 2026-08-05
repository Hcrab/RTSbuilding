package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 带 trace 的显式区域破坏 v2 包。 */
public record C2SRtsAreaDestroyTracePayload(
        long traceId,
        int sequence,
        long clientTick,
        int heldMs,
        byte inputKind,
        byte stopOrigin,
        List<BlockPos> positions,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean toolProtectionEnabled) implements CustomPacketPayload, RtsTracedPayload {
    public static final Type<C2SRtsAreaDestroyTracePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_area_destroy_v2"), C2SRtsAreaDestroyTracePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaDestroyTracePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                C2SRtsMineTracePayload.writeTraceHeader(buf, payload.traceId(), payload.sequence(),
                        payload.clientTick(), payload.heldMs(), payload.inputKind(), payload.stopOrigin());
                List<BlockPos> positions = payload.positions() == null ? List.of() : payload.positions();
                int size = Math.min(positions.size(), C2SRtsAreaDestroyPayload.MAX_POSITIONS);
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) buf.writeBlockPos(positions.get(i));
                buf.writeByte(payload.toolSlot());
                C2SRtsMineTracePayload.writeTool(buf, payload.toolItemId(), payload.toolPrototype());
                buf.writeBoolean(payload.toolProtectionEnabled());
            },
            buf -> {
                var header = C2SRtsMineTracePayload.readTraceHeader(buf);
                int size = buf.readVarInt();
                if (size < 0 || size > C2SRtsAreaDestroyPayload.MAX_POSITIONS) {
                    throw new IllegalArgumentException("Invalid RTS area destroy target count: " + size);
                }
                List<BlockPos> positions = new ArrayList<>(size);
                for (int i = 0; i < size; i++) positions.add(buf.readBlockPos().immutable());
                byte toolSlot = buf.readByte();
                String toolId = buf.readUtf(256);
                ItemStack tool = buf.readBoolean() ? RtsForgeBufCodecs.readItem(buf) : ItemStack.EMPTY;
                return new C2SRtsAreaDestroyTracePayload(
                        header.traceId(), header.sequence(), header.clientTick(), header.heldMs(),
                        header.inputKind(), header.stopOrigin(), positions, toolSlot, toolId, tool,
                        buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
