package com.rtsbuilding.rtsbuilding.client.rendering.overlay;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 鍌ㄥ瓨鏂瑰潡楂樹寒娓叉煋??
 * 璐熻矗娓叉煋宸查摼鎺ョ殑鍌ㄥ瓨瀹瑰櫒鏂瑰潡鐨勮摑鑹茶竟妗嗭紝甯姪鐜╁璇嗗埆RTS绯荤粺鐨勫偍瀛樼綉缁?
 */
public final class StorageRenderer {

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private StorageRenderer() {
    }

    /**
     * 娓叉煋鎵€鏈夊凡閾炬帴鐨勫偍瀛樻柟鍧楅珮??
     *
     * @param minecraft Minecraft瀹㈡埛绔疄??
     * @param controller RTS鎺у埗鍣紝鎻愪緵鍌ㄥ瓨浣嶇疆鍒楄??
     * @param poseStack 濮垮娍鏍堬紝鐢ㄤ簬鍧愭爣鍙樻??
     * @param lineBuffer 绾挎潯缂撳啿??
     */
    public static void renderLinkedStorages(Minecraft minecraft, ClientRtsController controller, PoseStack poseStack,
                                            VertexConsumer lineBuffer) {
        if (minecraft.level == null || controller.getLinkedStoragePositions().isEmpty()) {
            return;
        }

        // 閬嶅巻鎵€鏈夊凡閾炬帴鐨勫偍瀛樹綅缃?
        for (BlockPos pos : controller.getLinkedStoragePositions()) {
            // 妫€鏌ュ尯鍧楁槸鍚﹀凡鍔犺浇
            if (!minecraft.level.hasChunkAt(pos)) {
                continue;
            }

            // 妫€鏌ユ柟鍧楁槸鍚﹀瓨鍦紙闈炵┖姘旓級
            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            // 缁樺埗钃濊壊杈规锛屽悜澶栨墿灞?.002鍗曚綅浠ラ伩鍏峑-fighting
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() - 0.002D,
                    pos.getY() - 0.002D,
                    pos.getZ() - 0.002D,
                    pos.getX() + 1.002D,
                    pos.getY() + 1.002D,
                    pos.getZ() + 1.002D,
                    0.24F, 0.55F, 1.00F, 1.0F);  // 澶╄摑鑹?
        }
    }
}
