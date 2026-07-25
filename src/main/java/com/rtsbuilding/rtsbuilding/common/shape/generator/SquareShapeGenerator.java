package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * 姝ｆ柟褰?鐭╁舰锛?D 骞抽潰锛夊舰鐘剁敓鎴愬櫒銆?
 * <p>
 * 鍦ㄧ偣鍑婚潰鎵€纭畾鐨勫钩闈笂鐢熸垚涓€涓墎骞崇煩褰㈠尯鍩燂紝
 * 浠呮湁涓€灞傚帤搴︼紝鏃犻珮搴︽墿灞曘€傛敮鎸?FILL锛堝疄蹇冿級鍜?HOLLOW锛堢┖蹇冿級妯″紡銆?
 */
public class SquareShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "square";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 鏍规嵁鐐瑰嚮闈㈢‘瀹氬钩闈笂鐨勪袱涓酱鍚?
        Direction face = input.clickedFace();
        Direction[] axes = resolvePlaneAxes(face);

        // 璁＄畻鍋忕Щ骞堕檺鍒惰寖鍥?
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dy = clampOffset(input.end().getY() - input.start().getY());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());

        // 灏嗗亸绉绘姇褰卞埌涓や釜骞抽潰杞翠笂
        int aOffset = clampOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampOffset(dotDelta(dx, dy, dz, axes[1]));

        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);

        // 鐢熸垚骞抽潰涓婄殑鎵€鏈夋柟鍧椾綅缃?
        List<BlockPos> all = buildPlanePositions(input.start(), axes[0], axes[1], minA, maxA, minB, maxB);

        if (fillMode == ShapeFillMode.FILL || all.isEmpty()) {
            return all;
        }

        // HOLLOW / SKELETON锛氳皟鐢ㄩ€氱敤杈圭晫杩囨护鍣?
        int minY = Math.min(0, clampOffset(input.end().getY() - input.start().getY()));
        int maxY = Math.max(0, clampOffset(input.end().getY() - input.start().getY()));
        return filterBoundary(all, minY, maxY);
    }
}
