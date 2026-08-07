package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsConvenienceDestroyService;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 三种便捷破坏工具的真实世界规划与正式任务接线测试。
 *
 * <p>这里不复制 Task Engine 的既有细节测试，只验证声明式请求会得到正确、完整且有界的 世界目标；其中一例继续穿过正式拆除服务，保证功能不是只有预览没有执行。
 */
public final class RtsConvenienceDestroyGameTests implements FabricGameTest {
  private static final String EMPTY_TEMPLATE = FabricGameTest.EMPTY_STRUCTURE;

  private RtsConvenienceDestroyGameTests() {}

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void repeatXyzSettingsPlanAtTwoIndependentAnchors(GameTestHelper helper) {
    RtsConvenienceDestroySettings settings = new RtsConvenienceDestroySettings(3, 2, 2, 0, 0, 64);
    BlockPos first = helper.absolutePos(new BlockPos(4, 3, 4));
    BlockPos second = helper.absolutePos(new BlockPos(10, 3, 4));
    List<BlockPos> firstExpected = fillRepeatBox(helper.getLevel(), first, 3, 2, 2);
    List<BlockPos> secondExpected = fillRepeatBox(helper.getLevel(), second, 3, 2, 2);

    RtsConvenienceDestroyPlanner.Plan firstPlan =
        RtsConvenienceDestroyPlanner.plan(
            helper.getLevel(), RtsConvenienceDestroyMode.REPEAT_BOX, first, Direction.UP, settings);
    RtsConvenienceDestroyPlanner.Plan secondPlan =
        RtsConvenienceDestroyPlanner.plan(
            helper.getLevel(),
            RtsConvenienceDestroyMode.REPEAT_BOX,
            second,
            Direction.UP,
            settings);

    helper.assertTrue(firstPlan.ready() && secondPlan.ready(), "同一组 XYZ 参数应能在两个新锚点直接重复规划");
    helper.assertTrue(firstPlan.targets().containsAll(firstExpected), "第一次重复 XYZ 应覆盖完整目标盒");
    helper.assertTrue(secondPlan.targets().containsAll(secondExpected), "第二次重复 XYZ 不应要求重新框选两个角");
    helper.assertTrue(
        firstPlan.targets().size() == 12 && secondPlan.targets().size() == 12,
        "3×2×2 应精确产生 12 个目标");
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
  public static void negativeChunkQuarrySnapsToExactSixteenBySixteen(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos anchor = new BlockPos(-33, 200, -33);
    ChunkPos chunk = new ChunkPos(anchor);
    level.getChunk(chunk.x, chunk.z);
    BlockPos first = new BlockPos(chunk.getMinBlockX(), 200, chunk.getMinBlockZ());
    BlockPos second = new BlockPos(chunk.getMaxBlockX(), 200, chunk.getMaxBlockZ());
    BlockPos outside = new BlockPos(chunk.getMaxBlockX() + 1, 200, chunk.getMaxBlockZ());
    level.setBlock(first, Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(second, Blocks.DIRT.defaultBlockState(), 3);
    level.setBlock(outside, Blocks.GOLD_BLOCK.defaultBlockState(), 3);

    RtsConvenienceDestroyPlanner.Plan plan =
        RtsConvenienceDestroyPlanner.plan(
            level,
            RtsConvenienceDestroyMode.CHUNK_QUARRY,
            anchor,
            Direction.UP,
            new RtsConvenienceDestroySettings(1, 1, 1, 0, 0, 64));

    helper.assertTrue(plan.ready(), "负坐标区块应使用 ChunkPos 正确吸附");
    helper.assertTrue(
        plan.targets().contains(first) && plan.targets().contains(second), "区块两个对角边界都必须包含");
    helper.assertTrue(!plan.targets().contains(outside), "相邻区块的方块不能泄漏进当前 16×16");
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void touchingTreesAreOneAllowedGroupAtExactLimit(GameTestHelper helper) {
    TreeFixture tree = touchingTrees(helper);
    RtsConvenienceDestroyPlanner.Plan plan =
        RtsConvenienceDestroyPlanner.plan(
            helper.getLevel(),
            RtsConvenienceDestroyMode.TREE_FELL,
            tree.firstTrunk(),
            Direction.UP,
            new RtsConvenienceDestroySettings(1, 1, 1, 0, 0, tree.blocks().size()));

    helper.assertTrue(plan.ready(), "相接的多棵树在总数等于上限时应作为一组接受");
    helper.assertTrue(
        plan.targets().contains(tree.firstTrunk()) && plan.targets().contains(tree.secondTrunk()),
        "树叶桥接后的两个树干都应进入同一次砍伐");
    helper.assertTrue(plan.targets().size() == tree.blocks().size(), "上限按最终实际方块总数计算");
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void touchingTreeGroupOverLimitRejectsEveryBlock(GameTestHelper helper) {
    TreeFixture tree = touchingTrees(helper);
    RtsConvenienceDestroyPlanner.Plan plan =
        RtsConvenienceDestroyPlanner.plan(
            helper.getLevel(),
            RtsConvenienceDestroyMode.TREE_FELL,
            tree.firstTrunk(),
            Direction.UP,
            new RtsConvenienceDestroySettings(1, 1, 1, 0, 0, tree.blocks().size() - 1));

    helper.assertTrue(
        plan.code() == RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT, "完整连通树组超过最大方块数时必须报告超限");
    helper.assertTrue(plan.targets().isEmpty(), "超限不能截断目标或只砍第一棵树");
    for (BlockPos pos : tree.blocks()) {
      helper.assertTrue(!helper.getLevel().getBlockState(pos).isAir(), "被拒绝的树组必须保持完整");
    }
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 240)
  public static void repeatBoxSubmitsThroughExistingDestructionTask(GameTestHelper helper) {
    Config.setSurvivalProgressionEnabled(false);
    BlockPos anchorRel = new BlockPos(5, 3, 5);
    BlockPos anchor = helper.absolutePos(anchorRel);
    List<BlockPos> expected = fillRepeatBox(helper.getLevel(), anchor, 2, 2, 2);
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);

    RtsConvenienceDestroyPlanner.Plan plan =
        RtsConvenienceDestroyService.INSTANCE.submit(
            player,
            RtsConvenienceDestroyMode.REPEAT_BOX,
            anchor,
            Direction.UP,
            new RtsConvenienceDestroySettings(2, 2, 2, 0, 0, 64),
            (byte) 0,
            "",
            ItemStack.EMPTY,
            false);
    helper.assertTrue(
        plan.ready() && plan.targets().size() == expected.size(), "服务端声明式请求应规划完整盒并交给正式拆除任务");

    helper.succeedWhen(
        () -> {
          for (BlockPos pos : expected) {
            helper.assertTrue(
                helper.getLevel().getBlockState(pos).isAir(), "正式 Task Engine 应最终破坏所有便捷目标");
          }
          RtsServerGameTests.stopPlayers(player);
        });
  }

  private static List<BlockPos> fillRepeatBox(
      ServerLevel level, BlockPos anchor, int sizeX, int sizeY, int sizeZ) {
    int minX = anchor.getX() - (sizeX - 1) / 2;
    int minZ = anchor.getZ() - (sizeZ - 1) / 2;
    List<BlockPos> result = new ArrayList<>();
    for (int y = anchor.getY() - sizeY + 1; y <= anchor.getY(); y++) {
      for (int x = minX; x < minX + sizeX; x++) {
        for (int z = minZ; z < minZ + sizeZ; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
          result.add(pos);
        }
      }
    }
    return result;
  }

  private static TreeFixture touchingTrees(GameTestHelper helper) {
    List<BlockPos> blocks = new ArrayList<>();
    BlockPos first = helper.absolutePos(new BlockPos(4, 2, 6));
    BlockPos second = helper.absolutePos(new BlockPos(8, 2, 6));
    for (int dy = 0; dy < 3; dy++) {
      BlockPos firstLog = first.above(dy);
      BlockPos secondLog = second.above(dy);
      helper.getLevel().setBlock(firstLog, Blocks.OAK_LOG.defaultBlockState(), 3);
      helper.getLevel().setBlock(secondLog, Blocks.OAK_LOG.defaultBlockState(), 3);
      blocks.add(firstLog);
      blocks.add(secondLog);
    }
    for (int dx = 0; dx <= 4; dx++) {
      BlockPos leaf = first.offset(dx, 3, 0);
      helper.getLevel().setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState(), 3);
      blocks.add(leaf);
    }
    return new TreeFixture(first, second, List.copyOf(blocks));
  }

  private record TreeFixture(BlockPos firstTrunk, BlockPos secondTrunk, List<BlockPos> blocks) {}
}
