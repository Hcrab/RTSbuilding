package com.rtsbuilding.rtsbuilding.client.rendering.overlay;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 浜や簰鐩爣楂樹寒娓叉煋??
 * 璐熻矗娓叉煋鐜╁榧犳爣鎮仠鐨勬柟鍧楁垨瀹炰綋锛屾彁渚涜瑙夊弽??
 */
public final class InteractionTargetRenderer {

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private InteractionTargetRenderer() {
    }

    /**
     * 娓叉煋褰撳墠榧犳爣鎮仠鐨勪氦浜掔洰鏍囷紙鏂瑰潡鎴栧疄浣擄??
     *
     * @param minecraft Minecraft瀹㈡埛绔疄??
     * @param controller RTS鎺у埗??
     * @param poseStack 濮垮娍鏍堬紝鐢ㄤ簬鍧愭爣鍙樻??
     * @param lineBuffer 绾挎潯缂撳啿??
     */
    public static void renderHoveredInteractionTarget(Minecraft minecraft, ClientRtsController controller,
            PoseStack poseStack, VertexConsumer lineBuffer) {
        // 鍦ㄦ棆杞崟鑾锋ā寮忔垨鏈姞杞戒笘鐣屾椂涓嶆覆鏌?
        if (controller.isRotateCaptured() || minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }

        // 璁＄畻榧犳爣灏勭??
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 viewDir = RaycastHelper.computeCursorRayDirection(minecraft);
        Vec3 to = camPos.add(viewDir.scale(128.0D));

        // 鎵ц灏勭嚎妫€娴?
        BlockHitResult blockHit = RaycastHelper.raycastBlockFromCursor(minecraft, camPos, to, false);
        EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(minecraft, camPos, to, viewDir, 128.0D);

        // 璁＄畻璺濈锛屼紭鍏堥€夋嫨鏇磋繎鐨勭洰??
        double blockDist = blockHit != null ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDist = entityHit != null ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        // 濡傛灉瀹炰綋鏇磋繎锛屾覆鏌撳疄浣撹竟妗?
        if (entityHit != null && entityDist <= blockDist) {
            renderEntityHighlight(poseStack, lineBuffer, entityHit.getEntity());
            return;
        }

        // 濡傛灉娌℃湁鍛戒腑鏂瑰潡锛岀洿鎺ヨ繑??
        if (blockHit == null || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        // 娓叉煋鏂瑰潡楂樹??
        renderBlockHighlight(minecraft, poseStack, lineBuffer, blockHit.getBlockPos());
    }

    /**
     * 娓叉煋瀹炰綋楂樹寒妗嗭紙缁胯壊??
     *
     * @param poseStack 濮垮娍鏍?
     * @param lineBuffer 绾挎潯缂撳啿??
     * @param entity 鐩爣瀹炰??
     */
    private static void renderEntityHighlight(PoseStack poseStack, VertexConsumer lineBuffer, Entity entity) {
        AABB bb = entity.getBoundingBox().inflate(0.03D);
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                bb.minX, bb.minY, bb.minZ,
                bb.maxX, bb.maxY, bb.maxZ,
                0.35F, 1.0F, 0.55F, 1.0F);  // 缁胯??
    }

    /**
     * 娓叉煋鏂瑰潡楂樹寒锛堥粍鑹诧??
     * 鏍规嵁鏂瑰潡鐘舵€侀€夋嫨鍚堥€傜殑娓叉煋鏂瑰紡
     *
     * @param minecraft Minecraft瀹㈡埛绔疄??
     * @param poseStack 濮垮娍鏍?
     * @param lineBuffer 绾挎潯缂撳啿??
     * @param pos 鏂瑰潡浣嶇疆
     */
    private static void renderBlockHighlight(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer, BlockPos pos) {
        BlockState state = null;
        if (minecraft.level != null) {
            state = minecraft.level.getBlockState(pos);
        }

        // 绌烘皵鏂瑰潡锛氭覆鏌撳畬鏁寸珛鏂逛綋
        if (state != null && state.isAir()) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                    1.0F, 0.95F, 0.2F, 1.0F);  // 榛勮??
            return;
        }

        // 鑾峰彇鏂瑰潡鐨勭鎾炲舰??
        VoxelShape shape = null;
        if (state != null) {
            shape = state.getShape(minecraft.level, pos);
        }

        // 绌哄舰鐘讹細娓叉煋瀹屾暣绔嬫柟??
        if (shape != null && shape.isEmpty()) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                    1.0F, 0.95F, 0.2F, 1.0F);
            return;
        }

        // 鏈夊舰鐘讹細鎸夊疄闄呯鎾炵洅娓叉煋锛堟敮鎸佹ゼ姊€佸彴闃剁瓑涓嶈鍒欐柟鍧楋??
        if (shape != null) {
            for (AABB box : shape.toAabbs()) {
                LevelRenderer.renderLineBox(
                        poseStack,
                        lineBuffer,
                        pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ,
                        pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ,
                        1.0F, 0.95F, 0.2F, 1.0F);
            }
        }
    }
}
