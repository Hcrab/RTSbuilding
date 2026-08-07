package com.rtsbuilding.rtsbuilding.compat.create;

import com.rtsbuilding.rtsbuilding.network.create.C2SRtsCreateValueSettingsPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * RTS Create Value Settings 的服务端权限边界。
 *
 * <p>本类只接纳已经处于有效 RTS 会话的玩家提交，并重新从已加载区块中的 SmartBlockEntity 找到对应
 * Create 行为。它不会调用 canInteractWithBlock、maxRange 或任何玩家实体到目标的距离检查；远程能力
 * 的边界由 RTS 会话本身决定。未加载区块、已移除 BE 或反射不可用时一律安全忽略，绝不主动加载区块。</p>
 */
public final class RtsCreateValueSettingsServerCompat {
    private static final double HIT_BOUND_EPSILON = 1.0E-4D;

    private RtsCreateValueSettingsServerCompat() {
    }

    public static void handle(ServerPlayer player, C2SRtsCreateValueSettingsPayload payload) {
        boolean activeRtsSession = player != null && payload != null && RtsCameraManager.isActive(player);
        if (!activeRtsSession) {
            return;
        }
        ServerLevel level = player.level();
        boolean targetChunkLoaded = level.hasChunkAt(payload.pos());
        if (!targetChunkLoaded) {
            return;
        }

        Vec3 hitLocation = new Vec3(payload.hitX(), payload.hitY(), payload.hitZ());
        if (!isFiniteHitInsideTarget(hitLocation, payload)) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(hitLocation, payload.face(), payload.pos(), false);
        RtsCreateValueSettingsRuntime.Candidate candidate =
                RtsCreateValueSettingsRuntime.findEligible(level, hit, player);
        boolean eligibleBehaviour = candidate != null && candidate.netId() == payload.behaviourNetId();
        if (!eligibleBehaviour) {
            return;
        }

        if (payload.shortInteraction()) {
            if (RtsCreateValueSettingsPolicy.shouldApplyOnServer(
                    activeRtsSession, targetChunkLoaded, true, true)) {
                RtsCreateValueSettingsRuntime.applyShortInteraction(candidate, player, payload.face(), hit);
            }
            return;
        }

        Object board = RtsCreateValueSettingsRuntime.createBoard(candidate, player, hit);
        if (!RtsCreateValueSettingsPolicy.shouldApplyOnServer(
                activeRtsSession, targetChunkLoaded, eligibleBehaviour,
                RtsCreateValueSettingsRuntime.isValueAllowed(board, payload.row(), payload.value()))) {
            return;
        }
        RtsCreateValueSettingsRuntime.applyValue(
                candidate, player, payload.row(), payload.value(), payload.ctrlDown());
    }

    private static boolean isFiniteHitInsideTarget(Vec3 hit, C2SRtsCreateValueSettingsPayload payload) {
        if (!Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)) {
            return false;
        }
        double minX = payload.pos().getX();
        double minY = payload.pos().getY();
        double minZ = payload.pos().getZ();
        return hit.x >= minX - HIT_BOUND_EPSILON && hit.x <= minX + 1.0D + HIT_BOUND_EPSILON
                && hit.y >= minY - HIT_BOUND_EPSILON && hit.y <= minY + 1.0D + HIT_BOUND_EPSILON
                && hit.z >= minZ - HIT_BOUND_EPSILON && hit.z <= minZ + 1.0D + HIT_BOUND_EPSILON;
    }
}
