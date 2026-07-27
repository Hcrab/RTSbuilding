package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.PerformanceConfig;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BoxSelectionPass implements RenderPass {

    
    private static final double OFFSET = 0.01D;
    
    private static final double OVERLAY_OFFSET = 0.02D;
    private static final float DEPTH_ALPHA = 0.9f;
    
    private static final double FLOW_SPEED = 0.02D;

    
    public static boolean depthTestEnabled = true;

    
    public static boolean flowAnimationEnabled = true;

    

    
    public static int selectionColor = 0xFFFFFFFF;
    
    public static int previewOverlayColor = 0xFF4D80FF;
    
    public static int selectionGapColor = 0xFF000000;
    
    public static int entitySelectionColor = 0xFF4CAF50;

    

    private static final CornerBracketRenderer.Rgb selColor = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.Rgb gapColor = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.Rgb overlayColor = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.Rgb entitySelColor = new CornerBracketRenderer.Rgb();

    private final BoxSelector selector;
    private final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    
    private record BoxAABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    

    
    private record CachedGroup(float r, float g, float b,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {}

    
    private BlockPos cachedScanMin;
    private BlockPos cachedScanMax;
    
    private List<CachedGroup> cachedRenderData = List.of();

    public BoxSelectionPass(BoxSelector selector) {
        this.selector = selector;
    }

    
    public void clearCache() {
        cachedScanMin = null;
        cachedScanMax = null;
        cachedRenderData = List.of();
        smoothTarget.reset();
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen
            && isConfigSafe() && PerformanceConfig.shouldRenderBoxSelection();
    }
    
    private boolean isConfigSafe() {
        try {
            PerformanceConfig.shouldRenderBoxSelection();
            return true;
        } catch (IllegalStateException e) {
            
            return true; 
        }
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (!(mc.screen instanceof BuilderScreen screen)) return;

        
        if (screen.isClickButtonSelected()) {
            selector.reset();
            clearCache();
            return;
        }

        BlockPos hover = selector.getHoverPos();
        BoxSelector.Phase phase = selector.getPhase();

        BoxAABB box = computeBoxAABB(phase, hover);
        if (box == null) return;

        double flowOffset = (phase == BoxSelector.Phase.COMPLETE && flowAnimationEnabled)
                ? (frameIndex * FLOW_SPEED) % 0.5D : 0;

        
        double tMinX = box.minX() - OFFSET;
        double tMinY = box.minY() - OFFSET;
        double tMinZ = box.minZ() - OFFSET;
        double tMaxX = box.maxX() + OFFSET;
        double tMaxY = box.maxY() + OFFSET;
        double tMaxZ = box.maxZ() + OFFSET;

        
        smoothTarget.update(tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ);

        
        var camera = mc.getCameraEntity();
        if (camera == null) return;
        Vec3 cameraPos = camera.getEyePosition(partialTick);
        double distance = smoothTarget.centerDistanceTo(cameraPos);
        
        
        try {
            if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                distance > PerformanceConfig.getMaxRenderDistance()) {
                return;
            }
        } catch (IllegalStateException e) {
            
        }

        
        selColor.update(selectionColor);
        gapColor.update(selectionGapColor);
        float selR = selColor.r, selG = selColor.g, selB = selColor.b;
        float gapR = gapColor.r, gapG = gapColor.g, gapB = gapColor.b;
        CornerBracketRenderer.renderDashedCornerBrackets(poseStack, alloc.brackets(),
                smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                selR, selG, selB, gapR, gapG, gapB, DEPTH_ALPHA, distance, flowOffset);
        if (depthTestEnabled) {
            CornerBracketRenderer.renderDashedCornerBrackets(poseStack, alloc.noDepth(),
                    smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                    selR, selG, selB, gapR, gapG, gapB, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, distance, flowOffset);
        }

        
        if (phase != BoxSelector.Phase.IDLE && phase != BoxSelector.Phase.COMPLETE) {
            overlayColor.update(previewOverlayColor);
            float ovR = overlayColor.r, ovG = overlayColor.g, ovB = overlayColor.b;
            
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                    ovR, ovG, ovB, 0.18f);
            if (depthTestEnabled) {
                
                CornerBracketRenderer.renderFilledFaces(alloc.noDepth(), poseStack,
                        smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                        smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                        ovR, ovG, ovB, 0.06f);
            }
        }

        
        if (phase == BoxSelector.Phase.COMPLETE) {
            renderBlockOverlay(mc, alloc, poseStack, box);
            renderEntityBrackets(mc, alloc, poseStack, partialTick);
            
            if (screen.isBindModeActive()) {
                renderContainerBrackets(mc, alloc, poseStack, box);
            }
        }
    }

    
    private void renderBlockOverlay(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, BoxAABB box) {
        var level = mc.level;
        if (level == null) return;

        BlockPos minCorner = selector.getMinCorner();
        BlockPos maxCorner = selector.getMaxCorner();
        if (minCorner == null || maxCorner == null) return;

        
        if (!minCorner.equals(cachedScanMin) || !maxCorner.equals(cachedScanMax)) {
            cachedScanMin = minCorner.immutable();
            cachedScanMax = maxCorner.immutable();
            cachedRenderData = scanGroups(level, minCorner, maxCorner);
        }

        
        for (var g : cachedRenderData) {
            
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    g.minX(), g.minY(), g.minZ(), g.maxX(), g.maxY(), g.maxZ(),
                    g.r(), g.g(), g.b(), 0.12f);
            
            if (depthTestEnabled) {
                CornerBracketRenderer.renderFilledFaces(alloc.noDepth(), poseStack,
                        g.minX(), g.minY(), g.minZ(), g.maxX(), g.maxY(), g.maxZ(),
                        g.r(), g.g(), g.b(), CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA);
            }
        }
    }

    
    private List<CachedGroup> scanGroups(net.minecraft.world.level.Level level, BlockPos minCorner, BlockPos maxCorner) {
        int minX = minCorner.getX();
        int minY = minCorner.getY();
        int minZ = minCorner.getZ();
        int maxX = maxCorner.getX();
        int maxY = maxCorner.getY();
        int maxZ = maxCorner.getZ();

        
        var reg = BuiltInRegistries.BLOCK;
        
        Map<Integer, GroupBounds> groups = new HashMap<>(32);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    var state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.isAir()) continue;
                    int id = reg.getId(state.getBlock());
                    var group = groups.computeIfAbsent(id, k -> new GroupBounds());
                    group.expand(x, y, z);
                    group.recordState(state);
                }
            }
        }

        if (groups.isEmpty()) return List.of();

        double off = OVERLAY_OFFSET;
        var result = new ArrayList<CachedGroup>(groups.size());

        for (var entry : groups.entrySet()) {
            var bounds = entry.getValue();
            float[] rgb = bounds.getMapColorRGB(level);

            result.add(new CachedGroup(rgb[0], rgb[1], rgb[2],
                    bounds.minX - off, bounds.minY - off, bounds.minZ - off,
                    bounds.maxX + off, bounds.maxY + off, bounds.maxZ + off));
        }

        return result;
    }

    
    private static final class GroupBounds {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        BlockState firstState;

        void expand(int x, int y, int z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x + 1);
            maxY = Math.max(maxY, y + 1);
            maxZ = Math.max(maxZ, z + 1);
        }

        void recordState(BlockState state) {
            if (firstState == null) firstState = state;
        }

        
        float[] getMapColorRGB(net.minecraft.world.level.Level level) {
            if (firstState == null) return new float[]{0.3f, 0.5f, 1.0f}; 
            int rgb = firstState.getMapColor(level, BlockPos.ZERO).col;
            return new float[]{
                    ((rgb >> 16) & 0xFF) / 255.0f,
                    ((rgb >> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f
            };
        }
    }

    

    
    private void renderEntityBrackets(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick) {
        if (mc.level == null) return;

        entitySelColor.update(entitySelectionColor);

        BlockPos min = selector.getMinCorner();
        BlockPos max = selector.getMaxCorner();
        if (min == null || max == null) return;

        AABB selectionBox = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ());

        List<Entity> entities = mc.level.getEntities((Entity) null, selectionBox, e -> true);
        for (Entity entity : entities) {
            if (!entity.getBoundingBox().intersects(selectionBox)) continue;

            
            double renderX = Mth.lerp(partialTick, entity.xo, entity.getX());
            double renderY = Mth.lerp(partialTick, entity.yo, entity.getY());
            double renderZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
            var bounds = entity.getBoundingBox()
                    .move(renderX - entity.getX(), renderY - entity.getY(), renderZ - entity.getZ())
                    .inflate(0.03D);
            
            
            var camera = mc.getCameraEntity();
            if (camera != null) {
                double distance = Math.sqrt(
                    Math.pow(renderX - camera.getX(), 2) +
                    Math.pow(renderY - camera.getY(), 2) +
                    Math.pow(renderZ - camera.getZ(), 2)
                );
                
                
                try {
                    if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                        distance > PerformanceConfig.getMaxRenderDistance()) {
                        continue; 
                    }
                } catch (IllegalStateException e) {
                    
                }
            }

            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    entitySelColor.r,
                    entitySelColor.g,
                    entitySelColor.b,
                    0.9f, 0);
            if (depthTestEnabled) {
                CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                        bounds.minX, bounds.minY, bounds.minZ,
                        bounds.maxX, bounds.maxY, bounds.maxZ,
                        entitySelColor.r,
                        entitySelColor.g,
                        entitySelColor.b,
                        CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, 0);
            }
        }
    }

    

    
    private void renderContainerBrackets(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, BoxAABB box) {
        if (mc.level == null) return;
        
        BlockPos min = selector.getMinCorner();
        BlockPos max = selector.getMaxCorner();
        if (min == null || max == null) return;

        
        float r = 0.24F, g = 0.55F, b = 1.00F;

        var camera = mc.getCameraEntity();
        var level = mc.level;

        for (int x = min.getX(); x < max.getX(); x++) {
            int cx = x >> 4;
            for (int z = min.getZ(); z < max.getZ(); z++) {
                int cz = z >> 4;
                
                if (!level.hasChunk(cx, cz)) continue;
                var chunk = level.getChunk(cx, cz);
                if (chunk.getBlockEntities().isEmpty()) continue;
                for (int y = min.getY(); y < max.getY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.hasBlockEntity()) continue;

                    double camDist = camera != null
                            ? Math.sqrt(camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
                            : 8.0;
                    
                    
                    try {
                        if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                            camDist > PerformanceConfig.getMaxRenderDistance()) {
                            continue; 
                        }
                    } catch (IllegalStateException e) {
                        
                    }

                    
                    CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                            pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01,
                            pos.getX() + 1.01, pos.getY() + 1.01, pos.getZ() + 1.01,
                            r, g, b, 0.6f, camDist);
                    
                    if (depthTestEnabled) {
                        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                                pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01,
                                pos.getX() + 1.01, pos.getY() + 1.01, pos.getZ() + 1.01,
                                r, g, b, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, camDist);
                    }
                }
            }
        }
    }

    

    
    private BoxAABB computeBoxAABB(BoxSelector.Phase phase, BlockPos hover) {
        return switch (phase) {
            case IDLE -> {
                if (hover == null) yield null;
                yield new BoxAABB(hover.getX(), hover.getY(), hover.getZ(),
                        hover.getX() + 1, hover.getY() + 1, hover.getZ() + 1);
            }
            case AWAITING_B -> {
                BlockPos a = selector.getPointA();
                if (a == null) yield null;
                if (hover != null) {
                    yield new BoxAABB(
                            Math.min(a.getX(), hover.getX()), Math.min(a.getY(), hover.getY()), Math.min(a.getZ(), hover.getZ()),
                            Math.max(a.getX() + 1, hover.getX() + 1), Math.max(a.getY() + 1, hover.getY() + 1), Math.max(a.getZ() + 1, hover.getZ() + 1));
                } else {
                    yield new BoxAABB(a.getX(), a.getY(), a.getZ(), a.getX() + 1, a.getY() + 1, a.getZ() + 1);
                }
            }
            case AWAITING_C -> {
                BlockPos a = selector.getPointA();
                BlockPos b = selector.getPointB();
                if (a == null || b == null) yield null;
                int offset = selector.getScrollHeightOffset();
                double baseBottom = Math.min(a.getY(), b.getY());
                double baseTop = Math.max(a.getY() + 1, b.getY() + 1);
                double previewMinY, previewMaxY;
                if (offset >= 0) {
                    
                    previewMinY = baseBottom;
                    previewMaxY = baseTop + offset;
                } else {
                    
                    previewMinY = baseBottom + offset;
                    previewMaxY = baseTop;
                }
                if (previewMaxY < previewMinY) previewMaxY = previewMinY;
                yield new BoxAABB(
                        Math.min(a.getX(), b.getX()), previewMinY, Math.min(a.getZ(), b.getZ()),
                        Math.max(a.getX() + 1, b.getX() + 1), previewMaxY, Math.max(a.getZ() + 1, b.getZ() + 1));
            }
            case COMPLETE -> {
                BlockPos min = selector.getMinCorner();
                BlockPos max = selector.getMaxCorner();
                if (min == null || max == null) yield null;
                yield new BoxAABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
            }
        };
    }
}
