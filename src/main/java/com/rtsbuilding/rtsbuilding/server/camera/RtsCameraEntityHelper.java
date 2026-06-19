package com.rtsbuilding.rtsbuilding.server.camera;

import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * 相机实体的创建、查找、丢弃等纯实体操作。
 * <p>包私有——仅供 {@link RtsCameraManager} 内部委托。
 */
final class RtsCameraEntityHelper {

    private RtsCameraEntityHelper() {
    }

    // ======================================================================
    //  查找
    // ======================================================================

    /**
     * 在所有维度中查找指定 UUID 的相机实体。
     *
     * @param server     Minecraft 服务器实例
     * @param cameraUuid 相机实体的 UUID
     * @return 找到的实体，若未找到则返回 {@code null}
     */
    static Entity findCameraEntity(MinecraftServer server, UUID cameraUuid) {
        if (server == null || cameraUuid == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(cameraUuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    // ======================================================================
    //  丢弃
    // ======================================================================

    /**
     * 丢弃指定玩家拥有的所有相机实体。
     *
     * @param player   目标玩家
     */
    static void discardOwnedCameras(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        UUID ownerUuid = player.getUUID();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RtsCameraEntity camera
                        && ownerUuid.equals(camera.getOwnerUuid())) {
                    camera.discard();
                }
            }
        }
    }

    // ======================================================================
    //  创建
    // ======================================================================

    /**
     * 创建并生成一个 RTS 相机实体。
     *
     * @param level     目标服务端维度
     * @param ownerUuid 所属玩家 UUID
     * @param x         X 坐标
     * @param y         Y 坐标
     * @param z         Z 坐标
     * @param yaw       偏航角
     * @param pitch     俯仰角
     * @return 创建的相机实体
     */
    static RtsCameraEntity createAndSpawnCamera(ServerLevel level, UUID ownerUuid,
            double x, double y, double z, float yaw, float pitch) {
        RtsCameraEntity camera = new RtsCameraEntity(RtsEntities.RTS_CAMERA_ENTITY.get(), level);
        camera.setOwnerUuid(ownerUuid);
        camera.snapTo(x, y, z, yaw, pitch);
        level.addFreshEntity(camera);
        return camera;
    }

    // ======================================================================
    //  孤儿清理（需要外部传入活跃相机判断）
    // ======================================================================

    /**
     * 清理不再活跃的"孤儿"相机实体。
     * <p>遍历所有维度，丢弃那些不在活跃会话中的相机实体。</p>
     *
     * @param server         Minecraft 服务器实例
     * @param isActiveCamera 判断相机 UUID 是否仍处于活跃会话中的谓词
     */
    static void cleanupOrphanCameras(MinecraftServer server, Predicate<UUID> isActiveCamera) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RtsCameraEntity camera && !isActiveCamera.test(camera.getUUID())) {
                    camera.discard();
                }
            }
        }
    }
}
