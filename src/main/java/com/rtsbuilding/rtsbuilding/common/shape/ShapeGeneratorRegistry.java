package com.rtsbuilding.rtsbuilding.common.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for all area shape generators.
 * <p>
 * Provides lookup by ordinal (byte) to support the existing network protocol
 * that transmits shape type as a byte ordinal matching {@link AreaShape}.
 * <p>
 * Each shape type maps to exactly one generator instance, shared across all
 * callers.
 */
public final class ShapeGeneratorRegistry {

    private static final Map<AreaShape, AreaShapeGenerator> GENERATORS = new EnumMap<>(AreaShape.class);

    static {
        GENERATORS.put(AreaShape.BLOCK, new SingleBlockGenerator());
        GENERATORS.put(AreaShape.LINE, new LineShapeGenerator());
        GENERATORS.put(AreaShape.SQUARE, new SquareShapeGenerator());
        GENERATORS.put(AreaShape.WALL, new WallShapeGenerator());
        GENERATORS.put(AreaShape.CIRCLE, new CircleShapeGenerator());
        GENERATORS.put(AreaShape.BOX, new BoxShapeGenerator());
    }

    private ShapeGeneratorRegistry() {
    }

    /**
     * Returns the generator for the given shape type.
     *
     * @param shape the shape type
     * @return the generator, or a no-op default if unknown
     */
    public static AreaShapeGenerator getGenerator(AreaShape shape) {
        return GENERATORS.getOrDefault(shape, GENERATORS.get(AreaShape.BLOCK));
    }

    /**
     * Convenience: returns the generator for a shape ordinal (byte).
     *
     * @param shapeOrdinal shape ordinal matching {@link AreaShape#ordinal()}
     * @return the generator
     */
    public static AreaShapeGenerator getGenerator(byte shapeOrdinal) {
        AreaShape[] values = AreaShape.values();
        if (shapeOrdinal < 0 || shapeOrdinal >= values.length) {
            return GENERATORS.get(AreaShape.BLOCK);
        }
        return getGenerator(values[shapeOrdinal]);
    }

    /**
     * A special single-block generator used for AreaShape.BLOCK.
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
