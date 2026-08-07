package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 远程菜单服务端生命期回归测试。
 *
 * <p>这些测试只验证服务端的公共关门闸门，不假装替代真实客户端 Screen 测试。重点是确保 任意第三方菜单自己的 {@code stillValid()} 即使始终失败，也不会在被 RTS
 * 精确跟踪时闪退； 同时证明普通菜单、其他玩家和复用 containerId 的新菜单不会被误放行。
 */
public final class RtsRemoteMenuGameTests implements FabricGameTest {
  private static final String EMPTY_TEMPLATE = FabricGameTest.EMPTY_STRUCTURE;

  public RtsRemoteMenuGameTests() {}

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void farRtsChestSurvivesServerDistanceValidation(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    openFarBlockThroughProductionRts(helper, player, new BlockPos(13, 1, 3), Blocks.CHEST);
    AbstractContainerMenu opened = player.containerMenu;
    helper.assertTrue(opened instanceof ChestMenu, "RTS 生产交互链应当打开远距离箱子菜单");

    helper.runAfterDelay(
        20,
        () -> {
          helper.assertTrue(player.containerMenu == opened, "远距离箱子菜单不应被服务端 stillValid 关闭");
          RtsServerGameTests.stopPlayers(player);
          helper.succeed();
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void farRtsEnchantingTableSurvivesServerDistanceValidation(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    openFarBlockThroughProductionRts(
        helper, player, new BlockPos(13, 1, 3), Blocks.ENCHANTING_TABLE);
    AbstractContainerMenu opened = player.containerMenu;
    helper.assertTrue(opened instanceof EnchantmentMenu, "RTS 生产交互链应当打开远距离附魔台菜单");

    helper.runAfterDelay(
        20,
        () -> {
          helper.assertTrue(player.containerMenu == opened, "远距离附魔台菜单不应被服务端 stillValid 关闭");
          RtsServerGameTests.stopPlayers(player);
          helper.succeed();
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void clientEmptyHandPlacementPathOpensAndKeepsFarChestMenu(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    BlockPos target = helper.absolutePos(new BlockPos(13, 1, 3));
    helper.getLevel().setBlockAndUpdate(target, Blocks.CHEST.defaultBlockState());
    Vec3 hit = Vec3.atCenterOf(target);
    Vec3 rayOrigin = player.getEyePosition();
    Vec3 rayDirection = hit.subtract(rayOrigin).normalize();

    // 客户端普通 RTS 空手右键走 C2SRtsPlacePayload(forceEmptyHand=true)，不能只测较短的 Interaction API 路径。
    ServiceRegistry.getInstance()
        .placement()
        .placeSelected(
            player,
            target,
            Direction.UP,
            hit.x,
            hit.y,
            hit.z,
            (byte) 0,
            "",
            false,
            false,
            "",
            ItemStack.EMPTY,
            rayOrigin.x,
            rayOrigin.y,
            rayOrigin.z,
            rayDirection.x,
            rayDirection.y,
            rayDirection.z,
            false,
            true);

    AbstractContainerMenu opened = player.containerMenu;
    helper.assertTrue(opened instanceof ChestMenu, "客户端空手右键的完整生产路径应立即打开远距离箱子菜单");
    helper.runAfterDelay(
        20,
        () -> {
          helper.assertTrue(player.containerMenu == opened, "完整客户端路径打开的箱子菜单也必须通过后续 stillValid 检查");
          RtsServerGameTests.stopPlayers(player);
          helper.succeed();
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void arbitraryInvalidModMenuSurvivesWhenTracked(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    AlwaysInvalidMenu opened = new AlwaysInvalidMenu(91);
    installMenu(player, opened);
    RtsStorageSession session = requireSession(player);
    RtsRemoteMenuService.markRemoteMenuOpen(
        player, session, opened, helper.absolutePos(new BlockPos(13, 1, 3)));
    helper.assertTrue(!opened.stillValid(player), "测试夹具必须模拟一个拥有自定义、始终失败 stillValid 的第三方菜单");

    helper.runAfterDelay(
        20,
        () -> {
          helper.assertTrue(player.containerMenu == opened, "被精确跟踪的任意第三方菜单应绕过服务端公共关门闸门");
          RtsServerGameTests.stopPlayers(player);
          helper.succeed();
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
  public static void trackedRemoteMenuRejectsThirdPartyServerClose(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    AlwaysInvalidMenu opened = new AlwaysInvalidMenu(96);
    installMenu(player, opened);
    RtsStorageSession session = requireSession(player);
    RtsRemoteMenuService.markRemoteMenuOpen(
        player, session, opened, helper.absolutePos(new BlockPos(13, 1, 3)));

    // 模拟 Create、Ender IO 等菜单在自己的同步阶段直接请求服务端关窗。
    player.closeContainer();
    helper.assertTrue(player.containerMenu == opened, "被 RTS 跟踪的第三方菜单不应被模组自己的服务端主动关窗立即关闭");

    // RTS 明确结束远程会话时仍必须能关闭，证明兜底不会制造无法退出的 GUI。
    RtsRemoteMenuService.closeTracked(player, session);
    helper.assertTrue(player.containerMenu == player.inventoryMenu, "撤销远程跟踪后，明确的服务端关窗仍应恢复原版行为");
    RtsServerGameTests.stopPlayers(player);
    helper.succeed();
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void untrackedInvalidMenuKeepsVanillaCloseBehavior(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    AlwaysInvalidMenu untracked = new AlwaysInvalidMenu(92);
    installMenu(player, untracked);

    helper.runAfterDelay(
        5,
        () -> {
          helper.assertTrue(
              player.containerMenu == player.inventoryMenu, "未经过 RTS 打开的菜单仍应由原版 stillValid 正常关闭");
          RtsServerGameTests.stopPlayers(player);
          helper.succeed();
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void reusedContainerIdDoesNotKeepDifferentMenuOpen(GameTestHelper helper) {
    ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    AlwaysInvalidMenu tracked = new AlwaysInvalidMenu(93);
    installMenu(player, tracked);
    RtsRemoteMenuService.markRemoteMenuOpen(
        player, requireSession(player), tracked, helper.absolutePos(new BlockPos(13, 1, 3)));

    // 模拟旧菜单关闭后，另一个菜单恰好复用了相同 ID；对象身份不同，绝不能继承豁免。
    AlwaysInvalidMenu replacement = new AlwaysInvalidMenu(93);
    installMenu(player, replacement);
    helper.runAfterDelay(
        5,
        () -> {
          helper.assertTrue(
              player.containerMenu == player.inventoryMenu, "复用 containerId 的新菜单不应继承旧 RTS 菜单的豁免");
          RtsServerGameTests.stopPlayers(player);
          helper.succeed();
        });
  }

  @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
  public static void remoteMenuTrackingIsIsolatedPerPlayer(GameTestHelper helper) {
    ServerPlayer trackedPlayer = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    ServerPlayer localPlayer = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
    AlwaysInvalidMenu tracked = new AlwaysInvalidMenu(94);
    AlwaysInvalidMenu local = new AlwaysInvalidMenu(94);
    installMenu(trackedPlayer, tracked);
    installMenu(localPlayer, local);
    RtsRemoteMenuService.markRemoteMenuOpen(
        trackedPlayer,
        requireSession(trackedPlayer),
        tracked,
        helper.absolutePos(new BlockPos(13, 1, 3)));

    helper.runAfterDelay(
        10,
        () -> {
          helper.assertTrue(trackedPlayer.containerMenu == tracked, "被跟踪玩家的远程菜单应保持打开");
          helper.assertTrue(
              localPlayer.containerMenu == localPlayer.inventoryMenu, "另一个玩家的同 ID 菜单不应获得远程豁免");
          RtsServerGameTests.stopPlayers(trackedPlayer);
          RtsServerGameTests.stopPlayers(localPlayer);
          helper.succeed();
        });
  }

  private static void openFarBlockThroughProductionRts(
      GameTestHelper helper, ServerPlayer player, BlockPos targetRelative, Block block) {
    BlockPos target = helper.absolutePos(targetRelative);
    helper.getLevel().setBlockAndUpdate(target, block.defaultBlockState());
    Vec3 hit = Vec3.atCenterOf(target);
    Vec3 rayOrigin = player.getEyePosition();
    Vec3 rayDirection = hit.subtract(rayOrigin).normalize();
    RtsAPI.get()
        .interaction()
        .interactTarget(
            player,
            C2SRtsInteractPayload.NO_ENTITY,
            target,
            Direction.UP,
            hit.x,
            hit.y,
            hit.z,
            C2SRtsInteractPayload.SOURCE_EMPTY_HAND,
            (byte) 0,
            "",
            rayOrigin.x,
            rayOrigin.y,
            rayOrigin.z,
            rayDirection.x,
            rayDirection.y,
            rayDirection.z);
  }

  private static void installMenu(ServerPlayer player, AbstractContainerMenu menu) {
    player.containerMenu = menu;
  }

  private static RtsStorageSession requireSession(ServerPlayer player) {
    RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
    if (session == null) {
      throw new IllegalStateException("RTS GameTest player should own a session");
    }
    return session;
  }

  /** 模拟完全不依赖 Container/ContainerLevelAccess 的第三方自定义距离校验。 */
  private static final class AlwaysInvalidMenu extends AbstractContainerMenu {
    private AlwaysInvalidMenu(int containerId) {
      super(null, containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
      return false;
    }
  }
}
