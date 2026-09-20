package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningDropCapture;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge 1.20.1 已追踪瞬时回收的真实服务端回归探针。
 *
 * <p>场景从生产 {@code MiningService -> MINE_SINGLE} 管线进入，验证回收发生在
 * WorkflowStart/ToolBorrow 之前、掉落不经过普通缓冲、owner/legacy/换块凭据语义、
 * 自动入库开关、真实主手、取消事件和组件化方块掉落。运行由后续串行验收阶段负责。</p>
 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsPlacedRecoveryGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private RtsPlacedRecoveryGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "placed_recovery")
    public static void emptyHandRecoveryCompletesBeforeBorrowAndKeepsVanillaDrop(
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
        helper.assertTrue(player.getMainHandItem().isEmpty(), "内部工具不得留在主手");
        helper.assertTrue(session.mining.miningToolLease == null
                        || session.mining.miningToolLease.isEmpty(),
                "回收阶段不得创建真实工具租约");
        RtsGameTestAssertions.assertValueEqual(helper, -1, session.mining.workflowEntryId,
                "同步回收不得创建普通挖掘工作流");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "同步回收不得进入普通掉落缓冲");

        stopSingleMine(player, target);
        helper.succeedWhen(() -> {
            helper.assertTrue(inventoryMatches(player, inventoryBefore),
                    "关闭自动入库时玩家背包必须保持原样");
            RtsGameTestAssertions.assertValueEqual(helper, 1, countWorldItem(helper, List.of(targetRel), Items.STONE),
                    "真实原版掉落必须落到世界");
            helper.assertTrue(session.placement.recoveryJobs.isEmpty(),
                    "新回收链不得创建 legacy recovery job");
            RtsServerGameTests.stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void autoStoreExcludesTargetAndFallsBackToInventory(
            GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos linkedRel = new BlockPos(4, 1, 4);
        BlockPos fallbackRel = new BlockPos(6, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(linkedRel, Blocks.STONE);
        helper.setBlock(fallbackRel, Blocks.DIRT);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, true);
        player.getInventory().clearContent();
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());

        BlockPos linkedTarget = helper.absolutePos(linkedRel);
        track(tracker, player, linkedTarget);
        startSingleMine(player, linkedTarget, true);
        RtsGameTestAssertions.assertValueEqual(helper, 1, countChestItem(helper, chestRel, Items.STONE),
                "自动入库必须进入链接箱");
        RtsGameTestAssertions.assertValueEqual(helper, 0, countWorldItem(helper, List.of(linkedRel), Items.STONE),
                "已接收掉落不得重复落地");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "直接入库不得进入普通缓冲");

        // 链接储存已满时仍走同一真实 remainder 回退，不创建旧 recovery claim，也不吞掉落。
        player.getInventory().clearContent();
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestRel);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        chest.setChanged();
        session.linkedStorageInfo.clear();
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        BlockPos fullStorageTarget = helper.absolutePos(new BlockPos(8, 1, 4));
        helper.setBlock(new BlockPos(8, 1, 4), Blocks.STONE);
        track(tracker, player, fullStorageTarget);
        startSingleMine(player, fullStorageTarget, true);
        RtsGameTestAssertions.assertValueEqual(helper, 0, countChestItem(helper, chestRel, Items.STONE),
                "满链接储存不得伪称已接收方块");
        RtsGameTestAssertions.assertValueEqual(helper, 1, countPlayerItem(player, Items.STONE),
                "链接储存满时真实 remainder 必须回退玩家背包");
        RtsGameTestAssertions.assertValueEqual(helper, 0, countWorldItem(helper, List.of(new BlockPos(8, 1, 4)), Items.STONE),
                "玩家背包接收 remainder 后不得重复落地");

        session.linkedStorageInfo.clear();
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
        BlockPos fallbackTarget = helper.absolutePos(fallbackRel);
        track(tracker, player, fallbackTarget);
        startSingleMine(player, fallbackTarget, true);
        RtsGameTestAssertions.assertValueEqual(helper, 1, countPlayerItem(player, Items.DIRT),
                "无链接储存时必须回退玩家背包");
        helper.assertTrue(player.getMainHandItem().is(Items.DIRT),
                "唯一空主手槽必须收到 fallback 结果");
        RtsGameTestAssertions.assertValueEqual(helper, 0, countWorldItem(helper, List.of(fallbackRel), Items.DIRT),
                "背包已接收数量不得重复落地");
        helper.assertTrue(session.placement.recoveryJobs.isEmpty(),
                "直接回收不得创建 legacy recovery job");

        stopSingleMine(player, fallbackTarget);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void outwardDropFromRealBreakStaysInsideInstantRecoveryScope(
            GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos targetRel = new BlockPos(4, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(targetRel, Blocks.STONE);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, player, true);
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        BlockPos target = helper.absolutePos(targetRel);
        track(PlacedBlockTrackerData.get(player.serverLevel()), player, target);
        OutwardDropMover mover = new OutwardDropMover(target);
        MinecraftForge.EVENT_BUS.register(mover);
        try {
            startSingleMine(player, target, true);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(mover);
        }

        helper.assertBlockPresent(Blocks.AIR, targetRel);
        RtsGameTestAssertions.assertValueEqual(helper, 1, countChestItem(helper, chestRel, Items.STONE),
                "真实破坏产生的外移 ItemEntity 仍必须归属于本次回收");
        RtsGameTestAssertions.assertValueEqual(helper, 0, countWorldItem(helper, List.of(targetRel), Items.STONE),
                "已接收的外移掉落不得重复留在世界");
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void diskLoadedAndCancelledEntityEventsAreNeverStored(
            GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos targetRel = new BlockPos(4, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(targetRel, Blocks.STONE);
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, true);
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        BlockPos target = helper.absolutePos(targetRel);

        ItemEntity loadedEntity = new ItemEntity(player.serverLevel(), target.getX() + 2.0D,
                target.getY() + 0.5D, target.getZ() + 2.0D, new ItemStack(Items.STONE));
        RtsMiningDropCapture.captureInstantRecovery(player, session, target, () -> {
            EntityJoinLevelEvent event = new EntityJoinLevelEvent(loadedEntity, player.serverLevel(), true);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(!event.isCanceled(), "磁盘加载实体不能被 RTS 回收器取消");
            return true;
        });
        RtsGameTestAssertions.assertValueEqual(helper, 0, countChestItem(helper, chestRel, Items.STONE),
                "loadedFromDisk 实体不得被写入链接储存");
        RtsGameTestAssertions.assertValueEqual(helper, 1, loadedEntity.getItem().getCount(),
                "loadedFromDisk 实体必须保留原始物品");

        ItemEntity cancelledEntity = new ItemEntity(player.serverLevel(), target.getX() + 2.0D,
                target.getY() + 0.5D, target.getZ() + 2.0D, new ItemStack(Items.STONE));
        EntityJoinCanceller canceller = new EntityJoinCanceller(cancelledEntity);
        MinecraftForge.EVENT_BUS.register(canceller);
        try {
            RtsMiningDropCapture.captureInstantRecovery(player, session, target, () -> {
                EntityJoinLevelEvent event = new EntityJoinLevelEvent(cancelledEntity, player.serverLevel(), false);
                MinecraftForge.EVENT_BUS.post(event);
                helper.assertTrue(event.isCanceled(), "已取消的实体事件必须保持取消状态");
                return true;
            });
        } finally {
            MinecraftForge.EVENT_BUS.unregister(canceller);
        }
        RtsGameTestAssertions.assertValueEqual(helper, 0, countChestItem(helper, chestRel, Items.STONE),
                "已取消实体事件不得被写入链接储存");
        RtsGameTestAssertions.assertValueEqual(helper, 1, cancelledEntity.getItem().getCount(),
                "已取消实体事件必须保留原始物品");
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "placed_recovery")
    public static void disabledOrUntrackedTargetKeepsNormalPath(
            GameTestHelper helper) {
        BlockPos disabledRel = new BlockPos(4, 1, 4);
        BlockPos untrackedRel = new BlockPos(6, 1, 4);
        BlockPos disabled = helper.absolutePos(disabledRel);
        BlockPos untracked = helper.absolutePos(untrackedRel);
        helper.setBlock(disabledRel, Blocks.STONE);
        helper.setBlock(untrackedRel, Blocks.DIRT);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, player, false);
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
            helper.assertTrue(tracker.isPlaced(disabled), "关闭回收开关不得消费 tracker");
            helper.assertTrue(!tracker.isPlaced(untracked), "普通挖掘不得补写 tracker");
            RtsServerGameTests.stopPlayers(player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void legacyAndChangedBlockCredentialsAreConservative(GameTestHelper helper) {
        BlockPos legacyRel = new BlockPos(4, 1, 8);
        BlockPos changedRel = new BlockPos(7, 1, 8);
        BlockPos legacy = helper.absolutePos(legacyRel);
        BlockPos changed = helper.absolutePos(changedRel);
        helper.setBlock(legacyRel, Blocks.STONE);
        helper.setBlock(changedRel, Blocks.DIRT);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, player, false);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        tracker.markLegacy(legacy);
        track(tracker, player, changed);
        helper.setBlock(changedRel, Blocks.DIAMOND_BLOCK);

        startSingleMine(player, legacy, true);
        helper.assertBlockPresent(Blocks.AIR, legacyRel);
        helper.assertTrue(!tracker.isPlaced(legacy), "legacy 成功回收后必须清除凭据");

        startSingleMine(player, changed, true);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, changedRel);
        helper.assertTrue(!tracker.isPlaced(changed), "已换注册 ID 必须清除陈旧凭据");
        stopSingleMine(player, changed);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void ownerMismatchPreservesCredential(GameTestHelper helper) {
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
                "owner 不匹配不得消费原 owner 凭据");
        stopSingleMine(other, target);
        RtsServerGameTests.stopPlayers(owner);
        RtsServerGameTests.stopPlayers(other);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void cancelledBreakRestoresTrackerAndWorld(GameTestHelper helper) {
        BlockPos targetRel = new BlockPos(4, 1, 16);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(targetRel, Blocks.STONE);
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        prepareSession(helper, player, true);
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(player.serverLevel());
        track(tracker, player, target);
        BreakCanceller canceller = new BreakCanceller(player, target);
        MinecraftForge.EVENT_BUS.register(canceller);
        try {
            startSingleMine(player, target, true);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(canceller);
        }
        helper.assertBlockPresent(Blocks.STONE, targetRel);
        helper.assertTrue(tracker.captureSnapshot(target) != null,
                "取消破坏不得清除有效 tracker");
        RtsGameTestAssertions.assertValueEqual(helper, 0, countPlayerItem(player, Items.STONE),
                "取消破坏不得生成或回收入库物品");
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void realMainHandAndComponentShulkerArePreserved(GameTestHelper helper) {
        BlockPos targetRel = new BlockPos(4, 1, 4);
        BlockPos target = helper.absolutePos(targetRel);
        helper.setBlock(targetRel, Blocks.SHULKER_BOX);
        BlockEntity blockEntity = helper.getBlockEntity(targetRel);
        helper.assertTrue(blockEntity instanceof ShulkerBoxBlockEntity,
                "测试场景必须创建潜影盒方块实体");
        ShulkerBoxBlockEntity shulker = (ShulkerBoxBlockEntity) blockEntity;
        shulker.setCustomName(Component.literal("component-sentinel"));
        shulker.setItem(0, new ItemStack(Items.DIAMOND, 7));
        shulker.setChanged();
        // 1.20.1 的原版掉落把名称放到 display.Name；saveToItem 的方块实体快照不是掉落格式。
        List<ItemStack> vanillaDrops = Block.getDrops(
                helper.getLevel().getBlockState(target), helper.getLevel(), target, shulker);
        helper.assertTrue(vanillaDrops.size() == 1 && vanillaDrops.get(0).is(Items.SHULKER_BOX),
                "原版掉落基线必须是一个完整潜影盒");
        ItemStack expected = vanillaDrops.get(0).copy();
        helper.assertTrue(expected.getHoverName().getString().equals("component-sentinel"),
                "原版掉落基线必须保留自定义名称");

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        RtsStorageSession session = prepareSession(helper, player, true);
        player.getInventory().clearContent();
        ItemStack realHand = namedStack(Items.STICK, "real-hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, realHand.copy());
        track(PlacedBlockTrackerData.get(player.serverLevel()), player, target);

        startSingleMine(player, target, true);
        ItemStack recovered = findPlayerStack(player, Items.SHULKER_BOX);
        helper.assertTrue(ItemStack.matches(expected, recovered),
                "直接回收必须保留潜影盒名称与容器 NBT");
        var recoveredContents = recovered.getTagElement("BlockEntityTag");
        helper.assertTrue(recoveredContents != null, "回收潜影盒必须携带容器 NBT");
        NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(recoveredContents, contents);
        helper.assertTrue(ItemStack.matches(new ItemStack(Items.DIAMOND, 7), contents.get(0))
                        && contents.stream().filter(stack -> !stack.isEmpty()).count() == 1,
                "回收容器必须只保留原槽位的七颗钻石，不丢失、不增发");
        helper.assertTrue(ItemStack.matches(realHand, player.getMainHandItem()),
                "真实主手必须在组件掉落回收后完整恢复");
        helper.assertTrue(session.miningDropBuffer.isEmpty(),
                "组件掉落不得进入普通缓冲");
        RtsGameTestAssertions.assertValueEqual(helper, 0, countWorldItem(helper, List.of(targetRel), Items.SHULKER_BOX),
                "背包已接收组件方块后世界不得重复落地");

        stopSingleMine(player, target);
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "placed_recovery")
    public static void sameBlockIdGenerationGuardsUndo(GameTestHelper helper) {
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
        tracker.clear(target);
        tracker.markPlaced(target, player.getUUID(), player.serverLevel().getBlockState(target));
        PlacedBlockTrackerData.CredentialSnapshot replacement = tracker.captureSnapshot(target);

        helper.assertTrue(historical != null && replacement != null
                        && historical.generation() != replacement.generation(),
                "测试夹具必须生成不同 generation");
        RtsGameTestAssertions.assertValueEqual(helper, 0, ServerHistoryManager.executeUndo(player),
                "旧撤回不得删除后来进入同坐标的同 ID 方块");
        helper.assertTrue(replacement.equals(tracker.captureSnapshot(target)),
                "旧撤回不得覆盖新一代凭据");
        RtsServerGameTests.stopPlayers(player);
        helper.succeed();
    }

    private static RtsStorageSession prepareSession(
            GameTestHelper helper, ServerPlayer player, boolean autoStore) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        helper.assertTrue(session != null, "GameTest RTS 玩家必须有服务端会话");
        session.linkedStorageInfo.clear();
        session.sessionFlags.useBdNetwork = false;
        RtsAPI.get().bindings().setAutoStoreMinedDrops(player, autoStore);
        helper.assertTrue(session.miningDropBuffer.isEmpty(), "场景必须从空掉落缓冲开始");
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
        stack.setHoverName(Component.literal(name));
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
        helper.assertTrue(blockEntity instanceof ChestBlockEntity, "测试场景必须包含真实箱子");
        ChestBlockEntity chest = (ChestBlockEntity) blockEntity;
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countWorldItem(GameTestHelper helper, List<BlockPos> targetsRel, Item item) {
        BlockPos first = helper.absolutePos(targetsRel.get(0));
        BlockPos last = helper.absolutePos(targetsRel.get(targetsRel.size() - 1));
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

    private static final class BreakCanceller {
        private final ServerPlayer player;
        private final BlockPos target;

        private BreakCanceller(ServerPlayer player, BlockPos target) {
            this.player = player;
            this.target = target.immutable();
        }

        @SubscribeEvent
        public void onBreak(BlockEvent.BreakEvent event) {
            if (event.getPlayer() == player && target.equals(event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    /** 将真实方块破坏产生的实体移出目标格，验证接管不依赖瞬时坐标相等。 */
    private static final class OutwardDropMover {
        private final BlockPos target;

        private OutwardDropMover(BlockPos target) {
            this.target = target.immutable();
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onJoin(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof ItemEntity item
                    && !event.loadedFromDisk()
                    && target.equals(item.blockPosition())) {
                item.setPos(item.getX() + 1.25D, item.getY(), item.getZ() + 0.25D);
            }
        }
    }

    /** 先于 RTS LOWEST 监听器取消实体加入，验证 receiveCanceled 不会吞掉物品。 */
    private static final class EntityJoinCanceller {
        private final ItemEntity target;

        private EntityJoinCanceller(ItemEntity target) {
            this.target = target;
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onJoin(EntityJoinLevelEvent event) {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        }
    }
}
