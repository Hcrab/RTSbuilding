package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsMinePayload(
        BlockPos pos,
        byte face,
        boolean start,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean allowPlacedBlockRecovery) implements CustomPacketPayload {
    public static final Type<C2SRtsMinePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_mine"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsMinePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.face());
                buf.writeBoolean(payload.start());
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                ItemStack toolPrototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
                buf.writeBoolean(!toolPrototype.isEmpty());
                if (!toolPrototype.isEmpty()) {
                    ItemStack.STREAM_CODEC.encode(buf, toolPrototype);
                }
                buf.writeBoolean(payload.allowPlacedBlockRecovery());
            },
            (buf) -> new C2SRtsMinePayload(
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readBoolean(),
                    buf.readByte(),
                    buf.readUtf(256),
                    buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY,
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
