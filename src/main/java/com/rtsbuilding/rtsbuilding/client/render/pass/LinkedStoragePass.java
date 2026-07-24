package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.PerformanceConfig;
import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;


public final class LinkedStoragePass implements RenderPass {

    private static final double LINE_OFFSET = 0.002D;

    
    public static int bidirectionalColor = 0xFF4CAF50;
    
    public static int extractOnlyColor = 0xFFFF4CD1;

    

    private static final CornerBracketRenderer.Rgb biColor = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.Rgb extColor = new CornerBracketRenderer.Rgb();

    
    private static final float DEPTH_ALPHA = 0.70F;
    
    private static final float NO_DEPTH_ALPHA = 0.25F;
    
    private static final float FOG_ALPHA = 0.10F;

    
    private static final long ANIM_DURATION_MS = 300L;

    

    
    private final Map<BlockPos, AnimState> animStates = new HashMap<>();
    
    private Set<BlockPos> prevPositions = Collections.emptySet();
    
    private boolean initialized = false;

    
    private static final class AnimState {
        enum Phase { BINDING, BOUND, UNBINDING }

        Phase phase;
        long startTime;

        
        AABB bounds;

        
        float targetR, targetG, targetB;
        
        float currentR, currentG, currentB;
        
        boolean colorsSet;

        AnimState(Phase phase, long now) {
            this.phase = phase;
            this.startTime = now;
        }

        float progress(long now) {
            return Math.min(1.0F, (float) (now - startTime) / (float) ANIM_DURATION_MS);
        }
    }

    

    @Override
    public boolean shouldRender(Minecraft mc) {
        
        if (!(mc.screen instanceof BuilderScreen screen)) return false;
        return screen.isBindModeActive()
            && isConfigSafe() && PerformanceConfig.shouldRenderStorageLinks();
    }
    
