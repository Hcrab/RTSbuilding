package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * 挖矿服务——管理单方块挖掘、连锁挖掘、范围挖掘和范围破坏??
 *
 * <p>职责范围??
 * <ul>
 *   <li>单方块挖??/li>
 *   <li>连锁挖掘（Ultimine??/li>
 *   <li>范围挖掘（Area Mine??/li>
 *   <li>范围破坏（Area Destroy??/li>
 *   <li>临时主手持物品切??/li>
 * </ul>
 */
public final class RtsMiningService {

    public static final RtsMiningService INSTANCE = new RtsMiningService();

    private RtsMiningService() {
    }

    // =========================================================================
    //  Single-block mine
    // =========================================================================

    /**
     * 单方块挖掘——开??停止远程挖掘并完成工具借用/归还??
     */
    public static void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery,
            boolean toolProtectionEnabled) {
        if (start) {
            PipelineRegistry.execute(RtsWorkflowType.MINE_SINGLE,
                    MiningContext.builder(player)
                            .toolSlot(toolSlot)
                            .toolItemId(toolItemId)
                            .toolPrototype(toolPrototype)
                            .pos(pos)
                            .face(face)
                            .allowPlacedBlockRecovery(allowPlacedBlockRecovery)
                            .toolProtectionEnabled(toolProtectionEnabled)
                            .totalBlocks(1)
                            .build());
            return;
        }

        // Stop
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) {
            return;
        }
        if (!RtsMiningValidator.isCommittedUltimineBatch(session)) {
            PipelineRegistry.execute(RtsWorkflowType.STOP_MINING,
                    MiningContext.builder(player).build());
        }
    }

    // =========================================================================
    //  Ultimine
    // =========================================================================

    /**
     * 连锁挖掘（Ultimine???
     */
    public static void startUltimine(ServerPlayer player, BlockPos pos, Direction face, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, int requestedLimit, byte mode, boolean toolProtectionEnabled) {
        PipelineRegistry.execute(RtsWorkflowType.ULTIMINE,
                MiningContext.builder(player)
                        .toolSlot(toolSlot)
                        .toolItemId(toolItemId)
                        .toolPrototype(toolPrototype)
                        .pos(pos)
                        .face(face)
                        .requestedLimit(requestedLimit)
                        .mode(mode)
                        .toolProtectionEnabled(toolProtectionEnabled)
                        .build());
    }

    // =========================================================================
    //  Area Mine
    // =========================================================================

    /**
     * 范围挖掘（Area Mine???
     */
    public static void areaMine(ServerPlayer player,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        PipelineRegistry.execute(RtsWorkflowType.AREA_MINE,
                MiningContext.builder(player)
                        .toolSlot(toolSlot)
                        .toolItemId(toolItemId)
                        .toolPrototype(toolPrototype)
                        .minX(minX)
                        .maxX(maxX)
                        .minY(minY)
                        .maxY(maxY)
                        .minZ(minZ)
                        .maxZ(maxZ)
                        .shapeType(shapeType)
                        .fillType(fillType)
                        .toolProtectionEnabled(toolProtectionEnabled)
                        .build());
    }

    // =========================================================================
    //  Area Destroy
    // =========================================================================

    /**
     * 范围破坏（Area Destroy???
     */
    public static void areaDestroy(ServerPlayer player, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        PipelineRegistry.execute(RtsWorkflowType.AREA_DESTROY,
                MiningContext.builder(player)
                        .toolSlot(toolSlot)
                        .toolItemId(toolItemId)
                        .toolPrototype(toolPrototype)
                        .positions(positions)
                        .toolProtectionEnabled(toolProtectionEnabled)
                        .build());
    }

    // =========================================================================
    //  Temporary context switcher (shared utility)
    // =========================================================================

    /**
     * 临时切换主手持物品执行操???
     */
    public static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        return RtsMiningStateMachine.withTemporaryMainHandItem(player, stack, action);
    }
}
