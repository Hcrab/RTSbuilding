package com.rtsbuilding.rtsbuilding.compat.create;

import com.rtsbuilding.rtsbuilding.network.create.C2SRtsCreateValueSettingsPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * RTS Create Value Settings 的服务端窄安全边界。
 *
 * <p>正常用途是让玩家在 RTS 自身可配置范围内远程调节 Create 机械，所以这里明确
 * 不调用 Create/原版约 20 格近距检查，也不添加冷却。只复核活跃会话、精确维度与
 * 目标快照、RTS 产品范围、交互权限、实际行为和 board 参数；不会主动加载区块。</p>
 */
public final class RtsCreateValueSettingsServerCompat {
    private static final double HIT_BOUND_EPSILON = 1.0E-4D;

    private RtsCreateValueSettingsServerCompat() {
    }

    public static void handle(ServerPlayer player, C2SRtsCreateValueSettingsPayload payload) {
        if (player == null || payload == null || payload.dimension() == null
                || payload.pos() == null || payload.face() == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean activeRtsSession = RtsCameraManager.isActive(player);
        boolean exactDimension = level.dimension().location().equals(payload.dimension());
        boolean targetChunkLoaded = exactDimension
                && !level.isOutsideBuildHeight(payload.pos())
                && level.hasChunkAt(payload.pos());
        boolean withinRtsActionRange = activeRtsSession
                && RtsCameraManager.isWithinActionRange(player, payload.pos());
        boolean mayInteract = targetChunkLoaded && level.mayInteract(player, payload.pos());
        if (!activeRtsSession || !exactDimension || !targetChunkLoaded
                || !withinRtsActionRange || !mayInteract) {
            return;
        }

        Vec3 hitLocation = new Vec3(payload.hitX(), payload.hitY(), payload.hitZ());
        if (!isFiniteHitInsideTarget(hitLocation, payload)) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(
                hitLocation, payload.face(), payload.pos(), false);
        RtsCreateValueSettingsRuntime.Candidate candidate =
                RtsCreateValueSettingsRuntime.findEligible(level, hit, player);
        boolean eligibleBehaviour = candidate != null && candidate.netId() == payload.behaviourNetId();
        if (!eligibleBehaviour) {
            return;
        }

        if (payload.shortInteraction()) {
            if (RtsCreateValueSettingsPolicy.shouldApplyOnServer(
                    activeRtsSession, exactDimension, targetChunkLoaded,
                    withinRtsActionRange, mayInteract, eligibleBehaviour, true)) {
                RtsCreateValueSettingsRuntime.applyShortInteraction(
                        candidate, player, payload.face(), hit);
            }
            return;
        }

        Object board = RtsCreateValueSettingsRuntime.createBoard(candidate, player, hit);
        boolean legalValue = RtsCreateValueSettingsRuntime.isValueAllowed(
                board, payload.row(), payload.value());
        if (!RtsCreateValueSettingsPolicy.shouldApplyOnServer(
                activeRtsSession, exactDimension, targetChunkLoaded,
                withinRtsActionRange, mayInteract, eligibleBehaviour, legalValue)) {
            return;
        }
        RtsCreateValueSettingsRuntime.applyValue(
                candidate, player, payload.row(), payload.value(), payload.ctrlDown());
    }

    private static boolean isFiniteHitInsideTarget(
            Vec3 hit, C2SRtsCreateValueSettingsPayload payload) {
        if (!Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)) {
            return false;
        }
        double minX = payload.pos().getX();
        double minY = payload.pos().getY();
        double minZ = payload.pos().getZ();
        return hit.x >= minX - HIT_BOUND_EPSILON
                && hit.x <= minX + 1.0D + HIT_BOUND_EPSILON
                && hit.y >= minY - HIT_BOUND_EPSILON
                && hit.y <= minY + 1.0D + HIT_BOUND_EPSILON
                && hit.z >= minZ - HIT_BOUND_EPSILON
                && hit.z <= minZ + 1.0D + HIT_BOUND_EPSILON;
    }
}
