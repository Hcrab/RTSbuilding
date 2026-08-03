package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.RtsBuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 使用真实 Mekanism Tools 物品执行 RTS 范围破坏的黑箱兼容测试。
 *
 * <p>该测试有独立命名空间，专用兼容测试运行必须同时安装 Mekanism 与 Mekanism Tools。
 * 生产逻辑仍只依赖原版 {@link ItemStack} 工具语义。</p>
 */
@GameTestHolder(MekanismToolsCompatibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismToolsCompatibilityGameTests {
    public static final String NAMESPACE = "rtsbuilding_mekanism_compat";
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private MekanismToolsCompatibilityGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void osmiumPaxelAreaDestroyMinesStoneAndUnderwaterStone(GameTestHelper helper) {
        Item osmiumPaxel = RtsBuiltInRegistries.ITEM.get(
                new ResourceLocation("mekanismtools", "osmium_paxel"));
        if (osmiumPaxel == Items.AIR) {
            helper.fail("Mekanism Tools 未安装，无法执行真实整合兼容测试");
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
                RtsBuiltInRegistries.ITEM.getKey(osmiumPaxel).toString(),
                tool,
                false);

        helper.succeedWhen(() -> {
            RtsServerGameTests.tickMiningPlayer(helper, player, 20);
            helper.assertTrue(!helper.getBlockState(dirtRel).is(Blocks.DIRT),
                    "Mekanism paxel should remove the dirt target");
            helper.assertTrue(!helper.getBlockState(stoneRel).is(Blocks.STONE),
                    "Mekanism paxel should remove the stone target");
            helper.assertBlockPresent(Blocks.WATER, underwaterStoneRel);
            Config.setSurvivalProgressionEnabled(false);
            RtsServerGameTests.stopPlayers(player);
        });
    }
}
