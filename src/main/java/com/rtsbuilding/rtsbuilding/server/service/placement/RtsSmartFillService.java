package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCandidateClassifier;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlanner;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsConfirmSmartFillPayload;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能填洞的服务端权威入口。
 *
 * <p>本类验证 RTS 会话、参数、材料、范围和领地权限，并根据点击面重新运行共享规划器。
 * 它不创建第二套执行循环：通过验证的目标仍提交给现有 PLACE_BATCH / Task Engine，继续
 * 复用材料等待、公平切片、持久化 checkpoint 与 Ctrl+Z。</p>
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
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        Direction face = direction(payload.face());
        if (session == null || face == null
                || !validParameters(payload.maxBlocks(), payload.detectionDiameter())
                || !validInteractionGeometry(payload)
                || !RtsLinkedStorageResolver.canAccessWorldTarget(player, payload.clickedPos())) {
            notifyFailure(player, SmartFillPlan.Status.INVALID_START);
            return rejected(SmartFillPlan.Status.INVALID_START);
        }
        if (!isBlockMaterial(payload.itemId())) {
            player.displayClientMessage(
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
                pos -> SmartFillCandidateClassifier.classify(player.serverLevel(), pos));
        if (!plan.canSubmit()) {
            notifyFailure(player, plan.status());
            return new ConfirmResult(plan.status(), 0, false);
        }

        List<BlockPos> authorized = new ArrayList<>(plan.targets().size());
        for (BlockPos target : plan.targets()) {
            if (RtsLinkedStorageResolver.canAccessWorldTarget(player, target)
                    && RtsClaimProtectionService.canPlaceBlock(player, target)
                    && SmartFillCandidateClassifier.classify(player.serverLevel(), target)
                    == com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCell.CANDIDATE) {
                authorized.add(target.immutable());
            }
        }
        if (authorized.isEmpty()) {
            notifyFailure(player, SmartFillPlan.Status.NO_TARGET);
            return new ConfirmResult(SmartFillPlan.Status.NO_TARGET, 0, false);
        }

        boolean queued = ServiceRegistry.getInstance().placement().enqueuePlaceBatch(
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
                false,
                payload.itemId(),
                payload.itemPrototype(),
                payload.rayOriginX(),
                payload.rayOriginY(),
                payload.rayOriginZ(),
                payload.rayDirX(),
                payload.rayDirY(),
                payload.rayDirZ());
        if (queued) {
            player.displayClientMessage(
                    Component.translatable(
                            plan.partial()
                                    ? "message.rtsbuilding.smart_fill.queued_partial"
                                    : "message.rtsbuilding.smart_fill.queued",
                            authorized.size()),
                    true);
        }
        return new ConfirmResult(plan.status(), authorized.size(), queued);
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
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
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
        player.displayClientMessage(
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
