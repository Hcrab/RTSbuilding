package com.rtsbuilding.rtsbuilding.server.camera;

import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.common.entity.RtsDroneEntity;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtsCameraManager {
    // 相机高度下限（相对锚点）
    private static final double MIN_HEIGHT = -35.0D;
    // 相机高度上限（相对锚点）
    private static final double MAX_HEIGHT = 110.0D;

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
     * @param terminalUuid     开启该模式的那把终端的 UUID（可为 null）
     */
    public static void toggle(ServerPlayer player, boolean startAtPlayerHead, @Nullable String terminalUuid) {
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player);
        } else {
            start(player, startAtPlayerHead, terminalUuid);
        }
    }

    /**
     * 以默认方式启动 RTS 相机。
     *
     * @param player 目标玩家
     */
    public static void start(ServerPlayer player) {
        start(player, false, null);
    }

    /**
     * 启动 RTS 相机。根据玩家当前的解锁进度决定进入正常模式还是家选择模式。
     *
     * @param player           目标玩家
     * @param startAtPlayerHead 是否从玩家头部高度开始
     * @param terminalUuid     开启该模式的那把终端的 UUID（可为 null）
     */
    public static void start(ServerPlayer player, boolean startAtPlayerHead, @Nullable String terminalUuid) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CAMERA)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("RTS camera is not unlocked."), true);
            return;
        }
        if (RtsProgressionManager.shouldStartHomeSelection(player)) {
            startHomeSelection(player, startAtPlayerHead, terminalUuid);
            return;
        }
        if (!RtsProgressionManager.canStartNormalRts(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Set an RTS home first."), true);
            return;
        }
        startNormal(player, startAtPlayerHead, terminalUuid);
    }

    /**
     * 启动正常 RTS 模式（非家选择）。
     * <p>将锚点对齐到玩家脚下方块中心，并根据半径限制创建相机实体。</p>
     */
    private static void startNormal(ServerPlayer player, boolean startAtPlayerHead, @Nullable String terminalUuid) {
        cleanupOrphanCameras(player.getServer());
        RtsCameraEntityHelper.discardOwnedCameras(player);
        RtsCameraEntityHelper.discardOwnedDrones(player);
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();
        // 将锚点对齐到方块中心，使相机边界与放置边界匹配
        Vec3 anchor = new Vec3(Math.floor(playerPos.x) + 0.5D, playerPos.y, Math.floor(playerPos.z) + 0.5D);
        double maxRadius = RtsProgressionManager.getActionRadius(player);

        // 偏航角吸附到 90° 倍数，俯仰角固定 70°
        float yaw = snapQuarter(player.getYRot());
        float pitch = 70.0F;
        // 相机 Y 坐标：玩家头顶上方 8 格
        double cameraY = player.getEyeY() + 8.0D;

        RtsCameraEntity camera = RtsCameraEntityHelper.createAndSpawnCamera(level, player.getUUID(),
                anchor.x, cameraY, anchor.z, yaw, pitch);

        // 创建跟随无人机的展示实体：打开 RTS 模式时从玩家身体前方出现，随后飞到相机上方跟随位
        RtsDroneEntity drone = spawnDroneInFrontOfPlayer(level, player, yaw);

        // 记录会话
        Session session = new Session(camera.getUUID(), anchor, camera.position(), yaw, pitch,
                camera.getY() - anchor.y, false, maxRadius, startAtPlayerHead, terminalUuid,
                drone.getUUID());
        SESSIONS.put(player.getUUID(), session);
        RtsServer.get().session().onRtsEnabled(player);

        // 向客户端发送相机状态同步包
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
                session.closeRangeAllowed(),
                session.terminalUuid()));
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
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("RTS camera is not unlocked."), true);
            return;
        }
        if (!RtsProgressionManager.canChangeHome(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.rtsbuilding.home.cooldown",
                    RtsProgressionManager.remainingHomeCooldownDays(player)), true);
            return;
        }
        stopIfActive(player);
        startHomeSelection(player, false, null);
    }

    /**
     * 启动家的选择流程。
     * <p>将锚点对齐到玩家所在区块中心（8, Y, 8），进入家选择会话。</p>
     */
    private static void startHomeSelection(ServerPlayer player, boolean startAtPlayerHead, @Nullable String terminalUuid) {
        cleanupOrphanCameras(player.getServer());
        RtsCameraEntityHelper.discardOwnedCameras(player);
        RtsCameraEntityHelper.discardOwnedDrones(player);
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        // 计算玩家所在区块的中心坐标
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        Vec3 anchor = new Vec3((centerChunkX << 4) + 8.0D, player.getY(), (centerChunkZ << 4) + 8.0D);
        double maxRadius = RtsProgressionManager.HOME_SELECTION_RADIUS_BLOCKS;

        float yaw = snapQuarter(player.getYRot());
        float pitch = 70.0F;
        double cameraX = anchor.x;
        double cameraY = player.getEyeY() + 8.0D;
        double cameraZ = anchor.z;

        RtsCameraEntity camera = RtsCameraEntityHelper.createAndSpawnCamera(level, player.getUUID(),
                cameraX, cameraY, cameraZ, yaw, pitch);

        // 创建跟随无人机的展示实体：家选择模式同样从玩家身体前方出现
        RtsDroneEntity drone = spawnDroneInFrontOfPlayer(level, player, yaw);

        RtsProgressionManager.beginHomeSelection(player);
        Session session = new Session(camera.getUUID(), anchor, camera.position(), yaw, pitch,
                camera.getY() - anchor.y, true, maxRadius, startAtPlayerHead, terminalUuid,
                drone.getUUID());
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
                session.closeRangeAllowed(),
                session.terminalUuid()));
    }

    /**
     * 在玩家身体前方创建跟随无人机（打开 RTS 模式时无人机从玩家面前出现）。
     * <p>初始位置取玩家面朝方向前方 3 格、眼睛高度；随后由 {@link #updateCameraPose} 的
     * {@code setTarget} 把它"飞"到相机上方跟随位，保留起飞动画可见。</p>
     *
     * @param level  服务端维度
     * @param player 目标玩家
     * @param yaw    无人机的初始偏航角（度，取相机朝向）
     * @return 创建的无人机实体
     */
    private static RtsDroneEntity spawnDroneInFrontOfPlayer(ServerLevel level, ServerPlayer player, float yaw) {
        Vec3 playerPos = player.position();
        double yawRad = Math.toRadians(player.getYRot());
        // 玩家面朝方向的水平单位向量（MC：yaw=0 朝 +Z 南、yaw=90 朝 -X 西）
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double dist = 3.0D;
        double x = playerPos.x + dirX * dist;
        double y = player.getEyeY();
        double z = playerPos.z + dirZ * dist;
        return RtsCameraEntityHelper.createAndSpawnDrone(level, player.getUUID(), x, y, z, yaw);
    }

    /**
     * 停止 RTS 相机。<p>移除会话、丢弃相机实体，并向客户端发送关闭状态包。</p>
     */
    public static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            Entity entity = RtsCameraEntityHelper.findCameraEntity(player.getServer(), session.cameraUuid());
            if (entity != null) {
                entity.discard();
            }
            // 丢弃跟随无人机
            if (session.droneUuid() != null) {
                Entity drone = RtsCameraEntityHelper.findDroneEntity(player.getServer(), session.droneUuid());
                if (drone != null) {
                    drone.discard();
                }
            }
            // 如果当前是家选择模式，结束家选择流程
            if (session.homeSelection()) {
                RtsProgressionManager.endHomeSelection(player);
            }
        }
        RtsCameraEntityHelper.discardOwnedCameras(player);
        RtsCameraEntityHelper.discardOwnedDrones(player);

        PacketDistributor.sendToPlayer(player, new S2CRtsCameraStatePayload(false, -1, 0.0D, 0.0D, 0.0D,
                RtsProgressionManager.DEFAULT_MAX_ACTION_RADIUS_BLOCKS, 18.0D, 0.0F, 70.0F, false, false, null));
        RtsServer.get().session().onRtsDisabled(player);
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
        Entity entity = RtsCameraEntityHelper.findCameraEntity(player.getServer(), session.cameraUuid());
        if (entity != null) {
            entity.discard();
        }
        SESSIONS.remove(player.getUUID());
        startNormal(player, session.closeRangeAllowed(), session.terminalUuid());
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
     * 判断给定物品栈是否为“开启玩家当前 RTS 模式的那把终端”。
     * <p>RTS 模式下禁止对该终端进行拿去/启用等网格操作，防止玩家把自己的模式开关拿走。</p>
     *
     * @param player 目标玩家
     * @param stack  待检测的物品栈
     * @return 是否是被锁定的终端
     */
    public static boolean isLockedTerminal(ServerPlayer player, ItemStack stack) {
        if (!isActive(player) || stack == null || stack.isEmpty()) {
            return false;
        }
        if (!stack.is(RtsItems.RTS_TERMINAL.get())) {
            return false;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.terminalUuid() == null) {
            return false;
        }
        String uuid = stack.get(RtsItems.TERMINAL_UUID.get());
        return uuid != null && session.terminalUuid().equals(uuid);
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
     * 更新玩家 RTS 相机的姿态（客户端权威上报）。
     * <p>相机移动/旋转为纯客户端计算，客户端每 tick 通过 {@code CAMERA_POSE}
     * 消息上报相机真实位置与朝向；服务端据此刷新会话记录，供权威逻辑
     * （如 {@link #getCameraPosition}、动作范围校验、实体跟随）使用。</p>
     * <p>仅当玩家存在活跃的 RTS 相机会话时生效；未激活则忽略。</p>
     *
     * @param player 目标玩家
     * @param x      相机世界 X 坐标
     * @param y      相机世界 Y 坐标
     * @param z      相机世界 Z 坐标
     * @param yaw    偏航角（度）
     * @param pitch  俯仰角（度）
     */
    public static void updateCameraPose(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        // 无人机跟随相机：目标直接位于相机位置、朝向与相机一致。
        // 通过 setTarget 设置飞行目标，无人机在自身 tick 中以有限速度"飞向"目标（插值平滑），
        // 而非瞬移锁死，保证飞行动画可见。同时把相机俯仰角传给无人机，驱动相机云台上下角度动画。
        // 实体缺失或残留于其他维度时自动重建。
        UUID droneUuid = session.droneUuid();
        if (droneUuid != null) {
            Entity drone = RtsCameraEntityHelper.findDroneEntity(player.getServer(), droneUuid);
            if (drone instanceof RtsDroneEntity d && drone.level() == player.serverLevel()) {
                d.setTarget(x, y, z, yaw, pitch);
                // 注意：无人机动画状态包不在这里发——此处由客户端 CAMERA_POSE 往返触发，
                // 会让动画值比位置包多一次往返延迟且到达时机不齐，导致客户端插值跳变卡顿。
                // 改为无人机服务端 tick 直发（与位置包同相位）。
            } else {
                if (drone != null) {
                    drone.discard();
                }
                droneUuid = null;
            }
        }
        if (droneUuid == null) {
            RtsDroneEntity drone = RtsCameraEntityHelper.createAndSpawnDrone(player.serverLevel(), player.getUUID(),
                    x, y, z, yaw);
            droneUuid = drone.getUUID();
        }

        SESSIONS.put(player.getUUID(), new Session(
                session.cameraUuid(), session.anchor(), new Vec3(x, y, z),
                yaw, pitch, y - session.anchor().y,
                session.homeSelection(), session.maxRadius(), session.closeRangeAllowed(),
                session.terminalUuid(), droneUuid));
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

        float pitch = Mth.wrapDegrees(session.pitchDeg() + (safeRotateY * ROTATE_GAIN_Y));

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
                yaw, pitch, heightOffset, session.homeSelection(), session.maxRadius(), session.closeRangeAllowed(),
                session.terminalUuid(), session.droneUuid()));

        // 通知客户端更新后的锚点位置，使可视边界保持同步
        PacketDistributor.sendToPlayer(player, new S2CRtsCameraAnchorPayload(
                newAnchor.x, newAnchor.y, newAnchor.z, maxRadius(player, session)));
    }

    /**
     * 每 Tick 更新锚点——当玩家物理移动时，让建筑边界跟随玩家。<p>
     * 摄像机视角移动和旋转已改为纯客户端处理，不再经服务端；
     * 此方法仅负责锚点（building bounds）的跟随。</p>
     *
     * @param player 目标玩家
     */
    public static void updateAnchorForPlayer(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;

        Vec3 playerPos = player.position();
        Vec3 currentAnchor = session.anchor();
        Vec3 newAnchor = new Vec3(Math.floor(playerPos.x) + 0.5D, playerPos.y, Math.floor(playerPos.z) + 0.5D);

        // 只有锚点真正变化时才发包
        if (currentAnchor.distanceToSqr(newAnchor) < 0.01D) return;

        SESSIONS.put(player.getUUID(), new Session(
                session.cameraUuid(), newAnchor, session.cameraPos(),
                session.yawDeg(), session.pitchDeg(), session.heightOffset(),
                session.homeSelection(), session.maxRadius(), session.closeRangeAllowed(),
                session.terminalUuid(), session.droneUuid()));

        PacketDistributor.sendToPlayer(player, new S2CRtsCameraAnchorPayload(
                newAnchor.x, newAnchor.y, newAnchor.z, maxRadius(player, session)));
    }

    /**
     * 获取或恢复相机实体。<p>如果相机丢失（因维度切换等），则按上次记录的会话状态重新创建。</p>
     */
    @SuppressWarnings("resource")
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

        // 若无人机实体缺失，则一并重建（相机丢失通常伴随维度切换等异常）
        UUID droneUuid = session.droneUuid();
        if (droneUuid != null && !(RtsCameraEntityHelper.findDroneEntity(player.getServer(), droneUuid)
                instanceof RtsDroneEntity)) {
            RtsDroneEntity restoredDrone = RtsCameraEntityHelper.createAndSpawnDrone(player.serverLevel(), player.getUUID(),
                    cameraPos.x, cameraPos.y, cameraPos.z, session.yawDeg());
            droneUuid = restoredDrone.getUUID();
        }

        SESSIONS.put(player.getUUID(), new Session(
                restored.getUUID(),
                session.anchor(),
                cameraPos,
                session.yawDeg(),
                session.pitchDeg(),
                session.heightOffset(),
                session.homeSelection(),
                session.maxRadius(),
                session.closeRangeAllowed(),
                session.terminalUuid(),
                droneUuid));

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
                session.closeRangeAllowed(),
                session.terminalUuid()));
        return restored;
    }

    /**
     * 清理所有不在 SESSIONS 中的孤儿相机实体。
     */
    public static void cleanupOrphanCameras(MinecraftServer server) {
        Set<UUID> activeDrones = new HashSet<>();
        for (Session session : SESSIONS.values()) {
            if (session.droneUuid() != null) {
                activeDrones.add(session.droneUuid());
            }
        }
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
        RtsCameraEntityHelper.cleanupOrphanDrones(server, activeDrones);
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
     * @param terminalUuid     开启该模式的那把终端的 UUID（可为 null）
     * @param droneUuid        跟随无人机的实体 UUID（可为 null）
     */
    private record Session(UUID cameraUuid, Vec3 anchor, Vec3 cameraPos, float yawDeg, float pitchDeg,
                           double heightOffset, boolean homeSelection, double maxRadius, boolean closeRangeAllowed,
                           @Nullable String terminalUuid, @Nullable UUID droneUuid) {
    }
}