    private boolean isConfigSafe() {
        try {
            PerformanceConfig.shouldRenderStorageLinks();
            return true;
        } catch (IllegalStateException e) {
            
            return true; 
        }
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;

        RtsClientKernel kernel = CompositionRoot.get().kernel();
        StorageModule sm = kernel.module(StorageModule.class);
        if (sm == null) return;

        var entries = sm.getLinkedStorageEntries();
        if (entries == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        long now = System.currentTimeMillis();

        
        Set<BlockPos> currPositions = new HashSet<>();
        for (LinkedStorageEntry e : entries) {
            if (e.worldAvailable() && e.pos() != null) currPositions.add(e.pos());
        }

        if (!initialized) {
            
            for (BlockPos p : currPositions) {
                if (!mc.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
                BlockState st = mc.level.getBlockState(p);
                if (st.isAir()) continue;
                AnimState a = new AnimState(AnimState.Phase.BOUND, now);
                a.bounds = computeStorageBounds(mc.level, p, st);
                animStates.put(p, a);
            }
            prevPositions = new HashSet<>(currPositions);
            initialized = true;
            return; 
        }

        
        for (BlockPos p : prevPositions) {
            if (!currPositions.contains(p)) {
                AnimState existing = animStates.get(p);
                if (existing != null && existing.bounds != null) {
                    AnimState ub = new AnimState(AnimState.Phase.UNBINDING, now);
                    ub.bounds = existing.bounds;
                    ub.targetR = existing.targetR;
                    ub.targetG = existing.targetG;
                    ub.targetB = existing.targetB;
                    ub.currentR = existing.currentR;
                    ub.currentG = existing.currentG;
                    ub.currentB = existing.currentB;
                    ub.colorsSet = existing.colorsSet;
                    animStates.put(p, ub);
                }
            }
        }

        
        for (LinkedStorageEntry e : entries) {
            if (!e.worldAvailable()) continue;
            BlockPos p = e.pos();
            if (p == null || prevPositions.contains(p)) continue;
            AnimState existing = animStates.get(p);
            if (existing != null && existing.phase == AnimState.Phase.UNBINDING) {
                
                animStates.put(p, new AnimState(AnimState.Phase.BINDING, now));
            } else if (!animStates.containsKey(p)) {
                animStates.put(p, new AnimState(AnimState.Phase.BINDING, now));
            }
        }

        prevPositions = new HashSet<>(currPositions);

        
        for (Iterator<Map.Entry<BlockPos, AnimState>> it = animStates.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, AnimState> e = it.next();
            AnimState a = e.getValue();
            BlockPos p = e.getKey();

            switch (a.phase) {
                case BINDING:
                    
                    if (currPositions.contains(p) && mc.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                        BlockState st = mc.level.getBlockState(p);
                        if (!st.isAir()) {
                            a.bounds = computeStorageBounds(mc.level, p, st);
                        }
                    }
                    if (a.progress(now) >= 1.0F) {
                        a.phase = AnimState.Phase.BOUND;
                        a.startTime = now;
                    }
                    break;
                case UNBINDING:
                    if (a.progress(now) >= 1.0F) {
                        
                        it.remove();
                    }
                    break;
                case BOUND:
                    if (!currPositions.contains(p)) {
                        it.remove();
                    }
                    break;
            }
        }

        
        for (LinkedStorageEntry entry : entries) {
            if (!entry.worldAvailable()) continue;
            BlockPos pos = entry.pos();
            if (pos == null || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;

            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;

            AABB fullBounds = computeStorageBounds(mc.level, pos, state);

            
            biColor.update(bidirectionalColor);
            extColor.update(extractOnlyColor);
            float targetR = entry.isExtractOnly() ? extColor.r : biColor.r;
            float targetG = entry.isExtractOnly() ? extColor.g : biColor.g;
            float targetB = entry.isExtractOnly() ? extColor.b : biColor.b;

            AnimState a = animStates.get(pos);
            if (a != null) {
                if (!a.colorsSet) {
                    
                    a.currentR = a.targetR = targetR;
                    a.currentG = a.targetG = targetG;
                    a.currentB = a.targetB = targetB;
                    a.colorsSet = true;
                } else {
                    
                    float lerpSpeed = 0.15F;
                    a.currentR += (targetR - a.currentR) * lerpSpeed;
                    a.currentG += (targetG - a.currentG) * lerpSpeed;
                    a.currentB += (targetB - a.currentB) * lerpSpeed;
                    a.targetR = targetR;
                    a.targetG = targetG;
                    a.targetB = targetB;
                }
            }

            float renderR = a != null ? a.currentR : targetR;
            float renderG = a != null ? a.currentG : targetG;
            float renderB = a != null ? a.currentB : targetB;

            
            AABB renderBounds = getAnimatedBounds(pos, fullBounds, now);

            double distance = cameraPos.distanceTo(renderBounds.getCenter());
            
            
            try {
                if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                    distance > PerformanceConfig.getMaxRenderDistance()) {
                    continue; 
                }
            } catch (IllegalStateException e) {
                
            }

            
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    renderBounds.minX, renderBounds.minY, renderBounds.minZ,
                    renderBounds.maxX, renderBounds.maxY, renderBounds.maxZ,
                    renderR, renderG, renderB, FOG_ALPHA);

            
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    renderR, renderG, renderB, DEPTH_ALPHA, distance);

            
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    renderR, renderG, renderB, NO_DEPTH_ALPHA, distance);
        }

        
        for (Map.Entry<BlockPos, AnimState> ae : animStates.entrySet()) {
            AnimState a = ae.getValue();
            if (a.phase != AnimState.Phase.UNBINDING) continue;

            
            float t = 1.0F - a.progress(now);
            AABB renderBounds = expandBoundsFromCenter(a.bounds, t);

            double distance = cameraPos.distanceTo(renderBounds.getCenter());
            
            
            try {
                if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                    distance > PerformanceConfig.getMaxRenderDistance()) {
                    continue; 
                }
            } catch (IllegalStateException e) {
                
            }

            
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    renderBounds.minX, renderBounds.minY, renderBounds.minZ,
                    renderBounds.maxX, renderBounds.maxY, renderBounds.maxZ,
                    a.currentR, a.currentG, a.currentB, FOG_ALPHA);

            
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    a.currentR, a.currentG, a.currentB, DEPTH_ALPHA, distance);

            
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    a.currentR, a.currentG, a.currentB, NO_DEPTH_ALPHA, distance);
        }
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8; 
    }

    

    
    public void clearAnimationState() {
        this.animStates.clear();
        this.prevPositions = Collections.emptySet();
        this.initialized = false;
    }

    

    
    private AABB getAnimatedBounds(BlockPos pos, AABB fullBounds, long now) {
        AnimState a = animStates.get(pos);
        if (a == null || a.phase != AnimState.Phase.BINDING) return fullBounds;
        return expandBoundsFromCenter(fullBounds, a.progress(now));
    }

    
    private static AABB expandBoundsFromCenter(AABB bounds, float t) {
        float clamped = Math.min(1.0F, Math.max(0.0F, t));
        double s = 1.0 - Math.pow(1.0 - clamped, 3); 
        double cx = (bounds.minX + bounds.maxX) * 0.5;
        double cy = (bounds.minY + bounds.maxY) * 0.5;
        double cz = (bounds.minZ + bounds.maxZ) * 0.5;
        return new AABB(
                cx + (bounds.minX - cx) * s,
                cy + (bounds.minY - cy) * s,
                cz + (bounds.minZ - cz) * s,
                cx + (bounds.maxX - cx) * s,
                cy + (bounds.maxY - cy) * s,
                cz + (bounds.maxZ - cz) * s
        );
    }

    
    public static AABB computeStorageBounds(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType != ChestType.SINGLE) {
                var connectedDir = ChestBlock.getConnectedDirection(state);
                BlockPos connectedPos = pos.relative(connectedDir);
                if (level.hasChunk(connectedPos.getX() >> 4, connectedPos.getZ() >> 4)) {
                    BlockState connectedState = level.getBlockState(connectedPos);
                    if (!connectedState.isAir() && connectedState.getBlock() instanceof ChestBlock) {
                        double minX = Math.min(pos.getX(), connectedPos.getX());
                        double minY = Math.min(pos.getY(), connectedPos.getY());
                        double minZ = Math.min(pos.getZ(), connectedPos.getZ());
                        double maxX = Math.max(pos.getX(), connectedPos.getX()) + 1;
                        double maxY = Math.max(pos.getY(), connectedPos.getY()) + 1;
                        double maxZ = Math.max(pos.getZ(), connectedPos.getZ()) + 1;
                        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
                    }
                }
            }
        }
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
    }
}
