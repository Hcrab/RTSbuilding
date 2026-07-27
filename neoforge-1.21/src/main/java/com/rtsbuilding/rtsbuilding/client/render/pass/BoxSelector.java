package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public final class BoxSelector {

    
    public enum Phase {
        IDLE,
        AWAITING_B,
        AWAITING_C,
        COMPLETE
    }

    private Phase phase = Phase.IDLE;
    private BlockPos pointA;
    private BlockPos pointB;
    private BlockPos pointC;

    
    private BlockPos hoverPos;

    
    private int scrollHeightOffset;

    

    public Phase getPhase() { return phase; }
    public BlockPos getPointA() { return pointA; }
    public BlockPos getPointB() { return pointB; }
    public BlockPos getPointC() { return pointC; }
    public BlockPos getHoverPos() { return hoverPos; }
    public int getScrollHeightOffset() { return scrollHeightOffset; }

    
    public BlockPos getMinCorner() {
        if (pointA == null) return null;
        int minX = pointA.getX();
        int minY = pointA.getY();
        int minZ = pointA.getZ();
        if (pointB != null) {
            minX = Math.min(minX, pointB.getX());
            minY = Math.min(minY, pointB.getY());
            minZ = Math.min(minZ, pointB.getZ());
        }
        if (pointC != null) {
            minY = Math.min(minY, pointC.getY());
        }
        return new BlockPos(minX, minY, minZ);
    }

    
    public BlockPos getMaxCorner() {
        if (pointA == null) return null;
        int maxX = pointA.getX() + 1;
        int maxY = pointA.getY() + 1;
        int maxZ = pointA.getZ() + 1;
        if (pointB != null) {
            maxX = Math.max(maxX, pointB.getX() + 1);
            maxY = Math.max(maxY, pointB.getY() + 1);
            maxZ = Math.max(maxZ, pointB.getZ() + 1);
        }
        if (pointC != null) {
            maxY = Math.max(maxY, pointC.getY() + 1);
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    

    
    public void updateHoverFromScreen(Minecraft mc, BuilderScreen screen, boolean ctrlDown) {
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray != null) {
            var blockHit = ray.raycastBlock(mc);
            if (blockHit != null) {
                setHoverPos(ctrlDown
                        ? blockHit.getBlockPos().relative(blockHit.getDirection())
                        : blockHit.getBlockPos());
                return;
            }
        }
        setHoverPos(null);
    }

    
    public void handleRightClickWithHover() {
        if (this.hoverPos == null) return;

        if (phase == Phase.COMPLETE) {
            
            if (isOutsideSelection(this.hoverPos)) {
                reset();
            }
            
            return;
        }

        handleRightClick(this.hoverPos);
    }

    
    private boolean isOutsideSelection(BlockPos pos) {
        BlockPos min = getMinCorner();
        BlockPos max = getMaxCorner();
        if (min == null || max == null) return true;
        return pos.getX() < min.getX() || pos.getX() >= max.getX()
                || pos.getY() < min.getY() || pos.getY() >= max.getY()
                || pos.getZ() < min.getZ() || pos.getZ() >= max.getZ();
    }

    
    public boolean handleScroll(double scrollY) {
        if (phase == Phase.AWAITING_C) {
            int delta = scrollY > 0 ? 1 : (scrollY < 0 ? -1 : 0);
            if (delta != 0) {
                adjustHeight(delta);
                return true;
            }
        }
        return false;
    }

    

    
    private void setHoverPos(@Nullable BlockPos pos) {
        this.hoverPos = pos;
    }

    
    private void adjustHeight(int delta) {
        if (phase == Phase.AWAITING_C) {
            this.scrollHeightOffset += delta;
        }
    }

    
    private boolean handleRightClick(BlockPos clicked) {
        if (clicked == null) return false;

        switch (phase) {
            case IDLE:
                pointA = clicked.immutable();
                phase = Phase.AWAITING_B;
                scrollHeightOffset = 0;
                return true;
            case AWAITING_B:
                pointB = clicked.immutable();
                phase = Phase.AWAITING_C;
                scrollHeightOffset = 0;
                return true;
            case AWAITING_C:
                
                if (pointA != null && pointB != null) {
                    int baseTopY = Math.max(pointA.getY(), pointB.getY());
                    pointC = new BlockPos(clicked.getX(), baseTopY + scrollHeightOffset, clicked.getZ());
                } else {
                    pointC = clicked.immutable();
                }
                phase = Phase.COMPLETE;
                return true;
            case COMPLETE:
                return false;
        }
        return false;
    }

    
    public void reset() {
        phase = Phase.IDLE;
        pointA = null;
        pointB = null;
        pointC = null;
        hoverPos = null;
        scrollHeightOffset = 0;
    }
}
