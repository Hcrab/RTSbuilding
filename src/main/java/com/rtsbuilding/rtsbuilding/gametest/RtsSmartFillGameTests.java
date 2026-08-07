package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCandidateClassifier;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCell;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsConfirmSmartFillPayload;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsSmartFillService;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 智能填坑从真实世界分类、服务端重规划到持久化放置任务的回归测试。
 *
 * <p>算法的穷举边界由纯 Java 单测覆盖；这里专门证明客户端不提供坐标时，服务端仍能根据点击意图 找到封闭目标、拒绝开放空间，并把成功结果交给已有放置与撤销链。
 */
public final class RtsSmartFillGameTests implements FabricGameTest {
  private static final String EMPTY_TEMPLATE = FabricGameTest.EMPTY_STRUCTURE;

  private RtsSmartFillGameTests() {}

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160, batch = "smart_fill")
  public static void boundedHoleIsReplannedPlacedAndUndoable(GameTestHelper helper) {
    List<BlockPos> cavity = buildBoundedCavity(helper);
    BlockPos clickedRel = new BlockPos(9, 3, 7);
    BlockPos untouchedRel = new BlockPos(12, 3, 8);
    helper.setBlock(untouchedRel, Blocks.GOLD_BLOCK);

    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);
    RtsSmartFillService.ConfirmResult result =
        RtsSmartFillService.confirm(
            player, payload(player, helper.absolutePos(clickedRel), Direction.SOUTH));
    helper.assertTrue(result.queued(), "封闭洞穴应由服务端重新规划后进入正式放置任务");
    helper.assertValueEqual(cavity.size(), result.targetCount(), "服务端重规划应得到完整的 3x3 洞穴目标");

    helper.succeedWhen(
        () -> {
          for (BlockPos target : cavity) {
            helper.assertBlockPresent(Blocks.STONE, target);
          }
          helper.assertBlockPresent(Blocks.GOLD_BLOCK, untouchedRel);
          helper.assertTrue(
              ServerHistoryManager.executeUndo(player) > 0, "智能填坑必须复用普通放置历史并可由 Ctrl+Z 撤销");
          for (BlockPos target : cavity) {
            helper.assertBlockPresent(Blocks.AIR, target);
          }
          RtsServerGameTests.stopPlayers(player);
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200, batch = "smart_fill")
  public static void survivalFillConsumesAndUndoRefundsLinkedStorage(GameTestHelper helper) {
    List<BlockPos> cavity = buildBoundedCavity(helper);
    BlockPos chestRel = new BlockPos(3, 1, 3);
    BlockPos clickedRel = new BlockPos(9, 3, 7);
    helper.setBlock(chestRel, Blocks.CHEST);
    ChestBlockEntity chest = requireChest(helper, chestRel);
    chest.setItem(0, new ItemStack(Items.STONE, cavity.size()));
    chest.setChanged();

    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    RtsAPI.get()
        .bindings()
        .linkStorage(
            player, helper.absolutePos(chestRel), RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
    ServerHistoryManager.clear(player.getUUID());

    RtsSmartFillService.ConfirmResult result =
        RtsSmartFillService.confirm(
            player, payload(player, helper.absolutePos(clickedRel), Direction.SOUTH));
    helper.assertTrue(
        result.queued() && result.targetCount() == cavity.size(), "生存智能填坑应把服务端重规划结果交给正式放置任务");

    helper.succeedWhen(
        () -> {
          helper.assertTrue(
              !RtsServerGameTests.hasActiveTask(player, TaskType.PLACEMENT), "生存智能填坑任务尚未完成");
          for (BlockPos target : cavity) {
            helper.assertBlockPresent(Blocks.STONE, target);
          }
          helper.assertValueEqual(
              0, countChestItem(chest, Items.STONE), "生存智能填坑必须从 linked storage 精确消耗材料");
          helper.assertTrue(ServerHistoryManager.executeUndo(player) > 0, "生存智能填坑必须进入现有 Ctrl+Z 历史");
          for (BlockPos target : cavity) {
            helper.assertBlockPresent(Blocks.AIR, target);
          }
          helper.assertValueEqual(
              cavity.size(), countChestItem(chest, Items.STONE), "生存撤回必须把智能填坑材料退回 linked storage");
          RtsServerGameTests.stopPlayers(player);
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80, batch = "smart_fill")
  public static void openPlainIsRejectedWithoutCreatingWork(GameTestHelper helper) {
    BlockPos floorRel = new BlockPos(8, 1, 8);
    helper.setBlock(floorRel, Blocks.DIRT);
    for (int x = 6; x <= 10; x++) {
      for (int y = 2; y <= 5; y++) {
        for (int z = 6; z <= 10; z++) {
          helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
      }
    }
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);

    RtsSmartFillService.ConfirmResult result =
        RtsSmartFillService.confirm(
            player,
            payload(
                player, helper.absolutePos(floorRel), Direction.UP, SmartFillLimits.MIN_DIAMETER));
    helper.assertTrue(!result.queued() && result.targetCount() == 0, "开放地面不能被误判为需要填满的洞穴");
    helper.assertBlockPresent(Blocks.AIR, floorRel.above());
    RtsServerGameTests.stopPlayers(player);
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80, batch = "smart_fill")
  public static void malformedRayIntentIsRejectedBeforePlanning(GameTestHelper helper) {
    List<BlockPos> cavity = buildBoundedCavity(helper);
    BlockPos clickedRel = new BlockPos(9, 3, 7);
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);
    C2SRtsConfirmSmartFillPayload valid =
        payload(player, helper.absolutePos(clickedRel), Direction.SOUTH);
    C2SRtsConfirmSmartFillPayload malformed =
        new C2SRtsConfirmSmartFillPayload(
            valid.clickedPos(),
            valid.face(),
            valid.maxBlocks(),
            valid.detectionDiameter(),
            valid.hitOffsetX(),
            valid.hitOffsetY(),
            valid.hitOffsetZ(),
            valid.rotateSteps(),
            valid.statePreset(),
            valid.itemId(),
            valid.itemPrototype(),
            valid.rayOriginX(),
            valid.rayOriginY(),
            valid.rayOriginZ(),
            Double.NaN,
            valid.rayDirY(),
            valid.rayDirZ());

    RtsSmartFillService.ConfirmResult result = RtsSmartFillService.confirm(player, malformed);
    helper.assertTrue(!result.queued() && result.targetCount() == 0, "非有限射线不得进入智能填坑规划或任务队列");
    for (BlockPos target : cavity) {
      helper.assertBlockPresent(Blocks.AIR, target);
    }
    RtsServerGameTests.stopPlayers(player);
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80, batch = "smart_fill")
  public static void classifierSeparatesSafeReplaceablesAndForbiddenCells(GameTestHelper helper) {
    BlockPos waterRel = new BlockPos(7, 1, 7);
    BlockPos chestRel = new BlockPos(9, 1, 7);
    BlockPos grassRel = new BlockPos(7, 1, 9);
    BlockPos snowRel = new BlockPos(9, 1, 9);
    BlockPos stoneRel = new BlockPos(8, 1, 10);
    helper.setBlock(waterRel, Blocks.WATER);
    helper.setBlock(chestRel, Blocks.CHEST);
    helper.setBlock(grassRel, Blocks.SHORT_GRASS);
    helper.setBlock(snowRel, Blocks.SNOW);
    helper.setBlock(stoneRel, Blocks.STONE);

    helper.assertTrue(
        SmartFillCandidateClassifier.classify(helper.getLevel(), helper.absolutePos(waterRel))
            == SmartFillCell.FORBIDDEN,
        "流体不得被智能填坑覆盖");
    helper.assertTrue(
        SmartFillCandidateClassifier.classify(helper.getLevel(), helper.absolutePos(chestRel))
            == SmartFillCell.FORBIDDEN,
        "方块实体不得被智能填坑覆盖");
    helper.assertTrue(
        SmartFillCandidateClassifier.classify(helper.getLevel(), helper.absolutePos(grassRel))
            == SmartFillCell.CANDIDATE,
        "安全可替换植物应当允许被智能填坑替换");
    helper.assertTrue(
        SmartFillCandidateClassifier.classify(helper.getLevel(), helper.absolutePos(snowRel))
            == SmartFillCell.CANDIDATE,
        "薄雪层应当允许被智能填坑替换");
    helper.assertTrue(
        SmartFillCandidateClassifier.classify(helper.getLevel(), helper.absolutePos(stoneRel))
            == SmartFillCell.BOUNDARY,
        "完整实体方块应当作为真实洞壁");
    helper.succeed();
  }

  private static List<BlockPos> buildBoundedCavity(GameTestHelper helper) {
    for (int x = 7; x <= 11; x++) {
      for (int y = 1; y <= 5; y++) {
        for (int z = 7; z <= 9; z++) {
          helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
      }
    }
    List<BlockPos> cavity = new ArrayList<>();
    for (int x = 8; x <= 10; x++) {
      for (int y = 2; y <= 4; y++) {
        BlockPos target = new BlockPos(x, y, 8);
        helper.setBlock(target, Blocks.AIR);
        cavity.add(target);
      }
    }
    return List.copyOf(cavity);
  }

  private static C2SRtsConfirmSmartFillPayload payload(
      ServerPlayer player, BlockPos clicked, Direction face) {
    return payload(player, clicked, face, SmartFillLimits.DEFAULT_DIAMETER);
  }

  private static C2SRtsConfirmSmartFillPayload payload(
      ServerPlayer player, BlockPos clicked, Direction face, int diameter) {
    Vec3 rayOrigin = player.getEyePosition();
    Vec3 rayDirection = Vec3.atCenterOf(clicked).subtract(rayOrigin).normalize();
    return new C2SRtsConfirmSmartFillPayload(
        clicked,
        (byte) face.get3DDataValue(),
        SmartFillLimits.DEFAULT_BLOCKS,
        diameter,
        0.5D,
        0.5D,
        0.5D,
        (byte) 0,
        "",
        "minecraft:stone",
        new ItemStack(Items.STONE),
        rayOrigin.x,
        rayOrigin.y,
        rayOrigin.z,
        rayDirection.x,
        rayDirection.y,
        rayDirection.z);
  }

  private static ChestBlockEntity requireChest(GameTestHelper helper, BlockPos chestRel) {
    BlockEntity blockEntity = helper.getBlockEntity(chestRel);
    helper.assertTrue(blockEntity instanceof ChestBlockEntity, "智能填坑生存测试必须存在可访问的箱子");
    return (ChestBlockEntity) blockEntity;
  }

  private static int countChestItem(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
    int count = 0;
    for (int slot = 0; slot < chest.getContainerSize(); slot++) {
      ItemStack stack = chest.getItem(slot);
      if (stack.getItem() == item) {
        count += stack.getCount();
      }
    }
    return count;
  }
}
