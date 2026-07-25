package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 真实第三方工具兼容测试。
 *
 * <p>该类使用独立命名空间，避免把 Mekanism 的客户端同步事件带进 500 多项基础
 * GameTest。运行时应只启用 {@value #NAMESPACE}，并在测试目录安装 Mekanism 与
 * Mekanism Tools；生产逻辑不直接链接第三方类。</p>
 */
@GameTestHolder(MekanismToolsCompatibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismToolsCompatibilityGameTests {

    public static final String NAMESPACE = "rtsbuilding_mekanism_compat";
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private MekanismToolsCompatibilityGameTests() {
    }

    /**
     * 新时代科技整合包回归：关闭生存平衡后，任务奖励同款锇制 Paxel 必须在同一范围任务中
     * 同时破坏泥土、石头与水下石头。
     */
    @GameTest(template = EMPTY_TEMPLATE, templateNamespace = NAMESPACE, timeoutTicks = 200)
    public static void osmiumPaxelAreaDestroyMinesStoneAndUnderwaterStone(GameTestHelper helper) {
        Item osmiumPaxel = BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("mekanismtools:osmium_paxel"));
        if (osmiumPaxel == Items.AIR) {
            helper.fail("Mekanism Tools 未安装，无法执行真实整合包兼容测试");
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
            // 上方水会在三个目标依次破坏后横向流动；这里验证原方块已被真正移除，
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
