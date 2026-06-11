package com.rtsbuilding.rtsbuilding.client.rendering.overlay;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 鍖哄潡寮曞绾挎覆鏌撳櫒
 * 璐熻矗鍦≧TS妯″紡涓嬫覆鏌撲互鐜╁涓轰腑蹇冪殑3x3鍖哄潡缃戞牸锛岀敤浜庤瑙夊弬鑰?
 */
public final class ChunkGuideRenderer {
    // 鍖哄潡寮曞鑼冨洿鍗婂緞锛堜互鍖哄潡涓哄崟浣嶏級锛?琛ㄧず娓叉煋涓績鍖哄潡鍛ㄥ洿3x3鍖哄煙
    private static final int CHUNK_GUIDE_RADIUS_CHUNKS = 1;

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private ChunkGuideRenderer() {
    }

    /**
     * 娓叉煋鍖哄潡寮曞缃戞牸
     *
     * @param minecraft Minecraft瀹㈡埛绔疄渚?
     * @param cameraPosition 鐩告満浣嶇疆
     * @param poseStack 濮垮娍鏍堬紝鐢ㄤ簬鍧愭爣鍙樻崲
     * @param fillBuffer 濉厖缂撳啿鍖猴紝鐢ㄤ簬缁樺埗鍗婇€忔槑鏂瑰潡
     * @param lineBuffer 绾挎潯缂撳啿鍖猴紝鐢ㄤ簬缁樺埗杈规绾?
     */
    public static void renderChunkGuides(
            Minecraft minecraft,
            Vec3 cameraPosition,
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer) {
        if (minecraft.level == null) {
            return;
        }

        // 璁＄畻鐩告満鎵€鍦ㄥ尯鍧楀潗鏍?
        BlockPos cameraBlockPos = BlockPos.containing(cameraPosition);
        int centerChunkX = SectionPos.blockToSectionCoord(cameraBlockPos.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(cameraBlockPos.getZ());

        // 璁＄畻娓叉煋鑼冨洿鐨勮竟鐣?
        int minChunkX = centerChunkX - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkX = centerChunkX + CHUNK_GUIDE_RADIUS_CHUNKS;
        int minChunkZ = centerChunkZ - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkZ = centerChunkZ + CHUNK_GUIDE_RADIUS_CHUNKS;

        // 纭畾寮曞绾跨殑Y杞撮珮搴︼細浼樺厛浣跨敤鐜╁浣嶇疆锛屽惁鍒欎娇鐢ㄧ浉鏈轰綅缃?
        int guideYSource = minecraft.player == null ? cameraBlockPos.getY() : minecraft.player.blockPosition().getY();
        int guideY = Mth.clamp(guideYSource, minecraft.level.getMinBuildHeight(), minecraft.level.getMaxBuildHeight() - 1);

        // 閬嶅巻鑼冨洿鍐呯殑鎵€鏈夊尯鍧楋紝娓叉煋杈圭紭楂樹寒
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                renderChunkEdgeHighlights(minecraft, poseStack, fillBuffer, lineBuffer, cx, cz, guideY);
            }
        }
    }

    /**
     * 娓叉煋鍗曚釜鍖哄潡鐨勮竟缂橀珮浜?
     *
     * @param minecraft Minecraft瀹㈡埛绔疄渚?
     * @param poseStack 濮垮娍鏍?
     * @param fillBuffer 濉厖缂撳啿鍖?
     * @param lineBuffer 绾挎潯缂撳啿鍖?
     * @param chunkX 鍖哄潡X鍧愭爣
     * @param chunkZ 鍖哄潡Z鍧愭爣
     * @param guideY 寮曞绾縔杞撮珮搴?
     */
    private static void renderChunkEdgeHighlights(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer,
            int chunkX,
            int chunkZ,
            int guideY) {
        // 灏嗗尯鍧楀潗鏍囪浆鎹负涓栫晫鍧愭爣锛堟瘡涓尯鍧?6x16锛?
        int startX = chunkX << 4;  // 绛夊悓浜?chunkX * 16
        int startZ = chunkZ << 4;
        int endX = startX + 15;
        int endZ = startZ + 15;

        // 浼樺寲锛氬湪鍖哄潡绾у埆妫€鏌ュ姞杞界姸鎬侊紝閬垮厤姣忎釜鍗曞厓鏍奸噸澶嶆鏌?
        if (minecraft.level != null && !minecraft.level.hasChunkAt(new BlockPos(startX, guideY, startZ))) {
            return;
        }

        // 鏍规嵁鍖哄潡鍧愭爣鐨勫鍋舵€ч€夋嫨棰滆壊锛堟鐩樻牸鏁堟灉锛?
        ChunkGuideColor color = chunkGuideColor(chunkX, chunkZ);

        // 娓叉煋鍖哄潡鍥涙潯杈圭殑鎵€鏈夋柟鍧楀崟鍏冩牸
        // 涓婁笅杈癸紙瀹屾暣琛岋級
        for (int x = startX; x <= endX; x++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, startZ, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, endZ, guideY, color);
        }
        // 宸﹀彸杈癸紙鎺掗櫎瑙掔偣锛岄伩鍏嶉噸澶嶆覆鏌擄級
        for (int z = startZ + 1; z < endZ; z++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, startX, z, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, endX, z, guideY, color);
        }
    }

    /**
     * 娓叉煋鍗曚釜鍗曞厓鏍肩殑寮曞楂樹寒锛堝～鍏?杈规锛?
     *
     * @param poseStack 濮垮娍鏍?
     * @param fillBuffer 濉厖缂撳啿鍖?
     * @param lineBuffer 绾挎潯缂撳啿鍖?
     * @param x 涓栫晫X鍧愭爣
     * @param z 涓栫晫Z鍧愭爣
     * @param guideY Y杞撮珮搴?
     * @param color 棰滆壊閰嶇疆
     */
    private static void renderChunkGuideCell(
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer,
            int x,
            int z,
            int guideY,
            ChunkGuideColor color) {
        // 鍚戝唴鏀剁缉0.04鍗曚綅锛屼娇鐩搁偦鍗曞厓鏍间箣闂翠骇鐢熼棿闅?
        double inset = 0.04D;
        double minX = x + inset;
        double minY = guideY + inset;
        double minZ = z + inset;
        double maxX = x + 1.0D - inset;
        double maxY = guideY + 1.0D - inset;
        double maxZ = z + 1.0D - inset;

        // 缁樺埗鍗婇€忔槑濉厖
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                fillBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                color.r(), color.g(), color.b(), color.a());

        // 缁樺埗杈规绾匡紙棰滆壊姣斿～鍏呯◢浜級
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                Math.min(1.0F, color.r() + 0.18F),
                Math.min(1.0F, color.g() + 0.18F),
                Math.min(1.0F, color.b() + 0.18F),
                0.92F);
    }

    /**
     * 鏍规嵁鍖哄潡鍧愭爣鐢熸垚妫嬬洏鏍奸鑹?
     * 鍋舵暟鍖哄潡浣跨敤闈掕摑鑹诧紝濂囨暟鍖哄潡浣跨敤閲戦粍鑹?
     *
     * @param chunkX 鍖哄潡X鍧愭爣
     * @param chunkZ 鍖哄潡Z鍧愭爣
     * @return 棰滆壊閰嶇疆
     */
    private static ChunkGuideColor chunkGuideColor(int chunkX, int chunkZ) {
        return ((chunkX ^ chunkZ) & 1) == 0
                ? new ChunkGuideColor(0.16F, 0.78F, 1.0F, 0.24F)   // 闈掕摑鑹?
                : new ChunkGuideColor(1.0F, 0.88F, 0.16F, 0.22F);  // 閲戦粍鑹?
    }

    /**
     * 棰滆壊璁板綍绫伙紝瀛樺偍RGBA鍊?
     */
    private record ChunkGuideColor(float r, float g, float b, float a) {
    }
}
