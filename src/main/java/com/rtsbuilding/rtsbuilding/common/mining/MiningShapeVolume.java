package com.rtsbuilding.rtsbuilding.common.mining;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;

/** 形状预览提交前的包围盒体积校验；形状生成器本身保持原语义。 */
public final class MiningShapeVolume {
    private MiningShapeVolume() {
    }

    public static boolean fits(AreaShape shape, AreaShapeInput input, int maxVolume) {
        return fits(shape, input, new SelectionVolumeLimit(MiningLimits.clampVolume(maxVolume),
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    public static boolean fits(AreaShape shape, AreaShapeInput input, SelectionVolumeLimit limit) {
        if (shape == null || input == null || input.start() == null || input.end() == null || limit == null) return false;
        long dx = (long) input.end().getX() - input.start().getX();
        long dy = (long) input.end().getY() - input.start().getY();
        long dz = (long) input.end().getZ() - input.start().getZ();
        long height = Math.abs((long) input.heightOffset()) + 1L;
        if (shape == AreaShape.CIRCLE || shape == AreaShape.CYLINDER || shape == AreaShape.BALL) {
            double squared = dx * (double) dx + dz * (double) dz;
            if (shape == AreaShape.BALL) squared += dy * (double) dy;
            long diameter = Math.round(Math.sqrt(squared)) * 2L + 1L;
            return limit.fits(diameter, shape == AreaShape.BALL ? diameter : shape == AreaShape.CIRCLE ? 1L : height, diameter);
        }
        return limit.fits(Math.abs(dx) + 1L,
                shape == AreaShape.BOX || shape == AreaShape.WALL ? height : Math.abs(dy) + 1L,
                Math.abs(dz) + 1L);
    }
}
