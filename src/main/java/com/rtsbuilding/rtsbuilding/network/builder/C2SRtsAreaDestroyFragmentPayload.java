package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Forge 1.20.1 的区域破坏分片协议；大选区在服务器收齐前不会触碰世界。 */
public record C2SRtsAreaDestroyFragmentPayload(
        long traceId,
        long requestId,
        int sequence,
        long clientTick,
        int heldMs,
        byte inputKind,
        byte stopOrigin,
        int fragmentIndex,
        int fragmentCount,
        int totalPositions,
        boolean metadataPresent,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean toolProtectionEnabled,
        List<BlockPos> positions) implements CustomPacketPayload, RtsTracedPayload {

    public static final int SERVERBOUND_MAX_PAYLOAD_BYTES = 32_767;
    public static final int MAX_FRAGMENT_BYTES = 28_000;
    public static final int MAX_POSITIONS_PER_FRAGMENT = 256;
    public static final int MAX_FRAGMENTS =
            (MiningLimits.MAX_VOLUME + MAX_POSITIONS_PER_FRAGMENT - 1) / MAX_POSITIONS_PER_FRAGMENT;

    public static final Type<C2SRtsAreaDestroyFragmentPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_area_destroy_fragment"),
            C2SRtsAreaDestroyFragmentPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaDestroyFragmentPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        int start = buf.writerIndex();
                        validateForWire(payload);
                        buf.writeLong(payload.traceId());
                        buf.writeLong(payload.requestId());
                        buf.writeVarInt(payload.sequence());
                        buf.writeVarLong(payload.clientTick());
                        buf.writeVarInt(Math.max(0, payload.heldMs()));
                        buf.writeByte(payload.inputKind());
                        buf.writeByte(payload.stopOrigin());
                        buf.writeVarInt(payload.fragmentIndex());
                        buf.writeVarInt(payload.fragmentCount());
                        buf.writeVarInt(payload.totalPositions());
                        buf.writeBoolean(payload.metadataPresent());
                        if (payload.metadataPresent()) {
                            buf.writeByte(payload.toolSlot());
                            buf.writeUtf(payload.toolItemId(), 256);
                            ItemStack tool = payload.toolPrototype();
                            buf.writeBoolean(!tool.isEmpty());
                            if (!tool.isEmpty()) RtsForgeBufCodecs.writeItem(buf, tool);
                            buf.writeBoolean(payload.toolProtectionEnabled());
                        }
                        buf.writeVarInt(payload.positions().size());
                        for (BlockPos position : payload.positions()) buf.writeBlockPos(position);
                        int encodedBytes = buf.writerIndex() - start;
                        if (encodedBytes > MAX_FRAGMENT_BYTES) {
                            throw new IllegalArgumentException(
                                    "RTS area destroy fragment exceeds safe C2S budget: "
                                            + encodedBytes + " > " + MAX_FRAGMENT_BYTES);
                        }
                    },
                    buf -> {
                        int start = buf.readerIndex();
                        long traceId = buf.readLong();
                        long requestId = buf.readLong();
                        int sequence = buf.readVarInt();
                        long clientTick = buf.readVarLong();
                        int heldMs = buf.readVarInt();
                        byte inputKind = buf.readByte();
                        byte stopOrigin = buf.readByte();
                        int fragmentIndex = buf.readVarInt();
                        int fragmentCount = buf.readVarInt();
                        int totalPositions = buf.readVarInt();
                        boolean metadataPresent = buf.readBoolean();
                        byte toolSlot = 0;
                        String toolItemId = "";
                        ItemStack toolPrototype = ItemStack.EMPTY;
                        boolean toolProtectionEnabled = false;
                        if (metadataPresent) {
                            toolSlot = buf.readByte();
                            toolItemId = buf.readUtf(256);
                            toolPrototype = buf.readBoolean() ? RtsForgeBufCodecs.readItem(buf) : ItemStack.EMPTY;
                            toolProtectionEnabled = buf.readBoolean();
                        }
                        int size = buf.readVarInt();
                        if (size < 0 || size > MAX_POSITIONS_PER_FRAGMENT) {
                            throw new IllegalArgumentException("Invalid RTS area destroy fragment target count: " + size);
                        }
                        List<BlockPos> positions = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) positions.add(buf.readBlockPos().immutable());
                        if (buf.readerIndex() - start > MAX_FRAGMENT_BYTES) {
                            throw new IllegalArgumentException("RTS area destroy fragment exceeds safe C2S budget");
                        }
                        C2SRtsAreaDestroyFragmentPayload payload = new C2SRtsAreaDestroyFragmentPayload(
                                traceId, requestId, sequence, clientTick, heldMs, inputKind, stopOrigin,
                                fragmentIndex, fragmentCount, totalPositions, metadataPresent, toolSlot,
                                toolItemId, toolPrototype, toolProtectionEnabled, positions);
                        validateForWire(payload);
                        return payload;
                    });

    public C2SRtsAreaDestroyFragmentPayload {
        toolItemId = toolItemId == null ? "" : toolItemId;
        toolPrototype = toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copy();
        positions = positions == null ? List.of() : positions.stream().map(BlockPos::immutable).toList();
    }

    public static List<C2SRtsAreaDestroyFragmentPayload> split(
            long traceId, long requestId, int sequence, long clientTick, int heldMs,
            byte inputKind, byte stopOrigin, List<BlockPos> positions, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        List<BlockPos> safePositions = positions == null ? List.of() : List.copyOf(positions);
        if (safePositions.isEmpty()) return List.of();
        if (safePositions.size() > MiningLimits.MAX_VOLUME) {
            throw new IllegalArgumentException("RTS area destroy target count exceeds implementation limit: "
                    + safePositions.size() + " > " + MiningLimits.MAX_VOLUME);
        }
        if (requestId == 0L) throw new IllegalArgumentException("RTS area destroy request id must be non-zero");
        int total = safePositions.size();
        int fragmentCount = (total + MAX_POSITIONS_PER_FRAGMENT - 1) / MAX_POSITIONS_PER_FRAGMENT;
        List<C2SRtsAreaDestroyFragmentPayload> result = new ArrayList<>(fragmentCount);
        for (int index = 0; index < fragmentCount; index++) {
            int from = index * MAX_POSITIONS_PER_FRAGMENT;
            int to = Math.min(total, from + MAX_POSITIONS_PER_FRAGMENT);
            boolean metadata = index == 0;
            result.add(new C2SRtsAreaDestroyFragmentPayload(
                    traceId, requestId, sequence, clientTick, heldMs, inputKind, stopOrigin,
                    index, fragmentCount, total, metadata,
                    metadata ? toolSlot : (byte) 0,
                    metadata ? toolItemId : "",
                    metadata ? toolPrototype : ItemStack.EMPTY,
                    metadata && toolProtectionEnabled,
                    safePositions.subList(from, to)));
        }
        return List.copyOf(result);
    }

    /** 在发送前使用真实 Forge codec 检查工具 NBT 和每片预算。 */
    public static void validateTransfer(List<C2SRtsAreaDestroyFragmentPayload> fragments) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            for (C2SRtsAreaDestroyFragmentPayload fragment : fragments) {
                buffer.clear();
                STREAM_CODEC.encode(buffer, fragment);
            }
        } finally {
            buffer.release();
        }
    }

    private static void validateForWire(C2SRtsAreaDestroyFragmentPayload payload) {
        if (payload == null || payload.requestId() == 0L) {
            throw new IllegalArgumentException("RTS area destroy fragment request identity is invalid");
        }
        if (payload.fragmentCount() < 1 || payload.fragmentCount() > MAX_FRAGMENTS
                || payload.fragmentIndex() < 0 || payload.fragmentIndex() >= payload.fragmentCount()) {
            throw new IllegalArgumentException("RTS area destroy fragment index/count is invalid");
        }
        if (payload.totalPositions() < 1 || payload.totalPositions() > MiningLimits.MAX_VOLUME) {
            throw new IllegalArgumentException("RTS area destroy total target count is invalid");
        }
        List<BlockPos> positions = payload.positions();
        if (positions == null || positions.isEmpty() || positions.size() > MAX_POSITIONS_PER_FRAGMENT
                || positions.size() > payload.totalPositions()) {
            throw new IllegalArgumentException("RTS area destroy fragment target count is invalid");
        }
        if (!payload.metadataPresent() && (!payload.toolItemId().isEmpty()
                || !payload.toolPrototype().isEmpty() || payload.toolProtectionEnabled())) {
            throw new IllegalArgumentException("RTS area destroy fragment metadata flag is invalid");
        }
        if (payload.metadataPresent() && (payload.toolSlot() < 0 || payload.toolSlot() > 8)) {
            throw new IllegalArgumentException("RTS area destroy tool slot is invalid");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
