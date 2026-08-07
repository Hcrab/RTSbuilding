package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCandidateClassifier;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCell;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlanner;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsConfirmSmartFillPayload;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能填坑的服务端权威入口。
 *
 * <p>服务端只接受锚点和受限参数，随后按当前世界重新运行共享规划器，再将通过
 * 原有世界访问与领地权限检查的目标交给现有 PLACE_BATCH 管线。这里不复制放置、
 * 耗材、分 Tick 或撤销逻辑，也不额外施加距离、冷却或会话闸门。</p>
 */
public final class RtsSmartFillService {
    private RtsSmartFillService() {
    }

    public static ConfirmResult confirm(
            ServerPlayer player,
            C2SRtsConfirmSmartFillPayload payload) {
        if (player == null || payload == null) {
            return rejected(SmartFillPlan.Status.INVALID_START);
        }
        Direction face = direction(payload.face());
        if (face == null
                || !validParameters(payload.maxBlocks(), payload.detectionDiameter())
                || !validInteractionGeometry(payload)) {
            notifyFailure(player, SmartFillPlan.Status.INVALID_START);
            return rejected(SmartFillPlan.Status.INVALID_START);
        }
        if (!isBlockMaterial(payload.itemId())) {
            player.sendSystemMessage(
                    Component.translatable("message.rtsbuilding.smart_fill.select_material"), true);
            return rejected(SmartFillPlan.Status.INVALID_START);
        }

        SmartFillPlan plan = SmartFillPlanner.plan(
                payload.clickedPos(),
                face,
                new SmartFillPlanner.Limits(
                        payload.maxBlocks(),
                        payload.detectionDiameter(),
                        SmartFillLimits.HARD_MAX_BLOCKS,
                        SmartFillLimits.QUERY_BUDGET),
                pos -> SmartFillCandidateClassifier.classify(player.level(), pos));
        if (!plan.canSubmit()) {
            notifyFailure(player, plan.status());
            return new ConfirmResult(plan.status(), 0, false);
        }

        List<BlockPos> authorized = new ArrayList<>(plan.targets().size());
        for (BlockPos target : plan.targets()) {
            if (RtsLinkedStorageResolver.canAccessWorldTarget(player, target)
                    && RtsClaimProtectionService.canPlaceBlock(player, target)
                    && SmartFillCandidateClassifier.classify(player.level(), target)
                    == SmartFillCell.CANDIDATE) {
                authorized.add(target.immutable());
            }
        }
        if (authorized.isEmpty()) {
            notifyFailure(player, SmartFillPlan.Status.NO_TARGET);
            return new ConfirmResult(SmartFillPlan.Status.NO_TARGET, 0, false);
        }

        ServiceRegistry.getInstance().placement().enqueuePlaceBatch(
                player,
                authorized,
                face,
                payload.hitOffsetX(),
                payload.hitOffsetY(),
                payload.hitOffsetZ(),
                payload.rotateSteps(),
                payload.statePreset(),
                false,
                true,
                payload.itemId(),
                payload.itemPrototype(),
                payload.rayOriginX(),
                payload.rayOriginY(),
                payload.rayOriginZ(),
                payload.rayDirX(),
                payload.rayDirY(),
                payload.rayDirZ());
        player.sendSystemMessage(
                Component.translatable(
                        plan.partial()
                                ? "message.rtsbuilding.smart_fill.queued_partial"
                                : "message.rtsbuilding.smart_fill.queued",
                        authorized.size()),
                true);
        return new ConfirmResult(plan.status(), authorized.size(), true);
    }

    private static boolean validParameters(int blocks, int diameter) {
        return blocks >= SmartFillLimits.MIN_BLOCKS && blocks <= SmartFillLimits.MAX_BLOCKS
                && diameter >= SmartFillLimits.MIN_DIAMETER
                && diameter <= SmartFillLimits.MAX_DIAMETER;
    }

    private static boolean validInteractionGeometry(C2SRtsConfirmSmartFillPayload payload) {
        if (!unitInterval(payload.hitOffsetX())
                || !unitInterval(payload.hitOffsetY())
                || !unitInterval(payload.hitOffsetZ())
                || !Double.isFinite(payload.rayOriginX())
                || !Double.isFinite(payload.rayOriginY())
                || !Double.isFinite(payload.rayOriginZ())
                || !Double.isFinite(payload.rayDirX())
                || !Double.isFinite(payload.rayDirY())
                || !Double.isFinite(payload.rayDirZ())) {
            return false;
        }
        double lengthSquared = payload.rayDirX() * payload.rayDirX()
                + payload.rayDirY() * payload.rayDirY()
                + payload.rayDirZ() * payload.rayDirZ();
        return lengthSquared > 1.0E-8D;
    }

    private static boolean unitInterval(double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
    }

    private static boolean isBlockMaterial(String itemId) {
        Identifier id = Identifier.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item instanceof BlockItem;
    }

    private static Direction direction(byte value) {
        return value < 0 || value >= Direction.values().length
                ? null : Direction.from3DDataValue(value);
    }

    private static void notifyFailure(ServerPlayer player, SmartFillPlan.Status status) {
        String suffix = switch (status) {
            case UNLOADED_BOUNDARY -> "unloaded";
            case QUERY_BUDGET_EXCEEDED, HARD_LIMIT_REJECTED -> "too_large";
            case INVALID_START -> "invalid";
            default -> "no_target";
        };
        player.sendSystemMessage(
                Component.translatable("message.rtsbuilding.smart_fill." + suffix), true);
    }

    private static ConfirmResult rejected(SmartFillPlan.Status status) {
        return new ConfirmResult(status, 0, false);
    }

    public record ConfirmResult(
            SmartFillPlan.Status status,
            int targetCount,
            boolean queued) {
    }
}
