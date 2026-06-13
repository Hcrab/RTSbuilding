package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsUltimineProcessor;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLeaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * 挖矿服务——管理单方块挖掘、连锁挖掘、范围挖掘和范围破坏。
 *
 * <p>职责范围：
 * <ul>
 *   <li>单方块挖掘</li>
 *   <li>连锁挖掘（Ultimine）</li>
 *   <li>范围挖掘（Area Mine）</li>
 *   <li>范围破坏（Area Destroy）</li>
 *   <li>临时主手持物品切换</li>
 * </ul>
 */
public final class RtsMiningService {

    private RtsMiningService() {
    }

    // =========================================================================
    //  Single-block mine
    // =========================================================================

    /**
     * 单方块挖掘——开始/停止远程挖掘并完成工具借用/归还。
     */
    public static void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery,
            boolean toolProtectionEnabled) {
        if (start && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);

        if (start) {
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }

            // Placed block recovery
            if (allowPlacedBlockRecovery
                    && RtsMiningValidator.tryRecoverPlacedBlock(player, session, pos, face)) {
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }

            RtsMiningStateMachine.stopActiveMining(player, session);
            if (player.isCreative()) {
                Direction actualFace = face == null ? Direction.DOWN : face;
                ServerHistoryManager.recordBreak(
                        player, List.of(pos.immutable()), actualFace);
                RtsMiningStateMachine.destroyMinedBlock(player, session, pos, slot);
                RtsPageService.requestPage(
                        player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
                return;
            }

            session.mining.miningSelectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
            session.mining.miningToolLease = RtsToolLeaseManager.borrowMiningTool(
                    player, session, toolItemId, toolPrototype, slot);
            if (session.mining.miningSelectedToolRequested && session.mining.miningToolLease.isEmpty()) {
                RtsMiningStateMachine.resetMiningState(session);
                return;
            }
            session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
            RtsMiningStateMachine.beginRemoteMining(player, session, pos, face, slot);
            return;
        }

        // Stop
        if (!RtsMiningValidator.isCommittedUltimineBatch(session)) {
            RtsMiningStateMachine.stopActiveMining(player, session);
        }
    }

    // =========================================================================
    //  Ultimine
    // =========================================================================

    /**
     * 连锁挖掘（Ultimine）。
     */
    public static void startUltimine(ServerPlayer player, BlockPos pos, Direction face, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, int requestedLimit, byte mode, boolean toolProtectionEnabled) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        RtsUltimineProcessor.startUltimine(player, session, pos, face, toolSlot, toolItemId, toolPrototype,
                requestedLimit, mode, toolProtectionEnabled);
    }

    // =========================================================================
    //  Area Mine
    // =========================================================================

    /**
     * 范围挖掘（Area Mine）。
     */
    public static void areaMine(ServerPlayer player,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        RtsUltimineProcessor.areaMine(player, session, minX, maxX, minY, maxY, minZ, maxZ,
                toolSlot, toolItemId, toolPrototype, shapeType, fillType, toolProtectionEnabled);
    }

    // =========================================================================
    //  Area Destroy
    // =========================================================================

    /**
     * 范围破坏（Area Destroy）。
     */
    public static void areaDestroy(ServerPlayer player, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        RtsUltimineProcessor.areaDestroy(player, session, positions,
                toolSlot, toolItemId, toolPrototype, toolProtectionEnabled);
    }

    // =========================================================================
    //  Temporary context switcher (shared utility)
    // =========================================================================

    /**
     * 临时切换主手持物品执行操作。
     */
    public static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        return RtsMiningStateMachine.withTemporaryMainHandItem(player, stack, action);
    }
}
