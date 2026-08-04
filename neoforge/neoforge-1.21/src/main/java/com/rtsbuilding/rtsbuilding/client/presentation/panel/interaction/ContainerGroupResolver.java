package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 多方块容器 GUI 归一化解析器：
 * 将"多方块共用一个 GUI"的多个方块坐标归一化为同一个代表坐标（组内最小坐标），
 * 供容器标签去重与容器匹配使用。
 *
 * <p>内置规则为通用检测：识别方块状态中使用 "type" 属性（值为 {@link ChestType}
 * LEFT/RIGHT）模拟双格机制的方块，覆盖原版双箱（普通箱/陷阱箱）及复用该机制的
 * Mod 方块。</p>
 *
 * <p>可通过 {@link #registerRule(GroupRule)} 注册自定义规则，以兼容其他结构
 * （如三格及以上、按方块实体关联的多格机器）。</p>
 */
public final class ContainerGroupResolver {

    /**
     * 归一化规则：输入方块坐标，若属于多方块结构则返回组内代表坐标，否则原样返回。
     */
    @FunctionalInterface
    public interface GroupRule {
        BlockPos normalize(Level level, BlockPos pos);
    }

    private static final List<GroupRule> RULES = new ArrayList<>();

    static {
        registerRule(ContainerGroupResolver::normalizeChestPair);
    }

    private ContainerGroupResolver() {
    }

    /**
     * 注册自定义多方块归一化规则（供 Mod 兼容代码调用）。
     * 规则按注册顺序依次尝试，第一个"改变坐标"的规则生效。
     */
    public static void registerRule(GroupRule rule) {
        if (rule != null) RULES.add(rule);
    }

    /**
     * 归一化方块坐标：返回该方块所属容器组的代表坐标；非多方块方块原样返回。
     */
    public static BlockPos normalize(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return pos;
        Level level = mc.level;
        for (GroupRule rule : RULES) {
            BlockPos normalized = rule.normalize(level, pos);
            if (!normalized.equals(pos)) return normalized;
        }
        return pos;
    }

    // ==================== 内置规则 ====================

    /**
     * 双格容器归一化：识别"两格共享同一个 GUI"的方块对。
     *
     * <p>通用检测逻辑：方块状态中存在名为 "type" 且值为 {@link ChestType}
     * 的属性（LEFT/RIGHT 表示双格结构），并通过水平朝向推导相邻另一半；
     * 邻居同样是双格容器时，取两格中坐标较小者作为代表坐标。</p>
     */
    private static BlockPos normalizeChestPair(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ChestType type = chestTypeOf(state);
        if (type == null || type == ChestType.SINGLE) return pos;

        Direction dir = connectedDirection(state, type);
        if (dir == null) return pos;

        BlockPos other = pos.relative(dir);
        BlockState otherState = level.getBlockState(other);
        ChestType otherType = chestTypeOf(otherState);
        if (otherType == null || otherType == ChestType.SINGLE) return pos;

        // 双向校验：另一半必须是配对类型（LEFT↔RIGHT），且其连接方向指回本格。
        // 否则（两个同侧箱子、朝向错位的残缺结构）视为独立容器，不参与归一化。
        if (otherType != type.getOpposite()) return pos;
        Direction otherDir = connectedDirection(otherState, otherType);
        if (otherDir == null || !otherDir.getOpposite().equals(dir)) return pos;

        return pos.compareTo(other) < 0 ? pos : other;
    }

    /**
     * 从方块状态中提取双格属性值：名为 "type" 且值为 {@link ChestType} 的属性。
     */
    @Nullable
    private static ChestType chestTypeOf(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals("type") && state.getValue(p) instanceof ChestType t) {
                return t;
            }
        }
        return null;
    }

    /**
     * 计算指向另一半的方向：LEFT 侧为朝向顺时针方向，RIGHT 侧为逆时针方向
     * （与原版 {@code ChestBlock} 语义一致）；无水平朝向属性时返回 {@code null}。
     */
    @Nullable
    private static Direction connectedDirection(BlockState state, ChestType type) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }
}
