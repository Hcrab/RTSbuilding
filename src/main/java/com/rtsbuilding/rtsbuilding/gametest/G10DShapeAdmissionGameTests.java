package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 验证普通建造的原始坐标经过真实 API、放置服务和 TaskStore 的准入结果。
 *
 * <p>测试独立生成圆盘，不引用客户端几何，也不放宽范围破坏的轴限制来掩盖
 * 普通建造回归。只检查已冻结任务，清理发生在下一个执行 tick 前，不放置整栋建筑。</p>
 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class G10DShapeAdmissionGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private G10DShapeAdmissionGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "g10d_shape")
    public static void legalSpan65CircleEntersDurableTask(GameTestHelper helper) {
        ConfigSnapshot previous = useDefaultShapeConfiguration();
        ServerPlayer player = null;
        try {
            player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);
            List<BlockPos> circle = circlePositions();
            // 放置服务要求目标区块已加载；这里只加载隔离测试世界，不改目标方块。
            circle.stream().map(helper::absolutePos).map(ChunkPos::new).distinct()
                    .forEach(chunk -> helper.getLevel().getChunk(chunk.x, chunk.z));
            enqueuePlacement(helper, player, circle);
            TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUUID());

            TaskSnapshot task = activePlacement(player);
            helper.assertTrue(task != null,
                    "dimension32/radius32 的 span65 圆盘必须进入真实 durable 放置任务");
            helper.assertTrue(circle.size() == task.totalUnits(),
                    "放置任务必须保留完整圆盘，不能按 dimension32 或范围轴限制裁切");
        } finally {
            cleanup(player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "g10d_shape")
    public static void overCapacityPlacementIsRejectedAsOneRequest(GameTestHelper helper) {
        ConfigSnapshot previous = useDefaultShapeConfiguration();
        ServerPlayer player = null;
        try {
            player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);
            BlockPos sentinel = new BlockPos(0, 2, 0);
            List<BlockPos> overLimit = new ArrayList<>(MiningLimits.MAX_VOLUME + 1);
            for (int index = 0; index <= MiningLimits.MAX_VOLUME; index++) {
                overLimit.add(new BlockPos(index % 65, 2 + index / (65 * 65), index / 65 % 65));
            }
            helper.setBlock(sentinel, Blocks.STONE);
            enqueuePlacement(helper, player, overLimit);
            TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUUID());

            helper.assertTrue(activePlacement(player) == null,
                    "超过262144目标必须整笔拒绝，不能只接纳前一段放置任务");
            helper.assertBlockPresent(Blocks.STONE, sentinel);
        } finally {
            cleanup(player, previous);
        }
        helper.succeed();
    }

    private static void enqueuePlacement(
            GameTestHelper helper, ServerPlayer player, List<BlockPos> targets) {
        Vec3 origin = player.getEyePosition();
        Vec3 direction = Vec3.atCenterOf(helper.absolutePos(targets.get(0)))
                .subtract(origin).normalize();
        RtsAPI.get().placement().enqueueBatch(
                player, RtsServerGameTests.asApiPositions(helper, targets), Direction.UP,
                0.5D, 1.0D, 0.5D, (byte) 0, false, false,
                "minecraft:dirt", new ItemStack(Items.DIRT),
                origin.x, origin.y, origin.z, direction.x, direction.y, direction.z);
    }

    private static List<BlockPos> circlePositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                int dx = x - 32;
                int dz = z - 32;
                if ((long) dx * dx + (long) dz * dz <= 32L * 32L) {
                    positions.add(new BlockPos(x, 2, z));
                }
            }
        }
        return List.copyOf(positions);
    }

    private static TaskSnapshot activePlacement(ServerPlayer player) {
        return TaskPersistenceRuntime.INSTANCE.coordinator().query().ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.PLACEMENT)
                .filter(snapshot -> !snapshot.state().terminal())
                .findFirst().orElse(null);
    }

    private static void cleanup(ServerPlayer player, ConfigSnapshot previous) {
        try {
            if (player != null) {
                try {
                    RtsWorkflowEngine.getInstance().cancelAll(player);
                } finally {
                    RtsServerGameTests.stopPlayers(player);
                }
            }
        } finally {
            previous.restore();
        }
    }

    private static ConfigSnapshot useDefaultShapeConfiguration() {
        ConfigSnapshot previous = new ConfigSnapshot(Config.maxShapeDimension(), Config.maxShapeRadius());
        Config.MAX_SHAPE_DIMENSION.set(32);
        Config.MAX_SHAPE_RADIUS.set(32);
        return previous;
    }

    private record ConfigSnapshot(int dimension, int radius) {
        private void restore() {
            Config.MAX_SHAPE_DIMENSION.set(dimension);
            Config.MAX_SHAPE_RADIUS.set(radius);
            Config.pollServerConfigRuntimeChange();
        }
    }
}
