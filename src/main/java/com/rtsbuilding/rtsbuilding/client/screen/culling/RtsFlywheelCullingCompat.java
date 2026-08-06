package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * 把 RTS 范围剔除状态同步到 Flywheel 0.6.8 已创建的方块实体实例。
 *
 * <p>本类只在范围或单块显隐状态改变时扫描受影响且已经加载的区块，不进入逐帧渲染路径，
 * 也不主动加载区块。外层不链接 Flywheel 类型；真实 API 调用放在确认模组存在后才加载的
 * 内部类中，因此未安装 Create/Flywheel 时兼容入口可以安静跳过。</p>
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
        if (level == null || !FlywheelAccess.isBackendActive()) {
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
        if (level == null || !level.hasChunkAt(pos) || !FlywheelAccess.isBackendActive()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            FlywheelAccess.sync(level, blockEntity);
        }
    }

    private static ClientLevel findActiveFlywheelLevel() {
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded(FLYWHEEL_MOD_ID)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.level;
    }

    /**
     * 只有确认 Flywheel 已安装后才解析 0.6.8 API。
     *
     * <p>0.6.8.a-14 的原 Maven 制品已经下线，因此这里用反射保持可选依赖；方法签名已由
     * Create 0.5.1.a 发布包内嵌的同版本 jar 校验。调用的仍是 Flywheel 自己的
     * {@code Backend.isOn}、{@code getBlockEntities} 与实例管理器 {@code add/remove}。</p>
     */
    private static final class FlywheelAccess {
        private static final Method BACKEND_IS_ON;
        private static final Method GET_BLOCK_ENTITIES;
        private static final Method ADD;
        private static final Method REMOVE;

        static {
            Method backendIsOn = null;
            Method getBlockEntities = null;
            Method add = null;
            Method remove = null;
            try {
                Class<?> backendClass = Class.forName("com.jozufozu.flywheel.backend.Backend");
                Class<?> dispatcherClass = Class.forName(
                        "com.jozufozu.flywheel.backend.instancing.InstancedRenderDispatcher");
                Class<?> managerClass = Class.forName(
                        "com.jozufozu.flywheel.backend.instancing.InstanceManager");
                backendIsOn = backendClass.getMethod("isOn");
                getBlockEntities = dispatcherClass.getMethod(
                        "getBlockEntities", net.minecraft.world.level.LevelAccessor.class);
                add = managerClass.getMethod("add", Object.class);
                remove = managerClass.getMethod("remove", Object.class);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // Create/Flywheel 小版本不兼容时安静回落到原生方块实体渲染路径。
            }
            BACKEND_IS_ON = backendIsOn;
            GET_BLOCK_ENTITIES = getBlockEntities;
            ADD = add;
            REMOVE = remove;
        }

        private FlywheelAccess() {
        }

        private static boolean isBackendActive() {
            if (BACKEND_IS_ON == null || GET_BLOCK_ENTITIES == null || ADD == null || REMOVE == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(BACKEND_IS_ON.invoke(null));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                return false;
            }
        }

        private static void sync(ClientLevel level, BlockEntity blockEntity) {
            try {
                Object instances = GET_BLOCK_ENTITIES.invoke(null, level);
                RtsFlywheelCullingPolicy.SyncAction action = RtsFlywheelCullingPolicy.actionFor(
                        RtsCullingClientState.shouldCull(blockEntity.getBlockPos()));
                if (action == RtsFlywheelCullingPolicy.SyncAction.REMOVE) {
                    REMOVE.invoke(instances, blockEntity);
                } else {
                    ADD.invoke(instances, blockEntity);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // 后端在状态切换期间关闭或重建时，下一次区块重建仍会经过 admission guard。
            }
        }
    }
}
