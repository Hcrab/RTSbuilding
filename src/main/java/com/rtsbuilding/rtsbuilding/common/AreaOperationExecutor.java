package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.common.shape.generator.AreaShapeGenerator;
import com.rtsbuilding.rtsbuilding.common.shape.generator.ShapeGeneratorRegistry;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 鍖哄煙鎿嶄綔鎵ц鍣?鈥斺€?鍩轰簬褰㈢姸鐨勫尯鍩熷缓閫犲拰鐮村潖鎿嶄綔涓績銆?
 * <p>
 * 杩欎釜鏃犵姸鎬佸伐鍏风被缂栨帓瀹屾暣鐨勬祦姘寸嚎锛?
 * <ol>
 *   <li>鍩轰簬褰㈢姸鐨勪綅缃敓鎴?/li>
 *   <li>閫愪綅缃獙璇侊紙涓栫晫鏉冮檺銆佸彲鐮村潖鎬с€佸彲鏇挎崲鎬э級</li>
 *   <li>鐗╁搧鎻愬彇</li>
 *   <li>閫氳繃 tick 澶勭悊鍣ㄦ垨鐩存帴鏂瑰潡鎿嶄綔鍦ㄦ湇鍔＄鎵ц</li>
 *   <li>鎿嶄綔璁板綍浠ヤ究鎾ら攢</li>
 * </ol>
 * 鎵€鏈夌姸鎬佺敱璋冪敤鏂圭殑 Session 绠＄悊銆?
 */
public final class AreaOperationExecutor {

    private AreaOperationExecutor() {
    }

    // ======================================================================
    // 鍖哄煙浣嶇疆鐢熸垚 鈥斺€?涓轰换浣曟搷浣滄壒閲忕敓鎴愭柟鍧椾綅缃?
    // ======================================================================

    /**
     * 涓哄尯鍩熸搷浣滐紙鏀剧疆鎴栫牬鍧忥級鐢熸垚鐩爣浣嶇疆銆?
     * <p>
     * 鍩轰簬褰㈢姸鐨勪綅缃敓鎴愪笌鏀剧疆鎴栫牬鍧忔棤鍏斥€斺€旇皟鐢ㄦ柟鍐冲畾濡備綍鎿嶄綔杩欎簺浣嶇疆銆?
     *
     * @param shape    褰㈢姸绫诲瀷
     * @param start    閿氱偣浣嶇疆
     * @param end      绗簩涓鐐逛綅缃?
     * @param height   3D 褰㈢姸鐨勯珮搴﹀亸绉?
     * @param face     鐐瑰嚮/鏀剧疆闈?
     * @param fillMode 濉厖绛栫暐
     * @return 缁濆涓栫晫鍧愭爣鍒楄〃
     */
    public static List<BlockPos> generatePositions(AreaShape shape, BlockPos start, BlockPos end,
                                                   int height, Direction face, ShapeFillMode fillMode) {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(shape);
        AreaShapeInput input = AreaShapeInput.of(start, end, height, face, face);
        return generator.generatePositions(input, fillMode);
    }

    // ======================================================================
    // 鍖哄煙鐮村潖 鈥斺€?鎵归噺鍦ㄨ澶氫綅缃牬鍧忔柟鍧?
    // ======================================================================

    /**
     * 涓哄尯鍩熺牬鍧忔搷浣滅敓鎴愮洰鏍囦綅缃€?
     * <p>
     * 璇箟涓婁笌 {@link #generatePositions} 鐩稿悓鈥斺€斾綅缃垪琛ㄦ槸涓€鏍风殑锛?
     * 璋冪敤鏂瑰喅瀹氭槸鏀剧疆杩樻槸鐮村潖銆?
     *
     * @param shape    褰㈢姸绫诲瀷
     * @param start    閿氱偣浣嶇疆
     * @param end      绗簩涓鐐逛綅缃?
     * @param height   3D 褰㈢姸鐨勯珮搴﹀亸绉?
     * @param face     鐐瑰嚮闈?
     * @param fillMode 濉厖绛栫暐
     * @return 灏濊瘯鐮村潖鐨勭洰鏍囦綅缃垪琛?
     */
    public static List<BlockPos> generateDestroyPositions(AreaShape shape, BlockPos start, BlockPos end,
                                                           int height, Direction face, ShapeFillMode fillMode) {
        return generatePositions(shape, start, end, height, face, fillMode);
    }

