package com.rtsbuilding.rtsbuilding.common.entity;

import com.rtsbuilding.rtsbuilding.client.entity.rts_drone;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsDroneAnimPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

/**
 * RTS 无人机实体 —— 悬浮于空中的展示/部署单位。
 * <p>
 * 无重力、无物理碰撞、不可推动、无碰撞箱，通过 {@link #tick()} 维持悬停状态。
 * 模型使用 {@link rts_drone}（Blockbench 导出），四个螺旋桨叶片骨骼（blade_fl/blade_bl/blade_fr/blade_br）
 * 在模型 {@code setupAnim} 中播放 {@code rts_droneAnimation.fly} 动画循环旋转。
 * <p>
 * 进入 RTS 模式后由 {@code RtsCameraManager} 创建，客户端每 tick 上报相机姿态
 * （{@code CAMERA_POSE} 包）后通过 {@link #setTarget} 设置飞行目标（相机上方 4 格、朝向与相机一致）；
 * 无人机以有限速度"飞向"目标而非瞬移锁死，保证飞行动画可见。
 */
public class RtsDroneEntity extends Entity {

    /**
     * 无人机模型相对实体位置的基准高度偏移（单位：格）。
     * Blockbench 导出的模型已中心对准原点（body 位于 y=15.84，模型总高约 8.8 像素），
     * 模型中心约为 y=15.6 像素（15.6/16 格），此处换算为格并向下偏移，使模型中心对准实体位置。
     */
    public static final float MODEL_HEIGHT_OFFSET = -15.6F / 16.0F;

    /** 无人机飞行封顶速度（格/tick）：40 格/秒。距离越远速度越快，达到该值后不再增加 */
    private static final double MAX_FLY_SPEED = 2.0D;
    /** 全速距离阈值（格）：与目标距离 ≥ 该值即全速飞行；距离越近速度按比例降低，距离为 0 时悬停 */
    private static final double FULL_SPEED_DISTANCE = 12.0D;
    /** 到达判定阈值（格）：小于该距离视为已到达 */
    private static final double ARRIVE_THRESHOLD = 0.05D;
    /** 到达落点阈值（格）：与目标距离 ≤ 该值时直接对准目标落点，杜绝亚像素无限逼近最后一段 */
    private static final double ARRIVE_SNAP_DISTANCE = 0.08D;
    /** 最小逼近速度（格/tick）：保证最后接近段位移仍高于位置同步发包阈值（≈0.014 格/tick），
     *  客户端不会因收不到位置包而在半路“冻结” */
    private static final double MIN_APPROACH_SPEED = 0.05D;
    /** 朝向插值速率：每 tick 向目标偏航角转剩余角度的比例 */
    private static final float YAW_LERP_RATE = 0.25F;

    /** 倾角满幅距离（格）：与目标水平距离达到该值时飞行倾角最大（与全速距离一致，保证减速区同步回正） */
    private static final double FULL_TILT_DISTANCE = 12.0D;
    /** 最大飞行倾角（度）：随剩余水平距离线性递增，最多 25° */
    private static final float MAX_TILT_DEG = 25.0F;
    /** 倾角每 tick 平滑系数（帧率无关，越大响应越快、回正越利落） */
    private static final float TILT_SMOOTHING = 0.3F;

