package com.rtsbuilding.rtsbuilding.server.pipeline.blueprint;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.VanillaStructureNbtReader;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedList;
import java.util.List;

/**
 * 蓝图持久化工具——将蓝图工作流的运行时数据序列化到工作流条目，
 * 并在服务端重启后从条目恢复蓝图管道。
 *
 * <p>对齐范围放置的 {@code PlaceBatchJob.toNbt()/fromNbt()} 模式，
 * 但将蓝图特定的数据存储在 {@link com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry#getExtraData()} 中。</p>
 */
public final class BlueprintPersistence {

    // ──────────────────────────────────────────────────────────────────
    //  NBT 键名
    // ──────────────────────────────────────────────────────────────────

    private static final String KEY_BLUEPRINT_STRUCTURE = "blueprint";
    private static final String KEY_BP_NAME = "bp_name";
    private static final String KEY_BP_SOURCE = "bp_source";
    private static final String KEY_BP_FORMAT = "bp_format";

    private static final String KEY_ANCHOR_X = "anchorX";
    private static final String KEY_ANCHOR_Y = "anchorY";
    private static final String KEY_ANCHOR_Z = "anchorZ";
    private static final String KEY_CENTER_OFFSET_X = "coX";
    private static final String KEY_CENTER_OFFSET_Y = "coY";
    private static final String KEY_CENTER_OFFSET_Z = "coZ";
    private static final String KEY_Y_STEPS = "ySteps";
    private static final String KEY_X_STEPS = "xSteps";
    private static final String KEY_Z_STEPS = "zSteps";

    private static final String KEY_REMAINING = "remaining";
    private static final String KEY_PLACED_COUNT = "placedCount";
    private static final String KEY_SKIPPED_MISSING = "skippedMissing";
    private static final String KEY_SKIPPED_UNSUPPORTED = "skippedUnsupported";
    private static final String KEY_SKIPPED_MISSING_BLOCKS = "skippedMissingBlocks";
    private static final String KEY_SKIPPED_BLOCKED = "skippedBlocked";

    private BlueprintPersistence() {
    }

    // ──────────────────────────────────────────────────────────────────
    //  保存
    // ──────────────────────────────────────────────────────────────────

    /**
     * 将蓝图上下文的当前状态序列化到工作流条目 {@code extraData}。
     *
     * <p>包含：蓝图源数据、放置参数、剩余队列、进度计数器。</p>
     */
    public static void saveToEntry(ServerPlayer player, int entryId, BlueprintContext bctx) {
        CompoundTag data = new CompoundTag();

        // 蓝图源数据
        RtsBlueprint blueprint = bctx.getBlueprint();
        if (blueprint != null) {
            data.put(KEY_BLUEPRINT_STRUCTURE, BlueprintWriters.toVanillaStructureTag(blueprint));
            data.putString(KEY_BP_NAME, blueprint.name() != null ? blueprint.name() : "");
            data.putString(KEY_BP_SOURCE, blueprint.sourceName() != null ? blueprint.sourceName() : "");
            data.putString(KEY_BP_FORMAT, blueprint.format() != null ? blueprint.format().name() : "VANILLA_NBT");
        }

        // 放置参数
        BlockPos anchor = bctx.getAnchor();
        if (anchor != null) {
            data.putInt(KEY_ANCHOR_X, anchor.getX());
            data.putInt(KEY_ANCHOR_Y, anchor.getY());
            data.putInt(KEY_ANCHOR_Z, anchor.getZ());
        }
        BlockPos centerOffset = bctx.getData(BlueprintContext.KEY_CENTER_OFFSET);
        if (centerOffset != null) {
            data.putInt(KEY_CENTER_OFFSET_X, centerOffset.getX());
            data.putInt(KEY_CENTER_OFFSET_Y, centerOffset.getY());
            data.putInt(KEY_CENTER_OFFSET_Z, centerOffset.getZ());
        }
        data.putInt(KEY_Y_STEPS, bctx.getYRotationSteps());
        data.putInt(KEY_X_STEPS, bctx.getXRotationSteps());
        data.putInt(KEY_Z_STEPS, bctx.getZRotationSteps());

        // 剩余队列
        LinkedList<Integer> remaining = bctx.getRemainingQueue();
        if (remaining != null && !remaining.isEmpty()) {
            int[] arr = new int[remaining.size()];
            int i = 0;
            for (int idx : remaining) {
                arr[i++] = idx;
            }
            data.putIntArray(KEY_REMAINING, arr);
        } else {
            data.putIntArray(KEY_REMAINING, new int[0]);
        }

        // 进度计数
        data.putInt(KEY_PLACED_COUNT, bctx.getPlacedCount());
        data.putInt(KEY_SKIPPED_MISSING, bctx.getSkippedMissing());
        data.putInt(KEY_SKIPPED_UNSUPPORTED, bctx.getSkippedUnsupported());
        data.putInt(KEY_SKIPPED_MISSING_BLOCKS, bctx.getSkippedMissingBlocks());
        data.putInt(KEY_SKIPPED_BLOCKED, bctx.getSkippedBlocked());

        // 持久化到工作流条目
        com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .setWorkflowExtraData(player, entryId, data);
    }

    /**
     * 清除工作流条目中的额外蓝图数据（完成/取消时调用）。
     */
    public static void clearFromEntry(ServerPlayer player, int entryId) {
        com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .setWorkflowExtraData(player, entryId, null);
    }

    // ──────────────────────────────────────────────────────────────────
    //  恢复（服务端重载路径）
    // ──────────────────────────────────────────────────────────────────

