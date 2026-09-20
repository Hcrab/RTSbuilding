package com.rtsbuilding.rtsbuilding.common.destruction;

import com.rtsbuilding.rtsbuilding.common.mining.SelectionVolumeLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 树木真实连通组必须按配置整体准入，不得先截断再提交。 */
class G10DRTreeAdmissionTest {
    private Set<TagKey<Block>> originalLogTags;

    @BeforeEach
    void bindRealLogTag() throws ReflectiveOperationException {
        // 纯 JVM bootstrap 不加载数据包；仅补真实木头 holder 的标签，结束后恢复。
        originalLogTags = new HashSet<>(Blocks.OAK_LOG.builtInRegistryHolder().tags().toList());
        Set<TagKey<Block>> tags = new HashSet<>(originalLogTags);
        tags.add(BlockTags.LOGS);
        bindLogTags(tags);
    }

    @AfterEach
    void restoreLogTags() throws ReflectiveOperationException {
        if (originalLogTags != null) {
            bindLogTags(originalLogTags);
        }
    }

    private static void bindLogTags(Collection<TagKey<Block>> tags) throws ReflectiveOperationException {
        // 两版 holder 可见性不同；这是测试数据包夹具，不修改生产注册表协议。
        var holder = Blocks.OAK_LOG.builtInRegistryHolder();
        var bind = holder.getClass().getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        bind.invoke(holder, tags);
    }

    @Test
    void configured9000AdmitsComplete8193ConnectedGroup() {
        Map<BlockPos, BlockState> blocks = connectedLogs(8_193);
        LevelReader level = level(blocks);
        RtsConvenienceDestroySettings settings =
                new RtsConvenienceDestroySettings(1, 1, 1, 0, 0, 9_000);

        RtsConvenienceDestroyPlanner.Plan plan = RtsConvenienceDestroyPlanner.plan(
                level, RtsConvenienceDestroyMode.TREE_FELL, BlockPos.ZERO, Direction.UP,
                settings, SelectionVolumeLimit.defaults(), 9_000);

        assertEquals(RtsConvenienceDestroyPlanner.ResultCode.READY, plan.code());
        assertEquals(8_193, plan.targets().size());
        assertEquals(8_193, plan.discoveredTargets());
        assertEquals(blocks.keySet(), Set.copyOf(plan.targets()));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.targets().add(BlockPos.ZERO));
    }

    @Test
    void configured8192RejectsWhole8193GroupWithoutTruncation() {
        LevelReader level = level(connectedLogs(8_193));
        RtsConvenienceDestroySettings settings =
                new RtsConvenienceDestroySettings(1, 1, 1, 0, 0, 8_192);

        RtsConvenienceDestroyPlanner.Plan plan = RtsConvenienceDestroyPlanner.plan(
                level, RtsConvenienceDestroyMode.TREE_FELL, BlockPos.ZERO, Direction.UP,
                settings, SelectionVolumeLimit.defaults(), 8_192);

        assertEquals(RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT, plan.code());
        assertTrue(plan.targets().isEmpty());
        assertEquals(8_193, plan.discoveredTargets());
        assertEquals(8_192, com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView
                .defaults().maxTreeBlocks());
    }

    private static Map<BlockPos, BlockState> connectedLogs(int count) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        for (int x = 0; x < count; x++) {
            blocks.put(new BlockPos(x, 0, 0), log);
        }
        return blocks;
    }

    private static LevelReader level(Map<BlockPos, BlockState> blocks) {
        return (LevelReader) Proxy.newProxyInstance(
                LevelReader.class.getClassLoader(),
                new Class<?>[] {LevelReader.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getBlockState" -> {
                            BlockPos pos = args == null || args.length == 0
                                    ? BlockPos.ZERO : (BlockPos) args[0];
                            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                        }
                        case "hasChunk" -> {
                            return true;
                        }
                        case "getMinBuildHeight" -> {
                            return 0;
                        }
                        case "getMaxBuildHeight" -> {
                            return 256;
                        }
                        default -> {
                            Class<?> type = method.getReturnType();
                            if (!type.isPrimitive()) {
                                return null;
                            }
                            if (type == boolean.class) return false;
                            if (type == long.class) return 0L;
                            if (type == double.class) return 0.0D;
                            if (type == float.class) return 0.0F;
                            if (type == short.class) return (short) 0;
                            if (type == byte.class) return (byte) 0;
                            if (type == char.class) return (char) 0;
                            return 0;
                        }
                    }
                });
    }
}
