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
 * 鍖哄煙褰㈢姸鐢熸垚鍣ㄦ敞鍐岃〃锛岀鐞嗘墍鏈夊舰鐘剁被鍨嬪埌瀵瑰簲鐢熸垚鍣ㄧ殑鏄犲皠銆?
 * <p>
 * 鏀寔閫氳繃 {@link AreaShape} 鏋氫妇鎴?byte 搴忔暟锛坥rdinal锛夋煡鎵剧敓鎴愬櫒锛?
 * byte 鏂瑰紡鐢ㄤ簬鍏煎鐜版湁鐨勭綉缁滃崗璁紙褰㈢姸绫诲瀷浠?byte 搴忔暟浼犺緭锛夈€?
 * 姣忕褰㈢姸绫诲瀷鏄犲皠鍒颁竴涓叡浜敓鎴愬櫒瀹炰緥锛屾墍鏈夎皟鐢ㄦ柟鍏辩敤銆?
 */
public final class ShapeGeneratorRegistry {

    /** 涓嶅彲淇敼鐨勭敓鎴愬櫒鏄犲皠琛?*/
    private static final Map<AreaShape, AreaShapeGenerator> GENERATORS = Collections.unmodifiableMap(initGenerators());

    /**
     * 鍒濆鍖栨墍鏈夊舰鐘剁敓鎴愬櫒骞舵敞鍐屽埌鏄犲皠琛ㄤ腑銆?
     */
    private static Map<AreaShape, AreaShapeGenerator> initGenerators() {
        Map<AreaShape, AreaShapeGenerator> map = new EnumMap<>(AreaShape.class);
        map.put(AreaShape.BLOCK, new SingleBlockGenerator());
        map.put(AreaShape.LINE, new LineShapeGenerator());
        map.put(AreaShape.SQUARE, new SquareShapeGenerator());
        map.put(AreaShape.WALL, new WallShapeGenerator());
        map.put(AreaShape.CIRCLE, new CircleShapeGenerator());
        map.put(AreaShape.BOX, new BoxShapeGenerator());
        map.put(AreaShape.CYLINDER, new CylinderShapeGenerator());
        map.put(AreaShape.BALL, new BallShapeGenerator());
        return map;
    }

    private ShapeGeneratorRegistry() {
    }

    /**
     * 鑾峰彇鎸囧畾褰㈢姸绫诲瀷鐨勭敓鎴愬櫒銆?
     *
     * @param shape 褰㈢姸绫诲瀷
     * @return 瀵瑰簲鐨勭敓鎴愬櫒瀹炰緥锛屾湭鐭ョ被鍨嬭繑鍥炲崟鏂瑰潡鐢熸垚鍣ㄤ綔涓洪粯璁ゅ€?
     */
    public static AreaShapeGenerator getGenerator(AreaShape shape) {
        return GENERATORS.getOrDefault(shape, GENERATORS.get(AreaShape.BLOCK));
    }

    /**
     * 閫氳繃 byte 搴忔暟鑾峰彇褰㈢姸鐢熸垚鍣紙鍏煎缃戠粶鍗忚锛夈€?
     *
     * @param shapeOrdinal 涓?{@link AreaShape#ordinal()} 瀵瑰簲鐨勫舰鐘跺簭鏁?
     * @return 瀵瑰簲鐨勭敓鎴愬櫒瀹炰緥
     */
    public static AreaShapeGenerator getGenerator(byte shapeOrdinal) {
        AreaShape[] values = AreaShape.values();
        if (shapeOrdinal < 0 || shapeOrdinal >= values.length) {
            return GENERATORS.get(AreaShape.BLOCK);
        }
        return getGenerator(values[shapeOrdinal]);
    }

    /**
     * 鍗曟柟鍧楃敓鎴愬櫒 鈥斺€?鐢ㄤ簬 {@link AreaShape#BLOCK} 绫诲瀷銆?
     * <p>
     * 浠呯敓鎴愰敋鐐逛綅缃殑涓€涓潗鏍囷紝涓嶆墽琛屼换浣曞舰鐘舵墿灞曘€?
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