    /**
     * 从工作流条目的 extraData 重建蓝图上下文，恢复 Tick 管道。
     *
     * <p>此方法由 {@link com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.BlueprintRestoreHandler}
     * 在服务端重启、玩家加入世界时调用。</p>
     */
    public static void restoreFromEntry(ServerPlayer player,
                                         com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry entry) {
        CompoundTag data = entry.getExtraData();
        if (data == null || data.isEmpty()) return;

        ServerLevel level = player.serverLevel();

        // ── 重建蓝图 ─────────────────────────────────────────────
        CompoundTag structureTag = data.contains(KEY_BLUEPRINT_STRUCTURE, Tag.TAG_COMPOUND)
                ? data.getCompound(KEY_BLUEPRINT_STRUCTURE) : null;
        if (structureTag == null || structureTag.isEmpty()) return;

        String bpName = data.getString(KEY_BP_NAME);
        String bpSource = data.getString(KEY_BP_SOURCE);
        RtsBlueprint blueprint = VanillaStructureNbtReader.parse(structureTag, bpName, bpSource, level.registryAccess());
        if (blueprint.blocks().isEmpty()) return;

        // ── 读取放置参数 ─────────────────────────────────────────
        BlockPos anchor = new BlockPos(data.getInt(KEY_ANCHOR_X), data.getInt(KEY_ANCHOR_Y), data.getInt(KEY_ANCHOR_Z));
        BlockPos centerOffset = new BlockPos(
                data.getInt(KEY_CENTER_OFFSET_X), data.getInt(KEY_CENTER_OFFSET_Y), data.getInt(KEY_CENTER_OFFSET_Z));
        int ySteps = data.getInt(KEY_Y_STEPS);
        int xSteps = data.getInt(KEY_X_STEPS);
        int zSteps = data.getInt(KEY_Z_STEPS);

        // ── 重算放置计划 ─────────────────────────────────────────
        List<PlacementPlan> plans = BlockPlacementPlanner.compute(blueprint, anchor, centerOffset, ySteps, xSteps, zSteps);

        // ── 重建剩余队列 ─────────────────────────────────────────
        LinkedList<Integer> remaining = new LinkedList<>();
        if (data.contains(KEY_REMAINING, Tag.TAG_INT_ARRAY)) {
            int[] arr = data.getIntArray(KEY_REMAINING);
            for (int idx : arr) {
                remaining.add(idx);
            }
        } else {
            // 空队列——工作流已完成
            return;
        }

        // ── 读取进度计数 ─────────────────────────────────────────
        int placedCount = data.getInt(KEY_PLACED_COUNT);
        int skippedMissing = data.getInt(KEY_SKIPPED_MISSING);
        int skippedUnsupported = data.getInt(KEY_SKIPPED_UNSUPPORTED);
        int skippedMissingBlocks = data.getInt(KEY_SKIPPED_MISSING_BLOCKS);
        int skippedBlocked = data.getInt(KEY_SKIPPED_BLOCKED);

        // ── 构建管线上下文 ───────────────────────────────────────
        BlueprintContext ctx = BlueprintContext.builder(player)
                .blueprint(blueprint)
                .anchor(anchor)
                .yRotationSteps(ySteps)
                .xRotationSteps(xSteps)
                .zRotationSteps(zSteps)
                .totalBlocks(blueprint.blockCount())
                .build();

        // 设置共享数据
        ctx.setData(BlueprintContext.KEY_CENTER_OFFSET, centerOffset);
        ctx.setPlacementPlans(plans);
        ctx.setRemainingQueue(remaining);
        ctx.setPlacedCount(placedCount);
        ctx.setSkippedMissing(skippedMissing);
        ctx.setSkippedUnsupported(skippedUnsupported);
        ctx.setSkippedMissingBlocks(skippedMissingBlocks);
        ctx.setSkippedBlocked(skippedBlocked);

        // 恢复 session（懒加载，若不存在则先创建）
        if (!ctx.hasData(SessionValidatePipe.KEY_SESSION)) {
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session =
                    ServiceRegistry.getInstance().session().getOrCreate(player);
            ctx.setData(SessionValidatePipe.KEY_SESSION, session);
        }

        // 设置工作流条目 ID
        ctx.setData(PipelineContext.KEY_WORKFLOW_ENTRY_ID, entry.id());

        // ── 注册 Tick 管道 ───────────────────────────────────────
        ctx.retainOnly(
                PipelineContext.KEY_WORKFLOW_ENTRY_ID,
                SessionValidatePipe.KEY_SESSION,
                ToolBorrowPipe.KEY_TOOL_LEASE,
                BlueprintContext.KEY_PLACEMENT_PLANS,
                BlueprintContext.KEY_REMAINING_QUEUE,
                BlueprintContext.KEY_CENTER_OFFSET,
                BlueprintContext.KEY_PLACED_COUNT,
                BlueprintContext.KEY_SKIPPED_MISSING,
                BlueprintContext.KEY_SKIPPED_UNSUPPORTED,
                BlueprintContext.KEY_SKIPPED_MISSING_BLOCKS,
                BlueprintContext.KEY_SKIPPED_BLOCKED
        );
        TickablePipelineRegistry.register(player, ctx, new BlueprintTickPipe());

        RtsbuildingMod.LOGGER.info("[BlueprintPersistence] 已恢复蓝图工作流 #{} ({} 剩余方块)",
                entry.id(), remaining.size());
    }

    /**
     * 创建蓝图重载处理器，供 {@link com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine}
     * 在加载玩家工作流时注册。
     */
    public static RtsWorkflowEngine.BlueprintRestoreHandler createRestoreHandler() {
        return BlueprintPersistence::restoreFromEntry;
    }

    /**
     * 日志引用。
     */
    private static final org.slf4j.Logger LOGGER = RtsbuildingMod.LOGGER;
}
