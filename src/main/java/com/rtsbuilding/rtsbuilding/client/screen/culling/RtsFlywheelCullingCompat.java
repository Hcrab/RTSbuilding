package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.ModList;

/**
 * 把范围剔除状态同步到 Flywheel 已创建的方块实体 Visual。
 *
 * <p>本类只在范围或单块显隐状态发生变化时扫描受影响的已加载区块，不参与逐帧渲染。
 * 外层不直接链接 Flywheel 类型；真正的 API 调用封装在延迟加载的内部类里，
 * 因而未安装 Flywheel 时不会把它变成硬依赖。</p>
 */
public final class RtsFlywheelCullingCompat {
    private static final String FLYWHEEL_MOD_ID = "flywheel";

    private RtsFlywheelCullingCompat() {
    }

    public static void syncBox(RtsCullingBox box) {
        if (box == null) {
            return;
        }
        ClientLevel level = findActiveFlywheelLevel();
        if (level == null || !FlywheelAccess.supportsVisualization(level)) {
            return;
        }

        int minChunkX = box.min().getX() >> 4;
        int maxChunkX = box.max().getX() >> 4;
        int minChunkZ = box.min().getZ() >> 4;
        int maxChunkZ = box.max().getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity != null && box.contains(blockEntity.getBlockPos())) {
                        FlywheelAccess.sync(level, blockEntity);
                    }
                }
            }
        }
    }

    public static void syncBlock(BlockPos pos) {
        if (pos == null) {
            return;
        }
        ClientLevel level = findActiveFlywheelLevel();
        if (level == null || !level.hasChunkAt(pos) || !FlywheelAccess.supportsVisualization(level)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            FlywheelAccess.sync(level, blockEntity);
        }
    }

    /** 只有模组列表、客户端实例和客户端世界都已就绪时，才开放 Flywheel API 访问。 */
    private static ClientLevel findActiveFlywheelLevel() {
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded(FLYWHEEL_MOD_ID)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.level;
    }

    /** 只有确认 Flywheel 已安装后才会加载该内部类及其 API 符号。 */
    private static final class FlywheelAccess {
        private FlywheelAccess() {
        }

        private static boolean supportsVisualization(ClientLevel level) {
            return dev.engine_room.flywheel.api.visualization.VisualizationManager.supportsVisualization(level);
        }

        private static void sync(ClientLevel level, BlockEntity blockEntity) {
            var visuals = dev.engine_room.flywheel.api.visualization.VisualizationManager
                    .getOrThrow(level)
                    .blockEntities();
            if (RtsCullingClientState.shouldCull(blockEntity.getBlockPos())) {
                visuals.queueRemove(blockEntity);
            } else {
                visuals.queueAdd(blockEntity);
            }
        }
    }
}
