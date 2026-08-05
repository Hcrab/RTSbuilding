package com.rtsbuilding.rtsbuilding.server.camera;

import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

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
        for (WorldServer level : com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.worlds(server)) {
            Entity entity = com.rtsbuilding.rtsbuilding.platform.entity.EntityCompat.findByUuid(level, cameraUuid);
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
    static void discardOwnedCameras(EntityPlayerMP player) {
        if (player == null || com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getServer(player) == null) {
            return;
        }
        UUID ownerUuid = player.getUniqueID();
        for (WorldServer level : com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.worlds(com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getServer(player))) {
            for (Entity entity : level.loadedEntityList) {
                if (entity instanceof RtsCameraEntity) {
                    RtsCameraEntity camera = (RtsCameraEntity) entity;
                    if (ownerUuid.equals(camera.getOwnerUuid())) {
                        camera.setDead();
                    }
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
    static RtsCameraEntity createAndSpawnCamera(WorldServer level, UUID ownerUuid,
            double x, double y, double z, float yaw, float pitch) {
        RtsCameraEntity camera = new RtsCameraEntity(RtsEntities.RTS_CAMERA_ENTITY.get(), level);
        camera.setOwnerUuid(ownerUuid);
        camera.snapTo(x, y, z, yaw, pitch);
        level.spawnEntityInWorld(camera);
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
        for (WorldServer level : com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.worlds(server)) {
            for (Entity entity : level.loadedEntityList) {
                if (entity instanceof RtsCameraEntity) {
                    RtsCameraEntity camera = (RtsCameraEntity) entity;
                    if (!isActiveCamera.test(camera.getUniqueID())) {
                        camera.setDead();
                    }
                }
            }
        }
    }
}
