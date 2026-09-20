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

public record C2SRtsAreaDestroyPayload(
        List<BlockPos> positions,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean toolProtectionEnabled) implements CustomPacketPayload {
    /** 旧包仍可接收，但不能再把合法的大区域静默截断。 */
    public static final int MAX_POSITIONS = MiningLimits.MAX_VOLUME;

    public static final Type<C2SRtsAreaDestroyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_area_destroy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaDestroyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                int start = buf.writerIndex();
                List<BlockPos> payloadPositions = payload.positions() == null ? List.of() : payload.positions();
                if (payloadPositions.size() > MAX_POSITIONS) {
                    throw new IllegalArgumentException(
                            "RTS area destroy target count exceeds legacy payload limit: "
                                    + payloadPositions.size() + " > " + MAX_POSITIONS);
                }
                int size = payloadPositions.size();
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeBlockPos(payloadPositions.get(i));
                }
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                ItemStack toolPrototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
                buf.writeBoolean(!toolPrototype.isEmpty());
                if (!toolPrototype.isEmpty()) {
                    ItemStack.STREAM_CODEC.encode(buf, toolPrototype);
                }
                buf.writeBoolean(payload.toolProtectionEnabled());
                requireSinglePacketBudget(buf.writerIndex() - start);
            },
            (buf) -> {
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_POSITIONS || size > buf.readableBytes() / Long.BYTES) {
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
                        buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY,
                        buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 旧入口不能发送大包；大区域应使用新分片协议，不能在编码中静默裁掉后半部分。 */
    static void requireSinglePacketBudget(int encodedBytes) {
        if (encodedBytes > C2SRtsAreaDestroyFragmentPayload.MAX_FRAGMENT_BYTES) {
            throw new IllegalArgumentException("区域破坏超过单包预算，请使用分片协议");
        }
    }
}
