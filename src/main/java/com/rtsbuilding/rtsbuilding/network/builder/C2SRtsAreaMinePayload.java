package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsAreaMinePayload(
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        byte shapeType,
        byte fillType,
        boolean toolProtectionEnabled) implements CustomPacketPayload {
    public static final Type<C2SRtsAreaMinePayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_area_mine"), C2SRtsAreaMinePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaMinePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.minX());
                buf.writeInt(payload.maxX());
                buf.writeInt(payload.minY());
                buf.writeInt(payload.maxY());
                buf.writeInt(payload.minZ());
                buf.writeInt(payload.maxZ());
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                ItemStack toolPrototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
                buf.writeBoolean(!toolPrototype.isEmpty());
                if (!toolPrototype.isEmpty()) {
                    com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, toolPrototype);
                }
                buf.writeByte(payload.shapeType());
                buf.writeByte(payload.fillType());
                buf.writeBoolean(payload.toolProtectionEnabled());
            },
            (buf) -> new C2SRtsAreaMinePayload(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readByte(),
                    buf.readUtf(256),
                    buf.readBoolean() ? com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf) : ItemStack.EMPTY,
                    buf.readByte(),
                    buf.readByte(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
