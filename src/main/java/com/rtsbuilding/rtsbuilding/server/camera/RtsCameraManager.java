package com.rtsbuilding.rtsbuilding.server.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtsCameraManager {
    // 相机高度下限（相对锚点）
    private static final double MIN_HEIGHT = -35.0D;
    // 相机高度上限（相对锚点）
    private static final double MAX_HEIGHT = 110.0D;
    // 俯仰角最小值
    private static final float MIN_PITCH = -90.0F;
    // 俯仰角最大值
    private static final float MAX_PITCH = 90.0F;

    // 旋转输入钳位值
    private static final float ROT_INPUT_CLAMP = 20.0F;
    // 水平旋转增益
    private static final float ROTATE_GAIN_X = 0.24F;
    // 垂直旋转增益
    private static final float ROTATE_GAIN_Y = 0.22F;
    // 每次滚轮缩放的距离
    private static final double DOLLY_PER_SCROLL = 2.6D;
    // 普通垂直移动速度
    private static final double VERTICAL_SPEED = 0.32D;
    // 快速垂直移动速度
    private static final double FAST_VERTICAL_SPEED = 0.55D;

    // 玩家 UUID -> 会话 的映射表
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private RtsCameraManager() {
    }

    /**
     * 切换 RTS 相机状态（开启/关闭）。
     *
     * @param player           目标玩家
     * @param startAtPlayerHead 是否从玩家头部高度开始
     */
    public static void toggle(ServerPlayer player, boolean startAtPlayerHead) {
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player);
        } else {
            start(player, startAtPlayerHead);
        }
    }

    /**
     * 以默认方式启动 RTS 相机。
     *
     * @param player 目标玩家
     */
    public static void start(ServerPlayer player) {
        start(player, false);
    }

    /**
     * 启动 RTS 相机。根据玩家当前的解锁进度决定进入正常模式还是家选择模式。
     *
     * @param player           目标玩家
     * @param startAtPlayerHead 是否从玩家头部高度开始
     */
    public static void start(ServerPlayer player, boolean startAtPlayerHead) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CAMERA)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("RTS camera is not unlocked."), true);
            return;
        }
        if (RtsProgressionManager.shouldStartHomeSelection(player)) {
            startHomeSelection(player, startAtPlayerHead);
            return;
        }
        if (!RtsProgressionManager.canStartNormalRts(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Set an RTS home first."), true);
            return;
        }
        startNormal(player, startAtPlayerHead);
    }

    /**
     * 启动正常 RTS 模式（非家选择）。
     * <p>将锚点对齐到玩家脚下方块中心，并根据半径限制创建相机实体。</p>
     */
    private static void startNormal(ServerPlayer player, boolean startAtPlayerHead) {
        cleanupOrphanCameras(player.level().getServer());
        RtsCameraEntityHelper.discardOwnedCameras(player);
        ServerLevel level = player.level();
        Vec3 playerPos = player.position();
        // 将锚点对齐到方块中心，使相机边界与放置边界匹配
        Vec3 anchor = new Vec3(Math.floor(playerPos.x) + 0.5D, playerPos.y, Math.floor(playerPos.z) + 0.5D);
        double maxRadius = RtsProgressionManager.getActionRadius(player);

        // 偏航角吸附到 90° 倍数，俯仰角固定 70°
        float yaw = snapQuarter(player.getYRot());
        float pitch = 70.0F;
        // 相机 Y 坐标：从玩家眼部或锚点上方 18 格
        double cameraY = startAtPlayerHead ? player.getEyeY() : anchor.y + 18.0D;

        RtsCameraEntity camera = RtsCameraEntityHelper.createAndSpawnCamera(level, player.getUUID(),
                anchor.x, cameraY, anchor.z, yaw, pitch);

        // 记录会话
        Session session = new Session(camera.getUUID(), anchor, camera.position(), yaw, pitch,
                camera.getY() - anchor.y, false, maxRadius, startAtPlayerHead);
        SESSIONS.put(player.getUUID(), session);
        ServiceRegistry.getInstance().session().onRtsEnabled(player);

        // 向客户端发送相机状态同步包
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCameraStatePayload(
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

    /**
     * 从操作面板启动家选择模式。
     * <p>会检查冷却时间等前置条件。</p>
     */
    public static void startHomeSelectionFromPanel(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled()) {
            return;
        }
        if (!RtsProgressionManager.canUse(player, RtsFeature.CAMERA)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("RTS camera is not unlocked."), true);
            return;
        }
        if (!RtsProgressionManager.canChangeHome(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.rtsbuilding.home.cooldown",
                    RtsProgressionManager.remainingHomeCooldownDays(player)), true);
            return;
        }
        stopIfActive(player);
        startHomeSelection(player, false);
    }

    /**
     * 启动家的选择流程。
     * <p>将锚点对齐到玩家所在区块中心（8, Y, 8），进入家选择会话。</p>
     */
    private static void startHomeSelection(ServerPlayer player, boolean startAtPlayerHead) {
        cleanupOrphanCameras(player.level().getServer());
        RtsCameraEntityHelper.discardOwnedCameras(player);
        ServerLevel level = player.level();
        BlockPos playerPos = player.blockPosition();
        // 计算玩家所在区块的中心坐标
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

        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCameraStatePayload(
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

    /**
     * 停止 RTS 相机。<p>移除会话、丢弃相机实体，并向客户端发送关闭状态包。</p>
     */
    public static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            Entity entity = RtsCameraEntityHelper.findCameraEntity(player.level().getServer(), session.cameraUuid());
            if (entity != null) {
                entity.discard();
            }
            // 如果当前是家选择模式，结束家选择流程
            if (session.homeSelection()) {
                RtsProgressionManager.endHomeSelection(player);
            }
        }
        RtsCameraEntityHelper.discardOwnedCameras(player);

        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCameraStatePayload(false, -1, 0.0D, 0.0D, 0.0D,
                RtsProgressionManager.DEFAULT_MAX_ACTION_RADIUS_BLOCKS, 18.0D, 0.0F, 70.0F, false, false));
        ServiceRegistry.getInstance().session().onRtsDisabled(player);
    }

    /**
     * 从家选择模式结束后直接启动正常 RTS 模式。
     * <p>丢弃旧的家选择相机，创建新的正常模式相机。</p>
     */
    public static void restartNormalFromHomeSelection(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.homeSelection()) {
            return;
        }
        Entity entity = RtsCameraEntityHelper.findCameraEntity(player.level().getServer(), session.cameraUuid());
        if (entity != null) {
            entity.discard();
        }
        SESSIONS.remove(player.getUUID());
        startNormal(player, session.closeRangeAllowed());
    }

    /**
     * 如果玩家有活跃相机，则停止它。
     */
    public static void stopIfActive(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player);
        }
    }

    /**
     * 判断玩家是否拥有活跃的 RTS 相机。
     */
    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /**
     * 获取当前玩家的 RTS 相机位置。
     *
     * @return 相机位置，若相机未激活则返回 {@code null}
     */
    public static Vec3 getCameraPosition(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null ? session.cameraPos() : null;
    }

    /**
     * 判断指定方块位置是否在玩家的 RTS 动作范围内（基于锚点的 AABB 碰撞检测）。
     * <p>家选择模式下始终返回 {@code false}。</p>
     *
     * @param player 目标玩家
     * @param pos    待检测的方块位置
     * @return 是否在动作范围内
     */
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

    /**
     * 移动 RTS 相机。<p>处理平移、旋转、垂直移动和滚轮变焦。</p>
     *
     * @param player      目标玩家
     * @param forward     前后移动输入（W/S）
     * @param strafe     左右平移输入（A/D）
     * @param vertical   垂直移动输入
     * @param panX       鼠标水平拖拽
     * @param panY       鼠标垂直拖拽
     * @param rotateX    水平旋转输入
     * @param rotateY    垂直旋转输入
     * @param scroll     滚轮输入（变焦）
     * @param rotateSteps 旋转步数（90° 倍数吸附）
     * @param fast       是否启用快速移动
     */
    public static void move(ServerPlayer player, float forward, float strafe, float vertical, float panX, float panY, float rotateX,
            float rotateY, float scroll, int rotateSteps, boolean fast) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        // 更新锚点以跟随玩家实体的当前位置
        Vec3 playerPos = player.position();
        Vec3 newAnchor = new Vec3(Math.floor(playerPos.x) + 0.5D, playerPos.y, Math.floor(playerPos.z) + 0.5D);

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

        // 沿当前视线方向推拉变焦（非机械式 Y 轴缩放）
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

        // 将相机移动限制在更新后的玩家跟随锚点范围内
        double halfExtent = actionHalfExtent(player, session);
        targetX = Mth.clamp(targetX, newAnchor.x - halfExtent, newAnchor.x + halfExtent);
        targetZ = Mth.clamp(targetZ, newAnchor.z - halfExtent, newAnchor.z + halfExtent);

        targetY = Mth.clamp(targetY, newAnchor.y + MIN_HEIGHT, newAnchor.y + MAX_HEIGHT);

        // 保持移动边界为正方形，与可见的建筑边界一致

        camera.snapTo(targetX, targetY, targetZ, yaw, pitch);

        double heightOffset = targetY - newAnchor.y;
        SESSIONS.put(player.getUUID(), new Session(camera.getUUID(), newAnchor, new Vec3(targetX, targetY, targetZ),
                yaw, pitch, heightOffset, session.homeSelection(), session.maxRadius(), session.closeRangeAllowed()));

        // 通知客户端更新后的锚点位置，使可视边界保持同步
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCameraAnchorPayload(
                newAnchor.x, newAnchor.y, newAnchor.z, maxRadius(player, session)));
    }

    /**
     * 获取或恢复相机实体。<p>如果相机丢失（因维度切换等），则按上次记录的会话状态重新创建。</p>
     */
    @SuppressWarnings("resource")
    private static RtsCameraEntity getOrRestoreCamera(ServerPlayer player, Session session) {
        Entity baseEntity = RtsCameraEntityHelper.findCameraEntity(player.level().getServer(), session.cameraUuid());
        if (baseEntity instanceof RtsCameraEntity camera && baseEntity.level() == player.level()) {
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
        RtsCameraEntity restored = RtsCameraEntityHelper.createAndSpawnCamera(player.level(), player.getUUID(),
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

        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCameraStatePayload(
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

    /**
     * 清理所有不在 SESSIONS 中的孤儿相机实体。
     */
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

    /**
     * 计算最大动作半径。<p>家选择模式下使用固定半径，否则从进度管理器获取。</p>
     */
    private static double maxRadius(ServerPlayer player, Session session) {
        if (session.homeSelection()) {
            return session.maxRadius();
        }
        return RtsProgressionManager.getActionRadius(player);
    }

    /**
     * 以锚点为中心的 AABB 半边长。<p>当前实现直接返回 maxRadius（正方形边界）。</p>
     */
    private static double actionHalfExtent(ServerPlayer player, Session session) {
        return maxRadius(player, session);
    }

    /**
     * 将偏航角吸附到最近的 90° 倍数。<p>使相机朝向锁定在东南西北四个方向。</p>
     */
    private static float snapQuarter(float yaw) {
        int quarter = Math.round(yaw / 90.0F);
        return quarter * 90.0F;
    }

    /**
     * RTS 相机会话记录。
     *
     * @param cameraUuid       相机实体的 UUID
     * @param anchor           锚点位置（玩家脚下方块中心）
     * @param cameraPos        相机当前位置
     * @param yawDeg           偏航角（度）
     * @param pitchDeg         俯仰角（度）
     * @param heightOffset     相机相对锚点的高度偏移
     * @param homeSelection    是否为家选择模式
     * @param maxRadius        最大动作半径
     * @param closeRangeAllowed 是否允许近距开始
     */
    private record Session(UUID cameraUuid, Vec3 anchor, Vec3 cameraPos, float yawDeg, float pitchDeg,
                           double heightOffset, boolean homeSelection, double maxRadius, boolean closeRangeAllowed) {
    }
}
