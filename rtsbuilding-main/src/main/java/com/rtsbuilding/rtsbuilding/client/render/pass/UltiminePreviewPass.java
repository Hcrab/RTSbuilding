package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.UltimineBlockMerger;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 连锁挖掘（Ultimine）预览边框渲染。
 *
 * <p>当左侧栏“连锁挖掘”按钮启用时，在鼠标指向的方块上执行与
 * {@code RtsUltimineCollector} 相同的 BFS 同类型方块收集，然后对外周方块
 * 合并为 AABB，用当前项目 {@link CornerBracketRenderer} 角括号风格渲染
 * 边框（深度 pass + 无深度 pass）。</p>
 *
 * <p>渲染链路参考连锁破坏（FTB Ultimine 风格）实现：
 * 收集 → 外周过滤 → AABB 合并 → 边框渲染。</p>
 */
public final class UltiminePreviewPass implements RenderPass {

    
    private static final int ULTIMINE_LIMIT = 256;

    
    private static final float HARDNESS_RATIO_LIMIT = 1.5F;

    
    private static final float BORDER_R = 1.00F;
    private static final float BORDER_G = 0.72F;
    private static final float BORDER_B = 0.24F;

    
    private static final float EDGE_ALPHA = 0.92F;

    
    private static final BlockPos[] FACE_OFFSETS = {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };

    @Override
    public boolean shouldRender(Minecraft mc) {
        if (!(mc.screen instanceof BuilderScreen screen)) return false;
        return screen.isBuildMode() && screen.isUltimineActive();
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null) return;
        if (!(mc.screen instanceof BuilderScreen screen)) return;
        if (!screen.isUltimineActive()) return;

        var cursorRay = alloc.cursorRay();
        if (cursorRay == null) return;
        var hit = cursorRay.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos seed = hit.getBlockPos();
        var level = mc.level;
        BlockState seedState = level.getBlockState(seed);
        if (seedState.isAir()) return;

        boolean creative = mc.player != null && mc.player.isCreative();
        List<BlockPos> targets = RtsUltimineCollector.collect(level, seed, ULTIMINE_LIMIT,
                (pos, state, original) -> {
                    if (state.isAir() || state.getBlock() != original.getBlock()) return false;
                    if (!creative && state.getDestroySpeed(level, pos) < 0.0F) return false;
                    // B1：与服务端 isUltimineCandidate 对齐的硬度比过滤（≤1.5x）
                    if (!creative) {
                        float seedSpeed = original.getDestroySpeed(level, pos);
                        float candSpeed = state.getDestroySpeed(level, pos);
                        if (seedSpeed >= 0.0F && candSpeed > seedSpeed * HARDNESS_RATIO_LIMIT) {
                            return false;
                        }
                    }
                    // B4/D4：动作半径（相机锚点 X/Z 半边长）内才预览，避免“高亮但点了没反应”
                    return isWithinActionRadius(pos);
                });
        if (targets.isEmpty()) return;

        List<BlockPos> outerBlocks = filterOuterBlocks(targets);
        if (outerBlocks.isEmpty()) return;

        
        // 合并相邻方块并提取外轮廓边（VoxelShape OR 组合消除内部共享边），
        // 使非长方体连通区域（L 形、环等）也呈现连续、完全合并的边框。
        List<UltimineBlockMerger.EdgeLine> edges = UltimineBlockMerger.getEdgeLines(outerBlocks);
        if (edges.isEmpty()) return;

        float r = BORDER_R;
        float g = BORDER_G;
        float b = BORDER_B;

        var camera = mc.getCameraEntity();
        Vec3 camPos = camera != null ? camera.getEyePosition(partialTick) : Vec3.ZERO;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : outerBlocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        double distance = camPos.distanceTo(new Vec3(
                (minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D));

        CornerBracketRenderer.renderEdges(poseStack, alloc.brackets(), edges,
                r, g, b, EDGE_ALPHA, distance);
        CornerBracketRenderer.renderEdges(poseStack, alloc.noDepth(), edges,
                r, g, b, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, distance);
    }

    
    private static boolean isWithinActionRadius(BlockPos pos) {
        RtsClientKernel kernel = RtsClientKernel.get();
        if (kernel == null) return true;
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null || !cam.isCameraEnabled()) return true;
        var state = cam.getState();
        double dx = (pos.getX() + 0.5D) - state.getAnchorX();
        double dz = (pos.getZ() + 0.5D) - state.getAnchorZ();
        double halfExtent = state.getMaxRadius();
        return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
    }

    
    private static List<BlockPos> filterOuterBlocks(List<BlockPos> blocks) {
        Set<BlockPos> allBlocks = new HashSet<>(blocks);
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (BlockPos pos : blocks) {
            boolean isOuter = false;
            for (BlockPos offset : FACE_OFFSETS) {
                if (!allBlocks.contains(pos.offset(offset))) {
                    isOuter = true;
                    break;
                }
            }
            if (isOuter) outerBlocks.add(pos);
        }
        return outerBlocks;
    }
}
