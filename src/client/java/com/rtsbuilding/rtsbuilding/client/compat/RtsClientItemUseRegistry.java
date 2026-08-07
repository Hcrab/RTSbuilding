package com.rtsbuilding.rtsbuilding.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

/**
 * RTS 模式下允许安全执行客户端物品 GUI 入口的白名单。
 *
 * <p>本类只做客户端预测并打开普通 {@link Screen}，不会直接发送原版交互包或改动服务端世界。未知物品一律不执行 客户端代码，防止武器、法杖等在 RTS
 * 点击时产生额外网络行为。Fabric 1.21.1 没有官方 Create 运行时，故不注册 Create 专属物品；如将来存在稳定官方 API，应在对应兼容层显式添加。
 */
public final class RtsClientItemUseRegistry {
  public enum Activation {
    /** 普通右键和 Shift+右键都允许客户端 use/useOn。 */
    ALWAYS,
    /** 仅 Shift+右键允许客户端 use/useOn。 */
    SHIFT_ONLY
  }

  private static final Map<ResourceLocation, Activation> POLICIES = new ConcurrentHashMap<>();

  static {
    register(ResourceLocation.withDefaultNamespace("written_book"), Activation.ALWAYS);
    register(ResourceLocation.withDefaultNamespace("writable_book"), Activation.ALWAYS);
  }

  private RtsClientItemUseRegistry() {}

  /** 注册已确认只需客户端预测的物品激活策略。 */
  public static void register(ResourceLocation itemId, Activation activation) {
    if (itemId != null && activation != null) {
      POLICIES.put(itemId, activation);
    }
  }

  /**
   * 按原版“对方块 useOn，PASS 后 use”的顺序尝试一次客户端 GUI 预测。
   *
   * @return 是否打开了不同于当前 RTS 主界面的客户端 Screen。
   */
  public static boolean tryOpenRegisteredScreen(BlockHitResult blockHit, boolean shiftDown) {
    Minecraft minecraft = Minecraft.getInstance();
    LocalPlayer player = minecraft.player;
    if (player == null || minecraft.level == null) {
      return false;
    }
    ItemStack stack = player.getMainHandItem();
    if (stack.isEmpty()) {
      return false;
    }
    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
    Activation activation = POLICIES.get(itemId);
    if (activation == null || activation == Activation.SHIFT_ONLY && !shiftDown) {
      return false;
    }

    Screen previousScreen = minecraft.screen;
    withTemporaryShift(
        player,
        shiftDown,
        () -> {
          InteractionResult useOnResult = InteractionResult.PASS;
          if (blockHit != null) {
            useOnResult =
                stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, blockHit));
          }
          if (!useOnResult.consumesAction()) {
            stack.use(minecraft.level, player, InteractionHand.MAIN_HAND);
          }
        });
    return minecraft.screen != null && minecraft.screen != previousScreen;
  }

  private static void withTemporaryShift(LocalPlayer player, boolean shiftDown, Runnable action) {
    boolean previousInput = player.input.shiftKeyDown;
    boolean previousEntity = player.isShiftKeyDown();
    player.input.shiftKeyDown = shiftDown;
    player.setShiftKeyDown(shiftDown);
    try {
      action.run();
    } finally {
      player.input.shiftKeyDown = previousInput;
      player.setShiftKeyDown(previousEntity);
    }
  }
}
