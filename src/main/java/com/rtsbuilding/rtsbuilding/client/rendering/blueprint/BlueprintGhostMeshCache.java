package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Function;

/**
 * 蓝图网格生命周期与移动失效策略。
 * 移动时复用已有网格；停稳后在后台无关的渲染线程预算中分批刷新位置相关取色，
 * 替换完成前保留旧网格，最多同时持有一份显示网格和一份替换网格。
 */
final class BlueprintGhostMeshCache implements AutoCloseable {
    private static final long SETTLE_NANOS = 150_000_000L;
    private final Function<Input, BlueprintGhostMesh> factory;
    private Object world;
    private List<BlueprintGhostBlock> geometry;
    private BlueprintPreviewClip clip;
    private BlockPos observedAnchor;
    private long lastMove;
    private BlueprintGhostMesh current;
    private BlueprintGhostMesh replacement;

    BlueprintGhostMeshCache() {
        this(input -> new BlueprintGhostMesh(input.blocks(), input.clip(), input.anchor()));
    }

    BlueprintGhostMeshCache(Function<Input, BlueprintGhostMesh> factory) {
        this.factory = factory;
    }

    void prepare(Object currentWorld, BlueprintGhostPreview preview,
            BlueprintPreviewClip currentClip, long now) {
        if (world != currentWorld || geometry != preview.localBlocks()
                || !currentClip.equals(clip)) {
            close();
            world = currentWorld;
            geometry = preview.localBlocks();
            clip = currentClip;
            observedAnchor = preview.anchor();
            lastMove = now;
            current = factory.apply(new Input(geometry, clip, observedAnchor));
        }
        if (!observedAnchor.equals(preview.anchor())) {
            observedAnchor = preview.anchor();
            lastMove = now;
            if (replacement != null) {
                replacement.close();
                replacement = null;
            }
        }
        if (replacement != null && replacement.complete()) {
            if (current != null) current.close();
            current = replacement;
            replacement = null;
        }
        if (current.complete() && replacement == null
                && !current.sampleAnchor().equals(observedAnchor)
                && now - lastMove >= SETTLE_NANOS) {
            replacement = factory.apply(new Input(geometry, clip, observedAnchor));
        }
    }

    BlueprintGhostMesh visible() { return current; }
    BlueprintGhostMesh building() { return replacement == null ? current : replacement; }

    @Override
    public void close() {
        if (current != null) current.close();
        if (replacement != null) replacement.close();
        current = replacement = null;
        world = null;
        geometry = null;
        clip = null;
        observedAnchor = null;
    }

    record Input(List<BlueprintGhostBlock> blocks, BlueprintPreviewClip clip, BlockPos anchor) { }
}
