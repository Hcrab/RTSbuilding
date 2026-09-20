package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** G09C 持久工作流回归：容量、隐藏投影和手动暂停必须走真实任务入口。 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class G09CWorkflowGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private G09CWorkflowGameTests() {
    }

    /** 未保护的最旧 quick-build 必须被替换；全部保护后，同一任务族必须拒绝新任务。 */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180, batch = "g09c_server")
    public static void durablePlacementAdmissionHonorsReplacementAndProtection(GameTestHelper helper) {
        int previousCapacity = Config.maxActiveWorkflowsPerPlayer();
        int previousFamilyLimit = Config.buildBatchMaxQueuedJobs();
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsWorkflowEngine workflows = RtsWorkflowEngine.getInstance();
        try {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(8);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(4);
            BlockPos supportRel = new BlockPos(2, 1, 2);
            helper.setBlock(supportRel, Blocks.STONE);
            player.getInventory().clearContent();
            RtsStorageSession session = requireSession(helper, player);
            int limit = Config.buildBatchMaxQueuedJobs();
            List<TaskSnapshot> initial = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                RtsWorkflowToken token = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
                initial.add(queuePlacement(helper, player, session, token, true, supportRel));
            }
            TaskSnapshot oldest = initial.get(0);
            helper.assertTrue(initial.stream().allMatch(task -> task.createdGameTime() == oldest.createdGameTime()),
                    "夹具必须在同一 tick 提交，验证最旧选择不依赖随机任务 ID");

            RtsWorkflowToken replacement = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
            TaskSnapshot replacementSnapshot = queuePlacement(
                    helper, player, session, replacement, true, supportRel);
            TaskSnapshot replacedSnapshot = TaskPersistenceRuntime.INSTANCE.coordinator().query()
                    .get(oldest.id()).orElseThrow();
            helper.assertTrue(replacedSnapshot.state() == TaskLifecycleState.CANCELLED
                            && replacedSnapshot.reason().name().equals("REPLACED"),
                    "同一 tick 最早提交的未保护任务必须以 REPLACED 终止");
            helper.assertTrue(!replacementSnapshot.state().terminal(),
                    "替换旧任务后必须接纳新建造");

            List<TaskSnapshot> active = livePlacements(player);
            helper.assertTrue(limit == active.size(), "替换后同族任务数量必须保持配置上限");
            for (TaskSnapshot snapshot : active) {
                workflows.setWorkflowProtected(player, snapshot.workflowEntryId(), true);
            }

            RtsWorkflowToken rejectedWorkflow = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
            boolean accepted = enqueuePlacement(
                    player, session, rejectedWorkflow, true, helper.absolutePos(supportRel));
            helper.assertTrue(!accepted, "同族任务全部保护时必须拒绝新建造");
            helper.assertTrue(TaskPersistenceRuntime.INSTANCE.coordinator().query()
                            .findByWorkflow(player.getUUID(), player.serverLevel().dimension().location().toString(),
                                    rejectedWorkflow.entryId()).isEmpty(),
                    "拒绝的新请求不能留下持久化孤儿任务");
        } finally {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(previousCapacity);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(previousFamilyLimit);
            workflows.cancelAll(player);
            RtsServerGameTests.stopPlayers(player);
        }
        helper.succeed();
    }

    /** 清掉 UI 投影后再次提交，旧 durable task 必须由真实入口按 orphan 规则回收。 */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160, batch = "g09c_server")
    public static void hiddenDurablePlacementIsReconciledBeforeNewAdmission(GameTestHelper helper) {
        BlockPos supportRel = new BlockPos(2, 1, 2);
        helper.setBlock(supportRel, Blocks.STONE);
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        player.getInventory().clearContent();
        RtsStorageSession session = requireSession(helper, player);
        RtsWorkflowEngine workflows = RtsWorkflowEngine.getInstance();

        RtsWorkflowToken hiddenToken = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
        TaskSnapshot hidden = queuePlacement(helper, player, session, hiddenToken, true, supportRel);
        workflows.clearPlayerData(player.getUUID());

        RtsWorkflowToken visibleToken = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
        TaskSnapshot visible = queuePlacement(helper, player, session, visibleToken, true, supportRel);
        TaskSnapshot reconciled = TaskPersistenceRuntime.INSTANCE.coordinator().query()
                .get(hidden.id()).orElseThrow();
        helper.assertTrue(reconciled.state() == TaskLifecycleState.CANCELLED
                        && reconciled.reason().name().equals("REPLACED"),
                "a hidden durable placement must be cancelled as an unprotected orphan before new admission");
        helper.assertTrue(!visible.state().terminal(),
                "the visible replacement task must remain admitted after orphan reconciliation");
        helper.assertTrue(workflows.from(player, visibleToken.entryId()).isPresent(),
                "the replacement task must rebuild a visible workflow projection");

        workflows.cancelAll(player);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    /** 补料事件只能唤醒等待资源；手动 PAUSED 的真实快照必须保持暂停，显式恢复后才可继续。 */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 220, batch = "g09c_server")
    public static void manualPauseBlocksResourceWakeUntilExplicitResume(GameTestHelper helper) {
        BlockPos supportRel = new BlockPos(2, 1, 2);
        BlockPos targetRel = supportRel.above();
        helper.setBlock(supportRel, Blocks.STONE);
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        player.getInventory().clearContent();
        RtsStorageSession session = requireSession(helper, player);
        RtsWorkflowToken token = startWorkflow(player, RtsWorkflowType.PLACE_SINGLE);
        TaskSnapshot admitted = queuePlacement(helper, player, session, token, false, supportRel);

        helper.runAfterDelay(20, () -> {
            var coordinator = TaskPersistenceRuntime.INSTANCE.coordinator();
            TaskSnapshot waiting = coordinator.query().findByWorkflow(
                    player.getUUID(), player.serverLevel().dimension().location().toString(), token.entryId())
                    .orElseThrow();
            helper.assertTrue(waiting.state() == TaskLifecycleState.WAITING_RESOURCE,
                    "a real placement with no material must enter WAITING_RESOURCE");

            helper.assertTrue(RtsTaskEngine.INSTANCE.setWorkflowPaused(player, token.entryId(), true),
                    "manual pause must update the authoritative durable task");
            TaskSnapshot paused = coordinator.query().findByWorkflow(
                    player.getUUID(), player.serverLevel().dimension().location().toString(), token.entryId())
                    .orElseThrow();
            helper.assertTrue(paused.state() == TaskLifecycleState.PAUSED,
                    "manual pause must be visible in TaskStore, not only in the UI projection");

            player.getInventory().setItem(0, new ItemStack(Items.DIRT));
            RtsTaskEngine.INSTANCE.resumeWaitingPlacementItems(player, List.of("minecraft:dirt"));
            TaskSnapshot stillPaused = coordinator.query().findByWorkflow(
                    player.getUUID(), player.serverLevel().dimension().location().toString(), token.entryId())
                    .orElseThrow();
            helper.assertTrue(stillPaused.state() == TaskLifecycleState.PAUSED,
                    "matching material must not wake a manually paused task");

            helper.assertTrue(RtsTaskEngine.INSTANCE.setWorkflowPaused(player, token.entryId(), false),
                    "explicit resume must reopen the authoritative durable task");
            TaskSnapshot queued = coordinator.query().findByWorkflow(
                    player.getUUID(), player.serverLevel().dimension().location().toString(), token.entryId())
                    .orElseThrow();
            helper.assertTrue(queued.state() == TaskLifecycleState.QUEUED,
                    "explicit resume must transition the task back to QUEUED");

            helper.runAfterDelay(40, () -> {
                helper.assertBlockPresent(Blocks.DIRT, targetRel);
                // 终态落盘后允许快照转成回执；不能把正常清理误报成任务丢失。
                var terminal = coordinator.query().get(admitted.id());
                var receipt = coordinator.query().receipt(admitted.id());
                helper.assertTrue(terminal.isPresent() != receipt.isPresent(),
                        "同一任务必须只保留一份终态快照或持久化回执");
                TaskLifecycleState finalState = terminal.map(TaskSnapshot::state)
                        .orElseGet(() -> receipt.orElseThrow().terminalState());
                helper.assertTrue(finalState == TaskLifecycleState.COMPLETED,
                        "显式恢复后必须真正完成，不能以取消或失败冒充终态成功");
                helper.assertTrue(player.getInventory().countItem(Items.DIRT) == 0,
                        "恢复放置必须恰好消耗提供的一个方块");
                RtsServerGameTests.stopPlayers(player);
                helper.succeed();
            });
        });
    }

    /**
     * 动态下调容量仍保留已接纳任务；一换一不增量、不误取消其他任务，重建投影后仍可控制。
     * 配置仅在当前同步调用内改变，finally 恢复，避免影响同服其他 GameTest。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180, batch = "workflow_capacity")
    public static void loweringCapacityPreservesAcceptedTasksAndAllowsOneReplacement(GameTestHelper helper) {
        checkLoweredCapacityReplacement(helper, false);
    }

    /** UI与同族上限同时下调时，仍只能合计替换一个活任务，而不是每层各替换一个。 */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180, batch = "workflow_capacity")
    public static void loweringBothCapacitiesDoesNotCancelTwoTasks(GameTestHelper helper) {
        checkLoweredCapacityReplacement(helper, true);
    }

    private static void checkLoweredCapacityReplacement(GameTestHelper helper, boolean lowerFamilyToo) {
        int previousCapacity = Config.maxActiveWorkflowsPerPlayer();
        int previousFamilyLimit = Config.buildBatchMaxQueuedJobs();
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsWorkflowEngine workflows = RtsWorkflowEngine.getInstance();
        var coordinator = TaskPersistenceRuntime.INSTANCE.coordinator();
        try {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(4);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(lowerFamilyToo ? 4 : 8);
            player.getInventory().clearContent();
            BlockPos supportRel = new BlockPos(2, 1, 2);
            helper.setBlock(supportRel, Blocks.STONE);
            RtsStorageSession session = requireSession(helper, player);
            List<TaskSnapshot> initial = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                RtsWorkflowToken token = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
                initial.add(queuePlacement(helper, player, session, token, true, supportRel));
                helper.assertTrue(RtsTaskEngine.INSTANCE.setWorkflowPaused(player, token.entryId(), true),
                        "已接纳任务必须能手动暂停");
            }

            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(2);
            if (lowerFamilyToo) Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(2);
            helper.assertTrue(livePlacements(player).size() == 4, "下调上限不能截断已接纳任务");
            RtsWorkflowToken replacement = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
            TaskSnapshot added = queuePlacement(helper, player, session, replacement, true, supportRel);
            helper.assertTrue(RtsTaskEngine.INSTANCE.setWorkflowPaused(player, replacement.entryId(), true),
                    "替换任务必须可控制");
            TaskSnapshot removed = coordinator.query().get(initial.get(0).id()).orElseThrow();
            helper.assertTrue(removed.state() == TaskLifecycleState.CANCELLED
                            && removed.reason().name().equals("REPLACED"),
                    "一次提交只替换最旧未保护任务");
            for (int i = 1; i < initial.size(); i++) {
                helper.assertTrue(!coordinator.query().get(initial.get(i).id()).orElseThrow().state().terminal(),
                        "一换一不能顺带取消其余旧任务");
            }
            helper.assertTrue(livePlacements(player).size() == 4 && !added.state().terminal(),
                    "容量调小后一换一必须接纳，且不能扩大既有总数");

            // 清除面板模拟投影缺失，再走真实 Task Engine tick 从 TaskStore 恢复。
            workflows.clearPlayerData(player.getUUID());
            RtsTaskEngine.INSTANCE.tick(helper.getLevel().getServer());
            List<TaskSnapshot> restored = livePlacements(player);
            helper.assertTrue(restored.size() == 4, "恢复不能按新容量终止旧任务");
            for (TaskSnapshot snapshot : restored) {
                helper.assertTrue(workflows.from(player, snapshot.workflowEntryId()).isPresent(),
                        "超出新容量的旧任务仍须有可操作的面板条目");
                helper.assertTrue(snapshot.state() == TaskLifecycleState.PAUSED, "恢复不能解除手动暂停");
                workflows.setWorkflowProtected(player, snapshot.workflowEntryId(), true);
            }
            helper.assertTrue(workflows.start(player, RtsWorkflowType.QUICK_BUILD,
                            RtsWorkflowPriority.NORMAL, 1).isEmpty(),
                    "全部保护后必须拒绝新任务");
            helper.assertTrue(livePlacements(player).size() == 4, "拒绝时不能误取消受保护任务");
        } finally {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(previousCapacity);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(previousFamilyLimit);
            workflows.cancelAll(player);
            RtsServerGameTests.stopPlayers(player);
        }
        helper.succeed();
    }

    /**
     * 同族队列下调后，每次新请求仅替换一条，不能为追赶新上限批量丢弃已接受任务。
     * 这里让 UI 总容量保持充足，独立覆盖 durable family 的生产准入链。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180, batch = "workflow_capacity")
    public static void loweringFamilyCapacityReplacesOnlyOneAcceptedTask(GameTestHelper helper) {
        int previousCapacity = Config.maxActiveWorkflowsPerPlayer();
        int previousFamilyLimit = Config.buildBatchMaxQueuedJobs();
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsWorkflowEngine workflows = RtsWorkflowEngine.getInstance();
        var coordinator = TaskPersistenceRuntime.INSTANCE.coordinator();
        try {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(8);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(4);
            player.getInventory().clearContent();
            BlockPos supportRel = new BlockPos(2, 1, 2);
            helper.setBlock(supportRel, Blocks.STONE);
            RtsStorageSession session = requireSession(helper, player);
            List<TaskSnapshot> initial = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                RtsWorkflowToken token = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
                initial.add(queuePlacement(helper, player, session, token, true, supportRel));
                helper.assertTrue(RtsTaskEngine.INSTANCE.setWorkflowPaused(player, token.entryId(), true),
                        "已接纳任务必须能手动暂停");
            }

            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(2);
            helper.assertTrue(livePlacements(player).size() == 4, "下调同族容量不能立即截断旧任务");
            for (int round = 0; round < 2; round++) {
                RtsWorkflowToken replacement = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
                queuePlacement(helper, player, session, replacement, true, supportRel);
                helper.assertTrue(RtsTaskEngine.INSTANCE.setWorkflowPaused(player, replacement.entryId(), true),
                        "新接纳任务仍能控制");
                TaskSnapshot removed = coordinator.query().get(initial.get(round).id()).orElseThrow();
                helper.assertTrue(removed.state() == TaskLifecycleState.CANCELLED
                                && removed.reason().name().equals("REPLACED"),
                        "连续新请求每次只替换当时最旧未保护任务");
                List<TaskSnapshot> live = livePlacements(player);
                helper.assertTrue(live.size() == 4, "下调后每次一换一保持既有总数，不能批量取消");
                helper.assertTrue(live.stream().allMatch(task -> task.state() == TaskLifecycleState.PAUSED),
                        "未被替换任务必须保留手动暂停");
                for (int i = round + 1; i < initial.size(); i++) {
                    helper.assertTrue(coordinator.query().get(initial.get(i).id()).orElseThrow().state()
                                    == TaskLifecycleState.PAUSED,
                            "不能顺带取消或唤醒其他旧任务");
                }
            }

            for (TaskSnapshot snapshot : livePlacements(player)) {
                workflows.setWorkflowProtected(player, snapshot.workflowEntryId(), true);
            }
            RtsWorkflowToken rejected = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
            helper.assertTrue(!enqueuePlacement(
                            player, session, rejected, true, helper.absolutePos(supportRel)),
                    "超额旧任务全部保护时仍必须拒绝新请求");
            helper.assertTrue(livePlacements(player).size() == 4, "拒绝不能误删被保护的超额任务");
            helper.assertTrue(coordinator.query().findByWorkflow(player.getUUID(),
                            player.serverLevel().dimension().location().toString(), rejected.entryId()).isEmpty(),
                    "被拒绝请求不能留下任务记录");
        } finally {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(previousCapacity);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(previousFamilyLimit);
            workflows.cancelAll(player);
            RtsServerGameTests.stopPlayers(player);
        }
        helper.succeed();
    }

    /** 终态行只占UI展示位置，清走它不能放宽仍满额的真实任务族容量。 */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180, batch = "workflow_capacity")
    public static void terminalWorkflowRowsDoNotGrantExtraFamilyCapacity(GameTestHelper helper) {
        int previousCapacity = Config.maxActiveWorkflowsPerPlayer();
        int previousFamilyLimit = Config.buildBatchMaxQueuedJobs();
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsWorkflowEngine workflows = RtsWorkflowEngine.getInstance();
        try {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(6);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(4);
            player.getInventory().clearContent();
            BlockPos supportRel = new BlockPos(2, 1, 2);
            helper.setBlock(supportRel, Blocks.STONE);
            RtsStorageSession session = requireSession(helper, player);
            for (int i = 0; i < 9; i++) {
                RtsWorkflowToken token = startWorkflow(player, RtsWorkflowType.QUICK_BUILD);
                queuePlacement(helper, player, session, token, true, supportRel);
                helper.assertTrue(livePlacements(player).size() == Math.min(i + 1, 4),
                        "旧终态行被清理后，活任务数量仍不能超过同族上限");
                helper.assertTrue(workflows.consumeAdmissionReplacement(player, token.entryId()) == -1,
                        "本次家族准入后必须已消费临时前驱，不能重复抵扣");
            }
        } finally {
            Config.WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(previousCapacity);
            Config.BUILD_BATCH_MAX_QUEUED_JOBS.set(previousFamilyLimit);
            workflows.cancelAll(player);
            RtsServerGameTests.stopPlayers(player);
        }
        helper.succeed();
    }

    private static List<TaskSnapshot> livePlacements(ServerPlayer player) {
        return TaskPersistenceRuntime.INSTANCE.coordinator().query().ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.PLACEMENT && !snapshot.state().terminal())
                .toList();
    }


    private static RtsWorkflowToken startWorkflow(ServerPlayer player, RtsWorkflowType type) {
        return RtsWorkflowEngine.getInstance()
                .start(player, type, RtsWorkflowPriority.NORMAL, 1)
                .orElseThrow(() -> new AssertionError("workflow admission unexpectedly rejected: " + type));
    }

    private static TaskSnapshot queuePlacement(GameTestHelper helper, ServerPlayer player,
            RtsStorageSession session, RtsWorkflowToken token, boolean quickBuild, BlockPos supportRel) {
        boolean accepted = enqueuePlacement(
                player, session, token, quickBuild, helper.absolutePos(supportRel));
        helper.assertTrue(accepted, "real placement admission should accept the fixture task");
        return TaskPersistenceRuntime.INSTANCE.coordinator().query().findByWorkflow(
                player.getUUID(), player.serverLevel().dimension().location().toString(), token.entryId())
                .orElseThrow(() -> new AssertionError("accepted placement must have a durable TaskStore snapshot"));
    }

    private static boolean enqueuePlacement(ServerPlayer player, RtsStorageSession session,
            RtsWorkflowToken token, boolean quickBuild, BlockPos support) {
        BlockPos target = support.above();
        Vec3 origin = player.getEyePosition();
        Vec3 hit = Vec3.atBottomCenterOf(target);
        Vec3 direction = hit.subtract(origin).normalize();
        return RtsPlacementBatch.enqueuePlaceBatch(
                player, session, List.of(support), Direction.UP,
                0.5D, 1.0D, 0.5D, (byte) 0, "", false, false,
                "minecraft:dirt", new ItemStack(Items.DIRT),
                origin.x, origin.y, origin.z,
                direction.x, direction.y, direction.z,
                quickBuild, false, false, token.entryId());
    }

    private static RtsStorageSession requireSession(GameTestHelper helper, ServerPlayer player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        helper.assertTrue(session != null, "real RTS player must have a server storage session");
        return session;
    }
}
