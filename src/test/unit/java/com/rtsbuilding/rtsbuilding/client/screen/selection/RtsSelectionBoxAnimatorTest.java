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
    void renderAutomaticallyRetargetsWhenLogicalBoxChanges() {
        AtomicLong clock = new AtomicLong(1_000L);
        RtsSelectionBoxAnimator animator = new RtsSelectionBoxAnimator(100L, clock::get);
        RtsCullingBox first = new RtsCullingBox(7, BlockPos.ZERO, new BlockPos(1, 1, 1));
        RtsCullingBox second = new RtsCullingBox(7, BlockPos.ZERO, new BlockPos(9, 1, 1));

        AABB initial = animator.renderAabb(first);
        clock.set(1_010L);
        AABB transitionStart = animator.renderAabb(second);
        assertEquals(initial.maxX, transitionStart.maxX, 1.0E-6D);

        clock.set(1_060L);
        AABB halfway = animator.renderAabb(second);
        assertTrue(halfway.maxX > initial.maxX && halfway.maxX < second.asAabb().maxX,
                "自动跟踪目标时应处于旧框和新框之间，而不是整格跳变");

        clock.set(1_120L);
        assertEquals(second.asAabb().maxX, animator.renderAabb(second).maxX, 1.0E-6D);
    }

    @Test
    void aNewTargetAfterCompletionStillStartsFromLastVisualBox() {
        AtomicLong clock = new AtomicLong(0L);
        RtsSelectionBoxAnimator animator = new RtsSelectionBoxAnimator(100L, clock::get);
        RtsCullingBox first = new RtsCullingBox(3, BlockPos.ZERO, BlockPos.ZERO);
        RtsCullingBox second = new RtsCullingBox(3, BlockPos.ZERO, new BlockPos(4, 0, 0));

        animator.renderAabb(first);
        clock.set(200L);
        animator.renderAabb(first);
        clock.set(210L);

        assertEquals(first.asAabb().maxX, animator.renderAabb(second).maxX, 1.0E-6D,
                "动画完成后必须保留上一个端点，下一次变化才不会瞬移");
    }
}
