package com.rtsbuilding.rtsbuilding.compat.jade;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
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
 * 用 RTS 的射线检测结果替换 Jade 默认的射线检测。
 * <p>
 * 仅在 RTS BuilderScreen 打开时介入，使用 RTS 的 128 格射线（穿透剔除）
 * 替换 Jade 默认的射线。Jade 默认使用 {@code player.blockInteractionRange()}
 * （~4.5 格）作为射程，而 RTS 相机通常离地 20+ 格，默认射程无法命中任何
 * 方块，导致 Jade 面板不可见。
 * <p>
 * 方块射线使用 RTS 剔除穿透检测（{@link RaycastHelper#raycastBlockFromCursorThroughCulling}），
 * 实体射线使用 RTS 实体检测（{@link RaycastHelper#raycastEntityFromCursor}），
 * 比较距离后选择最近的命中构建 {@link Accessor}。
 */
public class RtsJadeRayTraceCallback implements JadeRayTraceCallback {

    private static final double MAX_REACH = 128.0D;

    @Override
    public Accessor<?> onRayTrace(HitResult hitResult, Accessor<?> accessor, Accessor<?> originalAccessor) {
        Minecraft mc = Minecraft.getInstance();

        // 仅在 RTS BuilderScreen 打开时介入
        if (!(mc.screen instanceof BuilderScreen)) {
            return accessor;
        }

        Level level = mc.level;
        Entity cameraEntity = mc.getCameraEntity();
        if (level == null || cameraEntity == null) {
            return accessor;
        }

        // 使用 RTS 的射线：相机位置 + 光标方向（128 格射程，穿透剔除）
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 viewDir = RaycastHelper.computeCursorRayDirection(mc);

        // 带剔除穿透的方块射线
        BlockHitResult blockHit = RaycastHelper.raycastBlockFromCursorThroughCulling(
                mc, camPos, viewDir, MAX_REACH, false);
        // 实体射线
        Vec3 rayEnd = camPos.add(viewDir.scale(MAX_REACH));
        EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(
                mc, camPos, rayEnd, viewDir, MAX_REACH);

        // 比较距离，选最近的
        double blockDist = blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDist = entityHit != null
                ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        if (entityHit != null && entityDist <= blockDist) {
            return WailaClientRegistration.instance().entityAccessor()
                    .entity(entityHit.getEntity())
                    .hit(entityHit)
                    .build();
        }

        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);

            var builder = WailaClientRegistration.instance().blockAccessor()
                    .blockState(state)
                    .blockEntity(blockEntity)
                    .hit(blockHit)
                    .requireVerification();
            return builder.build();
        }

        // 若 RTS 射线也未命中（如看向天空），回退到 Jade 默认结果（可能为 null）
        return accessor;
    }
}