    /**
     * 杩囨护鐮村潖鐩爣鍒楄〃锛屽彧淇濈暀鍙湁鏁堢牬鍧忕殑浣嶇疆銆?
     * <p>
     * 鏉′欢锛氶潪绌烘皵銆佸湪涓栫晫浜や簰鑼冨洿鍐呫€佷笖鍏锋湁鏈夋晥鐨勭牬鍧忛€熷害銆?
     *
     * @param level   鏈嶅姟绔笘鐣?
     * @param targets 鍘熷浣嶇疆鍒楄〃
     * @param player  鎵ц鎿嶄綔鐨勭帺瀹?
     * @return 杩囨护鍚庡彲鐮村潖鐨勪綅缃垪琛?
     */
    public static List<BlockPos> filterBreakableTargets(ServerLevel level, List<BlockPos> targets, ServerPlayer player) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (pos == null) continue;
            if (!level.mayInteract(player, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;
            valid.add(pos.immutable());
        }
        return valid;
    }

    /**
     * 杩囨护鏀剧疆鐩爣鍒楄〃锛屽彧淇濈暀鍙湁鏁堟斁缃殑浣嶇疆銆?
     * <p>
     * 鏉′欢锛氬湪寤虹瓚楂樺害鍐呫€佸彲鏇挎崲銆佷笘鐣屽彲浜や簰銆?
     *
     * @param level   鏈嶅姟绔笘鐣?
     * @param targets 鍘熷浣嶇疆鍒楄〃
     * @param state   瑕佹斁缃殑鏂瑰潡鐘舵€?
     * @param player  鎵ц鎿嶄綔鐨勭帺瀹?
     * @return 杩囨护鍚庡彲鏀剧疆鐨勪綅缃垪琛?
     */
    public static List<BlockPos> filterPlaceableTargets(ServerLevel level, List<BlockPos> targets,
                                                         BlockState state, ServerPlayer player) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (pos == null) continue;
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;
            if (!level.mayInteract(player, pos)) continue;
            if (!state.canSurvive(level, pos)) continue;
            if (!level.getBlockState(pos).canBeReplaced()) continue;
            valid.add(pos.immutable());
        }
        return valid;
    }

    /**
     * 楠岃瘉鍗曚釜浣嶇疆鏄惁鏄湁鏁堢殑鐮村潖鐩爣銆?
     *
     * @param level  鏈嶅姟绔笘鐣?
     * @param pos    鐩爣鏂瑰潡浣嶇疆
     * @param player 鐜╁
     * @return true 濡傛灉璇ユ柟鍧楀彲琚牬鍧?
     */
    public static boolean isValidDestroyTarget(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return AreaShapeGenerator.validateDestroyPosition(level, pos, player);
    }

    /**
     * 楠岃瘉鍗曚釜浣嶇疆鏄惁鏄湁鏁堢殑鏀剧疆鐩爣銆?
     *
     * @param level  鏈嶅姟绔笘鐣?
     * @param pos    鐩爣浣嶇疆
     * @param state  瑕佹斁缃殑鏂瑰潡鐘舵€?
     * @param player 鐜╁
     * @return true 濡傛灉姝ゅ鍙互鏀剧疆鏂瑰潡
     */
    public static boolean isValidPlacementTarget(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        return AreaShapeGenerator.validatePlacementPosition(level, pos, state, player);
    }

    /**
     * 鎵弿 3D 鍖呭洿鐩掑苟杩斿洖鍏朵腑鎵€鏈夊彲鐮村潖鐨勬柟鍧椾綅缃€?
     * <p>
     * 搴旂敤褰㈢姸杩囨护鍣紝鐩稿綋浜?GadgetUtils.getDestructionArea()銆?
     *
     * @param level         鏈嶅姟绔笘鐣?
     * @param minX, maxX    鍖呭惈鐨?X 杈圭晫
     * @param minY, maxY    鍖呭惈鐨?Y 杈圭晫
     * @param minZ, maxZ    鍖呭惈鐨?Z 杈圭晫
     * @param player        鐜╁
     * @param shapeOrdinal  褰㈢姸绫诲瀷搴忔暟
     * @param fillOrdinal   濉厖妯″紡搴忔暟
     * @return 杈圭晫鍐呭彲鐮村潖鐨勬柟鍧椾綅缃垪琛?
     */
    public static List<BlockPos> scanAreaMineTargets(ServerLevel level,
                                                      int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                                      ServerPlayer player,
                                                      byte shapeOrdinal, byte fillOrdinal) {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(shapeOrdinal);
        ShapeFillMode fillMode = fillOrdinal <= 0 ? ShapeFillMode.FILL : ShapeFillMode.values()[Math.min(fillOrdinal, ShapeFillMode.values().length - 1)];

        AreaShapeInput input = new AreaShapeInput(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ),
                maxY - minY,
                Direction.DOWN,
                Direction.DOWN);

        List<BlockPos> candidates = generator.generatePositions(input, fillMode);
        return filterBreakableTargets(level, candidates, player);
    }
}
