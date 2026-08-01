package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 真实第三方工具兼容测试。
 *
 * <p>基础开发环境没有 Mekanism Tools 时，该测试会明确记录跳过并立即结束；当整合包运行目录安装了
 * Mekanism 与 Mekanism Tools 后，同一个 Fabric GameTest 会自动使用真实锇制 Paxel 验证生产逻辑。
 * 生产代码本身不直接链接任何第三方类。
 */
public final class MekanismToolsCompatibilityGameTests implements FabricGameTest {
    public static final String NAMESPACE = "rtsbuilding_mekanism_compat";
    private static final String EMPTY_TEMPLATE = FabricGameTest.EMPTY_STRUCTURE;

    public MekanismToolsCompatibilityGameTests() {
    }

    /**
     * 关闭生存进度限制后，任务奖励同款 Paxel 必须在一次范围任务中同时破坏泥土、石头和水下石头。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void osmiumPaxelAreaDestroyMinesStoneAndUnderwaterStone(GameTestHelper helper) {
        Item osmiumPaxel = BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("mekanismtools:osmium_paxel"));
        if (osmiumPaxel == Items.AIR) {
            RtsbuildingMod.LOGGER.info("跳过 Mekanism Tools GameTest：当前运行环境未安装 Mekanism Tools");
            helper.succeed();
            return;
        }

        Config.setSurvivalProgressionEnabled(false);
        BlockPos dirtRel = new BlockPos(3, 1, 3);
        BlockPos stoneRel = new BlockPos(4, 1, 3);
        BlockPos underwaterStoneRel = new BlockPos(5, 1, 3);
        helper.setBlock(dirtRel, Blocks.DIRT);
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(underwaterStoneRel, Blocks.STONE);
        helper.setBlock(underwaterStoneRel.above(), Blocks.WATER);
        helper.setBlock(underwaterStoneRel.above(2), Blocks.WATER);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        ItemStack tool = new ItemStack(osmiumPaxel);
        player.getInventory().setItem(0, tool.copy());
        player.getInventory().selected = 0;

        helper.assertTrue(tool.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
                "Mekanism osmium paxel should advertise itself as a correct stone tool");
        RtsAPI.get().mining().areaDestroy(
                player,
                RtsServerGameTests.asApiPositions(
                        helper, List.of(dirtRel, stoneRel, underwaterStoneRel)),
                (byte) 0,
                BuiltInRegistries.ITEM.getKey(osmiumPaxel).toString(),
                tool,
                false);

        helper.succeedWhen(() -> {
            // 上方水会在三个目标依次破坏后横向流动，因此验证原方块确实被移除，
            // 不把“随后变成空气还是水”误当成工具兼容结果。
            helper.assertTrue(!helper.getBlockState(dirtRel).is(Blocks.DIRT),
                    "Mekanism paxel should remove the dirt target");
            helper.assertTrue(!helper.getBlockState(stoneRel).is(Blocks.STONE),
                    "Mekanism paxel should remove the stone target");
            helper.assertBlockPresent(Blocks.WATER, underwaterStoneRel);
            helper.assertTrue(!RtsServerGameTests.hasActiveTask(player, TaskType.DESTRUCTION),
                    "Mekanism paxel area destroy should finish without a durable task");
            Config.setSurvivalProgressionEnabled(false);
            RtsServerGameTests.stopPlayers(player);
        });
    }
}
