package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 闀挎柟浣擄紙BOX / 3D 绔嬫柟浣擄級褰㈢姸鐢熸垚鍣ㄣ€?
 * <p>
 * 鐢熸垚鐢变袱涓瑙掔偣鍜屽彲閫夐珮搴﹀亸绉诲畾涔夌殑闀挎柟浣撳唴鐨勬墍鏈夋柟鍧椾綅缃€?
 * 鏀寔 FILL锛堝疄蹇冿級銆丠OLLOW锛堢┖蹇冿級鍜?SKELETON锛堥鏋讹級涓夌濉厖妯″紡銆?
 */
public class BoxShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "box";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 璁＄畻涓変釜杞翠笂鐨勫亸绉婚噺骞堕檺鍒舵渶澶ц寖鍥?
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());
        int dy = clampOffset(input.heightOffset());

        // 纭畾鍚勮酱鐨勬渶灏?鏈€澶ц寖鍥?
        int minX = Math.min(0, dx);
        int maxX = Math.max(0, dx);
        int minZ = Math.min(0, dz);
        int maxZ = Math.max(0, dz);
        int minY = Math.min(0, dy);
        int maxY = Math.max(0, dy);

        // 鐢熸垚瀹炲績闀挎柟浣撶殑鎵€鏈夋柟鍧椾綅缃紙浠庝笂寰€涓嬮€愬眰锛?
        List<BlockPos> full = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    full.add(input.start().offset(x, y, z));
                }
            }
        }

        // 瀹炲績妯″紡涓嬬洿鎺ヨ繑鍥炲叏閮ㄤ綅缃紝绌哄績/楠ㄦ灦妯″紡鍒欒繃婊ゅ嚭杈圭晫
        if (fillMode == ShapeFillMode.FILL || full.isEmpty()) {
            return full;
        }

        return filterBoundary(full, minY, maxY);
    }
}
