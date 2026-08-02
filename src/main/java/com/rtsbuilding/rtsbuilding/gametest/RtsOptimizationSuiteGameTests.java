package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingBoxSnapshot;
import com.rtsbuilding.rtsbuilding.server.culling.RtsCullingPersistence;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * 在本地 Fabric 优化全家桶中验证范围剔除的服务端持久化边界。
 *
 * <p>这里不冒充客户端渲染测试：Sodium 网格注入由客户端 GameTest 负责。本类只确认
 * 服务端优化核心确实加载，并在 C2ME/Lithium 等修改 tick 与区块路径后，范围剔除数据
 * 仍按 128 个盒子和 4096 个显式可见方块的硬边界稳定往返。</p>
 */
public final class RtsOptimizationSuiteGameTests implements FabricGameTest {
    private static final List<String> SERVER_OPTIMIZATION_COMPONENTS = List.of(
            "lithium",
            "ferritecore",
            "modernfix",
            "c2me",
            "c2me-base",
            "c2me-rewrites-chunk-system",
            "c2me-threading-lighting",
            "noisium",
            "krypton",
            "com_velocitypowered_velocity-native");

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "optimization_suite")
    public void optimizationSuiteKeepsRangeCullingPersistenceBounded(GameTestHelper helper) {
        if (!Boolean.getBoolean("rtsbuilding.optimizationSuite")) {
            helper.succeed();
            return;
        }

        FabricLoader loader = FabricLoader.getInstance();
        for (String modId : SERVER_OPTIMIZATION_COMPONENTS) {
            helper.assertTrue(loader.isModLoaded(modId),
                    "Optimization GameTest did not load required server component: " + modId);
        }

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);
        try {
            List<RtsCullingBoxSnapshot> boxes = new ArrayList<>();
            for (int index = 0; index < 160; index++) {
                BlockPos min = new BlockPos(index, 64, index);
                boxes.add(new RtsCullingBoxSnapshot(min, min.offset(3, 3, 3)));
            }
            List<BlockPos> revealed = new ArrayList<>();
            for (int index = 0; index < 4200; index++) {
                revealed.add(new BlockPos(index, 65, -index));
            }

            RtsCullingPersistence.save(player, boxes, revealed);
            RtsCullingPersistence.State restored = RtsCullingPersistence.load(player);
            helper.assertTrue(restored.boxes().size() == 128,
                    "Range-culling box persistence exceeded or lost its 128-entry boundary");
            helper.assertTrue(restored.revealed().size() == 4096,
                    "Range-culling revealed-block persistence exceeded or lost its 4096-entry boundary");
            helper.assertTrue(restored.boxes().get(127).min().equals(new BlockPos(127, 64, 127)),
                    "Range-culling box order drifted under the optimization suite");
            helper.assertTrue(restored.revealed().get(4095).equals(new BlockPos(4095, 65, -4095)),
                    "Range-culling revealed-block order drifted under the optimization suite");
            helper.succeed();
        } finally {
            RtsServerGameTests.stopPlayers(player);
        }
    }
}
