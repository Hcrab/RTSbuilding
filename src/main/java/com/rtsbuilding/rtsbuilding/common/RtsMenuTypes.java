package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistryEntry;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsSimpleRegistry;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * RTS 自有菜单类型注册表。
 *
 * <p>合成终端必须使用独立 MenuType，才能让客户端和服务端创建同一套可滚动布局与固定槽位契约；本类不承载 渲染或业务逻辑。
 */
public final class RtsMenuTypes {
  private static final RtsSimpleRegistry<MenuType<?>> MENU_TYPES =
      new RtsSimpleRegistry<>(BuiltInRegistries.MENU, RtsbuildingMod.MODID);

  public static final RtsRegistryEntry<MenuType<?>, MenuType<RtsCraftTerminalMenu>>
      RTS_CRAFT_TERMINAL =
          MENU_TYPES.register(
              "rts_craft_terminal",
              () -> new MenuType<>(RtsCraftTerminalMenu::new, FeatureFlags.DEFAULT_FLAGS));

  private RtsMenuTypes() {}

  /** 显式保留与其他公共注册表一致的初始化入口。 */
  public static void register() {
    // 静态字段由 RtsSimpleRegistry 在模组初始化阶段注册。
  }
}
