package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 智能填坑确认意图；只携带点击、参数和材料，不携带客户端规划坐标。
 */
public record C2SRtsConfirmSmartFillPayload(
        BlockPos clickedPos,
        byte face,
        int maxBlocks,
        int detectionDiameter,
        double hitOffsetX,
        double hitOffsetY,
        double hitOffsetZ,
        byte rotateSteps,
        String statePreset,
        String itemId,
        ItemStack itemPrototype,
        double rayOriginX,
        double rayOriginY,
        double rayOriginZ,
        double rayDirX,
        double rayDirY,
        double rayDirZ) implements CustomPacketPayload {
    public static final Type<C2SRtsConfirmSmartFillPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_confirm_smart_fill"),
            C2SRtsConfirmSmartFillPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsConfirmSmartFillPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.clickedPos());
                        buf.writeByte(payload.face());
                        buf.writeVarInt(payload.maxBlocks());
                        buf.writeVarInt(payload.detectionDiameter());
                        buf.writeDouble(payload.hitOffsetX());
                        buf.writeDouble(payload.hitOffsetY());
                        buf.writeDouble(payload.hitOffsetZ());
                        buf.writeByte(payload.rotateSteps());
                        buf.writeUtf(payload.statePreset() == null ? "" : payload.statePreset(), 256);
                        buf.writeUtf(payload.itemId() == null ? "" : payload.itemId(), 128);
                        ItemStack prototype = payload.itemPrototype() == null
                                ? ItemStack.EMPTY : payload.itemPrototype();
                        buf.writeBoolean(!prototype.isEmpty());
                        if (!prototype.isEmpty()) {
                            com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, prototype);
                        }
                        buf.writeDouble(payload.rayOriginX());
                        buf.writeDouble(payload.rayOriginY());
                        buf.writeDouble(payload.rayOriginZ());
                        buf.writeDouble(payload.rayDirX());
                        buf.writeDouble(payload.rayDirY());
                        buf.writeDouble(payload.rayDirZ());
                    },
                    buf -> new C2SRtsConfirmSmartFillPayload(
                            buf.readBlockPos().immutable(),
                            buf.readByte(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readByte(),
                            buf.readUtf(256),
                            buf.readUtf(128),
                            buf.readBoolean()
                                    ? com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf)
                                    : ItemStack.EMPTY,
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
