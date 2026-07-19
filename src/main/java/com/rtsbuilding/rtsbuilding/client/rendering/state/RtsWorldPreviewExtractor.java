package com.rtsbuilding.rtsbuilding.client.rendering.state;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.rendering.RtsVisualOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostBoundsFilter;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从当前 BuilderScreen 提取方块模型虚影，不执行任何几何提交。
 */
public final class RtsWorldPreviewExtractor {
    private static final float BUILD_ALPHA = 0.80F;
    private static final float BLUEPRINT_ALPHA = 0.30F;

    private RtsWorldPreviewExtractor() {
    }

    public static RtsWorldPreviewSnapshot capture(Minecraft minecraft, Vec3 cameraPosition) {
        if (minecraft == null || minecraft.level == null
                || !(minecraft.screen instanceof BuilderScreen screen)) {
            return RtsWorldPreviewSnapshot.EMPTY;
        }

        List<RtsWorldPreviewSnapshot.ModelGhost> ghosts = new ArrayList<>();
        collectSingleBlockBuildPreview(minecraft, screen, ghosts);
        collectBlueprintPreview(minecraft, screen, ghosts);
        collectPlacementAnimations(minecraft, ghosts);
        List<RtsRecordedGeometry.Batch> geometry =
                RtsVisualOverlayRenderer.capture(minecraft, cameraPosition);
        return ghosts.isEmpty() && geometry.isEmpty()
                ? RtsWorldPreviewSnapshot.EMPTY
                : new RtsWorldPreviewSnapshot(ghosts, geometry);
    }

    private static void collectSingleBlockBuildPreview(
            Minecraft minecraft,
            BuilderScreen screen,
            List<RtsWorldPreviewSnapshot.ModelGhost> out) {
        ShapeDataRecords.GhostPreview preview = screen.getShapeGhostPreview();
        if (preview == null
                || preview.destructive()
                || preview.chainDestroyPreview()
                || preview.blocks().size() != 1
                || !Config.isPlacementBlockGhostPreviewEnabled()) {
            return;
        }

        BlockPos pos = preview.blocks().getFirst();
        BlockState state = BuildGhostBlockStateResolver.resolve(minecraft, pos);
        addModelGhost(minecraft, out, state, pos, BUILD_ALPHA);
        expandMultiblockGhost(minecraft, out, state, pos, BUILD_ALPHA);
    }

    private static void collectBlueprintPreview(
            Minecraft minecraft,
            BuilderScreen screen,
            List<RtsWorldPreviewSnapshot.ModelGhost> out) {
        BlueprintGhostPreview preview = screen.getBlueprintGhostPreview();
        if (preview.blocks().isEmpty()) {
            return;
        }
        for (BlueprintPanel.BlueprintGhostBlock block
                : BlueprintGhostBoundsFilter.filter(preview.blocks())) {
            if (!block.missing()) {
                addModelGhost(minecraft, out, block.state(), block.pos(), BLUEPRINT_ALPHA);
            }
        }
    }

    private static void addModelGhost(
            Minecraft minecraft,
            List<RtsWorldPreviewSnapshot.ModelGhost> out,
            BlockState state,
            BlockPos pos,
            float alpha) {
        if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        out.add(new RtsWorldPreviewSnapshot.ModelGhost(
                state,
                pos,
                alpha,
                1.0F,
                LevelRenderer.getLightCoords(minecraft.level, pos),
                extractTintColors(minecraft, state, pos)));
    }

    private static void collectPlacementAnimations(
            Minecraft minecraft,
            List<RtsWorldPreviewSnapshot.ModelGhost> out) {
        for (PlacementAnimationRenderer.ModelAnimation animation
                : PlacementAnimationRenderer.captureModelAnimations()) {
            addModelGhost(
                    minecraft,
                    out,
                    animation.state(),
                    animation.pos(),
                    animation.alpha(),
                    animation.scale());
        }
    }

    private static void addModelGhost(
            Minecraft minecraft,
            List<RtsWorldPreviewSnapshot.ModelGhost> out,
            BlockState state,
            BlockPos pos,
            float alpha,
            float scale) {
        if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        out.add(new RtsWorldPreviewSnapshot.ModelGhost(
                state,
                pos,
                alpha,
                scale,
                LevelRenderer.getLightCoords(minecraft.level, pos),
                extractTintColors(minecraft, state, pos)));
    }

    /**
     * 颜色也在提取阶段读取，第三方树叶/草色回调因此拿到真实世界坐标；
     * Submit 阶段只消费已经冻结的整数颜色。
     */
    private static Map<Integer, Integer> extractTintColors(
            Minecraft minecraft, BlockState state, BlockPos pos) {
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(state.getSeed(pos)), parts);
        Map<Integer, Integer> colors = new HashMap<>();
        for (BlockStateModelPart part : parts) {
            collectPartTints(minecraft, state, pos, part, null, colors);
            for (Direction direction : Direction.values()) {
                collectPartTints(minecraft, state, pos, part, direction, colors);
            }
        }
        return colors;
    }

    private static void collectPartTints(
            Minecraft minecraft,
            BlockState state,
            BlockPos pos,
            BlockStateModelPart part,
            Direction direction,
            Map<Integer, Integer> colors) {
        for (BakedQuad quad : part.getQuads(direction)) {
            int tintIndex = quad.materialInfo().tintIndex();
            if (tintIndex < 0 || colors.containsKey(tintIndex)) {
                continue;
            }
            BlockTintSource source = minecraft.getBlockColors().getTintSource(state, tintIndex);
            int color = source == null
                    ? 0xFFFFFFFF
                    : source.colorInWorld(state, minecraft.level, pos);
            colors.put(tintIndex, color);
        }
    }

    private static void expandMultiblockGhost(
            Minecraft minecraft,
            List<RtsWorldPreviewSnapshot.ModelGhost> out,
            BlockState state,
            BlockPos pos,
            float alpha) {
        if (state == null) {
            return;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = state.setValue(
                    BlockStateProperties.DOUBLE_BLOCK_HALF,
                    half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            addModelGhost(minecraft, out, otherState, otherPos, alpha);
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos otherPos = part == BedPart.FOOT
                    ? pos.relative(facing)
                    : pos.relative(facing.getOpposite());
            BlockState otherState = state.setValue(
                    BlockStateProperties.BED_PART,
                    part == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT);
            addModelGhost(minecraft, out, otherState, otherPos, alpha);
        }
    }
}
