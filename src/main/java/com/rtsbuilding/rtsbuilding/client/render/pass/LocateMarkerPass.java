package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 位置标记渲染 Pass。
 *
 * <p>为每个开启了位置显示的已链接容器绘制红色闪烁角支架线框和摄像机连线，
 * 帮助玩家在复杂场景中快速定位容器位置。</p>
 */
public final class LocateMarkerPass implements RenderPass {

    /** 红色闪烁周期（毫秒） */
    private static final long BLINK_PERIOD_MS = 400L;

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.level != null && mc.getCameraEntity() != null;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;

        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return;

        var trackedPositions = sm.getLocationDisplayPositions();
        if (trackedPositions.isEmpty()) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        long now = System.currentTimeMillis();

        // 闪烁 alpha：正弦振荡 0.4 ~ 1.0
        float blinkFactor = (float) (Math.sin(now * Math.PI * 2.0 / BLINK_PERIOD_MS) * 0.3 + 0.7);
        float alpha = Math.max(0.4f, Math.min(1.0f, blinkFactor));

        VertexConsumer lines = alloc.lines();
        VertexConsumer brackets = alloc.brackets();
        VertexConsumer noDepth = alloc.noDepth();

        for (BlockPos pos : trackedPositions) {
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;

            AABB bounds = LinkedStoragePass.computeStorageBounds(mc.level, pos, state);
            Vec3 center = bounds.getCenter();
            double distance = cameraPos.distanceTo(center);

            // ---- 红色闪烁角支架线框（深度层 + 穿墙层） ----
            CornerBracketRenderer.renderCornerBrackets(poseStack, brackets,
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    1.0f, 0.1f, 0.1f, alpha * 0.8f, distance, 2.0);

            CornerBracketRenderer.renderCornerBrackets(poseStack, noDepth,
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    1.0f, 0.1f, 0.1f, alpha * 0.3f, distance, 2.0);

            // ---- 摄像机→容器中心的红色连线 ----
            float lineAlpha = alpha * 0.6f;
            double dx = center.x - cameraPos.x;
            double dy = center.y - cameraPos.y;
            double dz = center.z - cameraPos.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.01) continue;
            float nx = (float) (dx / len);
            float ny = (float) (dy / len);
            float nz = (float) (dz / len);

            var pose = poseStack.last();
            lines.addVertex(pose, (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z)
                    .setColor(1.0f, 0.1f, 0.1f, lineAlpha)
                    .setNormal(pose, nx, ny, nz);
            lines.addVertex(pose, (float) center.x, (float) center.y, (float) center.z)
                    .setColor(1.0f, 0.1f, 0.1f, lineAlpha)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public int requiredBuffers() {
        return 1 | 4 | 8; // LINES | BRACKET_QUADS | TARGET_NO_DEPTH
    }
}
