package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class SelectionHighlight {

    
    public static final double INFLATE = 0.03D;

    @Nullable
    private Entity entity;
    @Nullable
    private BlockHitResult blockHit;

    
    private final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    

    
    public void clear() {
        this.entity = null;
        this.blockHit = null;
    }

    /**
     * 设置高亮目标。
     *
     * <p>优先级语义：实体优先——传入非空实体时方块命中被忽略（{@code blockHit} 置 null）；
     * 仅当实体为 null 时才接受方块命中。调用方需保证每次调用只表达一个意图，
     * 实体与方块不会同时高亮。</p>
     */
    public void set(@Nullable Entity entity, @Nullable BlockHitResult blockHit) {
        if (entity != null) {
            this.entity = entity;
            this.blockHit = null; 
        } else if (blockHit != null) {
            this.blockHit = blockHit;
        }
    }

    

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    @Nullable
    public BlockHitResult getBlockHit() {
        return this.blockHit;
    }

    

    
    @Nullable
    public AABB updateAndGetSmoothBounds() {
        if (this.entity != null) {
            AABB bounds = this.entity.getBoundingBox().inflate(INFLATE);
            smoothTarget.update(
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ);
            return new AABB(smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ());
        }
        if (this.blockHit != null) {
            var pos = this.blockHit.getBlockPos();
            double off = INFLATE;
            smoothTarget.update(
                    pos.getX() - off, pos.getY() - off, pos.getZ() - off,
                    pos.getX() + 1 + off, pos.getY() + 1 + off, pos.getZ() + 1 + off);
            return new AABB(smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ());
        }
        
        smoothTarget.reset();
        return null;
    }

    
    public double smoothCenterDistanceTo(Vec3 point) {
        return smoothTarget.centerDistanceTo(point);
    }
}
