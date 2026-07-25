package com.rtsbuilding.rtsbuilding.common.shape.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 鍖哄煙褰㈢姸鐢熸垚杈撳叆鍙傛暟 鈥斺€?灏佽浜嗗舰鐘剁敓鎴愬櫒鎵€闇€鐨勬墍鏈夊嚑浣曞弬鏁般€?
 * <p>
 * 鎼哄甫閿氱偣浣嶇疆銆佷袱涓瑙掔偣锛堝畾涔夊舰鐘剁殑瑕嗙洊鑼冨洿锛夈€?
 * 楂樺害鍋忕Щ锛堢敤浜?BOX / WALL 绛?3D 褰㈢姸锛夈€佺偣鍑婚潰鏂瑰悜鍜屾斁缃潰鏂瑰悜銆?
 *
 * @param start        閿氱偣 / 绗竴涓鍧愭爣
 * @param end          绗簩涓鍧愭爣锛堝畾涔夊舰鐘剁殑寤朵几鑼冨洿锛?
 * @param heightOffset 鐩稿浜庡熀鍑嗗钩闈㈢殑鍨傜洿鍋忕Щ锛?D 褰㈢姸涓?0锛?
 * @param clickedFace  鐜╁鐐瑰嚮鐨勯潰鐨勬柟鍚?
 * @param placementFace 鏀剧疆鏂瑰潡鏃剁殑璐撮檮闈㈡柟鍚?
 */
public record AreaShapeInput(
        BlockPos start,
        BlockPos end,
        int heightOffset,
        Direction clickedFace,
        Direction placementFace) {

    /**
     * 鍒涘缓涓€涓粎鍖呭惈涓や釜瑙掔偣鐨勬渶灏忚緭鍏ワ紙榛樿浣跨敤 UP 鏂瑰悜锛夈€?
     *
     * @param start 绗竴涓鍧愭爣
     * @param end   绗簩涓鍧愭爣
     * @return AreaShapeInput 瀹炰緥
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.UP, Direction.UP);
    }

    /**
     * 鍒涘缓鐮村潖鎿嶄綔涓撶敤鐨勮緭鍏ワ紙鏃犻渶鏀剧疆闈㈡柟鍚戯級銆?
     *
     * @param start 绗竴涓鍧愭爣
     * @param end   绗簩涓鍧愭爣
     * @return AreaShapeInput 瀹炰緥
     */
    public static AreaShapeInput destroy(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.DOWN, Direction.DOWN);
    }

    /**
     * 鍒涘缓鍖呭惈鎵€鏈夊弬鏁扮殑瀹屾暣杈撳叆銆?
     *
     * @param start         绗竴涓鍧愭爣
     * @param end           绗簩涓鍧愭爣
     * @param heightOffset  楂樺害鍋忕Щ閲?
     * @param clickedFace   鐐瑰嚮闈㈡柟鍚?
     * @param placementFace 鏀剧疆闈㈡柟鍚?
     * @return AreaShapeInput 瀹炰緥
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end, int heightOffset,
                                     Direction clickedFace, Direction placementFace) {
        return new AreaShapeInput(start, end, heightOffset, clickedFace, placementFace);
    }
}
