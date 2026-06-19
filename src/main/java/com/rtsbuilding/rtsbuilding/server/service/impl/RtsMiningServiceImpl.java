package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.MiningService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link MiningService} 的默认实现——处理 RTS 模式下的各种远程挖掘操作。
 *
 * <p>该实现类通过 {@link com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry}
 * 执行挖掘流程：
 * <ul>
 *   <li>单方块挖掘（{@code MINE_SINGLE}）</li>
 *   <li>连锁挖掘（{@code ULTIMINE}）</li>
 *   <li>范围挖掘（{@code AREA_MINE}）</li>
 *   <li>范围破坏（{@code AREA_DESTROY}）</li>
 *   <li>停止挖掘（{@code STOP_MINING}）</li>
 * </ul>
 * 所有操作使用 {@link com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext} 封装参数。
 */
public final class RtsMiningServiceImpl implements MiningService {

    @Override
    public void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot,
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
        // 停止 — 委托给 STOP_MINING 流程
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null && !RtsMiningValidator.isCommittedUltimineBatch(session)) {
            PipelineRegistry.execute(RtsWorkflowType.STOP_MINING,
                    MiningContext.builder(player).build());
        }
    }

    @Override
    public void startUltimine(ServerPlayer player, BlockPos pos, Direction face, byte toolSlot,
                              String toolItemId, ItemStack toolPrototype, int requestedLimit,
                              byte mode, boolean toolProtectionEnabled) {
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

    @Override
    public void areaMine(ServerPlayer player, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
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

    @Override
    public void areaDestroy(ServerPlayer player, List<BlockPos> positions, byte toolSlot,
                            String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        PipelineRegistry.execute(RtsWorkflowType.AREA_DESTROY,
                MiningContext.builder(player)
                        .toolSlot(toolSlot)
                        .toolItemId(toolItemId)
                        .toolPrototype(toolPrototype)
                        .positions(positions)
                        .toolProtectionEnabled(toolProtectionEnabled)
                        .build());
    }

    @Override
    public int getAreaDestroyTotalBlocks(ServerPlayer player) {
        return getAreaDestroyMetric(player, RtsWorkflowStatus::totalBlocks);
    }

    @Override
    public int getAreaDestroyCompletedBlocks(ServerPlayer player) {
        return getAreaDestroyMetric(player, RtsWorkflowStatus::completedBlocks);
    }

    @Override
    public int getAreaDestroyRemainingBlocks(ServerPlayer player) {
        return getAreaDestroyMetric(player, RtsWorkflowStatus::remainingBlocks);
    }

    private static int getAreaDestroyMetric(ServerPlayer player, Function<RtsWorkflowStatus, Integer> metric) {
        return RtsWorkflowEngine.getInstance().getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.AREA_DESTROY)
                .findFirst()
                .map(metric)
                .orElse(0);
    }

    @Override
    public <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        return TemporaryContextSwitcher.withTemporaryMainHandItem(player, stack, action);
    }
}
