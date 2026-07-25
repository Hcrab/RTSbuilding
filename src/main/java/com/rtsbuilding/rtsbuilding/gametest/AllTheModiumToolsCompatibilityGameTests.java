package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * AllTheModium 工具的真实范围挖掘兼容门槛。
 *
 * <p>这个测试不在生产代码中加入 ATM 特判；它保证 RTS 始终使用物品栈自身声明的
 * Forge 工具能力与挖掘标签，因此附魔和第三方工具等级不会被替换成虚构的原版镐。</p>
 */
@GameTestHolder(AllTheModiumToolsCompatibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AllTheModiumToolsCompatibilityGameTests {
    public static final String NAMESPACE = "rtsbuilding_atm_compat";
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private AllTheModiumToolsCompatibilityGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void allthemodiumPickaxeAreaDestroyUsesRealToolStack(GameTestHelper helper) {
        Item pickaxe = BuiltInRegistries.ITEM.get(
                new ResourceLocation("allthemodium", "allthemodium_pickaxe"));
        if (pickaxe == Items.AIR) {
            helper.fail("AllTheModium 未安装，无法执行真实整合兼容测试");
            return;
        }

        Config.setSurvivalProgressionEnabled(false);
        BlockPos stoneRel = new BlockPos(4, 1, 4);
        helper.setBlock(stoneRel, Blocks.STONE);
        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        ItemStack tool = new ItemStack(pickaxe);
        player.getInventory().setItem(0, tool.copy());
        player.getInventory().selected = 0;

        helper.assertTrue(tool.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
                "AllTheModium pickaxe should advertise itself as a correct stone tool");
        RtsAPI.get().mining().areaDestroy(
                player,
                RtsServerGameTests.asApiPositions(helper, List.of(stoneRel)),
                (byte) 0,
                BuiltInRegistries.ITEM.getKey(pickaxe).toString(),
                tool,
                false);

        helper.succeedWhen(() -> {
            RtsServerGameTests.tickMiningPlayer(helper, player, 20);
            helper.assertBlockPresent(Blocks.AIR, stoneRel);
            Config.setSurvivalProgressionEnabled(false);
            RtsServerGameTests.stopPlayers(player);
        });
    }
}
