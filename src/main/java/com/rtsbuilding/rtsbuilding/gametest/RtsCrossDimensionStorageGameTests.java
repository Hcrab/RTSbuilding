package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.wake.RtsCrossDimensionStorageWakeService;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * 跨维度储存的真实服务端边界测试。
 *
 * <p>本类只验证区块唤醒与精确维度解析，不测试 UI，也不复制主测试类的玩家连接夹具。 玩家必须真实注册进 PlayerList，因为唤醒完成后的脏标记和正式解析路径都以在线玩家为准。
 */
public final class RtsCrossDimensionStorageGameTests implements FabricGameTest {
  private static final String EMPTY_TEMPLATE = FabricGameTest.EMPTY_STRUCTURE;
  private static final BlockPos FAR_NETHER_POS = new BlockPos(96_008, 64, 96_008);

  public RtsCrossDimensionStorageGameTests() {}

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
  public static void crossDimensionWakeLoadsChunkWithoutBlocking(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
    helper.assertTrue(nether != null, "GameTest 服务端应创建下界");
    helper.assertTrue(!nether.hasChunkAt(FAR_NETHER_POS), "测试坐标必须从未加载区块开始");

    boolean readyImmediately =
        RtsCrossDimensionStorageWakeService.INSTANCE.ensureReady(player, nether, FAR_NETHER_POS);
    helper.assertTrue(!readyImmediately, "首次跨维度唤醒不得同步阻塞等待区块");

    helper.succeedWhen(
        () -> {
          helper.assertTrue(nether.hasChunkAt(FAR_NETHER_POS), "短期票据应最终加载目标区块");
          RtsCrossDimensionStorageWakeService.INSTANCE.releasePlayer(
              helper.getLevel().getServer(), player.getUUID());
          RtsServerGameTests.stopPlayers(player);
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
  public static void linkedChestResolvesFromExactTargetDimension(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    ServerLevel overworld = helper.getLevel();
    ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
    helper.assertTrue(nether != null, "GameTest 服务端应创建下界");

    BlockPos target = FAR_NETHER_POS.offset(32, 0, 32);
    nether.getChunk(new ChunkPos(target).x, new ChunkPos(target).z);
    nether.setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
    helper.assertTrue(nether.getBlockEntity(target) instanceof ChestBlockEntity, "下界目标应创建箱子方块实体");
    ChestBlockEntity targetChest = (ChestBlockEntity) nether.getBlockEntity(target);
    targetChest.setItem(0, new ItemStack(Items.DIAMOND, 7));
    targetChest.setChanged();

    // 同坐标在主世界放置不同内容，证明复合身份不是只看 BlockPos。
    overworld.getChunk(new ChunkPos(target).x, new ChunkPos(target).z);
    overworld.setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
    ChestBlockEntity decoyChest = (ChestBlockEntity) overworld.getBlockEntity(target);
    helper.assertTrue(decoyChest != null, "主世界应创建同坐标干扰箱子");
    decoyChest.setItem(0, new ItemStack(Items.DIRT, 3));
    decoyChest.setChanged();

    RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
    helper.assertTrue(session != null, "RTS 玩家应拥有服务端会话");
    LinkedStorageRef ref = new LinkedStorageRef(Level.NETHER, target.immutable());
    session.linkedStorageInfo.add(ref, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL, 0);

    List<LinkedHandler> handlers =
        RtsLinkedHandlerResolutionService.resolveLinkedHandlers(player, session);
    helper.assertTrue(handlers.size() == 1, "应只解析出一个跨维度储存端点");
    helper.assertTrue(handlers.getFirst().ref().equals(ref), "解析结果必须保留目标维度身份");
    helper.assertTrue(
        handlers.getFirst().handler().getStackInSlot(0).is(Items.DIAMOND),
        "处理器必须读取下界箱子，而不是主世界同坐标箱子");
    helper.assertTrue(
        handlers.getFirst().handler().getStackInSlot(0).getCount() == 7, "跨维度处理器应保留真实槽位数量");

    RtsCrossDimensionStorageWakeService.INSTANCE.releasePlayer(
        overworld.getServer(), player.getUUID());
    RtsServerGameTests.stopPlayers(player);
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
  public static void sameCoordinateWakeLeasesKeepDimensionIdentityAndReleaseTogether(
      GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    ServerLevel overworld = helper.getLevel();
    ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
    ServerLevel end = overworld.getServer().getLevel(Level.END);
    helper.assertTrue(nether != null && end != null, "GameTest 服务端应同时创建下界与末地");

    BlockPos sameCoordinates = FAR_NETHER_POS.offset(96, 0, 96);
    RtsCrossDimensionStorageWakeService.INSTANCE.ensureReady(player, nether, sameCoordinates);
    RtsCrossDimensionStorageWakeService.INSTANCE.ensureReady(player, end, sameCoordinates);
    helper.assertTrue(
        RtsCrossDimensionStorageWakeService.INSTANCE.activeLeaseCount(player.getUUID()) == 2,
        "同坐标的下界与末地端点必须占用两个维度化租约，不能互相覆盖");

    RtsCrossDimensionStorageWakeService.INSTANCE.releasePlayer(
        overworld.getServer(), player.getUUID());
    helper.assertTrue(
        RtsCrossDimensionStorageWakeService.INSTANCE.activeLeaseCount(player.getUUID()) == 0,
        "玩家离开时应一次释放其所有跨维度短租约");
    RtsServerGameTests.stopPlayers(player);
    helper.succeed();
  }
}
