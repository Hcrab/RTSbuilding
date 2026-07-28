package com.rtsbuilding.rtsbuilding.server.pipeline.blueprint;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.VanillaStructureNbtReader;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.DimensionIdCodec;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.Constants;

import java.util.LinkedList;

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
    private static final String KEY_PREPARING = "preparing";
    private static final String KEY_SOURCE_DIMENSION = "source_dimension";

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
    public static void saveToEntry(EntityPlayerMP player, int entryId, BlueprintContext bctx) {
        NBTTagCompound data = new NBTTagCompound();

        // 蓝图源数据
        RtsBlueprint blueprint = bctx.getBlueprint();
        if (blueprint != null) {
            data.setTag(KEY_BLUEPRINT_STRUCTURE, BlueprintWriters.toVanillaStructureTag(blueprint));
            data.setString(KEY_BP_NAME, blueprint.name() != null ? blueprint.name() : "");
            data.setString(KEY_BP_SOURCE, blueprint.sourceName() != null ? blueprint.sourceName() : "");
            data.setString(KEY_BP_FORMAT, blueprint.format() != null ? blueprint.format().name() : "VANILLA_NBT");
        }

        // 放置参数
        BlockPos anchor = bctx.getAnchor();
        if (anchor != null) {
            data.setInteger(KEY_ANCHOR_X, anchor.getX());
            data.setInteger(KEY_ANCHOR_Y, anchor.getY());
            data.setInteger(KEY_ANCHOR_Z, anchor.getZ());
        }
        BlockPos centerOffset = bctx.getData(BlueprintContext.KEY_CENTER_OFFSET);
        if (centerOffset != null) {
            data.setInteger(KEY_CENTER_OFFSET_X, centerOffset.getX());
            data.setInteger(KEY_CENTER_OFFSET_Y, centerOffset.getY());
            data.setInteger(KEY_CENTER_OFFSET_Z, centerOffset.getZ());
        }
        data.setInteger(KEY_Y_STEPS, bctx.getYRotationSteps());
        data.setInteger(KEY_X_STEPS, bctx.getXRotationSteps());
        data.setInteger(KEY_Z_STEPS, bctx.getZRotationSteps());

        // 剩余队列
        LinkedList<Integer> remaining = bctx.getRemainingQueue();
        if (remaining != null && !remaining.isEmpty()) {
            int[] arr = new int[remaining.size()];
            int i = 0;
            for (int idx : remaining) {
                arr[i++] = idx;
            }
            data.setIntArray(KEY_REMAINING, arr);
        } else {
            data.setIntArray(KEY_REMAINING, new int[0]);
        }

        // 进度计数
        data.setInteger(KEY_PLACED_COUNT, bctx.getPlacedCount());
        data.setInteger(KEY_SKIPPED_MISSING, bctx.getSkippedMissing());
        data.setInteger(KEY_SKIPPED_UNSUPPORTED, bctx.getSkippedUnsupported());
        data.setInteger(KEY_SKIPPED_MISSING_BLOCKS, bctx.getSkippedMissingBlocks());
        data.setInteger(KEY_SKIPPED_BLOCKED, bctx.getSkippedBlocked());
        data.setBoolean(KEY_PREPARING, bctx.isPreparing());
        Integer sourceDimension = bctx.getData(BlueprintContext.KEY_SOURCE_DIMENSION);
        if (sourceDimension == null) sourceDimension = player.dimension;
        data.setString(KEY_SOURCE_DIMENSION, DimensionIdCodec.fromDimension(sourceDimension));

        // 持久化到工作流条目
        com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .setWorkflowExtraData(player, entryId, data);
    }

    /**
     * 清除工作流条目中的额外蓝图数据（完成/取消时调用）。
     */
    public static void clearFromEntry(EntityPlayerMP player, int entryId) {
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
    public static void restoreFromEntry(EntityPlayerMP player,
                                         com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry entry) {
        NBTTagCompound data = entry.getExtraData();
        if (data == null || data.isEmpty()) return;

        WorldServer level = player.getServerWorld();

        // ── 重建蓝图 ─────────────────────────────────────────────
        NBTTagCompound structureTag = data.hasKey(KEY_BLUEPRINT_STRUCTURE, Constants.NBT.TAG_COMPOUND)
                ? data.getCompoundTag(KEY_BLUEPRINT_STRUCTURE) : null;
        if (structureTag == null || structureTag.isEmpty()) return;

        String bpName = data.getString(KEY_BP_NAME);
        String bpSource = data.getString(KEY_BP_SOURCE);
        RtsBlueprint blueprint = VanillaStructureNbtReader.parse(structureTag, bpName, bpSource);
        if (blueprint.blocks().isEmpty()) return;

        // ── 读取放置参数 ─────────────────────────────────────────
        BlockPos anchor = new BlockPos(data.getInteger(KEY_ANCHOR_X), data.getInteger(KEY_ANCHOR_Y), data.getInteger(KEY_ANCHOR_Z));
        BlockPos centerOffset = new BlockPos(
                data.getInteger(KEY_CENTER_OFFSET_X), data.getInteger(KEY_CENTER_OFFSET_Y), data.getInteger(KEY_CENTER_OFFSET_Z));
        int ySteps = data.getInteger(KEY_Y_STEPS);
        int xSteps = data.getInteger(KEY_X_STEPS);
        int zSteps = data.getInteger(KEY_Z_STEPS);
        int sourceDimension = player.dimension;
        String sourceDimensionId = data.getString(KEY_SOURCE_DIMENSION);
        if (!sourceDimensionId.trim().isEmpty()) {
            try {
                sourceDimension = DimensionIdCodec.toDimension(sourceDimensionId);
            } catch (IllegalArgumentException invalidDimension) {
                RtsbuildingMod.LOGGER.warn(
                        "[BlueprintPersistence] 蓝图工作流 #{} 的来源维度无效：{}，保守绑定当前维度",
                        entry.id(), sourceDimensionId);
            }
        } else {
            RtsbuildingMod.LOGGER.info(
                    "[BlueprintPersistence] 蓝图工作流 #{} 缺少来源维度，按旧数据迁移到当前维度 {}",
                    entry.id(), sourceDimension);
        }

        // ── 重算放置计划 ─────────────────────────────────────────
        // ── 重建剩余队列 ─────────────────────────────────────────
        LinkedList<Integer> remaining = new LinkedList<>();
        if (data.hasKey(KEY_REMAINING, Constants.NBT.TAG_INT_ARRAY)) {
            int[] arr = data.getIntArray(KEY_REMAINING);
            for (int idx : arr) {
                remaining.add(idx);
            }
        } else {
            // 空队列——工作流已完成
            return;
        }

        // ── 读取进度计数 ─────────────────────────────────────────
        int placedCount = data.getInteger(KEY_PLACED_COUNT);
        int skippedMissing = data.getInteger(KEY_SKIPPED_MISSING);
        int skippedUnsupported = data.getInteger(KEY_SKIPPED_UNSUPPORTED);
        int skippedMissingBlocks = data.getInteger(KEY_SKIPPED_MISSING_BLOCKS);
        int skippedBlocked = data.getInteger(KEY_SKIPPED_BLOCKED);

        // ── 构建管线上下文 ───────────────────────────────────────
        SubmissionId legacySubmission = SubmissionId.fromLegacy(
                player.getUniqueID(), "blueprint",
                sourceDimension + ":" + entry.id());
        BlueprintContext ctx = BlueprintContext.builder(player)
                .submissionId(legacySubmission.value())
                .blueprint(blueprint)
                .anchor(anchor)
                .yRotationSteps(ySteps)
                .xRotationSteps(xSteps)
                .zRotationSteps(zSteps)
                .totalBlocks(blueprint.blockCount())
                .build();

        // 设置共享数据
        ctx.setData(BlueprintContext.KEY_CENTER_OFFSET, centerOffset);
        boolean preparing = data.getBoolean(KEY_PREPARING);
        ctx.setPreparing(preparing);
        ctx.setData(BlueprintContext.KEY_SOURCE_DIMENSION, sourceDimension);
        ctx.setPlacedCount(placedCount);
        ctx.setSkippedMissing(skippedMissing);
        ctx.setSkippedUnsupported(skippedUnsupported);
        ctx.setSkippedMissingBlocks(skippedMissingBlocks);
        ctx.setSkippedBlocked(skippedBlocked);
        if (!preparing) ctx.setRemainingQueue(remaining);

        // 恢复 session（懒加载，若不存在则先创建）
        if (!ctx.hasData(SessionValidatePipe.KEY_SESSION)) {
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session =
                    ServiceRegistry.getInstance().session().getOrCreate(player);
            ctx.setData(SessionValidatePipe.KEY_SESSION, session);
        }

        // 设置工作流条目 ID
        ctx.setData(PipelineContext.KEY_WORKFLOW_ENTRY_ID, entry.id());

        // ── 进入 durable 迁移准入；root ACK 前保留 heavy extraData，不注册旧执行器 ──
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
                BlueprintContext.KEY_SKIPPED_BLOCKED,
                BlueprintContext.KEY_PREPARING,
                BlueprintContext.KEY_SOURCE_DIMENSION
        );
        com.rtsbuilding.rtsbuilding.server.task.DurableBlueprintTaskBridge.QueueResult outcome =
                com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .queueLegacyDurableBlueprint(ctx);
        if (outcome == com.rtsbuilding.rtsbuilding.server.task.DurableBlueprintTaskBridge.QueueResult.ALREADY_FINISHED) {
            RtsWorkflowEngine.getInstance().from(player, entry.id()).ifPresent(token -> token.cancel());
            RtsbuildingMod.LOGGER.info("[BlueprintPersistence] 旧蓝图工作流 #{} 已有终态 receipt，已移除陈旧投影",
                    entry.id());
            return;
        }
        if (outcome == com.rtsbuilding.rtsbuilding.server.task.DurableBlueprintTaskBridge.QueueResult.QUEUE_FULL
                || outcome == com.rtsbuilding.rtsbuilding.server.task.DurableBlueprintTaskBridge.QueueResult.MEMORY_BUDGET_FULL) {
            RtsbuildingMod.LOGGER.warn("[BlueprintPersistence] durable 准入繁忙，旧蓝图工作流 #{} 保持 heavy 待下次恢复",
                    entry.id());
            return;
        }
        RtsbuildingMod.LOGGER.info("[BlueprintPersistence] 旧蓝图工作流 #{} 已进入 durable 准入 ({} 剩余方块)",
                entry.id(), remaining.size());
    }

    /**
     * 创建蓝图重载处理器，供 {@link com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine}
     * 在加载玩家工作流时注册。
     */
    public static RtsWorkflowEngine.BlueprintRestoreHandler createRestoreHandler() {
        return BlueprintPersistence::restoreFromEntry;
    }
}
