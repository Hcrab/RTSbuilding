package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * RTS 自有菜单类型注册表。
 *
 * <p>这里只登记需要自定义客户端槽位布局的菜单，不拥有界面渲染或业务逻辑。
 * 合成终端不能继续复用原版 {@code MenuType.CRAFTING}，否则客户端必然重建一份
 * 固定坐标的旧工作台菜单，并与新版一体化界面的槽位错位。</p>
 */
public final class RtsMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, RtsbuildingMod.MODID);

    public static final RegistryObject<MenuType<RtsCraftTerminalMenu>> RTS_CRAFT_TERMINAL =
            MENU_TYPES.register("rts_craft_terminal",
                    () -> IForgeMenuType.create((containerId, inventory, ignored) ->
                            new RtsCraftTerminalMenu(containerId, inventory)));

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }

    private RtsMenuTypes() {
    }
}
