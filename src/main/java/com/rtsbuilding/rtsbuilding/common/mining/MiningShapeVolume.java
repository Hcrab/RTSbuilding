package com.rtsbuilding.rtsbuilding.common.mining;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;

/**
 * 旧参数式区域挖掘在生成坐标之前核验实际形状包围盒，防止圆/球的中心半径语义扩大扫描。
 * 不修改形状算法，建造的旧偏移预算也不受影响；显式形状列表使用 MiningSelectionBounds。
 */
public final class MiningShapeVolume {
    private MiningShapeVolume() {
    }

    public static boolean fits(AreaShape shape, AreaShapeInput input, int maxVolume) {
        return fits(shape, input, new SelectionVolumeLimit(MiningLimits.clampVolume(maxVolume),
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    /** 同时按世界轴包围盒和体积校验形状，不能把轴长当成体积的隐含替代。 */
    public static boolean fits(AreaShape shape, AreaShapeInput input, SelectionVolumeLimit limit) {
        if (shape == null || input == null || input.start() == null || input.end() == null || limit == null) {
            return false;
        }
        long dx = (long) input.end().getX() - input.start().getX();
        long dy = (long) input.end().getY() - input.start().getY();
        long dz = (long) input.end().getZ() - input.start().getZ();
        long height = Math.abs((long) input.heightOffset()) + 1;
        if (shape == AreaShape.CIRCLE || shape == AreaShape.CYLINDER || shape == AreaShape.BALL) {
            double squared = dx * (double) dx + dz * (double) dz;
            if (shape == AreaShape.BALL) squared += dy * (double) dy;
            long diameter = Math.round(Math.sqrt(squared)) * 2 + 1;
            return limit.fits(diameter,
                    shape == AreaShape.BALL ? diameter : shape == AreaShape.CIRCLE ? 1 : height,
                    diameter);
        }
        return limit.fits(Math.abs(dx) + 1,
                shape == AreaShape.BOX || shape == AreaShape.WALL ? height : Math.abs(dy) + 1,
                Math.abs(dz) + 1);
    }
}
