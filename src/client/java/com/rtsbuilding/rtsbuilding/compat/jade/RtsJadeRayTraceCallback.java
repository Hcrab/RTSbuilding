package com.rtsbuilding.rtsbuilding.compat.jade;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.Accessor;
import snownee.jade.api.callback.JadeRayTraceCallback;
import snownee.jade.impl.WailaClientRegistration;

/**
 * 用 RTS 光标射线替换 Jade 默认的玩家视线射线。
 *
 * <p>RTS 相机通常离地超过原版交互距离，因此 Jade 默认射线无法命中。这里复用 RTS 自己的
 * 128 格剔除穿透规则，让高亮、实际操作和 Jade 看到同一个目标。
 */
public final class RtsJadeRayTraceCallback implements JadeRayTraceCallback {
    private static final double MAX_REACH = 128.0D;

    @Override
    public Accessor<?> onRayTrace(HitResult hitResult, Accessor<?> accessor, Accessor<?> originalAccessor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof BuilderScreen)) {
            return accessor;
        }
        if (RtsClientUiStateStore.isJadePanelHidden()) {
            return null;
        }

        Level level = minecraft.level;
        Entity cameraEntity = minecraft.getCameraEntity();
        if (level == null || cameraEntity == null) {
            return accessor;
        }

        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = RaycastHelper.computeCursorRayDirection(minecraft);
        BlockHitResult blockHit = RaycastHelper.raycastBlockFromCursorThroughCulling(
                minecraft, cameraPosition, direction, MAX_REACH, false);
        EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(
                minecraft,
                cameraPosition,
                cameraPosition.add(direction.scale(MAX_REACH)),
                direction,
                MAX_REACH);

        double blockDistance = blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                ? cameraPosition.distanceToSqr(blockHit.getLocation())
                : Double.MAX_VALUE;
        double entityDistance = entityHit != null
                ? cameraPosition.distanceToSqr(entityHit.getLocation())
                : Double.MAX_VALUE;

        if (entityHit != null && entityDistance <= blockDistance) {
            return WailaClientRegistration.instance().entityAccessor()
                    .entity(entityHit.getEntity())
                    .hit(entityHit)
                    .requireVerification()
                    .build();
        }
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos position = blockHit.getBlockPos();
            BlockState state = level.getBlockState(position);
            BlockEntity blockEntity = level.getBlockEntity(position);
            return WailaClientRegistration.instance().blockAccessor()
                    .blockState(state)
                    .blockEntity(blockEntity)
                    .hit(blockHit)
                    .requireVerification()
                    .build();
        }
        return accessor;
    }
}
