package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.PerformanceConfig;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.ShaderState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class BoundaryPass implements RenderPass {

    
    private static final double FALLBACK_RADIUS = 250.0;

    
    private static final float TILE_SIZE = 2.0F;

    
    private static final float WHITE = 1.0F;

    
    public static int barrierColor = 0xFFFFCC00;

    
    private static final float BARRIER_ALPHA = 0.80F;

    
    private static final int FULL_BRIGHT = 0xF0;

    
    private static final float SCROLL_SPEED = 0.5F;

    
    private static final long DEFAULT_FALLBACK_RECALC_MS = 500;

    
    private static final float MAX_DELTA_MS = 200.0F;

    
    private static final float SCROLL_MOD = 256.0F;

    

    
    private float scrollOffset;

    
    private long lastFrameMillis = -1;

    

    
    private int cachedMinX = Integer.MIN_VALUE;
    private int cachedMinZ = Integer.MIN_VALUE;
    private int cachedMaxX = Integer.MIN_VALUE;
    private int cachedMaxZ = Integer.MIN_VALUE;

    
    private int cachedHighestY = Integer.MIN_VALUE;

    
    private long fallbackLastRecalc;
    
    
    private long getFallbackRecalcInterval() {
        return PerformanceConfig.getBoundaryScanCacheTimeout();
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.player != null 
            && mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen
            && isConfigSafe() && PerformanceConfig.shouldRenderBoundaryWalls()
            // Shader packs treat the translucent barrier walls as lit, shadow-sampled
            // geometry, turning the wall into a dark band artifact. Skip it while a
            // shader pack is active.
            && !ShaderState.isShaderPackActive();
    }
    
    private boolean isConfigSafe() {
        try {
            PerformanceConfig.shouldRenderBoundaryWalls();
            return true;
        } catch (IllegalStateException e) {
            
            return true; 
        }
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.player == null) return;
        RtsClientKernel kernel = RtsClientKernel.get();
        double r, cx, cy, cz;
        boolean useFallback;
        if (kernel.isRegionValid()) {
            cx = kernel.getRegionAnchorX();
            cy = kernel.getRegionAnchorY();
            cz = kernel.getRegionAnchorZ();
            r  = kernel.getRegionMaxRadius();
            useFallback = false;
        } else {
            cx = mc.player.getX();
            cy = mc.player.getY();
            cz = mc.player.getZ();
            r  = FALLBACK_RADIUS;
            useFallback = true;
        }
        
        
        var camera = mc.getCameraEntity();
        if (camera != null) {
            double dx = Math.abs(camera.getX() - cx) - r;
            double dz = Math.abs(camera.getZ() - cz) - r;
            double distanceToBoundary = Math.sqrt(
                Math.max(0, dx) * Math.max(0, dx) +
                Math.max(0, dz) * Math.max(0, dz)
            );
            
            
            try {
                if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                    distanceToBoundary > PerformanceConfig.getMaxRenderDistance()) {
                    return;
                }
            } catch (IllegalStateException e) {
                
            }
        }

        
        long now = System.currentTimeMillis();
        if (this.lastFrameMillis < 0) {
            this.lastFrameMillis = now;
        } else {
            float deltaMs = (float) (now - this.lastFrameMillis);
            if (deltaMs > MAX_DELTA_MS) deltaMs = MAX_DELTA_MS; 
            this.scrollOffset = (this.scrollOffset + deltaMs * SCROLL_SPEED / 1000.0F) % SCROLL_MOD;
            this.lastFrameMillis = now;
        }

        renderBarrierWalls(alloc, mc.level, poseStack, cx, cy, cz, r, useFallback, now);
    }

    
    private void renderBarrierWalls(BufferAllocator alloc, Level level, PoseStack poseStack,
                                     double ax, double ay, double az, double r,
                                     boolean useFallback, long now) {
        
        ensureBarrierColor();
        float minX = (float) (ax - r);
        float minZ = (float) (az - r);
        float maxX = (float) (ax + r);
        float maxZ = (float) (az + r);

        
        int highest = resolveHighestY(level, minX, minZ, maxX, maxZ, useFallback, now);
        float yMax = (highest > Integer.MIN_VALUE)
                ? highest + 5.0F
                : (float) ay + 3.0F;
        float yMin = (float) level.getMinBuildHeight();
        float wallH = yMax - yMin;

        var pose = poseStack.last();
        VertexConsumer barrier = alloc.barrier();

        float wallWX = maxX - minX;
        float wallWZ = maxZ - minZ;
        float scroll = this.scrollOffset;

        
        addTexturedQuad(pose, barrier, minX, yMin, minZ, maxX, yMax, minZ,
                wallWX / TILE_SIZE, wallH / TILE_SIZE,
                0.0F, 0.0F, 1.0F, scroll);

        
        addTexturedQuad(pose, barrier, maxX, yMin, maxZ, minX, yMax, maxZ,
                wallWX / TILE_SIZE, wallH / TILE_SIZE,
                0.0F, 0.0F, -1.0F, scroll);

        
        addTexturedQuad(pose, barrier, minX, yMin, minZ, minX, yMax, maxZ,
                wallWZ / TILE_SIZE, wallH / TILE_SIZE,
                1.0F, 0.0F, 0.0F, scroll);

        
        addTexturedQuad(pose, barrier, maxX, yMin, maxZ, maxX, yMax, minZ,
                wallWZ / TILE_SIZE, wallH / TILE_SIZE,
                -1.0F, 0.0F, 0.0F, scroll);
    }

    
    private int resolveHighestY(Level level, float minX, float minZ, float maxX, float maxZ,
                                boolean useFallback, long now) {
        int iminX = (int) Math.floor(minX);
        int iminZ = (int) Math.floor(minZ);
        int imaxX = (int) Math.floor(maxX);
        int imaxZ = (int) Math.floor(maxZ);

        if (!useFallback) {
            
            if (iminX != cachedMinX || iminZ != cachedMinZ ||
                imaxX != cachedMaxX || imaxZ != cachedMaxZ) {
                cachedHighestY = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
                cachedMinX = iminX;
                cachedMinZ = iminZ;
                cachedMaxX = imaxX;
                cachedMaxZ = imaxZ;
            }
            return cachedHighestY;
        } else {
            
            if (now - fallbackLastRecalc >= getFallbackRecalcInterval()) {
                cachedHighestY = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
                fallbackLastRecalc = now;
            }
            return cachedHighestY;
        }
    }

    
    private static int findHighestBoundaryBlock(Level level, float minX, float minZ, float maxX, float maxZ) {
        int highest = Integer.MIN_VALUE;
        int x1 = (int) Math.floor(minX);
        int x2 = (int) Math.floor(maxX);
        int z1 = (int) Math.floor(minZ);
        int z2 = (int) Math.floor(maxZ);

        
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z1);
            if (h > highest) highest = h;
        }
        
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z2);
            if (h > highest) highest = h;
        }
        
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x1, z);
            if (h > highest) highest = h;
        }
        
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x2, z);
            if (h > highest) highest = h;
        }

        return highest;
    }

    
    

    private static final CornerBracketRenderer.Rgb barrierRgb = new CornerBracketRenderer.Rgb();

    private static void ensureBarrierColor() {
        barrierRgb.update(barrierColor);
    }

    private static void addTexturedQuad(PoseStack.Pose pose, VertexConsumer buffer,
                                         float x1, float yMin, float z1,
                                         float x2, float yMax, float z2,
                                         float tileU, float tileV,
                                         float nx, float ny, float nz,
                                         float scroll) {
        
        buffer.addVertex(pose, x1, yMin, z1).setUv(scroll, scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
        
        buffer.addVertex(pose, x2, yMin, z2).setUv(tileU + scroll, scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
        
        buffer.addVertex(pose, x2, yMax, z2).setUv(tileU + scroll, tileV + scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
        
        buffer.addVertex(pose, x1, yMax, z1).setUv(scroll, tileV + scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
    }

    @Override
    public int requiredBuffers() {
        return 16; 
    }
}
