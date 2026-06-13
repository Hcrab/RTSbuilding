package com.rtsbuilding.rtsbuilding.server.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * 相机实体的创建、查找、丢弃等纯实体操???
 * <p>包私有——仅??{@link RtsCameraManager} 内部委托??
 */
final class RtsCameraEntityHelper {

    private RtsCameraEntityHelper() {
    }

    // ======================================================================
    //  查找
    // ======================================================================

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

    static void discardOwnedCameras(ServerPlayer player, UUID keepUuid) {
        if (player == null || player.getServer() == null) {
            return;
        }
        UUID ownerUuid = player.getUUID();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RtsCameraEntity camera
                        && ownerUuid.equals(camera.getOwnerUuid())
                        && !camera.getUUID().equals(keepUuid)) {
                    camera.discard();
                }
            }
        }
    }

    // ======================================================================
    //  创建
    // ======================================================================

    static RtsCameraEntity createAndSpawnCamera(ServerLevel level, UUID ownerUuid,
            double x, double y, double z, float yaw, float pitch) {
        RtsCameraEntity camera = new RtsCameraEntity(RtsbuildingMod.RTS_CAMERA_ENTITY.get(), level);
        camera.setOwnerUuid(ownerUuid);
        camera.snapTo(x, y, z, yaw, pitch);
        level.addFreshEntity(camera);
        return camera;
    }

    // ======================================================================
    //  孤儿清理（需要外部传入活跃相机判断）
    // ======================================================================

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
