package com.rtsbuilding.rtsbuilding.network.message;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：RTS 相机姿态专用上报包（高频路径）。
 * <p>替代走 {@link C2SAction} 的 NBT 打包方式——相机姿态由客户端每 tick 上报，
 * 直接字段编解码可避免每次创建/解析 {@code CompoundTag} 的开销。</p>
 *
 * @param x     相机世界 X 坐标
 * @param y     相机世界 Y 坐标
 * @param z     相机世界 Z 坐标
 * @param yaw   偏航角（度）
 * @param pitch 俯仰角（度）
 */
public record C2SCameraPosePayload(
        double x,
        double y,
        double z,
        float yaw,
        float pitch) implements CustomPacketPayload {

    public static final Type<C2SCameraPosePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_camera_pose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCameraPosePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeFloat(p.yaw());
                buf.writeFloat(p.pitch());
            },
            (buf) -> new C2SCameraPosePayload(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
