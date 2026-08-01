package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * RTS 自有菜单类型注册表。
 *
 * <p>这里只登记需要自定义客户端槽位布局的菜单，不承载任何界面渲染或业务逻辑。
 * 合成终端使用独立菜单类型后，客户端和服务端会创建同一种菜单，避免继续借用原版
 * {@code MenuType.CRAFTING} 后只能得到原版固定槽位坐标的问题。</p>
 */
public final class RtsMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, RtsbuildingMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RtsCraftTerminalMenu>> RTS_CRAFT_TERMINAL =
            MENU_TYPES.register("rts_craft_terminal",
                    () -> new MenuType<>(RtsCraftTerminalMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }

    private RtsMenuTypes() {
    }
}
