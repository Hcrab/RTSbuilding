package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 鍖哄煙褰㈢姸鐢熸垚鍣ㄦ娊璞″熀绫?鈥斺€?褰㈢姸鍧愭爣鐢熸垚鐨勫熀纭€銆?
 * <p>
 * 姣忎釜鍏蜂綋瀛愮被璐熻矗涓轰竴绉嶅嚑浣曞舰鐘讹紙闀挎柟浣撱€佸浣撱€佺洿绾裤€佸渾褰㈢瓑锛?
 * 鏍规嵁 {@link AreaShapeInput} 鐢熸垚涓€缁?{@link BlockPos} 鍧愭爣銆?
 * 鐢熸垚鍣ㄤ骇鐢熺殑鍧愭爣鐩稿浜庨敋鐐逛綅缃紝涓嶆秹鍙婃柟鍧楃姸鎬佹搷浣滄垨鐗╁搧鎻愬彇銆?
 * 瀹為檯鐨勬柟鍧楁搷浣滅敱涓婂眰鎵ц鍣?{@link com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor} 璐熻矗銆?
 */
public abstract class AreaShapeGenerator {

    /**
     * 鐢熸垚璇ュ舰鐘剁殑鏂瑰潡浣嶇疆鍒楄〃銆?
     *
     * @param input    褰㈢姸杈撳叆鍙傛暟锛堥敋鐐广€佽竟鐣屻€侀潰绛夛級
     * @param fillMode 濉厖绛栫暐锛團ILL / HOLLOW / SKELETON锛?
     * @return 鏈夊簭鐨勭粷瀵逛笘鐣屽潗鏍囧垪琛?
     */
    public abstract List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode);

    /**
     * 杩斿洖璇ュ舰鐘剁殑鍙鍚嶇О / 缈昏瘧閿悗缂€銆?
     */
    public abstract String getName();

    // ======================================================================
    // 鍏变韩楠岃瘉杈呭姪鏂规硶
    // ======================================================================

    /**
     * 鍩虹浣嶇疆鏍￠獙锛氭鏌ュ缓绛戦珮搴﹁寖鍥村拰涓栫晫浜や簰鏉冮檺銆?
     * <p>
     * {@link #validatePlacementPosition} 鍜?{@link #validateDestroyPosition}
     * 閮戒互鍚屾牱鐨勪袱涓鏌ュ紑濮嬧€斺€旀鏂规硶缁熶竴浜嗗畠浠€?
     *
     * @param level  涓栫晫
     * @param pos    鐩爣浣嶇疆
     * @param player 鎵ц鎿嶄綔鐨勭帺瀹?
     * @return true 濡傛灉浣嶇疆鍦ㄥ缓绛戦珮搴﹀唴涓旂帺瀹跺彲涓庝箣浜や簰
     */
    private static boolean validatePositionBase(Level level, BlockPos pos, Player player) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        return level.mayInteract(player, pos);
    }

    /**
     * 楠岃瘉鏂瑰潡浣嶇疆鏄惁鍙互鏀剧疆鏂瑰潡銆?
     * <p>
     * 妫€鏌ュ缓绛戦珮搴︺€佷氦浜掓潈闄愪互鍙婄幇鏈夋柟鍧楁槸鍚﹀彲琚浛鎹€?
     *
     * @param level  涓栫晫
     * @param pos    鐩爣浣嶇疆
     * @param state  瑕佹斁缃殑鏂瑰潡鐘舵€?
     * @param player 鎵ц鎿嶄綔鐨勭帺瀹?
     * @return true 濡傛灉姝ゅ鍙互鏀剧疆鏂瑰潡
     */
    public static boolean validatePlacementPosition(Level level, BlockPos pos, BlockState state, Player player) {
        if (!validatePositionBase(level, pos, player)) {
            return false;
        }
        if (!state.canSurvive(level, pos)) {
            return false;
        }
        return level.getBlockState(pos).canBeReplaced();
    }

    /**
     * 楠岃瘉鏂瑰潡浣嶇疆鏄惁鍙互琚牬鍧忋€?
     * <p>
     * 妫€鏌ュ缓绛戦珮搴︺€佷氦浜掓潈闄愪互鍙婄洰鏍囨柟鍧楁槸鍚︿负绌烘垨涓嶅彲鐮村潖銆?
     *
     * @param level  涓栫晫
     * @param pos    鐩爣浣嶇疆
     * @param player 鎵ц鎿嶄綔鐨勭帺瀹?
     * @return true 濡傛灉姝ゅ鐨勬柟鍧楀彲浠ヨ鐮村潖
     */
    public static boolean validateDestroyPosition(ServerLevel level, BlockPos pos, Player player) {
        if (!validatePositionBase(level, pos, player)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return true;
    }

    /**
     * 灏嗗舰鐘跺亸绉婚噺闄愬埗鍦ㄦ渶澶у厑璁歌寖鍥村唴锛埪?4 鏍硷級銆?
     */
    protected static int clampOffset(int value) {
        int max = 64;
        return Math.max(-max, Math.min(max, value));
    }

    /**
     * 璁＄畻鍚戦噺 (dx, dy, dz) 鍦ㄦ寚瀹氳酱涓婄殑鎶曞奖锛堢偣绉級銆?
     */
    protected static int dotDelta(int dx, int dy, int dz, Direction axis) {
        return (dx * axis.getStepX()) + (dy * axis.getStepY()) + (dz * axis.getStepZ());
    }

    /**
     * 鍦ㄤ袱涓綅缃箣闂寸敓鎴愪竴鏉＄洿绾匡紙鍖呭惈涓ょ锛孊resenham 椋庢牸锛夈€?
     */
    protected static List<BlockPos> generateLinePositions(BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        List<BlockPos> result = new ArrayList<>(steps + 1);
        if (steps <= 0) {
            result.add(start);
            return result;
        }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = start.getX() + (int) Math.round(dx * t);
            int y = start.getY() + (int) Math.round(dy * t);
            int z = start.getZ() + (int) Math.round(dz * t);
            result.add(new BlockPos(x, y, z));
        }
        return result;
    }

    /**
     * 鏍规嵁鐐瑰嚮闈㈢‘瀹?2D 褰㈢姸鐨勪袱涓钩闈㈣酱銆?
     */
    protected static Direction[] resolvePlaneAxes(Direction face) {
        return switch (face.getAxis()) {
            case Y -> new Direction[]{Direction.EAST, Direction.SOUTH};
            case X -> new Direction[]{Direction.UP, Direction.SOUTH};
            case Z -> new Direction[]{Direction.EAST, Direction.UP};
        };
    }

    /**
     * 娌夸袱涓钩闈㈣酱浠庤捣鐐圭敓鎴愭墍鏈夋柟鍧椾綅缃€?
     */
    protected static List<BlockPos> buildPlanePositions(BlockPos start, Direction axisA, Direction axisB,
                                                         int aMin, int aMax, int bMin, int bMax) {
        List<BlockPos> result = new ArrayList<>();
        for (int a = aMin; a <= aMax; a++) {
            for (int b = bMin; b <= bMax; b++) {
                int dx = (axisA.getStepX() * a) + (axisB.getStepX() * b);
                int dy = (axisA.getStepY() * a) + (axisB.getStepY() * b);
                int dz = (axisA.getStepZ() * a) + (axisB.getStepZ() * b);
                result.add(start.offset(dx, dy, dz));
            }
        }
        return result;
    }

    /**
     * 过滤位置列表，仅保留边界位置（用于 HOLLOW / SKELETON 模式）。
     *
     * <p>如果一个方块在 X、Y、Z 任一方向上的邻居不在集合中，就把它视为边界方块。</p>
     */
    protected static List<BlockPos> filterBoundary(List<BlockPos> full, int minY, int maxY) {
        java.util.Set<BlockPos> set = new java.util.HashSet<>(full);
        List<BlockPos> boundary = new ArrayList<>();
        for (BlockPos pos : full) {
            boolean xEdge = !set.contains(pos.east()) || !set.contains(pos.west());
            // 单层 2D 形状没有上下邻居，不能因此把内部格子误判成边界。
            boolean yEdge = minY != maxY && (!set.contains(pos.above()) || !set.contains(pos.below()));
            boolean zEdge = !set.contains(pos.north()) || !set.contains(pos.south());
            int edges = (xEdge ? 1 : 0) + (yEdge ? 1 : 0) + (zEdge ? 1 : 0);
            if (edges >= 1) {
                boundary.add(pos);
            }
        }
        return boundary;
    }
}
