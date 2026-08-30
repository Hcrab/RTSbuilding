package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 已追踪瞬时回收的真实服务端回归探针。
 *
 * <p>所有场景都从生产 {@code MiningService -> MINE_SINGLE} 管线进入，锁定同 tick
 * 完成、Borrow2 隔离、原版精准采集掉落、直接入库、关闭入库时原样落地、组件身份和
 * 多玩家 ThreadLocal 隔离。测试不复制生产回收算法。</p>
 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsPlacedRecoveryGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private RtsPlacedRecoveryGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "placed_recovery")
    public static void emptyHandRecoveryCompletesBeforeSameTickStopWithoutBorrow2(
            GameTestHelper helper) {
        BlockPos targetRel = new BlockPos(4, 1, 4);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(targetRel, Blocks.STONE);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, false);
        player.getInventory().clearContent();
        List<ItemStack> inventoryBefore = snapshotInventory(player);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        track(tracker, player, target);

        startSingleMine(player, target, true);
        helper.assertBlockPresent(Blocks.AIR, targetRel);
        helper.assertTrue(!tracker.isPlaced(target), "成功回收必须同步清除 tracker");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "内部精准采集工具不得留在玩家主手");
        helper.assertTrue(session.mining.miningToolLease == null
                        || session.mining.miningToolLease.isEmpty(),
                "回收阶段位于 Borrow2 前，不得创建工具租约");
        helper.assertValueEqual(-1, session.mining.workflowEntryId,
                "同步回收不得创建普通挖掘工作流");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "同步回收不得进入普通三秒掉落缓冲");

        stopSingleMine(player, target);
        helper.assertBlockPresent(Blocks.AIR, targetRel);

        helper.succeedWhen(() -> {
            helper.assertTrue(inventoryMatches(player, inventoryBefore),
                    "关闭自动入库时玩家背包与真实主手必须保持原样");
            helper.assertValueEqual(1, countWorldItem(helper, List.of(targetRel), Items.STONE),
                    "空手回收石头必须按内部精准采集工具产生原版石头掉落");
            helper.assertTrue(session.placement.recoveryJobs.isEmpty(),
                    "新回收链不得创建 legacy recovery job");
            RtsServerGameTests.stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void autoStoreWritesLinkedThenInventoryFallbackWithoutDropBuffer(
            GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos linkedTargetRel = new BlockPos(4, 1, 4);
        BlockPos fallbackTargetRel = new BlockPos(6, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(linkedTargetRel, Blocks.STONE);
        helper.setBlock(fallbackTargetRel, Blocks.DIRT);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, true);
        player.getInventory().clearContent();
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());

        BlockPos linkedTarget = helper.absolutePos(linkedTargetRel);
        track(tracker, player, linkedTarget);
        startSingleMine(player, linkedTarget, true);
        helper.assertValueEqual(1, countChestItem(helper, chestRel, Items.STONE),
                "自动入库必须在破坏返回前把精准采集石头写入真实链接箱");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "链接箱写入不得经过普通 mining drop buffer");
        helper.assertValueEqual(0, countWorldItem(helper, List.of(linkedTargetRel), Items.STONE),
                "链接箱已接收数量不得再生成世界实体");

        session.linkedStorageInfo.clear();
        // 只留下玩家当前主手槽为空，证明内部工具不会遮住这一个合法 fallback 容量。
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
        BlockPos fallbackTarget = helper.absolutePos(fallbackTargetRel);
        track(tracker, player, fallbackTarget);
        startSingleMine(player, fallbackTarget, true);
        helper.assertValueEqual(1, countPlayerItem(player, Items.DIRT),
                "无链接储存时必须在同一掉落事件回退玩家背包");
        helper.assertTrue(player.getMainHandItem().is(Items.DIRT),
                "内部工具结束后必须把唯一空主手槽中的 fallback 结果完整提交");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "背包 fallback 也不得进入三秒缓冲");
        helper.assertValueEqual(0, countWorldItem(helper, List.of(fallbackTargetRel), Items.DIRT),
                "背包已接收数量不得重复落地");
        helper.assertTrue(session.placement.recoveryJobs.isEmpty(),
                "两次直接入库都不得创建 legacy recovery job");

        stopSingleMine(player, fallbackTarget);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "placed_recovery")
    public static void disabledAutoStoreLeavesVanillaEntityAndAllDestinationsUntouched(
            GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos targetRel = new BlockPos(4, 1, 4);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(targetRel, Blocks.STONE);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, false);
        player.getInventory().clearContent();
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        track(PlacedBlockTrackerData.get(player.serverLevel()), player, target);

        startSingleMine(player, target, true);
        helper.assertBlockPresent(Blocks.AIR, targetRel);
        helper.assertValueEqual(0, countChestItem(helper, chestRel, Items.STONE),
                "关闭自动入库时链接箱不得接收掉落");
        helper.assertValueEqual(0, countPlayerItem(player, Items.STONE),
                "关闭自动入库时玩家背包不得接收掉落");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "关闭自动入库时不得写入普通缓冲");
        helper.assertValueEqual(1, countWorldItem(helper, List.of(targetRel), Items.STONE),
                "事件列表未接管时精准采集石头必须由原版正常落地");

        stopSingleMine(player, target);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160, batch = "placed_recovery")
    public static void amethystVariantsUseVanillaSilkTouchAndBuddingDropsNothing(
            GameTestHelper helper) {
        List<Variant> variants = List.of(
                new Variant("amethyst_block", new BlockPos(2, 1, 6), Blocks.AMETHYST_BLOCK, true),
                new Variant("small_bud", new BlockPos(4, 1, 6), Blocks.SMALL_AMETHYST_BUD, true),
                new Variant("medium_bud", new BlockPos(6, 1, 6), Blocks.MEDIUM_AMETHYST_BUD, true),
                new Variant("large_bud", new BlockPos(8, 1, 6), Blocks.LARGE_AMETHYST_BUD, true),
                new Variant("cluster", new BlockPos(10, 1, 6), Blocks.AMETHYST_CLUSTER, true),
                new Variant("budding_amethyst", new BlockPos(12, 1, 6), Blocks.BUDDING_AMETHYST, false));

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, true);
        player.getInventory().clearContent();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());

        for (Variant variant : variants) {
            helper.setBlock(variant.relativePos(), variant.block());
            BlockPos absolute = helper.absolutePos(variant.relativePos());
            track(tracker, player, absolute);
            startSingleMine(player, absolute, true);

            helper.assertBlockPresent(Blocks.AIR, variant.relativePos());
            helper.assertTrue(!tracker.isPlaced(absolute),
                    variant.name() + " 成功破坏后必须清除 tracker");
            if (variant.hasDrop()) {
                helper.assertValueEqual(1, countPlayerItem(player, variant.block().asItem()),
                        variant.name() + " 必须产生一个原版精准采集方块物品");
                helper.assertValueEqual(0,
                        countWorldItem(helper, List.of(variant.relativePos()), variant.block().asItem()),
                        variant.name() + " 已由背包接收后不得重复落地");
            } else {
                helper.assertValueEqual(0, countPlayerItem(player, variant.block().asItem()),
                        variant.name() + " 必须服从原版零掉落结果，不能人工克隆方块物品");
                helper.assertValueEqual(0,
                        countWorldItem(helper, List.of(variant.relativePos()), variant.block().asItem()),
                        variant.name() + " 必须消失且不生成世界物品实体");
            }
        }

        stopSingleMine(player, helper.absolutePos(variants.getLast().relativePos()));
        helper.assertValueEqual(0, countPlayerItem(player, Items.AMETHYST_SHARD),
                "精准采集晶簇不得额外产生紫水晶碎片");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "紫水晶回收不得进入普通缓冲");
        helper.assertTrue(session.placement.recoveryJobs.isEmpty(),
                "零掉落的母岩也不得创建人工 recovery job");
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "placed_recovery")
    public static void disabledOrUntrackedTargetsContinueNormalMiningAndQuickStop(
            GameTestHelper helper) {
        BlockPos disabledRel = new BlockPos(4, 1, 4);
        BlockPos untrackedRel = new BlockPos(6, 1, 4);
        BlockPos disabled = helper.absolutePos(disabledRel);
        BlockPos untracked = helper.absolutePos(untrackedRel);
        helper.setBlock(disabledRel, Blocks.STONE);
        helper.setBlock(untrackedRel, Blocks.DIRT);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, player, false);
        player.getInventory().clearContent();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        track(tracker, player, disabled);

        startSingleMine(player, disabled, false);
        helper.assertBlockPresent(Blocks.STONE, disabledRel);
        stopSingleMine(player, disabled);

        startSingleMine(player, untracked, true);
        helper.assertBlockPresent(Blocks.DIRT, untrackedRel);
        stopSingleMine(player, untracked);

        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(Blocks.STONE, disabledRel);
            helper.assertBlockPresent(Blocks.DIRT, untrackedRel);
            helper.assertTrue(tracker.isPlaced(disabled),
                    "关闭回收开关不得消费 tracker");
            helper.assertTrue(!tracker.isPlaced(untracked),
                    "未追踪目标不得被回收入口补写 tracker");
            helper.assertValueEqual(0, countWorldItem(helper,
                    List.of(disabledRel, untrackedRel), Items.COBBLESTONE),
                    "快速 STOP 的普通挖掘不得提前破坏目标");
            RtsServerGameTests.stopPlayers(player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void differentBlockIdClearsStaleCredentialAndUsesNormalMining(
            GameTestHelper helper) {
        BlockPos targetRel = new BlockPos(4, 1, 12);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(targetRel, Blocks.DIRT);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, player, false);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        track(tracker, player, target);

        // 模拟活塞/Create/其他搬运器把同一坐标替换成不同注册 ID；不依赖任何模组名单。
        helper.setBlock(targetRel, Blocks.DIAMOND_BLOCK);
        startSingleMine(player, target, true);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, targetRel);
        helper.assertTrue(!tracker.isPlaced(target),
                "不同注册 ID 必须清掉泥土留下的回收凭据");

        stopSingleMine(player, target);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void sameBlockIdAllowsStateAndBlockEntityChanges(GameTestHelper helper) {
        BlockPos stateRel = new BlockPos(4, 1, 12);
        BlockPos nbtRel = new BlockPos(7, 1, 12);
        BlockPos state = helper.absolutePos(stateRel);
        BlockPos nbt = helper.absolutePos(nbtRel);
        helper.setBlock(stateRel, Blocks.OAK_TRAPDOOR);
        helper.setBlock(nbtRel, Blocks.CHEST);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, player, false);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        track(tracker, player, state);
        track(tracker, player, nbt);

        helper.setBlock(stateRel, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true));
        helper.assertTrue(helper.getBlockEntity(nbtRel) instanceof ChestBlockEntity,
                "同 ID NBT 场景必须存在箱子方块实体");
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(nbtRel);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        chest.setChanged();

        startSingleMine(player, state, true);
        startSingleMine(player, nbt, true);
        helper.assertBlockPresent(Blocks.AIR, stateRel);
        helper.assertBlockPresent(Blocks.AIR, nbtRel);
        helper.assertTrue(!tracker.isPlaced(state) && !tracker.isPlaced(nbt),
                "同一注册 ID 即使状态/NBT 变化也应沿用正常回收链并清除凭据");

        stopSingleMine(player, nbt);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void recoveryUndoRestoresOriginalOwnerCredential(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos targetRel = new BlockPos(10, 1, 12);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(targetRel, Blocks.STONE);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestRel);
        chest.setItem(0, new ItemStack(Items.STONE));
        chest.setChanged();

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, true);
        player.getInventory().clearContent();
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        track(tracker, player, target);
        ServerHistoryManager.clear(player.getUUID());

        startSingleMine(player, target, true);
        helper.assertBlockPresent(Blocks.AIR, targetRel);
        helper.assertTrue(tracker.captureSnapshot(target) == null,
                "回收成功后 Ctrl+Z 前必须先清除当前凭据");
        helper.assertValueEqual(2, countChestItem(helper, chestRel, Items.STONE),
                "回收掉落应先进入 linked storage，供 survival Ctrl+Z 消耗");

        helper.assertValueEqual(1, ServerHistoryManager.executeUndo(player),
                "瞬时回收的 survival 历史必须可以撤回一个方块");
        helper.assertBlockPresent(Blocks.STONE, targetRel);
        helper.assertValueEqual(1, countChestItem(helper, chestRel, Items.STONE),
                "survival Ctrl+Z 应从 linked storage 消耗一份恢复材料");
        PlacedBlockTrackerData.CredentialSnapshot restored = tracker.captureSnapshot(target);
        helper.assertTrue(restored != null && player.getUUID().equals(restored.owner()),
                "回收 Ctrl+Z 必须恢复原 owner，而不是用当前操作者重新认领");
        helper.assertTrue(tracker.checkRecovery(player.serverLevel(), target, player.getUUID()).canRecover(),
                "撤回后恢复的原 owner 凭据必须重新具备瞬时回收资格");

        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void undoDoesNotRemoveSameBlockFromANewerPlacementGeneration(
            GameTestHelper helper) {
        BlockPos targetRel = new BlockPos(13, 1, 12);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(targetRel, Blocks.STONE);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        tracker.markPlaced(target, player.getUUID(), player.serverLevel().getBlockState(target));
        PlacedBlockTrackerData.CredentialSnapshot historical = tracker.captureSnapshot(target);
        HistoryBlockRecord placement = HistoryBlockRecord.placement(
                target, Blocks.AIR.defaultBlockState(), null,
                Blocks.STONE.defaultBlockState(), null, null, historical);
        ServerHistoryManager.clear(player.getUUID());
        ServerHistoryManager.recordPlacementWithRecords(
                player, List.of(placement), Direction.UP, true);

        // 模拟原石头被搬走后，另一个同 ID 石头重新进入原坐标；BlockState 相同，
        // 只有持久化 generation 能证明它不是历史里的物理放置代次。
        tracker.clear(target);
        tracker.markPlaced(target, player.getUUID(), player.serverLevel().getBlockState(target));
        PlacedBlockTrackerData.CredentialSnapshot replacement = tracker.captureSnapshot(target);
        helper.assertTrue(historical != null && replacement != null
                        && historical.generation() != replacement.generation(),
                "测试夹具必须生成不同的放置 generation");

        helper.assertValueEqual(0, ServerHistoryManager.executeUndo(player),
                "旧 Ctrl+Z 不得删除后来进入同坐标的同 ID 方块");
        helper.assertBlockPresent(Blocks.STONE, targetRel);
        helper.assertTrue(replacement.equals(tracker.captureSnapshot(target)),
                "被跳过的旧 Ctrl+Z 不得覆盖新一代凭据");

        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void ownerMismatchPreservesCredentialAndFallsBackToNormalMining(
            GameTestHelper helper) {
        BlockPos targetRel = new BlockPos(7, 1, 12);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(targetRel, Blocks.GOLD_BLOCK);

        ServerPlayer owner = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        ServerPlayer other = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, owner, false);
        prepareSession(helper, other, false);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(owner.serverLevel());
        track(tracker, owner, target);

        startSingleMine(other, target, true);
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, targetRel);
        helper.assertTrue(tracker.captureSnapshot(target) != null
                        && owner.getUUID().equals(tracker.captureSnapshot(target).owner()),
                "owner 不匹配只能放弃瞬时回收，不能消费原 owner 凭据");

        stopSingleMine(other, target);
        RtsServerGameTests.stopPlayers(owner);
        RtsServerGameTests.stopPlayers(other);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void twoPlayersKeepDropsHandsAndTrackersIsolated(GameTestHelper helper) {
        BlockPos firstRel = new BlockPos(4, 1, 9);
        BlockPos secondRel = new BlockPos(8, 1, 9);
        BlockPos first = helper.absolutePos(firstRel);
        BlockPos second = helper.absolutePos(secondRel);
        helper.setBlock(firstRel, Blocks.GOLD_BLOCK);
        helper.setBlock(secondRel, Blocks.DIAMOND_BLOCK);

        ServerPlayer firstPlayer = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        ServerPlayer secondPlayer = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession firstSession = prepareSession(helper, firstPlayer, true);
        RtsStorageSession secondSession = prepareSession(helper, secondPlayer, true);
        firstPlayer.getInventory().clearContent();
        secondPlayer.getInventory().clearContent();
        ItemStack firstHand = namedStack(Items.STICK, "first-hand");
        ItemStack secondHand = namedStack(Items.BLAZE_ROD, "second-hand");
        firstPlayer.setItemInHand(InteractionHand.MAIN_HAND, firstHand.copy());
        secondPlayer.setItemInHand(InteractionHand.MAIN_HAND, secondHand.copy());
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(helper.getLevel());
        track(tracker, firstPlayer, first);
        track(tracker, secondPlayer, second);

        startSingleMine(firstPlayer, first, true);
        startSingleMine(secondPlayer, second, true);
        helper.assertBlockPresent(Blocks.AIR, firstRel);
        helper.assertBlockPresent(Blocks.AIR, secondRel);
        helper.assertTrue(!tracker.isPlaced(first) && !tracker.isPlaced(second),
                "两个成功目标必须各自清除 tracker");

        stopSingleMine(firstPlayer, first);
        stopSingleMine(secondPlayer, second);
        helper.assertTrue(ItemStack.matches(firstHand, firstPlayer.getMainHandItem()),
                "第一个玩家的真实主手必须完整恢复");
        helper.assertTrue(ItemStack.matches(secondHand, secondPlayer.getMainHandItem()),
                "第二个玩家的真实主手必须完整恢复");
        helper.assertValueEqual(1, countPlayerItem(firstPlayer, Items.GOLD_BLOCK),
                "第一个玩家必须只收到自己的金块");
        helper.assertValueEqual(0, countPlayerItem(firstPlayer, Items.DIAMOND_BLOCK),
                "第一个玩家不得收到第二人的钻石块");
        helper.assertValueEqual(1, countPlayerItem(secondPlayer, Items.DIAMOND_BLOCK),
                "第二个玩家必须只收到自己的钻石块");
        helper.assertValueEqual(0, countPlayerItem(secondPlayer, Items.GOLD_BLOCK),
                "第二个玩家不得收到第一人的金块");
        helper.assertTrue(firstSession.miningDropBuffer.isEmpty()
                        && secondSession.miningDropBuffer.isEmpty(),
                "两个玩家都不得串入普通掉落缓冲");
        RtsServerGameTests.stopPlayers(firstPlayer);
        RtsServerGameTests.stopPlayers(secondPlayer);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void componentRichShulkerDropKeepsExactItemStackIdentity(GameTestHelper helper) {
        BlockPos targetRel = new BlockPos(4, 1, 4);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(targetRel, Blocks.SHULKER_BOX);
        BlockEntity blockEntity = helper.getBlockEntity(targetRel);
        helper.assertTrue(blockEntity instanceof ShulkerBoxBlockEntity,
                "测试场景必须创建潜影盒方块实体");
        ShulkerBoxBlockEntity shulker = (ShulkerBoxBlockEntity) blockEntity;
        ItemStack namedShulker = new ItemStack(Items.SHULKER_BOX);
        namedShulker.set(DataComponents.CUSTOM_NAME, Component.literal("component-sentinel"));
        shulker.applyComponentsFromItemStack(namedShulker);
        shulker.setItem(0, new ItemStack(Items.DIAMOND, 7));
        shulker.setChanged();
        ItemStack expected = new ItemStack(Items.SHULKER_BOX);
        shulker.saveToItem(expected, helper.getLevel().registryAccess());

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, true);
        player.getInventory().clearContent();
        track(PlacedBlockTrackerData.get(player.serverLevel()), player, target);

        startSingleMine(player, target, true);
        ItemStack recovered = findPlayerStack(player, Items.SHULKER_BOX);
        helper.assertTrue(!recovered.isEmpty(), "组件化潜影盒必须进入玩家背包 fallback");
        helper.assertTrue(ItemStack.isSameItemSameComponents(expected, recovered),
                "直接入库必须保留完整组件身份，不能只按 item id 重建");
        helper.assertValueEqual("component-sentinel",
                recovered.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString(),
                "自定义名称组件必须保留");
        ItemContainerContents contents = recovered.getOrDefault(
                DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int diamonds = 0;
        for (ItemStack stack : contents.nonEmptyItems()) {
            if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
        }
        helper.assertValueEqual(7, diamonds, "容器内容组件必须完整保留");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "组件化掉落也不得进入普通缓冲");
        helper.assertValueEqual(0, countWorldItem(helper, List.of(targetRel), Items.SHULKER_BOX),
                "背包已接收潜影盒后世界不得重复生成实体");

        stopSingleMine(player, target);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    private static RtsStorageSession prepareSession(
            GameTestHelper helper, ServerPlayer player, boolean autoStore) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        helper.assertTrue(session != null, "GameTest RTS 玩家必须已经创建服务端会话");
        session.linkedStorageInfo.clear();
        session.sessionFlags.useBdNetwork = false;
        RtsAPI.get().bindings().setAutoStoreMinedDrops(player, autoStore);
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "每个回收场景必须从空 mining drop buffer 开始");
        return session;
    }

    private static void startSingleMine(ServerPlayer player, BlockPos target, boolean allowRecovery) {
        ServiceRegistry.getInstance().mining().mine(
                player, target, Direction.UP, true, (byte) 0,
                "", ItemStack.EMPTY, allowRecovery, true);
    }

    private static void stopSingleMine(ServerPlayer player, BlockPos target) {
        ServiceRegistry.getInstance().mining().mine(
                player, target, Direction.UP, false, (byte) 0,
                "", ItemStack.EMPTY, false, true);
    }

    private static void track(PlacedBlockTrackerData tracker, ServerPlayer player, BlockPos pos) {
        tracker.markPlaced(pos, player.getUUID(), player.serverLevel().getBlockState(pos));
    }

    private static ItemStack namedStack(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        List<ItemStack> snapshot = new ArrayList<>(player.getInventory().getContainerSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            snapshot.add(player.getInventory().getItem(slot).copy());
        }
        return snapshot;
    }

    private static boolean inventoryMatches(ServerPlayer player, List<ItemStack> expected) {
        if (player.getInventory().getContainerSize() != expected.size()) return false;
        for (int slot = 0; slot < expected.size(); slot++) {
            if (!ItemStack.matches(expected.get(slot), player.getInventory().getItem(slot))) return false;
        }
        return true;
    }

    private static ItemStack findPlayerStack(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static int countPlayerItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countChestItem(GameTestHelper helper, BlockPos chestRel, Item item) {
        BlockEntity blockEntity = helper.getBlockEntity(chestRel);
        helper.assertTrue(blockEntity instanceof ChestBlockEntity,
                "测试场景必须包含可访问的真实箱子");
        ChestBlockEntity chest = (ChestBlockEntity) blockEntity;
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countWorldItem(GameTestHelper helper, List<BlockPos> targetsRel, Item item) {
        BlockPos first = helper.absolutePos(targetsRel.getFirst());
        BlockPos last = helper.absolutePos(targetsRel.getLast());
        AABB bounds = new AABB(
                Math.min(first.getX(), last.getX()), Math.min(first.getY(), last.getY()),
                Math.min(first.getZ(), last.getZ()),
                Math.max(first.getX(), last.getX()) + 1.0D,
                Math.max(first.getY(), last.getY()) + 1.0D,
                Math.max(first.getZ(), last.getZ()) + 1.0D).inflate(1.0D);
        return helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, bounds,
                        entity -> entity.isAlive() && entity.getItem().is(item))
                .stream()
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    private record Variant(String name, BlockPos relativePos, Block block, boolean hasDrop) {
    }
}
