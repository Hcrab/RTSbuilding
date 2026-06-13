package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 钃濆浘骞界伒棰勮娓叉煋??
 * 璐熻矗鍦˙uilderScreen涓覆鏌撹摑鍥剧??D骞界伒棰勮锛屽寘鎷柟鍧楁ā鍨嬪拰缂哄け鏍囪??
 */
public final class BlueprintGhostRenderer {
    private static final float GHOST_BLOCK_ALPHA = 0.30F;
    private static final float TRUNCATED_BOX_ALPHA = 0.22F;

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private BlueprintGhostRenderer() {
    }

    /**
     * 娓叉煋钃濆浘鐨勫菇鐏甸??
     *
     * @param minecraft Minecraft瀹㈡埛绔疄??
     * @param poseStack 濮垮娍鏍堬紝鐢ㄤ簬鍧愭爣鍙樻??
     * @param lineBuffer 绾挎潯缂撳啿??
     * @param fillBuffer 濉厖缂撳啿鍖猴紙棰勭暀锛屽綋鍓嶆湭浣跨敤锛?
     */
    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        // 浠呭湪BuilderScreen涓覆鏌?
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return;
        }

        BlueprintGhostPreview preview = builderScreen.getBlueprintGhostPreview();
        if (preview.blocks().isEmpty()) {
            return;
        }

        // 鏍规嵁鏉愭枡鏄惁榻愬閫夋嫨棰滆壊
        // 鏉愭枡榻愬锛氱豢鑹茬郴锛涙潗鏂欑己澶憋細绾㈣壊??
        float lineR = preview.materialsReady() ? 0.35F : 1.00F;
        float lineG = preview.materialsReady() ? 0.95F : 0.72F;
        float lineB = preview.materialsReady() ? 0.72F : 0.22F;

        // 鍒濆鍖栧寘鍥寸洅杈圭晫
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        boolean renderedBlockModels = false;
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBlockBuffer = new AlphaBlockPreviewBufferSource(blockBuffer, GHOST_BLOCK_ALPHA);

        // 閬嶅巻鎵€鏈夎摑鍥炬柟鍧?
        for (BlueprintPanel.BlueprintGhostBlock block : preview.blocks()) {
            BlockPos pos = block.pos();

            // 鏇存柊鍖呭洿鐩掕竟鐣?
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);

            BlockState state = block.state();

            // 濡傛灉鏂瑰潡瀛樺湪涓斾笉鏄┖姘旓紝涓旀湁妯″瀷锛屽垯娓叉煋瀹為檯鏂瑰潡妯???
            if (!block.missing()
                    && state != null
                    && !state.isAir()
                    && state.getRenderShape() == RenderShape.MODEL) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                minecraft.getBlockRenderer().renderSingleBlock(
                        state,
                        poseStack,
                        translucentBlockBuffer,
                        LightTexture.FULL_BRIGHT,  // 浣跨敤鏈€澶т寒搴︼紝涓嶅彈鍏夌収褰卞??
                        OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
                renderedBlockModels = true;
                continue;
            }

            // 瀵逛簬缂哄け鎴栨棤妯″瀷鐨勬柟鍧楋紝缁樺埗杈规鍗犱綅??
            double cellMinX = pos.getX() + 0.04D;
            double cellMinY = pos.getY() + 0.04D;
            double cellMinZ = pos.getZ() + 0.04D;
            double cellMaxX = pos.getX() + 0.96D;
            double cellMaxY = pos.getY() + 0.96D;
            double cellMaxZ = pos.getZ() + 0.96D;

            // 缂哄け鏂瑰潡浣跨敤绾㈣壊锛屽叾浠栦娇鐢ㄧ姸鎬佽壊
            float fallbackR = block.missing() ? 1.00F : lineR;
            float fallbackG = block.missing() ? 0.25F : lineG;
            float fallbackB = block.missing() ? 0.25F : lineB;

            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fallbackR, fallbackG, fallbackB,
                    0.90F);
        }

        // 濡傛灉娓叉煋浜嗘柟鍧楁ā鍨嬶紝闇€瑕佹彁浜ゆ壒澶勭??
        if (renderedBlockModels) {
            blockBuffer.endBatch();
        }

        // 娓叉煋鏁翠綋鍖呭洿鐩掕竟??
        if (minX != Integer.MAX_VALUE) {
            // 濡傛灉钃濆浘琚埅鏂紙鏂瑰潡鏁伴噺杩囧锛夛紝闄嶄綆閫忔槑??
            float alpha = preview.truncated() ? TRUNCATED_BOX_ALPHA : GHOST_BLOCK_ALPHA;
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    minX - 0.02D, minY - 0.02D, minZ - 0.02D,
                    maxX + 0.02D, maxY + 0.02D, maxZ + 0.02D,
                    lineR, lineG, lineB,
                    alpha);
        }
    }

    /**
     * Routes preview block models through the translucent layer and applies a
     * fixed alpha. Blueprint previews are not real blocks yet, so they should
     * stay readable without blocking the player's view while following the mouse.
     */
    private record AlphaBlockPreviewBufferSource(MultiBufferSource delegate, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new AlphaVertexConsumer(delegate.getBuffer(RenderType.translucent()), alpha);
        }
    }

    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, Math.round(alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, Math.round(alpha * this.alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
