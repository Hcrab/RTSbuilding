package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 区域形状生成器注册表，管理所有形状类型到对应生成器的映射。
 * <p>
 * 支持通过 {@link AreaShape} 枚举或 byte 序数（ordinal）查找生成器，
 * byte 方式用于兼容现有的网络协议（形状类型以 byte 序数传输）。
 * 每种形状类型映射到一个共享生成器实例，所有调用方共用。
 */
public final class ShapeGeneratorRegistry {

    /** 不可修改的生成器映射表 */
    private static final Map<AreaShape, AreaShapeGenerator> GENERATORS = Collections.unmodifiableMap(initGenerators());

    /**
     * 初始化所有形状生成器并注册到映射表中。
     */
    private static Map<AreaShape, AreaShapeGenerator> initGenerators() {
        Map<AreaShape, AreaShapeGenerator> map = new EnumMap<>(AreaShape.class);
        map.put(AreaShape.BLOCK, new SingleBlockGenerator());
        map.put(AreaShape.LINE, new LineShapeGenerator());
        map.put(AreaShape.SQUARE, new SquareShapeGenerator());
        map.put(AreaShape.WALL, new WallShapeGenerator());
        map.put(AreaShape.CIRCLE, new CircleShapeGenerator());
        map.put(AreaShape.BOX, new BoxShapeGenerator());
        return map;
    }

    private ShapeGeneratorRegistry() {
    }

    /**
     * 获取指定形状类型的生成器。
     *
     * @param shape 形状类型
     * @return 对应的生成器实例，未知类型返回单方块生成器作为默认值
     */
    public static AreaShapeGenerator getGenerator(AreaShape shape) {
        return GENERATORS.getOrDefault(shape, GENERATORS.get(AreaShape.BLOCK));
    }

    /**
     * 通过 byte 序数获取形状生成器（兼容网络协议）。
     *
     * @param shapeOrdinal 与 {@link AreaShape#ordinal()} 对应的形状序数
     * @return 对应的生成器实例
     */
    public static AreaShapeGenerator getGenerator(byte shapeOrdinal) {
        AreaShape[] values = AreaShape.values();
        if (shapeOrdinal < 0 || shapeOrdinal >= values.length) {
            return GENERATORS.get(AreaShape.BLOCK);
        }
        return getGenerator(values[shapeOrdinal]);
    }

    /**
     * 单方块生成器 —— 用于 {@link AreaShape#BLOCK} 类型。
     * <p>
     * 仅生成锚点位置的一个坐标，不执行任何形状扩展。
     */
    private static class SingleBlockGenerator extends AreaShapeGenerator {
        @Override
        public String getName() {
            return "block";
        }

        @Override
        public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
            return List.of(input.start());
        }
    }
}