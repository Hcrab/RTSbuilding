package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家确认智能填洞时发送的意图包。
 *
 * <p>它只携带真实点击、玩家参数和材料原型，不携带客户端扫描出的坐标列表。服务端必须
 * 使用同一个规划器重新生成目标，再交给普通放置任务。</p>
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
            ResourceLocation.fromNamespaceAndPath(
                    RtsbuildingMod.MODID, "c2s_rts_confirm_smart_fill"));

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
                        buf.writeUtf(payload.statePreset(), 256);
                        buf.writeUtf(payload.itemId(), 128);
                        ItemStack prototype = payload.itemPrototype() == null
                                ? ItemStack.EMPTY : payload.itemPrototype();
                        buf.writeBoolean(!prototype.isEmpty());
                        if (!prototype.isEmpty()) {
                            ItemStack.STREAM_CODEC.encode(buf, prototype);
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
                            buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY,
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
