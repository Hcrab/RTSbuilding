package com.rtsbuilding.rtsbuilding.gametest;

import com.mojang.authlib.GameProfile;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsBindingService;
import com.rtsbuilding.rtsbuilding.server.service.RtsInteractionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsTransferService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * 服务端基础链路烟测。
 *
 * <p>这些测试只覆盖 GameTest server 能稳定验证的玩家可见链路：进入 RTS、远程右键箱子、
 * 链接存储、热栏存入和批量放置。客户端渲染、鼠标采样、真实第三方 GUI 动画由后续客户端/脚本矩阵覆盖。</p>
 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsServerGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private RtsServerGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void rtsEmptyHandRightClickOpensChest(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        ServerPlayer player = startRtsPlayer(helper);

        BlockPos chestAbs = helper.absolutePos(chestRel);
        Vec3 hit = Vec3.atCenterOf(chestAbs);
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 rayDir = hit.subtract(rayOrigin).normalize();

        RtsInteractionService.interactTarget(
                player,
                C2SRtsInteractPayload.NO_ENTITY,
                chestAbs,
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
                rayDir.x,
                rayDir.y,
                rayDir.z);

        helper.assertTrue(player.containerMenu instanceof ChestMenu,
                "RTS 空手右键箱子后应该打开箱子菜单");
        RtsCameraManager.stopIfActive(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void linkedStorageCountsChestContents(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        setChestStack(helper, chestRel, 0, new ItemStack(Items.STONE, 19));
        ServerPlayer player = startRtsPlayer(helper);

        BlockPos chestAbs = helper.absolutePos(chestRel);
        RtsBindingService.linkStorage(player, chestAbs, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);

        RtsStorageSession session = requireSession(helper, player);
        helper.assertTrue(session.linkedStorages.size() == 1,
                "RTS 链接箱子后会话里应该有 1 个链接存储");
        long stoneCount = RtsTransferService.countLinkedItemsMatching(player, stack -> stack.getItem() == Items.STONE);
        helper.assertTrue(stoneCount == 19L,
                "RTS 链接存储应该能统计箱子里的石头数量");
        RtsCameraManager.stopIfActive(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void storeHotbarSlotMovesItemsIntoLinkedChest(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        ServerPlayer player = startRtsPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 12));

        BlockPos chestAbs = helper.absolutePos(chestRel);
        RtsBindingService.linkStorage(player, chestAbs, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsBindingService.storeHotbarSlot(player, (byte) 0);

        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "RTS 存入快捷栏后，玩家快捷栏原槽位应该被清空");
        helper.assertTrue(countChestItem(helper, chestRel, Items.DIRT) == 12,
                "RTS 存入快捷栏应该把泥土放进链接箱子");
        RtsCameraManager.stopIfActive(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void placeBatchBuildsBlocksInWorld(GameTestHelper helper) {
        List<BlockPos> supportRel = List.of(
                new BlockPos(2, 1, 2),
                new BlockPos(3, 1, 2),
                new BlockPos(4, 1, 2));
        for (BlockPos pos : supportRel) {
            helper.setBlock(pos, Blocks.DIRT);
        }
        ServerPlayer player = startRtsPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.STONE, supportRel.size()));
        RtsStorageSession session = requireSession(helper, player);
        List<BlockPos> supportAbs = supportRel.stream()
                .map(helper::absolutePos)
                .toList();
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 firstHit = Vec3.atCenterOf(supportAbs.get(0)).add(0.0D, 0.5D, 0.0D);
        Vec3 rayDir = firstHit.subtract(rayOrigin).normalize();

        boolean queued = RtsPlacementBatch.enqueuePlaceBatch(
                player,
                session,
                supportAbs,
                Direction.UP,
                0.5D,
                1.0D,
                0.5D,
                (byte) 0,
                false,
                false,
                "minecraft:stone",
                new ItemStack(Items.STONE),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z,
                false,
                false,
                true);

        helper.assertTrue(queued, "RTS 批量放置任务应该能入队");
        helper.succeedWhen(() -> {
            RtsPlacementBatch.tickPlaceBatchJobs(player, session);
            for (BlockPos support : supportRel) {
                helper.assertBlockPresent(Blocks.STONE, support.above());
            }
            RtsCameraManager.stopIfActive(player);
        });
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper) {
        // Forge 1.20.1 的 GameTest mock server player 会触发无 channel 的登录握手。
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rts-gametest"));
        Vec3 playerPos = helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D));
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        RtsCameraManager.start(player);
        helper.assertTrue(RtsCameraManager.isActive(player), "GameTest 玩家应该能进入 RTS 模式");
        requireSession(helper, player);
        return player;
    }

    private static RtsStorageSession requireSession(GameTestHelper helper, ServerPlayer player) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        helper.assertTrue(session != null, "RTS 模式开启后应该存在服务端会话");
        return session;
    }

    private static void setChestStack(GameTestHelper helper, BlockPos chestRel, int slot, ItemStack stack) {
        ChestBlockEntity chest = requireChest(helper, chestRel);
        chest.setItem(slot, stack);
        chest.setChanged();
    }

    private static int countChestItem(GameTestHelper helper, BlockPos chestRel, Item item) {
        ChestBlockEntity chest = requireChest(helper, chestRel);
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ChestBlockEntity requireChest(GameTestHelper helper, BlockPos chestRel) {
        BlockEntity blockEntity = helper.getBlockEntity(chestRel);
        helper.assertTrue(blockEntity instanceof ChestBlockEntity,
                "测试场景需要一个可访问的箱子方块实体");
        return (ChestBlockEntity) blockEntity;
    }
}
