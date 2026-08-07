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
 * <p>本类不负责执行交互，也不改变服务端范围/权限判断。它只让仍然读取 {@link Minecraft#hitResult} 的第三方客户端预览看见 RTS 光标，例如机械动力的
 * 强力胶框选预览。离开 BuilderScreen 后原版 GameRenderer 会继续正常维护该字段。
 */
public final class RtsVanillaCursorHitBridge {
  private static final double MISS_DISTANCE = 128.0D;

  private RtsVanillaCursorHitBridge() {}

  /**
   * 返回当前 RTS 自由光标实际命中的方块。
   *
   * <p>这是给少数仍然自行从玩家视线发射射线的第三方预览使用的窄入口； 非 BuilderScreen、鼠标位于 UI 上或没有命中真实方块时一律返回 {@code null}。
   */
  public static BlockHitResult currentRtsBlockHit() {
    Minecraft minecraft = Minecraft.getInstance();
    if (!(minecraft.screen instanceof BuilderScreen screen)
        || minecraft.level == null
        || minecraft.getCameraEntity() == null) {
      return null;
    }

    double mouseX = screen.getCurrentMouseX();
    double mouseY = screen.getCurrentMouseY();
    if (!screen.isWorldArea(mouseX, mouseY)) {
      return null;
    }

    BlockHitResult hit = screen.pickBlockHit();
    // 形状工具可在空气中合成平面命中；第三方原版交互只能接收真实方块命中。
    if (hit == null || minecraft.level.getBlockState(hit.getBlockPos()).isAir()) {
      return null;
    }
    return hit;
  }

  public static void publish(BuilderScreen screen) {
    if (screen == null) {
      return;
    }
    Minecraft minecraft = screen.getMinecraft();
    if (minecraft == null || minecraft.level == null || minecraft.getCameraEntity() == null) {
      return;
    }

    BlockHitResult hit = currentRtsBlockHit();
    if (hit != null) {
      minecraft.hitResult = hit;
      return;
    }

    Vec3 origin = screen.currentRayOrigin();
    Vec3 direction = screen.computeCursorRayDirection();
    if (origin == null || direction == null || direction.lengthSqr() < 1.0E-9D) {
      return;
    }
    Vec3 missLocation = origin.add(direction.normalize().scale(MISS_DISTANCE));
    Direction missFace = Direction.getNearest(-direction.x, -direction.y, -direction.z);
    minecraft.hitResult =
        BlockHitResult.miss(missLocation, missFace, BlockPos.containing(missLocation));
  }
}
