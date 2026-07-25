package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 澧欎綋锛堝瀭鐩存媺浼哥嚎锛夊舰鐘剁敓鎴愬櫒銆?
 * <p>
 * 鍏堢敓鎴愬熀绾匡紙鍦?XZ 骞抽潰涓婁粠璧风偣鍒扮粓鐐圭殑鐩寸嚎锛夛紝
 * 鐒跺悗灏嗗熀绾挎部 Y 杞存寜楂樺害鍋忕Щ閲忓瀭鐩存媺浼革紝褰㈡垚涓€闈㈠銆?
 * 鏀寔 FILL锛堝疄蹇冨锛夊拰 HOLLOW锛堣竟妗嗭級涓ょ妯″紡銆?
 */
public class WallShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "wall";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 璁＄畻 XZ 骞抽潰鍜?Y 杞翠笂鐨勫亸绉婚噺骞堕檺鍒惰寖鍥?
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());
        int dy = clampOffset(input.heightOffset());

        // 鐢熸垚鍩虹嚎锛堣捣鐐?鈫?缁堢偣鍦?XZ 骞抽潰涓婄殑鎶曞奖锛?
        BlockPos endPos = new BlockPos(input.start().getX() + dx, input.start().getY(), input.start().getZ() + dz);
        BlockPos baseStart = input.start();
        List<BlockPos> base = generateLinePositions(baseStart, endPos);

        int minY = Math.min(0, dy);
        int maxY = Math.max(0, dy);
        List<BlockPos> result = new ArrayList<>();

        // 浠庝笂寰€涓嬮€愬眰鐢熸垚澧欓潰浣嶇疆
        for (int iy = maxY; iy >= minY; iy--) {
            for (int i = 0; i < base.size(); i++) {
                BlockPos basePos = base.get(i);
                boolean endColumn = (base.size() <= 1) || (i == 0 || i == base.size() - 1);
                // 绌哄績妯″紡涓嬪彧淇濈暀绔儴鍒楀拰椤堕儴/搴曢儴琛?
                if (fillMode != ShapeFillMode.FILL && !endColumn && iy != minY && iy != maxY) {
                    continue;
                }
                result.add(basePos.above(iy));
            }
        }

        return result;
    }
}
