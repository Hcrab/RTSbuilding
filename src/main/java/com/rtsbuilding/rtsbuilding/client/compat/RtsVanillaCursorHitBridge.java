package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 把 RTS 自由鼠标的方块命中结果发布到原版客户端命中槽。
 *
 * <p>本类只为读取 {@link Minecraft#hitResult} 的第三方客户端预览提供兼容数据，
 * 不执行交互，也不改变服务端范围和权限判断。离开 BuilderScreen 后，原版渲染器
 * 会继续维护该字段。</p>
 */
public final class RtsVanillaCursorHitBridge {
    private static final double MISS_DISTANCE = 128.0D;

    private RtsVanillaCursorHitBridge() {
    }

    /** 返回当前 RTS 自由光标实际命中的非空气方块。 */
    public static BlockHitResult currentRtsBlockHit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof BuilderScreen screen)
                || minecraft.level == null
                || minecraft.getCameraEntity() == null) {
            return null;
        }
        double mouseX = screen.getCurrentMouseX();
        double mouseY = screen.getCurrentMouseY();
        if (!screen.isWorldArea(mouseX, mouseY)) return null;
        BlockHitResult hit = screen.pickBlockHit();
        if (hit == null || minecraft.level.getBlockState(hit.getBlockPos()).isAir()) return null;
        return hit;
    }

    public static void publish(BuilderScreen screen) {
        if (screen == null) return;
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft == null || minecraft.level == null || minecraft.getCameraEntity() == null) return;
        BlockHitResult hit = currentRtsBlockHit();
        if (hit != null) {
            minecraft.hitResult = hit;
            return;
        }
        Vec3 origin = screen.currentRayOrigin();
        Vec3 direction = screen.computeCursorRayDirection();
        if (origin == null || direction == null || direction.lengthSqr() < 1.0E-9D) return;
        Vec3 missLocation = origin.add(direction.normalize().scale(MISS_DISTANCE));
        Direction missFace = Direction.getNearest(-direction.x, -direction.y, -direction.z);
        minecraft.hitResult = BlockHitResult.miss(
                missLocation, missFace,
                new BlockPos(missLocation.x, missLocation.y, missLocation.z));
    }
}
