package com.rtsbuilding.rtsbuilding.client.rendering.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * 世界预览在渲染状态提取阶段生成的不可变快照。
 *
 * <p>它不携带 NeoForge collector、VertexConsumer 或共享 buffer。平台桥只能读取
 * 此快照并提交几何，不能在渲染阶段反向修改任务、蓝图或 UI。</p>
 */
public record RtsWorldPreviewSnapshot(
        List<ModelGhost> modelGhosts,
        List<RtsRecordedGeometry.Batch> geometryBatches) {
    public static final RtsWorldPreviewSnapshot EMPTY =
            new RtsWorldPreviewSnapshot(List.of(), List.of());

    public RtsWorldPreviewSnapshot {
        modelGhosts = modelGhosts == null ? List.of() : List.copyOf(modelGhosts);
        geometryBatches = geometryBatches == null ? List.of() : List.copyOf(geometryBatches);
    }

    public record ModelGhost(
            BlockState state,
            BlockPos pos,
            float alpha,
            float scale,
            int packedLight,
            Map<Integer, Integer> tintColors) {
        public ModelGhost {
            pos = pos.immutable();
            alpha = Math.max(0.0F, Math.min(1.0F, alpha));
            scale = Math.max(0.0F, scale);
            tintColors = tintColors == null ? Map.of() : Map.copyOf(tintColors);
        }
    }
}
