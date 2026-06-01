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
 * 区块引导线渲染器
 * 负责在RTS模式下渲染以玩家为中心的3x3区块网格，用于视觉参考
 */
public final class ChunkGuideRenderer {
    // 区块引导范围半径（以区块为单位），1表示渲染中心区块周围3x3区域
    private static final int CHUNK_GUIDE_RADIUS_CHUNKS = 1;

    /**
     * 私有构造函数，防止实例化
     */
    private ChunkGuideRenderer() {
    }

    /**
     * 渲染区块引导网格
     * 
     * @param minecraft Minecraft客户端实例
     * @param cameraPosition 相机位置
     * @param poseStack 姿势栈，用于坐标变换
     * @param fillBuffer 填充缓冲区，用于绘制半透明方块
     * @param lineBuffer 线条缓冲区，用于绘制边框线
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

        // 计算相机所在区块坐标
        BlockPos cameraBlockPos = BlockPos.containing(cameraPosition);
        int centerChunkX = SectionPos.blockToSectionCoord(cameraBlockPos.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(cameraBlockPos.getZ());

        // 计算渲染范围的边界
        int minChunkX = centerChunkX - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkX = centerChunkX + CHUNK_GUIDE_RADIUS_CHUNKS;
        int minChunkZ = centerChunkZ - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkZ = centerChunkZ + CHUNK_GUIDE_RADIUS_CHUNKS;

        // 确定引导线的Y轴高度：优先使用玩家位置，否则使用相机位置
        int guideYSource = minecraft.player == null ? cameraBlockPos.getY() : minecraft.player.blockPosition().getY();
        int guideY = Mth.clamp(guideYSource, minecraft.level.getMinBuildHeight(), minecraft.level.getMaxBuildHeight() - 1);

        // 遍历范围内的所有区块，渲染边缘高亮
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                renderChunkEdgeHighlights(minecraft, poseStack, fillBuffer, lineBuffer, cx, cz, guideY);
            }
        }
    }

    /**
     * 渲染单个区块的边缘高亮
     * 
     * @param minecraft Minecraft客户端实例
     * @param poseStack 姿势栈
     * @param fillBuffer 填充缓冲区
     * @param lineBuffer 线条缓冲区
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @param guideY 引导线Y轴高度
     */
    private static void renderChunkEdgeHighlights(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer,
            int chunkX,
            int chunkZ,
            int guideY) {
        // 将区块坐标转换为世界坐标（每个区块16x16）
        int startX = chunkX << 4;  // 等同于 chunkX * 16
        int startZ = chunkZ << 4;
        int endX = startX + 15;
        int endZ = startZ + 15;

        // 优化：在区块级别检查加载状态，避免每个单元格重复检查
        if (minecraft.level != null && !minecraft.level.hasChunkAt(new BlockPos(startX, guideY, startZ))) {
            return;
        }

        // 根据区块坐标的奇偶性选择颜色（棋盘格效果）
        ChunkGuideColor color = chunkGuideColor(chunkX, chunkZ);

        // 渲染区块四条边的所有方块单元格
        // 上下边（完整行）
        for (int x = startX; x <= endX; x++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, startZ, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, endZ, guideY, color);
        }
        // 左右边（排除角点，避免重复渲染）
        for (int z = startZ + 1; z < endZ; z++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, startX, z, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, endX, z, guideY, color);
        }
    }

    /**
     * 渲染单个单元格的引导高亮（填充+边框）
     * 
     * @param poseStack 姿势栈
     * @param fillBuffer 填充缓冲区
     * @param lineBuffer 线条缓冲区
     * @param x 世界X坐标
     * @param z 世界Z坐标
     * @param guideY Y轴高度
     * @param color 颜色配置
     */
    private static void renderChunkGuideCell(
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer,
            int x,
            int z,
            int guideY,
            ChunkGuideColor color) {
        // 向内收缩0.04单位，使相邻单元格之间产生间隙
        double inset = 0.04D;
        double minX = x + inset;
        double minY = guideY + inset;
        double minZ = z + inset;
        double maxX = x + 1.0D - inset;
        double maxY = guideY + 1.0D - inset;
        double maxZ = z + 1.0D - inset;

        // 绘制半透明填充
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                fillBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                color.r(), color.g(), color.b(), color.a());

        // 绘制边框线（颜色比填充稍亮）
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
     * 根据区块坐标生成棋盘格颜色
     * 偶数区块使用青蓝色，奇数区块使用金黄色
     * 
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @return 颜色配置
     */
    private static ChunkGuideColor chunkGuideColor(int chunkX, int chunkZ) {
        return ((chunkX ^ chunkZ) & 1) == 0
                ? new ChunkGuideColor(0.16F, 0.78F, 1.0F, 0.24F)   // 青蓝色
                : new ChunkGuideColor(1.0F, 0.88F, 0.16F, 0.22F);  // 金黄色
    }

    /**
     * 颜色记录类，存储RGBA值
     */
    private record ChunkGuideColor(float r, float g, float b, float a) {
    }
}
