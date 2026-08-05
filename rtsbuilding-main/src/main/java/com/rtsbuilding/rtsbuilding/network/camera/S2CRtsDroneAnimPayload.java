package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：无人机动画状态同步包。
 * <p>
 * 服务端每 tick 把无人机的位置与动画状态（机身倾角、相机云台俯仰、机身偏航）打包下发。
 * 客户端收到后：
 * <ul>
 *   <li>位置：把 xo/yo/zo 置为上一包位置、当前位置置为本包位置，形成合法的 [old, current]
 *       插值窗口，由原版渲染器用 partialTick 平滑插值（无人机客户端不 tick，原版位置包
 *       不会维护 xo，导致位置跳变卡顿）。</li>
 *   <li>动画：写入 prev/current 缓存，渲染层插值 + AnimFloat 时间平滑。</li>
 * </ul>
 *
 * @param entityId 无人机实体 ID（客户端据此查找实体）
 * @param x        位置 X
 * @param y        位置 Y
 * @param z        位置 Z
 * @param yawDeg   相机偏航角（度，机身朝向目标）
 * @param pitchDeg 相机俯仰角（度，相机云台上下角度目标）
 * @param tiltX    机身俯仰倾角（度，服务端平滑后的值）
 * @param tiltZ    机身横滚倾角（度，服务端平滑后的值）
 */
public record S2CRtsDroneAnimPayload(
        int entityId,
        double x,
        double y,
        double z,
        float yawDeg,
        float pitchDeg,
        float tiltX,
        float tiltZ) implements CustomPacketPayload {

    public static final Type<S2CRtsDroneAnimPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_drone_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsDroneAnimPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeDouble(payload.x());
                buf.writeDouble(payload.y());
                buf.writeDouble(payload.z());
                buf.writeFloat(payload.yawDeg());
                buf.writeFloat(payload.pitchDeg());
                buf.writeFloat(payload.tiltX());
                buf.writeFloat(payload.tiltZ());
            },
            (buf) -> new S2CRtsDroneAnimPayload(
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
