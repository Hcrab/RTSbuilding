package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.common.shape.generator.AreaShapeGenerator;
import com.rtsbuilding.rtsbuilding.common.shape.generator.ShapeGeneratorRegistry;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域操作执行器 —— 基于形状的区域建造和破坏操作中心。
 * <p>
 * 这个无状态工具类编排完整的流水线：
 * <ol>
 *   <li>基于形状的位置生成</li>
 *   <li>逐位置验证（世界权限、可破坏性、可替换性）</li>
 *   <li>物品提取</li>
 *   <li>通过 tick 处理器或直接方块操作在服务端执行</li>
 *   <li>操作记录以便撤销</li>
 * </ol>
 * 所有状态由调用方的 Session 管理。
 */
public final class AreaOperationExecutor {

    private AreaOperationExecutor() {
    }

    // ======================================================================
    // 区域位置生成 —— 为任何操作批量生成方块位置
    // ======================================================================

    /**
     * 为区域操作（放置或破坏）生成目标位置。
     * <p>
     * 基于形状的位置生成与放置或破坏无关——调用方决定如何操作这些位置。
     *
     * @param shape    形状类型
     * @param start    锚点位置
     * @param end      第二个角点位置
     * @param height   3D 形状的高度偏移
     * @param face     点击/放置面
     * @param fillMode 填充策略
     * @return 绝对世界坐标列表
     */
    public static List<BlockPos> generatePositions(AreaShape shape, BlockPos start, BlockPos end,
                                                   int height, Direction face, ShapeFillMode fillMode) {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(shape);
        AreaShapeInput input = AreaShapeInput.of(start, end, height, face, face);
        return generator.generatePositions(input, fillMode);
    }

    // ======================================================================
    // 区域破坏 —— 批量在许多位置破坏方块
    // ======================================================================

    /**
     * 为区域破坏操作生成目标位置。
     * <p>
     * 语义上与 {@link #generatePositions} 相同——位置列表是一样的，
     * 调用方决定是放置还是破坏。
     *
     * @param shape    形状类型
     * @param start    锚点位置
     * @param end      第二个角点位置
     * @param height   3D 形状的高度偏移
     * @param face     点击面
     * @param fillMode 填充策略
     * @return 尝试破坏的目标位置列表
     */
    public static List<BlockPos> generateDestroyPositions(AreaShape shape, BlockPos start, BlockPos end,
                                                           int height, Direction face, ShapeFillMode fillMode) {
        return generatePositions(shape, start, end, height, face, fillMode);
    }

    /**
     * 过滤破坏目标列表，只保留可有效破坏的位置。
     * <p>
     * 条件：非空气、在世界交互范围内、且具有有效的破坏速度。
     *
     * @param level   服务端世界
     * @param targets 原始位置列表
     * @param player  执行操作的玩家
     * @return 过滤后可破坏的位置列表
     */
    public static List<BlockPos> filterBreakableTargets(ServerLevel level, List<BlockPos> targets, ServerPlayer player) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (pos == null) continue;
            if (!level.mayInteract(player, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;
            valid.add(pos.immutable());
        }
        return valid;
    }

    /**
     * 过滤放置目标列表，只保留可有效放置的位置。
     * <p>
     * 条件：在建筑高度内、可替换、世界可交互。
     *
     * @param level   服务端世界
     * @param targets 原始位置列表
     * @param state   要放置的方块状态
     * @param player  执行操作的玩家
     * @return 过滤后可放置的位置列表
     */
    public static List<BlockPos> filterPlaceableTargets(ServerLevel level, List<BlockPos> targets,
                                                         BlockState state, ServerPlayer player) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (pos == null) continue;
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;
            if (!level.mayInteract(player, pos)) continue;
            if (!state.canSurvive(level, pos)) continue;
            if (!level.getBlockState(pos).canBeReplaced()) continue;
            valid.add(pos.immutable());
        }
        return valid;
    }

    /**
     * 验证单个位置是否是有效的破坏目标。
     *
     * @param level  服务端世界
     * @param pos    目标方块位置
     * @param player 玩家
     * @return true 如果该方块可被破坏
     */
    public static boolean isValidDestroyTarget(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return AreaShapeGenerator.validateDestroyPosition(level, pos, player);
    }

    /**
     * 验证单个位置是否是有效的放置目标。
     *
     * @param level  服务端世界
     * @param pos    目标位置
     * @param state  要放置的方块状态
     * @param player 玩家
     * @return true 如果此处可以放置方块
     */
    public static boolean isValidPlacementTarget(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        return AreaShapeGenerator.validatePlacementPosition(level, pos, state, player);
    }

    /**
     * 扫描 3D 包围盒并返回其中所有可破坏的方块位置。
     * <p>
     * 应用形状过滤器，相当于 GadgetUtils.getDestructionArea()。
     *
     * @param level         服务端世界
     * @param minX, maxX    包含的 X 边界
     * @param minY, maxY    包含的 Y 边界
     * @param minZ, maxZ    包含的 Z 边界
     * @param player        玩家
     * @param shapeOrdinal  形状类型序数
     * @param fillOrdinal   填充模式序数
     * @return 边界内可破坏的方块位置列表
     */
    public static List<BlockPos> scanAreaMineTargets(ServerLevel level,
                                                      int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                                      ServerPlayer player,
                                                      byte shapeOrdinal, byte fillOrdinal) {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(shapeOrdinal);
        ShapeFillMode fillMode = fillOrdinal <= 0 ? ShapeFillMode.FILL : ShapeFillMode.values()[Math.min(fillOrdinal, ShapeFillMode.values().length - 1)];

        AreaShapeInput input = new AreaShapeInput(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ),
                maxY - minY,
                Direction.DOWN,
                Direction.DOWN);

        List<BlockPos> candidates = generator.generatePositions(input, fillMode);
        return filterBreakableTargets(level, candidates, player);
    }
}