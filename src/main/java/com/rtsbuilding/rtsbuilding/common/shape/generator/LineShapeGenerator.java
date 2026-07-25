package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 鐩寸嚎锛?D锛夊舰鐘剁敓鎴愬櫒銆?
 * <p>
 * 鍦ㄤ笁缁寸┖闂翠腑鐢熸垚涓€鏉¤繛鎺ヨ捣鐐瑰拰缁堢偣鐨勭洿绾匡紝
 * 閫氬父娌跨潃璧风偣鍜岀粓鐐逛箣闂磋窛绂绘渶澶х殑杞村欢浼搞€備粎鏀寔 FILL 妯″紡銆?
 */
public class LineShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "line";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        return generateLinePositions(input.start(), input.end());
    }
}
