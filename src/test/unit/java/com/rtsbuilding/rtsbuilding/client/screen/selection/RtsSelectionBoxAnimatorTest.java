package com.rtsbuilding.rtsbuilding.client.screen.selection;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsSelectionBoxAnimatorTest {
    @Test
    void resizeInterpolatesInsteadOfJumpingOneBlockPerFrame() {
        AtomicLong now = new AtomicLong(0L);
        RtsSelectionBoxAnimator animator = new RtsSelectionBoxAnimator(100L, now::get);
        RtsCullingBox from = new RtsCullingBox(7, BlockPos.ZERO, BlockPos.ZERO);
        RtsCullingBox to = new RtsCullingBox(7, BlockPos.ZERO, new BlockPos(4, 2, 0));

        animator.renderAabb(from);
        animator.animate(from, to);
        now.set(50L);
        AABB halfway = animator.renderAabb(to);

        assertTrue(halfway.maxX > from.asAabb().maxX);
        assertTrue(halfway.maxX < to.asAabb().maxX);
        assertTrue(halfway.maxY > from.asAabb().maxY);
        assertTrue(halfway.maxY < to.asAabb().maxY);

        now.set(100L);
        assertEquals(to.asAabb(), animator.renderAabb(to));
    }

    @Test
    void retargetingMidAnimationStartsFromCurrentVisualBox() {
        AtomicLong now = new AtomicLong(0L);
        RtsSelectionBoxAnimator animator = new RtsSelectionBoxAnimator(100L, now::get);
        RtsCullingBox first = new RtsCullingBox(3, BlockPos.ZERO, BlockPos.ZERO);
        RtsCullingBox second = new RtsCullingBox(3, BlockPos.ZERO, new BlockPos(4, 0, 0));
        RtsCullingBox third = new RtsCullingBox(3, BlockPos.ZERO, new BlockPos(8, 0, 0));

        animator.renderAabb(first);
        animator.animate(first, second);
        now.set(40L);
        AABB beforeRetarget = animator.renderAabb(second);
        animator.animate(second, third);
        AABB afterRetarget = animator.renderAabb(third);

        assertEquals(beforeRetarget.maxX, afterRetarget.maxX, 0.0001D);
    }
}
