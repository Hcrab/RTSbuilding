package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LocateMarkerPass implements RenderPass {

    
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

            
            CornerBracketRenderer.renderCornerBrackets(poseStack, brackets,
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    1.0f, 0.1f, 0.1f, alpha * 0.8f, distance, 2.0);

            CornerBracketRenderer.renderCornerBrackets(poseStack, noDepth,
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    1.0f, 0.1f, 0.1f, alpha * 0.3f, distance, 2.0);

            
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
        return 1 | 4 | 8; 
    }
}
