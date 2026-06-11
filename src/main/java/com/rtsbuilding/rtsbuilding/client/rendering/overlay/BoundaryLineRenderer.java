package com.rtsbuilding.rtsbuilding.client.rendering.overlay;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * RTS寤洪€犺寖鍥磋竟鐣岀嚎娓叉煋鍣?
 * 璐熻矗缁樺埗绾㈣壊鐨勬鏂瑰舰杈圭晫妗嗭紝鏍囪瘑鐜╁鍙搷浣滅殑鏈€澶ц寖鍥?
 */
public final class BoundaryLineRenderer {

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private BoundaryLineRenderer() {
    }

    /**
     * 缁樺埗绾㈣壊杈圭晫绾匡紙鍦ㄩ敋鐐筜楂樺害鐨勬鏂瑰舰杈规锛?
     *
     * @param minecraft Minecraft瀹㈡埛绔疄渚?
     * @param poseStack 濮垮娍鏍堬紝鐢ㄤ簬鍧愭爣鍙樻崲
     * @param lineBuffer 绾挎潯缂撳啿鍖?
     * @param minX X杞存渶灏忓€硷紙閿氱偣X - 鍗婂緞锛?
     * @param minZ Z杞存渶灏忓€硷紙閿氱偣Z - 鍗婂緞锛?
     * @param maxX X杞存渶澶у€硷紙閿氱偣X + 鍗婂緞锛?
     * @param maxZ Z杞存渶澶у€硷紙閿氱偣Z + 鍗婂緞锛?
     * @param defaultY Y杞撮珮搴︼紙浣跨敤閿氱偣Y鍧愭爣锛?
     */
    public static void renderRedBoundary(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, double minX, double minZ, double maxX, double maxZ, double defaultY) {
        if (minecraft.level == null) {
            return;
        }

        float y = (float) defaultY;

        // 缁樺埗姝ｆ柟褰㈢殑鍥涙潯杈?
        // 杈?: 鍓嶈竟 (minX, minZ) -> (maxX, minZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, y, minZ, maxX, y, minZ,
                1.0F, 0.25F, 0.25F, 1.0F);

        // 杈?: 鍙宠竟 (maxX, minZ) -> (maxX, maxZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, maxX, y, minZ, maxX, y, maxZ,
                1.0F, 0.25F, 0.25F, 1.0F);

        // 杈?: 鍚庤竟 (maxX, maxZ) -> (minX, maxZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, maxX, y, maxZ, minX, y, maxZ,
                1.0F, 0.25F, 0.25F, 1.0F);

        // 杈?: 宸﹁竟 (minX, maxZ) -> (minX, minZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, y, maxZ, minX, y, minZ,
                1.0F, 0.25F, 0.25F, 1.0F);
    }
}
