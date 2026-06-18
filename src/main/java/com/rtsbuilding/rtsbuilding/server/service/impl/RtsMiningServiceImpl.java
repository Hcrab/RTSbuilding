package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.MiningService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

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
        // Stop — delegate to STOP_MINING pipeline
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
        return RtsWorkflowEngine.getInstance().getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.AREA_DESTROY)
                .findFirst()
                .map(RtsWorkflowStatus::totalBlocks)
                .orElse(0);
    }

    @Override
    public int getAreaDestroyCompletedBlocks(ServerPlayer player) {
        return RtsWorkflowEngine.getInstance().getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.AREA_DESTROY)
                .findFirst()
                .map(RtsWorkflowStatus::completedBlocks)
                .orElse(0);
    }

    @Override
    public int getAreaDestroyRemainingBlocks(ServerPlayer player) {
        return RtsWorkflowEngine.getInstance().getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.AREA_DESTROY)
                .findFirst()
                .map(RtsWorkflowStatus::remainingBlocks)
                .orElse(0);
    }

    @Override
    public <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        return RtsMiningStateMachine.withTemporaryMainHandItem(player, stack, action);
    }
}
