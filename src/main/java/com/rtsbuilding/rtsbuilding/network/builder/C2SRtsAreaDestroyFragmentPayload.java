package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域破坏的独立 C2S 分片协议。
 *
 * <p>旧的 {@link C2SRtsAreaDestroyTracePayload} 仍用于接收兼容，但它不能承载一个
 * 合法的 262144 目标区域：Minecraft 的 serverbound custom payload 只有 32767 字节，
 * 而每个 {@link BlockPos} 固定占 8 字节。本协议把坐标拆成小片；工具和保护等请求
 * 元数据只随一个分片发送，服务端在收到完整请求前绝不会触碰世界。</p>
 */
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

    /** Minecraft 1.21.1 ServerboundCustomPayloadPacket 的硬上限。 */
    public static final int SERVERBOUND_MAX_PAYLOAD_BYTES = 32_767;

    /** 给包头、资源标识和工具 NBT 留出余量后的单分片预算。 */
    public static final int MAX_FRAGMENT_BYTES = 28_000;

    /** 坐标每个 8 字节；256 个坐标约 2 KiB，足以容纳常见工具 NBT。 */
    public static final int MAX_POSITIONS_PER_FRAGMENT = 256;

    /** 262144 / 256；重组器也以此限制分片数组大小。 */
    public static final int MAX_FRAGMENTS =
            (MiningLimits.MAX_VOLUME + MAX_POSITIONS_PER_FRAGMENT - 1)
                    / MAX_POSITIONS_PER_FRAGMENT;

    public static final Type<C2SRtsAreaDestroyFragmentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID,
                    "c2s_rts_area_destroy_fragment"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaDestroyFragmentPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        int start = buf.writerIndex();
                        validateForWire(payload);

                        // traceId 保持为网络包首字段，便于现有 trace 诊断按同一意图关联。
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
                            buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                            ItemStack tool = payload.toolPrototype() == null
                                    ? ItemStack.EMPTY : payload.toolPrototype();
                            buf.writeBoolean(!tool.isEmpty());
                            if (!tool.isEmpty()) ItemStack.STREAM_CODEC.encode(buf, tool);
                            buf.writeBoolean(payload.toolProtectionEnabled());
                        }
                        List<BlockPos> positions = payload.positions() == null
                                ? List.of() : payload.positions();
                        buf.writeVarInt(positions.size());
                        for (BlockPos position : positions) buf.writeBlockPos(position);

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
                            toolPrototype = buf.readBoolean()
                                    ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;
                            toolProtectionEnabled = buf.readBoolean();
                        }
                        int size = buf.readVarInt();
                        if (size < 0 || size > MAX_POSITIONS_PER_FRAGMENT) {
                            throw new IllegalArgumentException(
                                    "Invalid RTS area destroy fragment target count: " + size);
                        }
                        List<BlockPos> positions = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            positions.add(buf.readBlockPos().immutable());
                        }
                        int decodedBytes = buf.readerIndex() - start;
                        if (decodedBytes > MAX_FRAGMENT_BYTES) {
                            throw new IllegalArgumentException(
                                    "RTS area destroy fragment exceeds safe C2S budget: "
                                            + decodedBytes + " > " + MAX_FRAGMENT_BYTES);
                        }
                        C2SRtsAreaDestroyFragmentPayload payload =
                                new C2SRtsAreaDestroyFragmentPayload(
                                        traceId, requestId, sequence, clientTick, heldMs,
                                        inputKind, stopOrigin, fragmentIndex, fragmentCount,
                                        totalPositions, metadataPresent, toolSlot, toolItemId,
                                        toolPrototype, toolProtectionEnabled, positions);
                        validateForWire(payload);
                        return payload;
                    });

    public C2SRtsAreaDestroyFragmentPayload {
        toolItemId = toolItemId == null ? "" : toolItemId;
        toolPrototype = toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copy();
        positions = positions == null ? List.of() : positions.stream().map(BlockPos::immutable).toList();
    }

    /**
     * 按固定的小坐标预算生成完整分片序列；不截断、不排序，保留客户端预览顺序。
     * 分片 0 带工具元数据，服务端可先收其它分片后再等待它。
     */
    public static List<C2SRtsAreaDestroyFragmentPayload> split(
            long traceId, long requestId, int sequence, long clientTick, int heldMs,
            byte inputKind, byte stopOrigin, List<BlockPos> positions, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        List<BlockPos> safePositions = positions == null ? List.of() : List.copyOf(positions);
        if (safePositions.isEmpty()) return List.of();
        if (safePositions.size() > MiningLimits.MAX_VOLUME) {
            throw new IllegalArgumentException(
                    "RTS area destroy target count exceeds implementation limit: "
                            + safePositions.size() + " > " + MiningLimits.MAX_VOLUME);
        }
        if (requestId == 0L) {
            throw new IllegalArgumentException("RTS area destroy request id must be non-zero");
        }
        int total = safePositions.size();
        int fragmentCount = (total + MAX_POSITIONS_PER_FRAGMENT - 1)
                / MAX_POSITIONS_PER_FRAGMENT;
        List<C2SRtsAreaDestroyFragmentPayload> fragments = new ArrayList<>(fragmentCount);
        for (int index = 0; index < fragmentCount; index++) {
            int from = index * MAX_POSITIONS_PER_FRAGMENT;
            int to = Math.min(total, from + MAX_POSITIONS_PER_FRAGMENT);
            boolean metadata = index == 0;
            fragments.add(new C2SRtsAreaDestroyFragmentPayload(
                    traceId, requestId, sequence, clientTick, heldMs,
                    inputKind, stopOrigin, index, fragmentCount, total, metadata,
                    metadata ? toolSlot : (byte) 0,
                    metadata ? toolItemId : "",
                    metadata ? toolPrototype : ItemStack.EMPTY,
                    metadata && toolProtectionEnabled,
                    safePositions.subList(from, to)));
        }
        return List.copyOf(fragments);
    }

    private static void validateForWire(C2SRtsAreaDestroyFragmentPayload payload) {
        if (payload == null) throw new IllegalArgumentException("RTS area destroy fragment is null");
        if (payload.requestId() == 0L) {
            throw new IllegalArgumentException("RTS area destroy request id must be non-zero");
        }
        if (payload.fragmentCount() < 1 || payload.fragmentCount() > MAX_FRAGMENTS) {
            throw new IllegalArgumentException(
                    "Invalid RTS area destroy fragment count: " + payload.fragmentCount());
        }
        if (payload.fragmentIndex() < 0 || payload.fragmentIndex() >= payload.fragmentCount()) {
            throw new IllegalArgumentException(
                    "Invalid RTS area destroy fragment index: " + payload.fragmentIndex());
        }
        if (payload.totalPositions() < 1 || payload.totalPositions() > MiningLimits.MAX_VOLUME) {
            throw new IllegalArgumentException(
                    "Invalid RTS area destroy total target count: " + payload.totalPositions());
        }
        List<BlockPos> positions = payload.positions() == null ? List.of() : payload.positions();
        if (positions.isEmpty() || positions.size() > MAX_POSITIONS_PER_FRAGMENT) {
            throw new IllegalArgumentException(
                    "Invalid RTS area destroy fragment target count: " + positions.size());
        }
        if (positions.size() > payload.totalPositions()) {
            throw new IllegalArgumentException(
                    "RTS area destroy fragment exceeds declared target count");
        }
        if (!payload.metadataPresent()
                && (!payload.toolItemId().isEmpty()
                || !payload.toolPrototype().isEmpty()
                || payload.toolProtectionEnabled())) {
            throw new IllegalArgumentException(
                    "RTS area destroy fragment carries metadata without its metadata flag");
        }
        if (payload.metadataPresent() && (payload.toolSlot() < 0 || payload.toolSlot() > 8)) {
            throw new IllegalArgumentException(
                    "Invalid RTS area destroy tool slot: " + payload.toolSlot());
        }
    }

    /** 发送第一片前完成真实编码检查，工具 NBT 超限时不能半途断包或令客户端断线。 */
    public static void validateTransfer(List<C2SRtsAreaDestroyFragmentPayload> fragments,
                                        RegistryAccess registryAccess) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
            for (var fragment : fragments) {
                buffer.clear();
                STREAM_CODEC.encode(buffer, fragment);
            }
        } finally {
            buffer.release();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
