package com.rtsbuilding.rtsbuilding.network.create;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * RTS 远程提交 Create Value Settings 的精确目标快照。
 *
 * <p>载荷不携带方块 ID；它只带 Create 分配的行为 netId 作为瞬时身份快照。服务端仍会
 * 按维度、位置、面和命中点重新查找实际 ValueSettingsBehaviour，再比对 netId 并用该行为
 * 的 board 校验数值，因此客户端不能借此声明任意行为类型。</p>
 */
public record C2SRtsCreateValueSettingsPayload(
        ResourceLocation dimension,
        BlockPos pos,
        int behaviourNetId,
        int row,
        int value,
        boolean shortInteraction,
        Direction face,
        double hitX,
        double hitY,
        double hitZ,
        boolean ctrlDown) implements CustomPacketPayload {
    public static final Type<C2SRtsCreateValueSettingsPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_create_value_settings"),
            C2SRtsCreateValueSettingsPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCreateValueSettingsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeResourceLocation(payload.dimension());
                        buf.writeBlockPos(payload.pos());
                        buf.writeVarInt(payload.behaviourNetId());
                        buf.writeVarInt(payload.row());
                        buf.writeVarInt(payload.value());
                        buf.writeBoolean(payload.shortInteraction());
                        buf.writeEnum(payload.face());
                        buf.writeDouble(payload.hitX());
                        buf.writeDouble(payload.hitY());
                        buf.writeDouble(payload.hitZ());
                        buf.writeBoolean(payload.ctrlDown());
                    },
                    buf -> new C2SRtsCreateValueSettingsPayload(
                            buf.readResourceLocation(),
                            buf.readBlockPos(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readEnum(Direction.class),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
