package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 钃濆浘鎹曡幏妗嗘覆鏌撳櫒
 * 璐熻矗娓叉煋钃濆浘褰曞埗鏃剁殑閫夋嫨妗嗐€佸寘鍚柟鍧楅珮浜拰鎺掗櫎鏂瑰潡鏍囪??
 */
public final class BlueprintCaptureRenderer {
    // 鍖呭惈鏂瑰潡楂樹寒鐨勬渶澶ф暟閲忛檺鍒讹紝閬垮厤鎬ц兘闂??
    private static final int CAPTURE_BLOCK_HIGHLIGHT_LIMIT = 8192;
    // 鎺掗櫎鏂瑰潡楂樹寒鐨勬渶澶ф暟閲忛檺鍒?
    private static final int CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT = 1024;

    // 浼樺寲锛氭彁鍙栭鑹插父閲忥紝渚夸簬缁熶竴璋冩暣
    private static final float CAPTURE_FILL_R = 0.12F;
    private static final float CAPTURE_FILL_G = 0.46F;
    private static final float CAPTURE_FILL_B = 0.95F;
    private static final float CAPTURE_FILL_A = 0.06F;

    private static final float INCLUDED_BLOCK_R = 0.12F;
    private static final float INCLUDED_BLOCK_G = 0.56F;
    private static final float INCLUDED_BLOCK_B = 1.0F;
    private static final float INCLUDED_BLOCK_A = 0.11F;

    private static final float EXCLUDED_BLOCK_R = 1.0F;
    private static final float EXCLUDED_BLOCK_G = 0.36F;
    private static final float EXCLUDED_BLOCK_B = 0.12F;
    private static final float EXCLUDED_BLOCK_A = 0.95F;

    private static final float BOUNDARY_BOX_R = 0.35F;
    private static final float BOUNDARY_BOX_G = 0.78F;
    private static final float BOUNDARY_BOX_B = 1.0F;
    private static final float BOUNDARY_BOX_A = 0.95F;

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private BlueprintCaptureRenderer() {
    }

    /**
     * 娓叉煋钃濆浘鎹曡幏閫夋嫨妗嗗拰楂樹寒
     *
     * @param poseStack 濮垮娍鏍堬紝鐢ㄤ簬鍧愭爣鍙樻??
     * @param lineBuffer 绾挎潯缂撳啿??
     * @param fillBuffer 濉厖缂撳啿??
     */
    public static void renderBlueprintCaptureBox(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        // 鑾峰彇绗竴涓鐐癸紙璧峰鐐癸級
        BlockPos first = BlueprintPanel.getCapturePointA();
        if (first == null) {
            return;
        }

        // 鑾峰彇绗簩涓鐐癸紙棰勮鐐癸級锛屽鏋滄湭璁剧疆鍒欎娇鐢ㄧ涓€涓偣
        BlockPos second = BlueprintPanel.getCapturePreviewPointB();
        if (second == null) {
            second = first;
        }

        // 璁＄畻鍖呭洿鐩掕竟鐣岋紙鍚戝鎵╁睍0.01鍗曚綅浠ラ伩鍏峑-fighting??
        double minX = Math.min(first.getX(), second.getX()) - 0.01D;
        double minY = Math.min(first.getY(), second.getY()) + 0.99D;
        double minZ = Math.min(first.getZ(), second.getZ()) - 0.01D;
        double maxX = Math.max(first.getX(), second.getX()) + 1.01D;
        double maxY = Math.max(first.getY(), second.getY()) + 1.01D;
        double maxZ = Math.max(first.getZ(), second.getZ()) + 1.01D;

        // 纭繚Y杞磋寖鍥存湁??
        if (minY > maxY) {
            minY = maxY - 0.02D;
        }

        // 鑾峰彇鍖呭惈鐨勬柟鍧楀垪琛紙鍙楁暟閲忛檺鍒讹??
        List<BlockPos> includedBlocks = BlueprintPanel.getCaptureIncludedBlocksForRender(CAPTURE_BLOCK_HIGHLIGHT_LIMIT);

        // 濡傛灉闇€瑕佹覆鏌撴暣浣撳～鍏呬笖涓嶆覆鏌撳崟涓柟鍧楅珮浜紝鍒欑粯鍒跺崐閫忔槑钃濊壊濉??
        if (BlueprintPanel.shouldRenderCapturePreviewFill()
                && !BlueprintPanel.shouldRenderCaptureBlockHighlights(CAPTURE_BLOCK_HIGHLIGHT_LIMIT)) {
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    CAPTURE_FILL_R, CAPTURE_FILL_G, CAPTURE_FILL_B, CAPTURE_FILL_A);
        }

        // 娓叉煋姣忎釜鍖呭惈鏂瑰潡鐨勮摑鑹查珮??
        for (BlockPos pos : includedBlocks) {
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    pos.getX() + 0.04D, pos.getY() + 0.04D, pos.getZ() + 0.04D,
                    pos.getX() + 0.96D, pos.getY() + 0.96D, pos.getZ() + 0.96D,
                    INCLUDED_BLOCK_R, INCLUDED_BLOCK_G, INCLUDED_BLOCK_B, INCLUDED_BLOCK_A);
        }

        // 娓叉煋姣忎釜鎺掗櫎鏂瑰潡鐨勭孩鑹茶竟??
        for (BlockPos pos : BlueprintPanel.getCaptureExcludedBlocksForRender(CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT)) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() + 0.06D, pos.getY() + 0.06D, pos.getZ() + 0.06D,
                    pos.getX() + 0.94D, pos.getY() + 0.94D, pos.getZ() + 0.94D,
                    EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_A);
        }

        // 娓叉煋鏁翠釜閫夋嫨妗嗙殑钃濊壊杈规
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                BOUNDARY_BOX_R, BOUNDARY_BOX_G, BOUNDARY_BOX_B, BOUNDARY_BOX_A);
    }
}
