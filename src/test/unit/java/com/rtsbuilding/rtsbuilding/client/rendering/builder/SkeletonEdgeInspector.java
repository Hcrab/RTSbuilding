package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reflection bridge into the current merged skeleton implementation.
 *
 * <p>This keeps tests focused on the renderer's actual private geometry path
 * without widening the production API. The bridge must stay test-only: player
 * code should continue to reach skeletons through {@link MergedSkeletonRenderer}
 * render methods.</p>
 */
final class SkeletonEdgeInspector {

    private static final Method BUILD_MERGED_SKELETON = findBuildMergedSkeleton();
    private static final Method GET_CACHED_SKELETON = findGetCachedSkeleton();

    private SkeletonEdgeInspector() {
    }

    static Set<SkeletonEdgeKey> actualVisibleEdges(List<BlockPos> blocks) {
        try {
            ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                    blocks, true, true, List.of(), true, true);
            Object skeleton = BUILD_MERGED_SKELETON.invoke(null, preview);
            return edgeSet(skeleton);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect merged skeleton geometry", e);
        }
    }

    static ShapeDataRecords.GhostPreview preview(List<BlockPos> blocks) {
        return new ShapeDataRecords.GhostPreview(blocks, true, true, List.of(), true, true);
    }

    static void resetCachedSkeleton() {
        MergedSkeletonRenderer.clearCache();
    }

    static Set<SkeletonEdgeKey> cachedVisibleEdges(ShapeDataRecords.GhostPreview preview) {
        try {
            Object skeleton = GET_CACHED_SKELETON.invoke(null, preview);
            return edgeSet(skeleton);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect cached merged skeleton geometry", e);
        }
    }

    private static Set<SkeletonEdgeKey> edgeSet(Object skeleton) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<UltimineBlockMerger.EdgeLine> lines = (List<UltimineBlockMerger.EdgeLine>) readEdges(skeleton);

        Set<SkeletonEdgeKey> result = new HashSet<>(lines.size() * 2);
        for (UltimineBlockMerger.EdgeLine line : lines) {
            result.add(SkeletonEdgeKey.from(line));
        }
        return result;
    }

    private static Object readEdges(Object skeleton) throws ReflectiveOperationException {
        try {
            Method edgesMethod = skeleton.getClass().getDeclaredMethod("edges");
            edgesMethod.setAccessible(true);
            return edgesMethod.invoke(skeleton);
        } catch (NoSuchMethodException ignored) {
            Field edgesField = skeleton.getClass().getDeclaredField("edges");
            edgesField.setAccessible(true);
            return edgesField.get(skeleton);
        }
    }

    private static Method findBuildMergedSkeleton() {
        try {
            Method method = MergedSkeletonRenderer.class.getDeclaredMethod(
                    "build", ShapeDataRecords.GhostPreview.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method findGetCachedSkeleton() {
        try {
            Method method = MergedSkeletonRenderer.class.getDeclaredMethod(
                    "getCachedSkeleton", ShapeDataRecords.GhostPreview.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
