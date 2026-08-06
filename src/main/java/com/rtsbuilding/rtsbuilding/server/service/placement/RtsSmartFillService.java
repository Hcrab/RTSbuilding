package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCandidateClassifier;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCell;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlanner;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsConfirmSmartFillPayload;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能填坑的服务端权威入口。
 *
 * <p>本类重算客户端锚点附近的封闭区域，逐格复核远程操作半径与领地权限，随后仅复用既有
 * {@code PLACE_BATCH}。它不信任预览坐标，也不会借此缩短普通 RTS 的远程建造范围。</p>
 */
public final class RtsSmartFillService {
    private RtsSmartFillService() {
    }

    public static boolean confirm(EntityPlayerMP player, C2SRtsConfirmSmartFillPayload payload) {
        if (player == null || payload == null) return false;
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        EnumFacing face = payload.face() < 0 || payload.face() >= EnumFacing.values().length
                ? null : EnumFacing.byIndex(payload.face());
        if (session == null || face == null || !validParameters(payload)
                || !validGeometry(payload)
                || !RtsLinkedStorageResolver.canAccessWorldTarget(player, payload.clickedPos())) {
            notifyFailure(player, "invalid");
            return false;
        }
        if (!isBlockMaterial(payload.itemId())) {
            notifyFailure(player, "select_material");
            return false;
        }

        SmartFillPlan plan = SmartFillPlanner.plan(payload.clickedPos(), face,
                new SmartFillPlanner.Limits(payload.maxBlocks(), payload.detectionDiameter(),
                        SmartFillLimits.HARD_MAX_BLOCKS, SmartFillLimits.QUERY_BUDGET),
                pos -> SmartFillCandidateClassifier.classify(player.getServerWorld(), pos));
        if (!plan.canSubmit()) {
            notifyFailure(player, failureKey(plan.status()));
            return false;
        }

        List<BlockPos> authorized = new ArrayList<BlockPos>(plan.targets().size());
        for (BlockPos target : plan.targets()) {
            if (RtsLinkedStorageResolver.canAccessWorldTarget(player, target)
                    && RtsClaimProtectionService.canPlaceBlock(player, target)
                    && SmartFillCandidateClassifier.classify(player.getServerWorld(), target)
                    == SmartFillCell.CANDIDATE) {
                authorized.add(target.toImmutable());
            }
        }
        if (authorized.isEmpty()) {
            notifyFailure(player, "no_target");
            return false;
        }

        RtsPlacementService.enqueuePlaceBatch(player, authorized, face,
                payload.hitOffsetX(), payload.hitOffsetY(), payload.hitOffsetZ(),
                payload.rotateSteps(), false, true, payload.itemId(), payload.itemPrototype().copy(),
                payload.rayOriginX(), payload.rayOriginY(), payload.rayOriginZ(),
                payload.rayDirX(), payload.rayDirY(), payload.rayDirZ());
        player.sendStatusMessage(new TextComponentTranslation(
                plan.partial() ? "message.rtsbuilding.smart_fill.queued_partial"
                        : "message.rtsbuilding.smart_fill.queued",
                Integer.valueOf(authorized.size())), true);
        return true;
    }

    private static boolean validParameters(C2SRtsConfirmSmartFillPayload payload) {
        return payload.maxBlocks() >= SmartFillLimits.MIN_BLOCKS
                && payload.maxBlocks() <= SmartFillLimits.MAX_BLOCKS
                && payload.detectionDiameter() >= SmartFillLimits.MIN_DIAMETER
                && payload.detectionDiameter() <= SmartFillLimits.MAX_DIAMETER;
    }

    private static boolean validGeometry(C2SRtsConfirmSmartFillPayload payload) {
        if (!unit(payload.hitOffsetX()) || !unit(payload.hitOffsetY()) || !unit(payload.hitOffsetZ())) {
            return false;
        }
        double length = payload.rayDirX() * payload.rayDirX()
                + payload.rayDirY() * payload.rayDirY()
                + payload.rayDirZ() * payload.rayDirZ();
        return length > 1.0E-8D;
    }

    private static boolean unit(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= 0.0D && value <= 1.0D;
    }

    private static boolean isBlockMaterial(String itemId) {
        ResourceLocation id;
        try {
            id = new ResourceLocation(itemId == null ? "" : itemId);
        } catch (RuntimeException ignored) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item instanceof ItemBlock;
    }

    private static String failureKey(SmartFillPlan.Status status) {
        if (status == SmartFillPlan.Status.UNLOADED_BOUNDARY) return "unloaded";
        if (status == SmartFillPlan.Status.QUERY_BUDGET_EXCEEDED
                || status == SmartFillPlan.Status.HARD_LIMIT_REJECTED) return "too_large";
        if (status == SmartFillPlan.Status.INVALID_START) return "invalid";
        return "no_target";
    }

    private static void notifyFailure(EntityPlayerMP player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(
                "message.rtsbuilding.smart_fill." + key), true);
    }
}