    /** 所属玩家 UUID 同步通道（客户端据此判断"我的无人机"，供渲染器在摄像机视角隐藏） */
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(RtsDroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 飞行目标（由 RtsCameraManager 通过 {@link #setTarget} 每 tick 更新） */
    private double targetX, targetY, targetZ;
    private float targetYaw;
    /** RTS 相机俯仰角目标（度，来自 {@code CAMERA_POSE} 网络包，驱动无人机相机云台的上下角度） */
    private float targetPitch;
    private boolean hasTarget;

    /** 服务端当前飞行倾角（度）：俯仰/横滚，随剩余水平距离计算并每 tick 平滑，经动画网络包同步给客户端 */
    private float tiltX;
    private float tiltZ;

    /** 客户端渲染插值缓存（仅客户端使用，由服务端动画网络包 {@code S2CRtsDroneAnimPayload} 更新）：
     *  上一包 / 当前包的机身倾角、相机云台俯仰、机身偏航，渲染层用 partialTick 插值平滑补帧。 */
    public float animPrevTiltX;
    public float animCurrTiltX;
    public float animPrevTiltZ;
    public float animCurrTiltZ;
    public float animPrevPitch;
    public float animCurrPitch;
    public float animPrevYaw;
    public float animCurrYaw;

    /** 客户端位置插值缓存：上一包位置（供 xo 旧值，与位置包处理顺序无关），以及是否已初始化 */
    public double animCurrX;
    public double animCurrY;
    public double animCurrZ;
    public boolean animPosInit;

    /** 上次网络包发送的动画状态（服务端，用于变化检测——悬停无变化时停发，避免每 tick 空转包） */
    private double lastSentX, lastSentY, lastSentZ;
    private float lastSentYaw, lastSentPitch, lastSentTiltX, lastSentTiltZ;
    private boolean animStateSent;

    /** 动画网络包变化阈值：位移 ≥ 0.001 格或任一角度变化 ≥ 0.05° 才发送（悬停时收敛到 0，完全停发） */
    private static final double ANIM_MOVE_EPSILON_SQ = 1.0E-6D;   // (0.001)²
    private static final float ANIM_ANGLE_EPSILON_DEG = 0.05F;

    public RtsDroneEntity(EntityType<? extends RtsDroneEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 无人机动画状态（倾角/云台俯仰/偏航）改由 S2CRtsDroneAnimPayload 网络包每 tick 下发；
        // 这里只同步所属玩家 UUID，供客户端判断"我的无人机"。
        builder.define(DATA_OWNER_UUID, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // 零尺寸碰撞箱：无碰撞体积（模型为纯视觉，不参与碰撞检测）
        return EntityDimensions.scalable(0.0F, 0.0F);
    }

    public UUID getOwnerUuid() {
        return this.entityData.get(DATA_OWNER_UUID).orElse(null);
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.entityData.set(DATA_OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    /**
     * 快速设置无人机的位置和朝向——使用 {@link #setPosRaw} 跳过
     * {@link #setPos(double, double, double)} 中的区块位置重算逻辑，
     * 无人机不参与世界交互，无需更新区块引用（与 RtsCameraEntity 一致）。
     *
     * @param x   X 坐标
     * @param y   Y 坐标
     * @param z   Z 坐标
     * @param yaw 偏航角（度）
     */
    public void snapTo(double x, double y, double z, float yaw) {
        this.setPosRaw(x, y, z);
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setOldPosAndRot();
        this.yRotO = yaw;
        this.xRotO = 0.0F;
    }

    /**
     * 设置飞行目标位置与朝向。
     * <p>无人机不会瞬移，而是在 {@link #tick()} 中以有限速度逐步"飞向"目标
     * （远距离全速、接近时减速），保持飞行动画可见；目标持续更新时自动追向最新位置。</p>
     *
     * @param x     目标 X 坐标
     * @param y     目标 Y 坐标
     * @param z     目标 Z 坐标
     * @param yaw   目标偏航角（度）
     */
    public void setTarget(double x, double y, double z, float yaw) {
        this.setTarget(x, y, z, yaw, 0.0F);
    }

    /**
     * 设置飞行目标位置、朝向与 RTS 相机俯仰角。
     * <p>俯仰角/偏航角来自客户端上报的 {@code CAMERA_POSE} 网络包，作为无人机相机云台上下角度与
     * 机身朝向的目标，由服务端每 tick 经 {@code S2CRtsDroneAnimPayload} 动画网络包下发给客户端渲染。</p>
     *
     * @param x     目标 X 坐标
     * @param y     目标 Y 坐标
     * @param z     目标 Z 坐标
     * @param yaw   目标偏航角（度）
     * @param pitch 相机俯仰角（度，负值向上、正值向下）
     */
    public void setTarget(double x, double y, double z, float yaw, float pitch) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.hasTarget = true;
    }

    /**
     * 客户端接收无人机动画同步包（S2CRtsDroneAnimPayload）：
     * <ul>
     *   <li>位置：把 xo/yo/zo 置为当前（上一包）位置、再 setPosRaw 到本包位置，
     *       形成合法的 [old, current] 插值窗口，原版渲染器用 partialTick 平滑插值——
     *       无人机客户端不 tick，原版位置包不会维护 xo，位置会一跳一跳地卡顿。</li>
     *   <li>动画：上一包状态存入 prev、本包存入 curr，供渲染层 partialTick 插值。</li>
     * </ul>
     */
    public void receiveAnimState(double x, double y, double z, float yaw, float pitch, float tiltX, float tiltZ) {
        this.animPrevYaw = this.animCurrYaw;
        this.animPrevPitch = this.animCurrPitch;
        this.animPrevTiltX = this.animCurrTiltX;
        this.animPrevTiltZ = this.animCurrTiltZ;
        this.animCurrYaw = yaw;
        this.animCurrPitch = pitch;
        this.animCurrTiltX = tiltX;
        this.animCurrTiltZ = tiltZ;

        // 位置插值窗口：old = 上一包位置、current = 本包位置。
        // 旧值取 animCurrX/Y/Z（独立缓存），不读 getX()——否则当原版位置包先于我处理时
        // getX() 已是新位置，窗口归零导致无法插值。首次以客户端出生点初始化避免从原点飞来。
        // 渲染器插值用 xOld/yOld/zOld（EntityRenderDispatcher 用 lerp(partialTick, xOld, getX())），
        // 同时维护 xo/yo/zo 保持一致。
        if (!this.animPosInit) {
            this.animCurrX = this.getX();
            this.animCurrY = this.getY();
            this.animCurrZ = this.getZ();
            this.animPosInit = true;
        }
        this.xo = this.animCurrX;
        this.yo = this.animCurrY;
        this.zo = this.animCurrZ;
        this.xOld = this.animCurrX;
        this.yOld = this.animCurrY;
        this.zOld = this.animCurrZ;
        this.animCurrX = x;
        this.animCurrY = y;
        this.animCurrZ = z;
        this.setPosRaw(x, y, z);
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    public void tick() {
        // 悬停无人机：不做碰撞检测 / 重力 / 传送门检测
        this.noPhysics = true;
        this.setNoGravity(true);
        // 移动与倾角计算只在服务端执行（目标由服务端 RtsCameraManager 下发，位置包广播给客户端）；
        // 客户端实体只读取同步后的倾角供渲染，避免两端重复计算互相覆盖。
        if (!this.level().isClientSide()) {
            if (this.hasTarget) {
                this.moveTowardTarget();
            } else {
                // 无目标时平滑回正：飞行倾角归零，相机俯仰/偏航目标归零（由动画网络包下发给客户端淡出回正）
                this.smoothTiltToward(0.0F, 0.0F);
                this.targetPitch = 0.0F;
                this.targetYaw = 0.0F;
            }
            // 服务端每 tick 直发无人机动画状态包（与位置包同延迟、同到达相位）。
            // 不能改到 updateCameraPose 里发——那里由客户端 CAMERA_POSE 往返触发，
            // 会让动画值比位置多一次往返、到达时机不齐，客户端插值跳变导致卡顿。
            this.sendAnimStateToOwner();
        }
    }

    /**
     * 向所属玩家下发本 tick 的动画状态（机身倾角/云台俯仰/机身朝向）。
     * <p>只应在服务端调用（无人机 tick 内）。</p>
     * <p>悬停无变化时停发网络包：客户端 prev/curr 插值窗口保持相同值，渲染自然静止；
     * 首个状态强制发送，保证客户端实体生成后立即获得初始值。</p>
     */
    private void sendAnimStateToOwner() {
        UUID ownerUuid = this.getOwnerUuid();
        if (this.level() instanceof ServerLevel serverLevel && ownerUuid != null) {
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner == null) return;

            double x = this.getX(), y = this.getY(), z = this.getZ();
            float yaw = this.targetYaw, pitch = this.targetPitch, tiltX = this.tiltX, tiltZ = this.tiltZ;

            // 变化检测：位移/角度均低于阈值且非首包时跳过发送
            double dx = x - lastSentX, dy = y - lastSentY, dz = z - lastSentZ;
            boolean changed = !animStateSent
                    || dx * dx + dy * dy + dz * dz > ANIM_MOVE_EPSILON_SQ
                    || Math.abs(yaw - lastSentYaw) > ANIM_ANGLE_EPSILON_DEG
                    || Math.abs(pitch - lastSentPitch) > ANIM_ANGLE_EPSILON_DEG
                    || Math.abs(tiltX - lastSentTiltX) > ANIM_ANGLE_EPSILON_DEG
                    || Math.abs(tiltZ - lastSentTiltZ) > ANIM_ANGLE_EPSILON_DEG;
            if (!changed) return;

            PacketDistributor.sendToPlayer(owner, new S2CRtsDroneAnimPayload(
                    this.getId(), x, y, z, yaw, pitch, tiltX, tiltZ));
            animStateSent = true;
            lastSentX = x; lastSentY = y; lastSentZ = z;
            lastSentYaw = yaw; lastSentPitch = pitch;
            lastSentTiltX = tiltX; lastSentTiltZ = tiltZ;
        }
    }

    /**
     * 向飞行目标插值移动：速度与到目标的距离成正比——
     * 距离越远飞得越快（封顶 {@link #MAX_FLY_SPEED}），距离变近时速度按比例下降，
     * 最后通过最小逼近速度 + 精确落点收尾，避免“卡顿”；朝向同步向目标偏航角平滑旋转。
     * 每次调用（含已到达）都会更新飞行倾角。
     */
    private void moveTowardTarget() {
        double dx = this.targetX - this.getX();
        double dy = this.targetY - this.getY();
        double dz = this.targetZ - this.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > ARRIVE_THRESHOLD) {
            if (dist <= ARRIVE_SNAP_DISTANCE) {
                // 距目标足够近：直接对准目标精确落点。否则会以指数曲线无限逼近最后零点几格，
                // 该段每 tick 位移跌破位置同步发包阈值（≈0.014 格/tick），服务端还在动、
                // 客户端却收不到位置包而原地冻结——正是“快到位置卡顿一下”的根源。
                this.setPosRaw(this.targetX, this.targetY, this.targetZ);
            } else {
                // 速度 = 距离 × 比例（MAX_FLY_SPEED / FULL_SPEED_DISTANCE），并封顶全速；
                // 同时设最小逼近速度，保证最后接近段位移始终高于发包阈值，客户端运动连续不冻结。
                double speed = Math.min(MAX_FLY_SPEED,
                        Math.max(MIN_APPROACH_SPEED, dist * MAX_FLY_SPEED / FULL_SPEED_DISTANCE));
                double step = Math.min(dist, speed);
                this.setPosRaw(this.getX() + dx / dist * step,
                        this.getY() + dy / dist * step,
                        this.getZ() + dz / dist * step);
            }

            // 朝向平滑旋转（不直接锁定，保持飞行动画自然）
            float dyaw = Mth.wrapDegrees(this.targetYaw - this.getYRot());
            float newYaw = this.getYRot() + dyaw * YAW_LERP_RATE;
            this.setYRot(newYaw);
            this.setYHeadRot(newYaw);
            this.setYBodyRot(newYaw);

            this.setOldPosAndRot();
            this.xRotO = 0.0F;
        }
        // 无论是否已到达都更新倾角：到达后目标倾角为 0，平滑回正
        this.updateMovementTilt(dx, dz);
    }

    /**
     * 根据与目标的剩余**水平**距离计算并平滑更新飞行倾角（模拟真实无人机姿态）：
     * <ul>
     *   <li>只统计水平距离（XZ 平面，即网络包中目标点与无人机的 XY 横向距离），
     *       不计入高度轴（垂直方向），因此无人机在目标正上方垂直升降时不会产生倾角。</li>
     *   <li>倾角大小跟随剩余水平距离线性递增，最多 {@link #MAX_TILT_DEG}（25°）：
     *       水平距离 ≥ {@link #FULL_TILT_DISTANCE} 满幅，接近目标时按比例减小，到达后归零——即回正动画。</li>
     *   <li>倾角方向始终朝向目标水平方向——分解到模型前方（+Z）得俯仰、分解到横向（+X）得横滚：
     *       朝 +Z 移动低头、朝 +X 移动右倾。</li>
     *   <li>每 tick 用 {@link #TILT_SMOOTHING} 平滑逼近目标（帧率无关），渲染层再用 partialTick + 缓动插值，全程流畅。</li>
     * </ul>
     * 注：当前模型视觉朝向固定为 +Z（不随 yaw 旋转），故倾角按世界坐标分解。
     */
    private void updateMovementTilt(double dx, double dz) {
        // 水平距离（忽略高度差 dy）
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float tiltTarget = (float) Math.min(1.0D, horizontalDist / FULL_TILT_DISTANCE) * MAX_TILT_DEG;
        float pitchTarget = 0.0F;
        float rollTarget = 0.0F;
        if (horizontalDist > 1.0E-4D) {
            float nx = (float) (dx / horizontalDist);
            float nz = (float) (dz / horizontalDist);
            pitchTarget = -tiltTarget * nz;
            rollTarget = -tiltTarget * nx;
        }

        this.smoothTiltToward(pitchTarget, rollTarget);
    }

    /** 以固定系数向目标倾角平滑逼近（服务端），当前值经动画网络包同步给客户端 */
    private void smoothTiltToward(float pitchTarget, float rollTarget) {
        this.tiltX += (pitchTarget - this.tiltX) * TILT_SMOOTHING;
        this.tiltZ += (rollTarget - this.tiltZ) * TILT_SMOOTHING;
    }
}
