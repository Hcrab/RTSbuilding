package com.rtsbuilding.rtsbuilding.server.camera;

import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtsCameraManager {
    private static final double MIN_HEIGHT = -35.0D;
    private static final double MAX_HEIGHT = 110.0D;
    private static final float MIN_PITCH = -90.0F;
    private static final float MAX_PITCH = 90.0F;

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private RtsCameraManager() {
    }

    public static void toggle(ServerPlayer player, boolean startAtPlayerHead) {
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player);
        } else {
            start(player, startAtPlayerHead);
        }
    }

    public static void start(ServerPlayer player) {
        start(player, false);
    }

    public static void start(ServerPlayer player, boolean startAtPlayerHead) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CAMERA)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.rtsbuilding.camera_locked",
                    net.minecraft.network.chat.Component.translatable("item.rtsbuilding.rts_control_core")), true);
            return;
        }
        if (RtsProgressionManager.shouldStartHomeSelection(player)) {
            startHomeSelection(player, startAtPlayerHead);
            return;
        }
        if (!RtsProgressionManager.canStartNormalRts(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Set an RTS home first."), true);
            return;
        }
        startNormal(player, startAtPlayerHead);
    }

    private static void startNormal(ServerPlayer player, boolean startAtPlayerHead) {
        cleanupOrphanCameras(player.getServer());
        RtsCameraEntityHelper.discardOwnedCameras(player, null);
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();
        // Align the anchor to block center so camera bounds match the placement boundary.
        Vec3 anchor = new Vec3(Math.floor(playerPos.x) + 0.5D, playerPos.y, Math.floor(playerPos.z) + 0.5D);
        double maxRadius = RtsProgressionManager.getActionRadius(player);

        float yaw = snapQuarter(player.getYRot());
        float pitch = 70.0F;
        double cameraY = startAtPlayerHead ? player.getEyeY() : anchor.y + 18.0D;

        RtsCameraEntity camera = RtsCameraEntityHelper.createAndSpawnCamera(level, player.getUUID(),
                anchor.x, cameraY, anchor.z, yaw, pitch);

        Session session = new Session(camera.getUUID(), anchor, camera.position(), yaw, pitch,
                camera.getY() - anchor.y, false, maxRadius, startAtPlayerHead);
        SESSIONS.put(player.getUUID(), session);
        RtsSessionService.onRtsEnabled(player);

        PacketDistributor.sendToPlayer(player, new S2CRtsCameraStatePayload(
                true,
                camera.getId(),
                anchor.x,
                anchor.y,
                anchor.z,
                maxRadius,
                session.heightOffset(),
                session.yawDeg(),
                session.pitchDeg(),
                false,
                session.closeRangeAllowed()));
    }

    public static void startHomeSelectionFromPanel(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled()) {
            return;
        }
        if (!RtsProgressionManager.canUse(player, RtsFeature.CAMERA)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.rtsbuilding.camera_locked",
                    net.minecraft.network.chat.Component.translatable("item.rtsbuilding.rts_control_core")), true);
            return;
        }
        if (!RtsProgressionManager.canChangeHome(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.rtsbuilding.home.cooldown",
                    RtsProgressionManager.remainingHomeCooldownDays(player)), true);
            return;
        }
        stopIfActive(player);
        startHomeSelection(player, false);
    }

    private static void startHomeSelection(ServerPlayer player, boolean startAtPlayerHead) {
        cleanupOrphanCameras(player.getServer());
        RtsCameraEntityHelper.discardOwnedCameras(player, null);
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        Vec3 anchor = new Vec3((centerChunkX << 4) + 8.0D, player.getY(), (centerChunkZ << 4) + 8.0D);
        double maxRadius = RtsProgressionManager.HOME_SELECTION_RADIUS_BLOCKS;

        float yaw = snapQuarter(player.getYRot());
        float pitch = 70.0F;
        double cameraX = anchor.x;
        double cameraY = startAtPlayerHead ? player.getEyeY() : anchor.y + 18.0D;
        double cameraZ = anchor.z;

        RtsCameraEntity camera = RtsCameraEntityHelper.createAndSpawnCamera(level, player.getUUID(),
                cameraX, cameraY, cameraZ, yaw, pitch);

        RtsProgressionManager.beginHomeSelection(player);
        Session session = new Session(camera.getUUID(), anchor, camera.position(), yaw, pitch,
                camera.getY() - anchor.y, true, maxRadius, startAtPlayerHead);
        SESSIONS.put(player.getUUID(), session);

        PacketDistributor.sendToPlayer(player, new S2CRtsCameraStatePayload(
                true,
                camera.getId(),
                anchor.x,
                anchor.y,
                anchor.z,
                maxRadius,
                session.heightOffset(),
                session.yawDeg(),
                session.pitchDeg(),
                true,
                session.closeRangeAllowed()));
    }

    public static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            Entity entity = RtsCameraEntityHelper.findCameraEntity(player.getServer(), session.cameraUuid());
            if (entity != null) {
                entity.discard();
            }
            if (session.homeSelection()) {
                RtsProgressionManager.endHomeSelection(player);
            }
        }
        RtsCameraEntityHelper.discardOwnedCameras(player, null);

        PacketDistributor.sendToPlayer(player, new S2CRtsCameraStatePayload(false, -1, 0.0D, 0.0D, 0.0D,
                RtsProgressionManager.DEFAULT_MAX_ACTION_RADIUS_BLOCKS, 18.0D, 0.0F, 70.0F, false, false));
        RtsSessionService.onRtsDisabled(player);
    }

    public static void restartNormalFromHomeSelection(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.homeSelection()) {
            return;
        }
        Entity entity = RtsCameraEntityHelper.findCameraEntity(player.getServer(), session.cameraUuid());
        if (entity != null) {
            entity.discard();
        }
        SESSIONS.remove(player.getUUID());
        startNormal(player, session.closeRangeAllowed());
    }

    public static void stopIfActive(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player);
        }
    }

    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /**
     * Returns the current RTS camera position for the given player, or
     * {@code null} if the camera is not active.
     */
    public static Vec3 getCameraPosition(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null ? session.cameraPos() : null;
    }

    public static boolean isWithinActionRadius(ServerPlayer player, BlockPos pos) {
        return isWithinActionRange(player, pos);
    }

    public static boolean isWithinActionRange(ServerPlayer player, BlockPos pos) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || pos == null || session.homeSelection()) {
            return false;
        }

        double dx = (pos.getX() + 0.5D) - session.anchor().x;
        double dz = (pos.getZ() + 0.5D) - session.anchor().z;
        double halfExtent = actionHalfExtent(player, session);
        return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
    }

    public static void move(ServerPlayer player, float forward, float strafe, float vertical, float panX, float panY, float rotateX,
            float rotateY, float scroll, int rotateSteps, boolean fast) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        RtsCameraEntity camera = getOrRestoreCamera(player, session);

        float safeRotateX = Mth.clamp(rotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float safeRotateY = Mth.clamp(rotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);

        float yaw = session.yawDeg() + (safeRotateX * ROTATE_GAIN_X);
        if (rotateSteps != 0) {
            yaw = snapQuarter(yaw + (90.0F * rotateSteps));
        }

        float pitch = Mth.clamp(session.pitchDeg() + (safeRotateY * ROTATE_GAIN_Y), MIN_PITCH, MAX_PITCH);

        double speed = fast ? 0.80D : 0.45D;

        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double targetX = camera.getX();
        double targetY = camera.getY();
        double targetZ = camera.getZ();

        float safeVertical = Mth.clamp(vertical, -1.0F, 1.0F);
        double dx = (-sin * forward + cos * strafe) * speed;
        double dz = (cos * forward + sin * strafe) * speed;

        double dragScale = 0.020D * Math.max(8.0D, session.heightOffset());
        double moveRight = panX * dragScale;
        double moveForward = -panY * dragScale;

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);

        dx += rightX * moveRight + fwdX * moveForward;
        dz += rightZ * moveRight + fwdZ * moveForward;

        targetX += dx;
        targetY += safeVertical * (fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED);
        targetZ += dz;

        // Dolly zoom along current look direction (not mechanical Y-only zoom).
        if (scroll != 0.0F) {
            double pitchRad = Math.toRadians(pitch);
            double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double lookY = -Math.sin(pitchRad);
            double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

            double dolly = scroll * DOLLY_PER_SCROLL;
            targetX += lookX * dolly;
            targetY += lookY * dolly;
            targetZ += lookZ * dolly;
        }

        double halfExtent = actionHalfExtent(player, session);
        targetX = Mth.clamp(targetX, session.anchor().x - halfExtent, session.anchor().x + halfExtent);
        targetZ = Mth.clamp(targetZ, session.anchor().z - halfExtent, session.anchor().z + halfExtent);

        targetY = Mth.clamp(targetY, session.anchor().y + MIN_HEIGHT, session.anchor().y + MAX_HEIGHT);

        // Keep movement bounds square so they match the visible build boundary.

        camera.snapTo(targetX, targetY, targetZ, yaw, pitch);

        SESSIONS.put(player.getUUID(), new Session(camera.getUUID(), session.anchor(), new Vec3(targetX, targetY, targetZ),
                yaw, pitch, targetY - session.anchor().y, session.homeSelection(), session.maxRadius(), session.closeRangeAllowed()));
    }

    private static RtsCameraEntity getOrRestoreCamera(ServerPlayer player, Session session) {
        Entity baseEntity = RtsCameraEntityHelper.findCameraEntity(player.getServer(), session.cameraUuid());
        if (baseEntity instanceof RtsCameraEntity camera && baseEntity.level() == player.serverLevel()) {
            if (camera.getOwnerUuid() == null) {
                camera.setOwnerUuid(player.getUUID());
            }
            if (!player.getUUID().equals(camera.getOwnerUuid())) {
                camera.discard();
            } else {
                return camera;
            }
        }

        if (baseEntity != null) {
            baseEntity.discard();
        }

        Vec3 cameraPos = session.cameraPos();
        RtsCameraEntity restored = RtsCameraEntityHelper.createAndSpawnCamera(player.serverLevel(), player.getUUID(),
                cameraPos.x, cameraPos.y, cameraPos.z, session.yawDeg(), session.pitchDeg());

        SESSIONS.put(player.getUUID(), new Session(
                restored.getUUID(),
                session.anchor(),
                cameraPos,
                session.yawDeg(),
                session.pitchDeg(),
                session.heightOffset(),
                session.homeSelection(),
                session.maxRadius(),
                session.closeRangeAllowed()));

        PacketDistributor.sendToPlayer(player, new S2CRtsCameraStatePayload(
                true,
                restored.getId(),
                session.anchor().x,
                session.anchor().y,
                session.anchor().z,
                maxRadius(player, session),
                session.heightOffset(),
                session.yawDeg(),
                session.pitchDeg(),
                session.homeSelection(),
                session.closeRangeAllowed()));
        return restored;
    }

    public static void cleanupOrphanCameras(MinecraftServer server) {
        RtsCameraEntityHelper.cleanupOrphanCameras(server, cameraUuid -> {
            if (cameraUuid == null) {
                return false;
            }
            for (Session session : SESSIONS.values()) {
                if (cameraUuid.equals(session.cameraUuid())) {
                    return true;
                }
            }
            return false;
        });
    }

    private static double maxRadius(ServerPlayer player, Session session) {
        if (session.homeSelection()) {
            return session.maxRadius();
        }
        return RtsProgressionManager.getActionRadius(player);
    }

    private static double actionHalfExtent(ServerPlayer player, Session session) {
        return maxRadius(player, session);
    }

    private static float snapQuarter(float yaw) {
        int quarter = Math.round(yaw / 90.0F);
        return quarter * 90.0F;
    }

    private record Session(UUID cameraUuid, Vec3 anchor, Vec3 cameraPos, float yawDeg, float pitchDeg,
                           double heightOffset, boolean homeSelection, double maxRadius, boolean closeRangeAllowed) {
    }
}
